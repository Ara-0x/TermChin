package ir.courseplanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.courseplanner.app.BuildConfig
import ir.courseplanner.app.data.importer.CourseImporter
import ir.courseplanner.app.data.importer.ImportItem
import ir.courseplanner.app.data.importer.ImportResult
import ir.courseplanner.app.data.importer.MhtHtmlExtractor
import ir.courseplanner.app.data.importer.PooyaHtmlParser
import ir.courseplanner.app.data.model.AcademicTermWithCourses
import ir.courseplanner.app.data.model.AttendanceRecord
import ir.courseplanner.app.data.model.AttendanceStats
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Conflict
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.DataCounts
import ir.courseplanner.app.data.model.DataDeletionSelection
import ir.courseplanner.app.data.model.DocumentCategory
import ir.courseplanner.app.data.model.DocumentWithCourse
import ir.courseplanner.app.data.model.PassedCourse
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.TermCourseStatus
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.data.model.normalizeCode
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.preferences.ThemeMode
import ir.courseplanner.app.data.preferences.TimetableDensity
import ir.courseplanner.app.data.preferences.UserPreferences
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.engine.CourseSuggestionRanker
import ir.courseplanner.app.engine.OptimizationPreference
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.engine.ScheduleMetrics
import ir.courseplanner.app.engine.ScoredSchedule
import ir.courseplanner.app.update.ApkDownloader
import ir.courseplanner.app.update.ApkInspector
import ir.courseplanner.app.update.ApkVerifier
import ir.courseplanner.app.update.AvailableUpdate
import ir.courseplanner.app.update.DownloadException
import ir.courseplanner.app.update.UpdateChecker
import ir.courseplanner.app.update.UpdateDownloadState
import ir.courseplanner.app.update.UpdateFailure
import ir.courseplanner.app.update.UpdatePromptGate
import ir.courseplanner.app.util.JalaliDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

enum class AppDestination {
    HOME,
    COURSES,
    SCHEDULE,
    DOCUMENTS,
    SETTINGS,
    /** Sub-screen of Courses: smart discovery + add course (not a bottom-nav tab). */
    ADD_COURSE,
    /** Sub-screen of Courses: the student's academic record (passed courses). */
    PASSED_COURSES,
    /** Full page: choose the final status of every course and archive the term. */
    END_TERM,
    /** Full page: archived terms (name, courses, statuses, grades, credits). */
    ACADEMIC_RECORDS,
    /** Full page: recorded absences per course, editable in place. */
    ATTENDANCE
}

data class ManualSessionInput(
    val dayOfWeek: Int = 0,
    val startTime: String = "08:00",
    val endTime: String = "10:00",
    val location: String = "",
    val weekType: WeekType = WeekType.EVERY_WEEK
)

/**
 * Dialog/form input → persistable session. [sectionId] is a placeholder (0) for
 * new groups: the repository assigns the real id inside its transaction.
 *
 * Single definition shared by the manual dialogs and the ViewModel, so the
 * sessions that get validated are byte-for-byte the sessions that get stored.
 */
fun ManualSessionInput.toClassSession(sectionId: Long = 0L): ClassSession = ClassSession(
    sectionId = sectionId,
    dayOfWeek = dayOfWeek,
    startTime = startTime.trim(),
    endTime = endTime.trim(),
    location = location.trim(),
    weekType = weekType
)

data class GenerationState(
    val isGenerated: Boolean = false,
    val combinations: List<ScoredSchedule> = emptyList(),
    val rawCombinations: List<List<SectionWithDetails>> = emptyList(),
    val currentIndex: Int = 0,
    val message: String? = null,
    /** Selected courses with zero usable sections (reported, never silently dropped). */
    val skippedCourses: List<String> = emptyList(),
    /** True when the search hit its safety cap (best-so-far is still shown). */
    val truncated: Boolean = false
) {
    /**
     * True only when the generated result covers EVERY course selected for the
     * generator. An incomplete result stays visible (with the names in
     * [skippedCourses]) but must not be applied over the current program, which
     * is why the Apply button is disabled and `applyCurrentGeneratedSchedule()`
     * refuses the operation.
     */
    val isComplete: Boolean get() = skippedCourses.isEmpty()

    /** The current candidate may replace the user's program. */
    val canApplyCurrent: Boolean get() = isGenerated && combinations.isNotEmpty() && isComplete
}

/** Form state for one course on the end-term screen (survives rotation). */
data class TermCourseDraft(
    val status: TermCourseStatus? = null,
    val grade: String = ""
)

/** One current-term row shown in the end-term form and the final summary. */
data class EndTermCourseItem(
    val courseId: Long,
    val code: String,
    val name: String,
    val credits: Int,
    val sectionCode: String,
    val instructor: String,
    val attendance: AttendanceStats,
    val status: TermCourseStatus?,
    val grade: String
)

data class EndTermUiState(
    val courses: List<EndTermCourseItem> = emptyList(),
    val isSubmitting: Boolean = false,
    val showSummary: Boolean = false,
    val archivedTermId: Long? = null,
    val archivedTermName: String = "",
    val archivedCourseCount: Int = 0,
    val archivedPassedCount: Int = 0,
    val archivedPassedCredits: Int = 0,
    val error: String? = null
) {
    /** Every course has an explicit choice; [TermCourseStatus.INCOMPLETE] counts. */
    val isComplete: Boolean get() = courses.isNotEmpty() && courses.all { it.status != null }

    val passedCount: Int get() = courses.count { it.status == TermCourseStatus.PASSED }
    val failedCount: Int get() = courses.count { it.status == TermCourseStatus.FAILED }
    val withdrawnCount: Int get() = courses.count { it.status == TermCourseStatus.WITHDRAWN }
    val incompleteCount: Int get() = courses.count { it.status == TermCourseStatus.INCOMPLETE }

    val passedCredits: Int
        get() = courses.filter { it.status == TermCourseStatus.PASSED }.sumOf { it.credits }

    val totalCredits: Int get() = courses.sumOf { it.credits }

    fun countOf(status: TermCourseStatus): Int = courses.count { it.status == status }
}

data class AcademicRecordsSummary(
    val termCount: Int = 0,
    val courseCount: Int = 0,
    /** Distinct passed courses, so a re-take never double-counts its credits. */
    val passedCredits: Int = 0
)

enum class CourseStatusFilter(val titleFa: String) {
    // There is intentionally no «واحدهای من» tab: every enrolled course is
    // already inside «دروس من», so a third tab duplicated the same list and
    // made the course page read as a form instead of a list.
    MY_COURSES("دروس من"),
    ALL("همه دروس")
}

enum class CourseSortOrder(val titleFa: String) {
    NAME("نام درس"),
    CREDITS_DESC("بیشترین واحد"),
    CODE("کد درس")
}

@HiltViewModel
class CoursePlannerViewModel @Inject constructor(
    application: Application,
    private val repository: CourseRepository,
    private val preferencesManager: PreferencesManager,
    // Defaults to the real updater because most unit tests construct the
    // ViewModel by hand; Hilt resolves the binding in production.
    private val updateChecker: UpdateChecker = UpdateChecker(),
    private val apkDownloader: ApkDownloader = ApkDownloader(),
    // Tests override this to return canned metadata; production inspects the
    // archive with the platform PackageManager (ApkVerifier.read).
    private val apkInspector: ApkInspector = ApkInspector()
) : AndroidViewModel(application) {

    val userPreferences: StateFlow<UserPreferences> = preferencesManager.preferences

    // NOTE: there is deliberately NO startup "cleanup" here. Until v2.5.0 this
    // class ran `repository.clearAllData()` whenever a DataStore marker
    // ("release clean done") was missing — i.e. one lost/never-written flag was
    // enough to erase an existing student's courses, groups, sessions,
    // enrollments and documents on the next launch. The marker existed only to
    // drop sample/demo courses that an early development build shipped; the
    // sample catalog is now loaded explicitly by the user ("بارگذاری نمونه" in
    // Settings), so no automatic deletion may ever run on startup again.

    private val _currentDestination = MutableStateFlow(AppDestination.HOME)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDepartment = MutableStateFlow<String?>(null)
    val selectedDepartment: StateFlow<String?> = _selectedDepartment.asStateFlow()

    // Default is "my courses": the portal catalog stays hidden until the user
    // adds courses by code, instead of flooding the list with 200+ rows.
    private val _statusFilter = MutableStateFlow(CourseStatusFilter.MY_COURSES)
    val statusFilter: StateFlow<CourseStatusFilter> = _statusFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(CourseSortOrder.NAME)
    val sortOrder: StateFlow<CourseSortOrder> = _sortOrder.asStateFlow()

    // Extra catalog filters (all default to "no filtering").
    private val _unitsFilter = MutableStateFlow(CourseUnitsFilter.ALL)
    val unitsFilter: StateFlow<CourseUnitsFilter> = _unitsFilter.asStateFlow()

    private val _degreeFilter = MutableStateFlow(CourseDegreeFilter.ALL)
    val degreeFilter: StateFlow<CourseDegreeFilter> = _degreeFilter.asStateFlow()

    /** Day of week 0..5 (Sat..Thu), or null for "any day". */
    private val _dayFilter = MutableStateFlow<Int?>(null)
    val dayFilter: StateFlow<Int?> = _dayFilter.asStateFlow()

    private val _onlyWithSessions = MutableStateFlow(false)
    val onlyWithSessions: StateFlow<Boolean> = _onlyWithSessions.asStateFlow()

    private val _optimizationPreference = MutableStateFlow(OptimizationPreference.BALANCED)
    val optimizationPreference: StateFlow<OptimizationPreference> = _optimizationPreference.asStateFlow()

    val enrolledSections: StateFlow<List<SectionWithDetails>> = repository.enrolledSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSections: StateFlow<List<SectionWithDetails>> = repository.allSectionsWithDetails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coursesWithSections: StateFlow<List<CourseWithSections>> = repository.coursesWithSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCourses: StateFlow<List<Course>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<CourseDocument>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All documents paired with their Course entity
    val documentsWithCourse: StateFlow<List<DocumentWithCourse>> = combine(
        allDocuments,
        allCourses
    ) { docs, courses ->
        val courseMap = courses.associateBy { it.id }
        docs.mapNotNull { doc ->
            val course = courseMap[doc.courseId]
            if (course != null) {
                DocumentWithCourse(document = doc, course = course)
            } else {
                null
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Precomputed Courses-screen lookups: the Lazy list renders dozens of rows,
    // so per-row full-list scans (filter/count over ALL sections or documents)
    // are replaced by these O(1) maps derived once per data change.
    val sectionsByCourse: StateFlow<Map<Long, List<SectionWithDetails>>> = allSections
        .map { sections -> sections.groupBy { it.course.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val documentCountByCourse: StateFlow<Map<Long, Int>> = documentsWithCourse
        .map { docs -> docs.groupingBy { it.course.id }.eachCount() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val departmentNames: StateFlow<List<String>> = coursesWithSections
        .map { courses ->
            listOf("همه") + courses.map { it.course.department }.filter { it.isNotBlank() }.distinct()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("همه"))

    /** Portal-catalog courses not yet added to \"my courses\" (empty-state hint). */
    val catalogOnlyCourseCount: StateFlow<Int> = coursesWithSections
        .map { courses ->
            courses.count { cws ->
                !cws.course.isSelectedForGeneration && cws.sections.none { it.section.isEnrolled }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Document Filters
    private val _selectedDocCourseId = MutableStateFlow<Long?>(null)
    val selectedDocCourseId: StateFlow<Long?> = _selectedDocCourseId.asStateFlow()

    private val _selectedDocCategory = MutableStateFlow<DocumentCategory?>(null)
    val selectedDocCategory: StateFlow<DocumentCategory?> = _selectedDocCategory.asStateFlow()

    private val _docSearchQuery = MutableStateFlow("")
    val docSearchQuery: StateFlow<String> = _docSearchQuery.asStateFlow()

    private val _onlyBookmarkedDocs = MutableStateFlow(false)
    val onlyBookmarkedDocs: StateFlow<Boolean> = _onlyBookmarkedDocs.asStateFlow()

    val filteredDocuments: StateFlow<List<DocumentWithCourse>> = combine(
        documentsWithCourse,
        _selectedDocCourseId,
        _selectedDocCategory,
        _docSearchQuery,
        _onlyBookmarkedDocs
    ) { list, courseId, category, query, onlyBookmarked ->
        list.filter { dwc ->
            val matchesCourse = courseId == null || dwc.course.id == courseId
            val matchesCategory = category == null || dwc.document.category == category
            val matchesBookmarked = !onlyBookmarked || dwc.document.isBookmarked
            val matchesQuery = query.isBlank() ||
                dwc.document.title.contains(query, ignoreCase = true) ||
                dwc.document.contentNotes.contains(query, ignoreCase = true) ||
                (dwc.document.fileName?.contains(query, ignoreCase = true) == true) ||
                dwc.course.name.contains(query, ignoreCase = true) ||
                dwc.course.code.contains(query, ignoreCase = true)

            matchesCourse && matchesCategory && matchesBookmarked && matchesQuery
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered courses: text/department/status/sort first, then the extra
    // catalog filters (units, degree, day, has-sessions). Two typed stages
    // keep the combine overloads simple and null-safe.
    private val baseFilteredCourses: StateFlow<List<CourseWithSections>> = combine(
        coursesWithSections,
        _searchQuery,
        _selectedDepartment,
        _statusFilter,
        _sortOrder
    ) { courses, query, dept, status, sort ->
        val filtered = courses.filter { cws ->
            val matchQuery = query.isBlank() ||
                cws.course.name.contains(query, ignoreCase = true) ||
                cws.course.code.contains(query, ignoreCase = true) ||
                cws.sections.any { it.section.instructor.contains(query, ignoreCase = true) }

            val matchDept = dept == null || cws.course.department.equals(dept, ignoreCase = true)

            val matchStatus = when (status) {
                CourseStatusFilter.ALL -> true
                CourseStatusFilter.MY_COURSES ->
                    cws.course.isSelectedForGeneration ||
                        cws.sections.any { it.section.isEnrolled }
            }

            matchQuery && matchDept && matchStatus
        }

        when (sort) {
            CourseSortOrder.NAME -> filtered.sortedBy { it.course.name }
            CourseSortOrder.CREDITS_DESC -> filtered.sortedByDescending { it.course.credits }
            CourseSortOrder.CODE -> filtered.sortedBy { it.course.code }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCourses: StateFlow<List<CourseWithSections>> = combine(
        baseFilteredCourses,
        _unitsFilter,
        _degreeFilter,
        _dayFilter,
        _onlyWithSessions
    ) { courses, units, degree, day, withSessions ->
        courses.filter { cws ->
            units.matches(cws.course.credits) &&
                degree.matches(cws.course.degree) &&
                (day == null || courseHasSessionOnDay(cws, day)) &&
                (!withSessions || courseHasAnySession(cws))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enrolled sections conflicts (definite only — exam same-day unknowns are warnings).
    val enrolledConflicts: StateFlow<List<Conflict>> = enrolledSections
        .map { ScheduleEngine.findAllConflicts(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Same exam day with incomplete times — shown as warnings, never blocking. */
    val enrolledExamWarnings: StateFlow<List<Conflict>> = enrolledSections
        .map { ScheduleEngine.findExamWarnings(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enrolled metrics
    val enrolledMetrics: StateFlow<ScheduleMetrics> = enrolledSections
        .map { ScheduleEngine.computeMetrics(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleMetrics(0, 0, 0, 0f, 0))

    // ---------------------------------------------------------------------
    // Passed courses (academic record)
    // ---------------------------------------------------------------------

    val passedCourses: StateFlow<List<PassedCourse>> = repository.passedCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Normalized codes, used for the non-blocking «قبلاً پاس شده» badge. */
    val passedCourseCodes: StateFlow<Set<String>> = passedCourses
        .map { list -> list.map { normalizeCode(it.code) }.toSet() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val passedCourseCount: StateFlow<Int> = passedCourses
        .map { it.size }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val passedCourseCredits: StateFlow<Int> = passedCourses
        .map { list -> list.sumOf { it.credits } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ---------------------------------------------------------------------
    // Attendance
    // ---------------------------------------------------------------------

    val attendanceRecords: StateFlow<List<AttendanceRecord>> = repository.attendanceRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Per-group aggregate for the attendance sheet/summary. */
    val attendanceBySection: StateFlow<Map<Long, AttendanceStats>> = attendanceRecords
        .map { records -> records.groupBy { it.sectionId }.mapValues { AttendanceStats.from(it.value) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Per-course aggregate: a group's records are rolled up to its course so
     * the dashboard can warn about the limit at the level the student thinks
     * in (the course), while the sheet still writes against the group.
     */
    val attendanceByCourse: StateFlow<Map<Long, AttendanceStats>> = combine(
        attendanceRecords,
        allSections
    ) { records, sections ->
        val courseBySection = sections.associate { it.section.id to it.course.id }
        val grouped = mutableMapOf<Long, AttendanceStats>()
        for (record in records) {
            val courseId = courseBySection[record.sectionId] ?: continue
            grouped[courseId] = (grouped[courseId] ?: AttendanceStats()) + statsOf(record)
        }
        grouped
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Live counts for the advanced-deletion dialog. */
    val dataCounts: StateFlow<DataCounts> = repository.dataCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataCounts())

    // ---------------------------------------------------------------------
    // End of term / academic records
    // ---------------------------------------------------------------------

    /** Number of courses in the current term (selected for the generator OR enrolled). */
    val currentTermCourseCount: StateFlow<Int> = coursesWithSections
        .map { courses ->
            courses.count { cws ->
                cws.course.isSelectedForGeneration || cws.sections.any { it.section.isEnrolled }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val academicTerms: StateFlow<List<AcademicTermWithCourses>> = repository.academicTerms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Overall record facts. Credits are summed over DISTINCT normalized codes,
     * so passing the same course again in a later term never inflates the total.
     */
    val academicRecordsSummary: StateFlow<AcademicRecordsSummary> = academicTerms
        .map { terms ->
            val archivedCourses = terms.flatMap { it.courses }.map { it.course }
            val distinctPassed = archivedCourses
                .filter { it.status == TermCourseStatus.PASSED }
                .distinctBy { normalizeCode(it.code) }
            AcademicRecordsSummary(
                termCount = terms.size,
                courseCount = archivedCourses.size,
                passedCredits = distinctPassed.sumOf { it.credits }
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AcademicRecordsSummary()
        )

    private val _endTermDrafts = MutableStateFlow<Map<Long, TermCourseDraft>>(emptyMap())

    private data class EndTermFlags(
        val isSubmitting: Boolean = false,
        val showSummary: Boolean = false,
        val archivedTermId: Long? = null,
        val archivedTermName: String = "",
        val archivedCourseCount: Int = 0,
        val archivedPassedCount: Int = 0,
        val archivedPassedCredits: Int = 0,
        val error: String? = null
    )

    private val _endTermFlags = MutableStateFlow(EndTermFlags())

    private val _showStartNewTermDialog = MutableStateFlow(false)
    val showStartNewTermDialog: StateFlow<Boolean> = _showStartNewTermDialog.asStateFlow()

    /**
     * The end-term form: live current-term rows + the user's draft choices.
     * Draft state lives in the ViewModel so rotating the screen (or a data
     * refresh from another tab) cannot silently discard the student's work.
     */
    val endTermState: StateFlow<EndTermUiState> = combine(
        coursesWithSections,
        attendanceByCourse,
        _endTermDrafts,
        _endTermFlags
    ) { courses, attendance, drafts, flags ->
        val items = courses.filter { cws ->
            cws.course.isSelectedForGeneration || cws.sections.any { it.section.isEnrolled }
        }.map { cws ->
            val enrolled = cws.sections.firstOrNull { it.section.isEnrolled }
            val draft = drafts[cws.course.id] ?: TermCourseDraft()
            EndTermCourseItem(
                courseId = cws.course.id,
                code = cws.course.code,
                name = cws.course.name,
                credits = cws.course.credits,
                sectionCode = enrolled?.section?.sectionCode.orEmpty(),
                instructor = enrolled?.section?.instructor.orEmpty(),
                attendance = attendance[cws.course.id] ?: AttendanceStats(),
                status = draft.status,
                grade = draft.grade
            )
        }.sortedBy { it.name }
        EndTermUiState(
            courses = items,
            isSubmitting = flags.isSubmitting,
            showSummary = flags.showSummary,
            archivedTermId = flags.archivedTermId,
            archivedTermName = flags.archivedTermName,
            archivedCourseCount = flags.archivedCourseCount,
            archivedPassedCount = flags.archivedPassedCount,
            archivedPassedCredits = flags.archivedPassedCredits,
            error = flags.error
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EndTermUiState())

    private fun statsOf(record: AttendanceRecord): AttendanceStats = when (record.status) {
        AttendanceStatus.PRESENT -> AttendanceStats(present = 1)
        AttendanceStatus.ABSENT -> AttendanceStats(absent = 1)
        AttendanceStatus.LATE -> AttendanceStats(late = 1)
        AttendanceStatus.EXCUSED -> AttendanceStats(excused = 1)
    }

    /**
     * Wall-clock tick driving the Home dashboard's time-sensitive UI
     * (ongoing/upcoming status, countdowns, day rollover). Emits the current
     * epoch millis every [DASHBOARD_TICK_MS]; minute-granular display derived
     * from it changes at most once a minute, so recomposition stays cheap.
     * WhileSubscribed: no collectors (background) → the loop stops, no leaked
     * jobs, no competing timers; Foreground return restarts it with fresh time.
     */
    val dashboardTick: StateFlow<Long> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(DASHBOARD_TICK_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    // Schedule Generator State
    private val _generationState = MutableStateFlow(GenerationState())
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    // Status Messages
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isErrorMessage = MutableStateFlow(false)
    val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

    /**
     * True while the schedule generator is searching. The search walks a large
     * combination space on slower devices, so the screen has to say "working"
     * (and refuse a second tap) instead of looking frozen.
     */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private fun showInfo(message: String) {
        _isErrorMessage.value = false
        _userMessage.value = message
    }

    private fun showError(message: String) {
        _isErrorMessage.value = true
        _userMessage.value = message
    }

    /**
     * Runs a repository write inside a structured, user-visible error boundary:
     * - thrown exceptions (disk full, IO, constraint races) become a visible
     *   error snackbar instead of a silently swallowed coroutine failure;
     * - CancellationException is never caught (structured concurrency stays intact).
     */
    private inline fun launchDbWrite(
        crossinline onErrorMessage: () -> String = { "خطا در ذخیره اطلاعات؛ لطفاً دوباره تلاش کنید." },
        crossinline block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("CoursePlannerVM", "DB write failed", e)
                showError(onErrorMessage())
            }
        }
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onDepartmentSelected(dept: String?) {
        _selectedDepartment.value = dept
    }

    fun setStatusFilter(filter: CourseStatusFilter) {
        _statusFilter.value = filter
    }

    fun setSortOrder(order: CourseSortOrder) {
        _sortOrder.value = order
    }

    fun setUnitsFilter(filter: CourseUnitsFilter) {
        _unitsFilter.value = filter
    }

    fun setDegreeFilter(filter: CourseDegreeFilter) {
        _degreeFilter.value = filter
    }

    fun setDayFilter(dayOfWeek: Int?) {
        _dayFilter.value = dayOfWeek?.takeIf { it in 0..5 }
    }

    fun setOnlyWithSessions(only: Boolean) {
        _onlyWithSessions.value = only
    }

    // Add-course discovery screen state: its own local search keeps the
    // Courses tab search untouched, but both read the same Room sources.
    private val _addCourseQuery = MutableStateFlow("")
    val addCourseQuery: StateFlow<String> = _addCourseQuery.asStateFlow()

    fun onAddCourseQueryChange(query: String) {
        _addCourseQuery.value = query
    }

    /** Courses → smart discovery sub-screen; back returns to «دروس من». */
    fun openAddCourse() {
        _addCourseQuery.value = ""
        _currentDestination.value = AppDestination.ADD_COURSE
    }

    /**
     * Ranked catalog for the discovery screen, computed off the main thread:
     * real groups + enrolled program + optimization preference. Query
     * filtering is applied on top so the full result stays reachable.
     */
    private val rankedCatalogSuggestions: StateFlow<List<CourseSuggestionRanker.CourseSuggestion>> =
        combine(
            coursesWithSections,
            sectionsByCourse,
            enrolledSections,
            _optimizationPreference
        ) { courses, byCourse, enrolled, preference ->
            CourseSuggestionRanker.rank(courses, byCourse, enrolled, preference)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ranked catalog filtered by the discovery search (blank = everything). */
    val addCourseSuggestions: StateFlow<List<CourseSuggestionRanker.CourseSuggestion>> =
        combine(rankedCatalogSuggestions, _addCourseQuery) { ranked, query ->
            if (query.isBlank()) ranked
            else ranked.filter { suggestion ->
                coursesWithSections.value.firstOrNull { it.course.id == suggestion.course.id }
                    ?.let { CourseSuggestionRanker.matchesQuery(it, query) } == true
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Courses the student can still add (header count), excluding «دروس من». */
    val addableCourseCount: StateFlow<Int> = rankedCatalogSuggestions
        .map { ranked -> ranked.count { !it.alreadyAdded } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Number of non-default catalog filters (for the filter-panel badge). */
    fun activeFilterCount(): Int {
        var count = 0
        if (_selectedDepartment.value != null) count++
        if (_statusFilter.value != CourseStatusFilter.MY_COURSES) count++
        if (_unitsFilter.value != CourseUnitsFilter.ALL) count++
        if (_degreeFilter.value != CourseDegreeFilter.ALL) count++
        if (_dayFilter.value != null) count++
        if (_onlyWithSessions.value) count++
        return count
    }

    /** Resets every course-list filter (including search) to its default. */
    fun clearCourseFilters() {
        _searchQuery.value = ""
        _selectedDepartment.value = null
        _statusFilter.value = CourseStatusFilter.MY_COURSES
        _unitsFilter.value = CourseUnitsFilter.ALL
        _degreeFilter.value = CourseDegreeFilter.ALL
        _dayFilter.value = null
        _onlyWithSessions.value = false
    }

    fun setColorTheme(theme: AppColorTheme) {
        preferencesManager.setColorTheme(theme)
    }

    fun setThemeMode(mode: ThemeMode) {
        preferencesManager.setThemeMode(mode)
    }

    fun setStudentProfile(name: String, major: String, semester: String) {
        preferencesManager.setStudentProfile(name, major, semester)
    }

    fun setCreditTarget(target: Int) {
        preferencesManager.setCreditTarget(target)
    }

    fun setShowThursday(show: Boolean) {
        preferencesManager.setShowThursday(show)
    }

    fun setTimetableDensity(density: TimetableDensity) {
        preferencesManager.setTimetableDensity(density)
    }

    fun setSemesterStartEpochDay(epochDay: Long?) {
        preferencesManager.setSemesterStartEpochDay(epochDay)
    }

    fun setFirstWeekIsOdd(firstWeekIsOdd: Boolean) {
        preferencesManager.setFirstWeekIsOdd(firstWeekIsOdd)
    }

    fun setAbsenceLimit(limit: Int) {
        preferencesManager.setAbsenceLimit(limit)
    }

    /** Courses → academic record sub-screen; back returns to «دروس من». */
    fun openPassedCourses() {
        _currentDestination.value = AppDestination.PASSED_COURSES
    }

    // ---------------------------------------------------------------------
    // End of term / start of a new term
    // ---------------------------------------------------------------------

    /** Opens the form from a fresh draft; nothing is written until final confirm. */
    fun openEndTerm() {
        _endTermDrafts.value = emptyMap()
        _endTermFlags.value = EndTermFlags()
        _currentDestination.value = AppDestination.END_TERM
    }

    fun openAcademicRecords() {
        _currentDestination.value = AppDestination.ACADEMIC_RECORDS
    }

    /** Home attendance card → the editable absence page. */
    fun openAttendance() {
        _currentDestination.value = AppDestination.ATTENDANCE
    }

    fun setEndTermStatus(courseId: Long, status: TermCourseStatus) {
        _endTermDrafts.value = _endTermDrafts.value.toMutableMap().apply {
            val current = this[courseId] ?: TermCourseDraft()
            this[courseId] = current.copy(status = status)
        }
        if (_endTermFlags.value.error != null) {
            _endTermFlags.value = _endTermFlags.value.copy(error = null)
        }
    }

    fun setEndTermGrade(courseId: Long, grade: String) {
        _endTermDrafts.value = _endTermDrafts.value.toMutableMap().apply {
            val current = this[courseId] ?: TermCourseDraft()
            this[courseId] = current.copy(grade = grade)
        }
    }

    /** Shows the review step; refuses while any course is still unanswered. */
    fun reviewEndTerm() {
        val state = endTermState.value
        when {
            !userPreferences.value.isTermActive ->
                showError("ترم فعلی از قبل بسته شده است.")
            state.courses.isEmpty() ->
                showError("درسی برای ثبت پایان ترم وجود ندارد.")
            !state.isComplete ->
                showError("برای همهٔ درس‌ها یک وضعیت نهایی انتخاب کنید.")
            else -> _endTermFlags.value = _endTermFlags.value.copy(
                showSummary = true,
                error = null
            )
        }
    }

    fun editEndTerm() {
        _endTermFlags.value = _endTermFlags.value.copy(showSummary = false, error = null)
    }

    /**
     * Final confirmation. The repository archives everything in ONE database
     * transaction; the DataStore "active term" flag is flipped only AFTER that
     * succeeds. Cancelling before this point writes nothing at all.
     *
     * [termName] is the label the student confirms for the archive, so the
     * academic record is never named behind their back.
     */
    fun confirmEndTerm(termName: String) {
        val safeTermName = termName.trim()
        if (safeTermName.isBlank()) {
            showError("نام ترم نمی‌تواند خالی باشد.")
            return
        }
        val state = endTermState.value
        if (_endTermFlags.value.isSubmitting) return
        if (state.courses.isEmpty()) {
            showError("درسی برای ثبت پایان ترم وجود ندارد.")
            return
        }
        if (!state.isComplete) {
            showError("برای همهٔ درس‌ها یک وضعیت نهایی انتخاب کنید.")
            return
        }
        val preferences = userPreferences.value
        if (!preferences.isTermActive) {
            showError("ترم فعلی از قبل بسته شده است.")
            return
        }
        val entries = state.courses.associate { course ->
            course.courseId to CourseRepository.EndTermCourseEntry(
                status = course.status!!,
                grade = course.grade
            )
        }
        _endTermFlags.value = _endTermFlags.value.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                when (
                    val result = repository.endCurrentTerm(
                        termName = safeTermName,
                        startEpochDay = preferences.semesterStartEpochDay,
                        endEpochDay = JalaliDate.todayEpochDay(),
                        entries = entries
                    )
                ) {
                    is CourseRepository.EndTermResult.Success -> {
                        // Durable write first, then the UI may say "finished".
                        // A DataStore failure must NOT be reported as a failed
                        // archive: the database snapshot is already committed.
                        val activeFlagSaved = try {
                            preferencesManager.endActiveTerm(safeTermName)
                            true
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("CoursePlannerVM", "Term active flag write failed", e)
                            false
                        }
                        _generationState.value = GenerationState()
                        _endTermFlags.value = EndTermFlags(
                            archivedTermId = result.termId,
                            archivedTermName = safeTermName,
                            archivedCourseCount = state.courses.size,
                            archivedPassedCount = state.passedCount,
                            archivedPassedCredits = state.passedCredits
                        )
                        val archiveMessage =
                            "ترم «$safeTermName» آرشیو شد؛ " +
                                "${JalaliDate.toFaDigits(result.archivedCourses)} درس در سوابق تحصیلی ثبت شد."
                        if (activeFlagSaved) {
                            showInfo(archiveMessage)
                        } else {
                            showError(
                                archiveMessage +
                                    " اما وضعیت «ترم فعال» ذخیره نشد؛ با «شروع ترم جدید» ادامه دهید."
                            )
                        }
                    }
                    is CourseRepository.EndTermResult.MissingStatuses -> {
                        val message = "وضعیت این درس‌ها مشخص نشده است: " +
                            result.courseNames.joinToString("، ")
                        _endTermFlags.value = _endTermFlags.value.copy(
                            isSubmitting = false,
                            showSummary = false,
                            error = message
                        )
                        showError(message)
                    }
                    CourseRepository.EndTermResult.NoCourses -> {
                        _endTermFlags.value = _endTermFlags.value.copy(
                            isSubmitting = false,
                            showSummary = false,
                            error = "درسی برای ثبت پایان ترم وجود ندارد."
                        )
                        showError("درسی برای ثبت پایان ترم وجود ندارد.")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("CoursePlannerVM", "End-term archive failed", e)
                _endTermFlags.value = _endTermFlags.value.copy(
                    isSubmitting = false,
                    error = "آرشیو ترم ناموفق بود؛ هیچ تغییری ذخیره نشد. لطفاً دوباره تلاش کنید."
                )
                showError("آرشیو ترم ناموفق بود؛ هیچ تغییری ذخیره نشد. لطفاً دوباره تلاش کنید.")
            }
        }
    }

    fun requestStartNewTerm() {
        _showStartNewTermDialog.value = true
    }

    fun dismissStartNewTerm() {
        _showStartNewTermDialog.value = false
    }

    /**
     * Creates the next term: just a name + start date and the active flag.
     * Choosing courses is deliberately optional and can happen later.
     */
    fun startNewTerm(name: String, startEpochDay: Long?) {
        if (name.isBlank()) {
            showError("نام ترم جدید نمی‌تواند خالی باشد.")
            return
        }
        val alreadyArchived = _endTermFlags.value.archivedTermId != null
        if (userPreferences.value.isTermActive && !alreadyArchived) {
            showError("ابتدا ترم فعلی را با «ثبت پایان ترم» ببندید.")
            return
        }
        viewModelScope.launch {
            try {
                preferencesManager.startNewTerm(name.trim(), startEpochDay)
                _generationState.value = GenerationState()
                _endTermDrafts.value = emptyMap()
                _endTermFlags.value = EndTermFlags()
                _showStartNewTermDialog.value = false
                _currentDestination.value = AppDestination.HOME
                showInfo(
                    "ترم «${name.trim()}» آغاز شد؛ از «دروس» می‌توانید درس‌های این ترم را انتخاب کنید."
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("CoursePlannerVM", "Starting a new term failed", e)
                showError("شروع ترم جدید ناموفق بود؛ لطفاً دوباره تلاش کنید.")
            }
        }
    }

    // ---------------------------------------------------------------------
    // Passed course actions
    // ---------------------------------------------------------------------

    fun addPassedCourse(
        code: String,
        name: String,
        credits: Int,
        grade: String?,
        termLabel: String?,
        note: String
    ) {
        savePassedCourse(
            PassedCourse(
                code = code,
                name = name,
                credits = credits,
                grade = grade,
                termLabel = termLabel,
                note = note
            )
        )
    }

    fun updatePassedCourse(
        id: Long,
        code: String,
        name: String,
        credits: Int,
        grade: String?,
        termLabel: String?,
        note: String
    ) {
        savePassedCourse(
            PassedCourse(
                id = id,
                code = code,
                name = name,
                credits = credits,
                grade = grade,
                termLabel = termLabel,
                note = note
            )
        )
    }

    private fun savePassedCourse(course: PassedCourse) {
        launchDbWrite(onErrorMessage = { "ذخیرهٔ درس پاس‌شده ناموفق بود." }) {
            when (repository.savePassedCourse(course)) {
                is CourseRepository.PassedCourseResult.Success -> showInfo(
                    if (course.id == 0L) "درس پاس‌شده «${course.name.trim()}» ثبت شد."
                    else "درس پاس‌شده «${course.name.trim()}» ویرایش شد."
                )
                CourseRepository.PassedCourseResult.BlankCode ->
                    showError("کد درس نمی‌تواند خالی باشد.")
                CourseRepository.PassedCourseResult.BlankName ->
                    showError("نام درس نمی‌تواند خالی باشد.")
                CourseRepository.PassedCourseResult.InvalidCredits ->
                    showError("تعداد واحد باید بین ۰ تا ۳۰ باشد.")
                CourseRepository.PassedCourseResult.DuplicateCode ->
                    showError("این کد قبلاً در دروس پاس‌شده ثبت شده است.")
                CourseRepository.PassedCourseResult.NotFound ->
                    showError("درس پاس‌شده پیدا نشد؛ ممکن است قبلاً حذف شده باشد.")
            }
        }
    }

    fun deletePassedCourse(id: Long, name: String) {
        launchDbWrite(onErrorMessage = { "حذف درس پاس‌شده ناموفق بود." }) {
            repository.deletePassedCourse(id)
            showInfo("درس پاس‌شده «$name» حذف شد.")
        }
    }

    // ---------------------------------------------------------------------
    // Attendance actions
    // ---------------------------------------------------------------------

    fun markAttendance(
        sectionId: Long,
        dateEpochDay: Long,
        startTime: String,
        endTime: String,
        status: AttendanceStatus,
        note: String = ""
    ) {
        launchDbWrite(onErrorMessage = { "ثبت حضور ناموفق بود." }) {
            val saved = repository.markAttendance(
                sectionId = sectionId,
                dateEpochDay = dateEpochDay,
                startTime = startTime,
                endTime = endTime,
                status = status,
                note = note
            )
            if (saved) {
                showInfo("وضعیت «${status.titleFa}» برای این جلسه ثبت شد.")
            } else {
                showError("گروه این جلسه پیدا نشد؛ ممکن است حذف شده باشد.")
            }
        }
    }

    fun clearAttendance(sectionId: Long, dateEpochDay: Long, startTime: String) {
        launchDbWrite(onErrorMessage = { "حذف ثبت حضور ناموفق بود." }) {
            repository.clearAttendance(sectionId, dateEpochDay, startTime)
            showInfo("ثبت حضور این جلسه حذف شد.")
        }
    }

    fun deleteAttendance(id: Long) {
        launchDbWrite(onErrorMessage = { "حذف سابقهٔ حضور ناموفق بود." }) {
            repository.deleteAttendance(id)
            showInfo("سابقهٔ حضور حذف شد.")
        }
    }

    // ---------------------------------------------------------------------
    // Selective data deletion
    // ---------------------------------------------------------------------

    /**
     * Deletes only the selected domains. Nothing is implicit: `preferences`
     * resets DataStore separately, and an empty selection is a no-op.
     */
    fun deleteSelectedData(selection: DataDeletionSelection) {
        if (selection.isEmpty) {
            showInfo("هیچ داده‌ای برای حذف انتخاب نشده است.")
            return
        }
        launchDbWrite(onErrorMessage = { "حذف داده‌های انتخاب‌شده ناموفق بود." }) {
            repository.clearData(selection)
            if (selection.preferences) {
                preferencesManager.resetToDefaults()
            }
            if (selection.myCourses || selection.catalog || selection.schedule) {
                _generationState.value = GenerationState()
            }
            showInfo("داده‌های انتخاب‌شده با موفقیت حذف شدند.")
        }
    }

    fun setOptimizationPreference(preference: OptimizationPreference) {
        if (_optimizationPreference.value == preference) return
        _optimizationPreference.value = preference
        val state = _generationState.value
        if (!state.isGenerated) return
        // Only the previous Top-K (cut with the OLD weights) is retained, so a
        // mere re-rank of those few survivors could not recover combinations the
        // old cut already discarded. Re-run the search over the full candidate
        // space so the new preference judges every valid schedule.
        runScheduleGenerator()
    }

    /**
     * Toggles a course's "generator target" flag. Routed through [launchDbWrite]
     * because a failed write leaves the checkbox visually changed while the row
     * never changed — exactly the silent lie the shared error boundary prevents.
     */
    fun toggleCourseSelectedForGeneration(courseId: Long, isSelected: Boolean) {
        launchDbWrite(onErrorMessage = { "تغییر وضعیت انتخاب درس برای برنامه‌ساز ناموفق بود." }) {
            repository.toggleCourseSelectedForGeneration(courseId, isSelected)
        }
    }

    fun toggleSectionEnrolled(section: SectionWithDetails, isEnrolled: Boolean) {
        launchDbWrite(
            onErrorMessage = {
                if (isEnrolled) "ثبت‌نام در گروه «${section.section.sectionCode}» ناموفق بود."
                else "حذف گروه «${section.section.sectionCode}» از برنامه هفتگی ناموفق بود."
            }
        ) {
            repository.setSectionEnrolled(section, isEnrolled)
        }
    }

    fun checkConflictForCandidate(candidate: SectionWithDetails): Conflict? {
        return ScheduleEngine.findConflictWithCurrent(candidate, enrolledSections.value)
    }

    fun runScheduleGenerator() {
        // A second tap while a search is already running must not queue another
        // one; the busy flag is surfaced to the screen so it never looks frozen.
        if (_isGenerating.value) return
        _isGenerating.value = true
        viewModelScope.launch {
            val courses = coursesWithSections.value.filter { it.course.isSelectedForGeneration }
            if (courses.isEmpty()) {
                _generationState.value = GenerationState(
                    isGenerated = true,
                    combinations = emptyList(),
                    rawCombinations = emptyList(),
                    message = "هیچ درسی برای ساخت برنامه انتخاب نشده است. لطفاً در تب دروس، تیک «برنامه‌ساز» را فعال کنید."
                )
                return@launch
            }

            // Group sections with details for each selected course (names kept
            // so courses without any usable section can be reported by name).
            val sectionsGrouped = allSections.value.groupBy { it.course.id }
            val generatorCourses = courses.map { cws ->
                ir.courseplanner.app.engine.GeneratorCourse(
                    courseName = cws.course.name,
                    sections = sectionsGrouped[cws.course.id].orEmpty()
                )
            }

            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                ScheduleEngine.generateTopSchedules(generatorCourses, _optimizationPreference.value)
            }

            if (result.ranked.isEmpty()) {
                val detail = if (result.skippedCourses.isNotEmpty()) {
                    " برای این دروس هیچ گروه قابل‌استفاده‌ای وجود ندارد: ${result.skippedCourses.joinToString("، ")}."
                } else {
                    " همزمانی ساعات کلاس‌ها یا تداخل روز امتحانات مانع ساخت برنامه است."
                }
                _generationState.value = GenerationState(
                    isGenerated = true,
                    combinations = emptyList(),
                    rawCombinations = emptyList(),
                    message = "هیچ ترکیب بدون تداخلی برای دروس انتخاب شده یافت نشد." + detail,
                    skippedCourses = result.skippedCourses
                )
            } else {
                val skippedNote = if (result.skippedCourses.isNotEmpty()) {
                    " این دروس گروه قابل‌استفاده‌ای نداشتند و لحاظ نشدند: ${result.skippedCourses.joinToString("، ")}."
                } else ""
                val truncatedNote = if (result.truncated) {
                    " جست‌وجو به سقف امن رسید؛ بهترین‌های یافت‌شده نمایش داده می‌شود."
                } else ""
                _generationState.value = GenerationState(
                    isGenerated = true,
                    combinations = result.ranked,
                    rawCombinations = result.ranked.map { it.schedule },
                    currentIndex = 0,
                    message = "${result.totalValid} ترکیب بدون تداخل بررسی شد؛ ${result.ranked.size} پیشنهاد برتر نمایش داده می‌شود." +
                        skippedNote + truncatedNote,
                    skippedCourses = result.skippedCourses,
                    truncated = result.truncated
                )
            }
        }.invokeOnCompletion { _isGenerating.value = false }
    }

    fun nextCombination() {
        val state = _generationState.value
        if (state.combinations.isNotEmpty()) {
            val next = (state.currentIndex + 1) % state.combinations.size
            _generationState.value = state.copy(currentIndex = next)
        }
    }

    fun previousCombination() {
        val state = _generationState.value
        if (state.combinations.isNotEmpty()) {
            val prev = if (state.currentIndex - 1 < 0) state.combinations.size - 1 else state.currentIndex - 1
            _generationState.value = state.copy(currentIndex = prev)
        }
    }

    /**
     * Applies the currently shown candidate to the weekly program.
     *
     * Guard: an INCOMPLETE result (some selected course had no usable group) is
     * never applied — that would silently drop those courses from the student's
     * program. The repository re-checks the same invariant before writing.
     */
    fun applyCurrentGeneratedSchedule() {
        val state = _generationState.value
        val scheduleToApply = state.combinations.getOrNull(state.currentIndex)?.schedule
        if (scheduleToApply.isNullOrEmpty()) return
        if (!state.isComplete) {
            showError(
                "این برنامه کامل نیست و اعمال نمی‌شود؛ برای این دروس گروه قابل‌استفاده‌ای وجود ندارد: " +
                    state.skippedCourses.joinToString("، ") +
                    ". ابتدا برای آن‌ها گروه تعریف کنید (یا آن‌ها را از برنامه‌ساز خارج کنید)."
            )
            return
        }
        launchDbWrite(onErrorMessage = { "اعمال برنامه ناموفق بود؛ لطفاً دوباره تلاش کنید." }) {
            when (val applied = repository.applySchedule(scheduleToApply)) {
                CourseRepository.ApplyScheduleResult.Success -> {
                    showInfo("برنامه بهینه رتبه ${state.currentIndex + 1} با موفقیت به عنوان برنامه هفتگی اعمال شد.")
                    _currentDestination.value = AppDestination.HOME
                }
                is CourseRepository.ApplyScheduleResult.Incomplete -> {
                    // Defensive: the data changed between generation and apply.
                    showError(
                        "اعمال نشد؛ این برنامه دروس زیر را پوشش نمی‌دهد: " +
                            applied.missingCourseNames.joinToString("، ") +
                            ". برنامه‌ساز را دوباره اجرا کنید."
                    )
                }
                CourseRepository.ApplyScheduleResult.Empty -> {
                    showError("برنامه‌ای برای اعمال وجود ندارد.")
                }
            }
        }
    }

    /**
     * Creates a course manually (name, code, first group, sessions).
     *
     * The code is REQUIRED: a course code is domain data, so it is never
     * synthesized (`CRS-1234` fallback was removed — it produced courses the
     * student could never match against the portal). The repository enforces
     * uniqueness of the normalized code and rejects self-conflicting sessions.
     */
    fun addManualCourse(
        name: String,
        code: String,
        department: String,
        credits: Int,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ManualSessionInput>
    ) {
        if (name.isBlank()) {
            showError("نام درس نمی‌تواند خالی باشد.")
            return
        }
        if (code.isBlank()) {
            showError("کد درس الزامی است؛ لطفاً کد واقعی درس را وارد کنید.")
            return
        }
        if (sectionCode.isBlank()) {
            showError("کد گروه نمی‌تواند خالی باشد.")
            return
        }
        launchDbWrite(onErrorMessage = { "افزودن درس ناموفق بود؛ لطفاً دوباره تلاش کنید." }) {
            val course = Course(
                code = code.trim(),
                name = name.trim(),
                department = department.trim(),
                credits = credits.coerceIn(1, 20),
                isSelectedForGeneration = true
            )
            val section = CourseSection(
                courseId = 0,
                sectionCode = sectionCode.trim(),
                instructor = instructor.trim(),
                capacity = 30,
                examDate = examDate.trim(),
                examStartTime = examStartTime.trim(),
                examEndTime = examEndTime.trim(),
                isEnrolled = false
            )
            val sessionEntities = sessions.toSessionEntities(sectionId = 0)
            // `Success` already reported the new id through the info message; the
            // remaining branches turn every refusal into a visible reason.
            when (repository.addManualCourse(course, section, sessionEntities)) {
                is CourseRepository.AddCourseResult.Success -> {
                    if (passedCourseCodes.value.contains(normalizeCode(code))) {
                        showInfo(
                            "درس «${name.trim()}» اضافه شد؛ هشدار: این درس قبلاً در سوابق پاس‌شده ثبت شده است."
                        )
                    } else {
                        showInfo("درس «${name.trim()}» با موفقیت اضافه شد.")
                    }
                }
                CourseRepository.AddCourseResult.DuplicateCode ->
                    showError("کد «${code.trim()}» قبلاً برای درس دیگری ثبت شده است.")
                CourseRepository.AddCourseResult.BlankCode ->
                    showError("کد درس نمی‌تواند خالی باشد.")
                CourseRepository.AddCourseResult.BlankSectionCode ->
                    showError("کد گروه نمی‌تواند خالی باشد.")
                CourseRepository.AddCourseResult.ConflictingSessions ->
                    showError("جلسات این گروه با هم تداخل دارند؛ بازه‌های ساعتی را اصلاح کنید.")
                CourseRepository.AddCourseResult.InvalidSessions ->
                    showError("ساعت یکی از جلسات نامعتبر است؛ فرمت درست 08:00 و شروع قبل از پایان باشد.")
            }
        }
    }

    fun addSectionToCourse(
        courseId: Long,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ManualSessionInput>
    ) {
        if (sectionCode.isBlank()) {
            showError("کد گروه نمی‌تواند خالی باشد.")
            return
        }
        launchDbWrite(onErrorMessage = { "افزودن گروه ناموفق بود؛ لطفاً دوباره تلاش کنید." }) {
            val section = CourseSection(
                courseId = courseId,
                sectionCode = sectionCode.trim(),
                instructor = instructor.trim(),
                capacity = 30,
                examDate = examDate.trim(),
                examStartTime = examStartTime.trim(),
                examEndTime = examEndTime.trim(),
                isEnrolled = false
            )
            when (
                repository.addSectionToCourse(courseId, section, sessions.toSessionEntities(sectionId = 0))
            ) {
                is CourseRepository.AddSectionResult.Success -> {
                    showInfo("گروه ${sectionCode.trim()} با موفقیت به درس افزوده شد.")
                }
                CourseRepository.AddSectionResult.DuplicateCode ->
                    showError("کد گروه «${sectionCode.trim()}» قبلاً برای همین درس ثبت شده است.")
                CourseRepository.AddSectionResult.BlankCode ->
                    showError("کد گروه نمی‌تواند خالی باشد.")
                CourseRepository.AddSectionResult.CourseNotFound ->
                    showError("درس موردنظر یافت نشد؛ ممکن است حذف شده باشد.")
                CourseRepository.AddSectionResult.ConflictingSessions ->
                    showError("جلسات این گروه با هم تداخل دارند؛ بازه‌های ساعتی را اصلاح کنید.")
                CourseRepository.AddSectionResult.InvalidSessions ->
                    showError("ساعت یکی از جلسات نامعتبر است؛ فرمت درست 08:00 و شروع قبل از پایان باشد.")
            }
        }
    }

    /** Manual dialog input → persistable sessions (sectionId is filled by the repository). */
    private fun List<ManualSessionInput>.toSessionEntities(sectionId: Long): List<ClassSession> =
        map { input -> input.toClassSession(sectionId) }

    fun deleteCourse(courseId: Long, courseName: String) {
        launchDbWrite(onErrorMessage = { "حذف درس ناموفق بود؛ لطفاً دوباره تلاش کنید." }) {
            repository.deleteCourse(courseId)
            showInfo("درس «$courseName» حذف شد.")
        }
    }

    fun updateCourse(courseId: Long, name: String, code: String, department: String, credits: Int) {
        if (name.isBlank()) {
            showError("نام درس نمی‌تواند خالی باشد.")
            return
        }
        if (code.isBlank()) {
            showError("کد درس نمی‌تواند خالی باشد.")
            return
        }
        launchDbWrite(onErrorMessage = { "ذخیره تغییرات درس ناموفق بود." }) {
            when (
                repository.updateCourseDetails(
                    courseId = courseId,
                    name = name.trim(),
                    code = code.trim(),
                    department = department.trim(),
                    credits = credits.coerceIn(1, 20)
                )
            ) {
                CourseRepository.UpdateCourseResult.Success -> {
                    showInfo("تغییرات درس «${name.trim()}» ذخیره شد.")
                }
                CourseRepository.UpdateCourseResult.DuplicateCode -> {
                    showError("کد «${code.trim()}» قبلاً برای درس دیگری ثبت شده است.")
                }
                CourseRepository.UpdateCourseResult.BlankCode -> {
                    showError("کد درس نمی‌تواند خالی باشد.")
                }
                CourseRepository.UpdateCourseResult.NotFound -> {
                    showError("درس موردنظر یافت نشد؛ ممکن است حذف شده باشد.")
                }
            }
        }
    }

    fun deleteSection(sectionId: Long, sectionCode: String) {
        launchDbWrite(onErrorMessage = { "حذف گروه ناموفق بود؛ لطفاً دوباره تلاش کنید." }) {
            repository.deleteSection(sectionId)
            showInfo("گروه $sectionCode حذف شد.")
        }
    }

    /**
     * Edits a section/group in place (code, instructor, exam, sessions).
     * Enrollment is preserved by the repository, so editing an enrolled group
     * never drops it from the weekly program.
     */
    fun updateSection(
        sectionId: Long,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ManualSessionInput>
    ) {
        if (sectionCode.isBlank()) {
            showError("کد گروه نمی‌تواند خالی باشد.")
            return
        }
        launchDbWrite(onErrorMessage = { "ذخیره تغییرات گروه ناموفق بود." }) {
            val sessionEntities = sessions.map { input ->
                ClassSession(
                    sectionId = sectionId,
                    dayOfWeek = input.dayOfWeek,
                    startTime = input.startTime.trim(),
                    endTime = input.endTime.trim(),
                    location = input.location.trim(),
                    weekType = input.weekType
                )
            }
            when (
                repository.updateSectionDetails(
                    sectionId = sectionId,
                    sectionCode = sectionCode,
                    instructor = instructor,
                    examDate = examDate,
                    examStartTime = examStartTime,
                    examEndTime = examEndTime,
                    sessions = sessionEntities
                )
            ) {
                CourseRepository.UpdateSectionResult.Success -> {
                    showInfo("تغییرات گروه «${sectionCode.trim()}» ذخیره شد.")
                }
                CourseRepository.UpdateSectionResult.DuplicateCode -> {
                    showError("کد گروه «${sectionCode.trim()}» قبلاً برای همین درس ثبت شده است.")
                }
                CourseRepository.UpdateSectionResult.BlankCode -> {
                    showError("کد گروه نمی‌تواند خالی باشد.")
                }
                CourseRepository.UpdateSectionResult.ConflictingSessions -> {
                    showError("جلسات این گروه با هم تداخل دارند؛ بازه‌های ساعتی را اصلاح کنید.")
                }
                CourseRepository.UpdateSectionResult.InvalidSessions -> {
                    showError("ساعت یکی از جلسات نامعتبر است؛ فرمت درست 08:00 و شروع قبل از پایان باشد.")
                }
                CourseRepository.UpdateSectionResult.NotFound -> {
                    showError("گروه موردنظر یافت نشد؛ ممکن است حذف شده باشد.")
                }
            }
        }
    }

    fun importData(content: String, isJson: Boolean, clearExisting: Boolean) {
        val result = if (isJson) {
            CourseImporter.parseJson(content)
        } else {
            CourseImporter.parseCsv(content)
        }

        when (result) {
            is ImportResult.Success -> {
                launchDbWrite(onErrorMessage = { "درون‌ریزی در پایگاه داده ناموفق بود." }) {
                    repository.importItems(result.items, clearExisting = clearExisting)
                    showInfo(result.message)
                }
            }
            is ImportResult.Failure -> {
                showError(result.errorMessage)
            }
        }
    }

    /**
     * Import stage 1 — the picked file could not be READ (deleted file, revoked
     * permission, unsupported encoding). This must never be turned into an empty
     * HTML string: the parser would then report "the HTML file is empty" and the
     * real I/O failure would be invisible.
     */
    fun reportImportFileReadFailed(detail: String, displayName: String? = null) {
        val name = displayName?.takeIf { it.isNotBlank() } ?: "انتخاب‌شده"
        android.util.Log.e("CoursePlannerVM", "Import file read failed: $name -> $detail")
        showError("خواندن فایل «$name» ناموفق بود؛ فایل حذف/جابه‌جا شده یا دسترسی به آن ممکن نیست.")
    }

    /**
     * Import stage 2 — the file WAS read but contains nothing. Told apart from a
     * read failure so the user knows what to redo (save the page as HTML again).
     */
    fun reportImportFileEmpty(displayName: String? = null) {
        val name = displayName?.takeIf { it.isNotBlank() } ?: "انتخاب‌شده"
        showError("فایل «$name» خالی است؛ لطفاً صفحهٔ «دروس ارائه‌شده» را دوباره به‌صورت HTML یا تک‌فایل (.mht) ذخیره کنید.")
    }

    /** MHTML (.mht) archives never reach the parser: report the archive state. */
    fun reportMhtExtractionFailed(
        reason: MhtHtmlExtractor.Reason,
        displayName: String? = null,
    ) {
        val name = displayName?.takeIf { it.isNotBlank() } ?: "انتخاب‌شده"
        when (reason) {
            MhtHtmlExtractor.Reason.NO_HTML_PART ->
                showError("در فایل «$name» هیچ سند HTML پیدا نشد؛ فایل تک‌فایل (.mht) سالم را دوباره ذخیره کنید.")
            MhtHtmlExtractor.Reason.NO_COURSE_CONTENT ->
                showError("فایل «$name» جدول «دروس ارائه‌شده» را ندارد؛ مطمئن شوید صفحهٔ دروس را (نه صفحهٔ ورود) ذخیره کرده‌اید.")
            MhtHtmlExtractor.Reason.NOT_MHTML ->
                reportImportFileEmpty(displayName)
        }
    }

    /**
     * Byte entry point for the portal picker: sniffs MHTML vs plain HTML,
     * decodes accordingly, then follows the same stage-3/4 reporting as
     * [importPortalHtml]. The String path stays untouched for paste flows.
     */
    fun importPortalBytes(bytes: ByteArray, displayName: String?, clearExisting: Boolean) {
        if (bytes.isEmpty()) {
            reportImportFileEmpty(displayName)
            return
        }
        viewModelScope.launch {
            // Sniff on the head only: decoding the whole archive as text here
            // would defeat the byte path (binary parts must stay raw until
            // their own transfer-decoding step).
            val head = decodeHeadForSniff(bytes)
            val html = if (MhtHtmlExtractor.looksLikeMhtml(head, displayName)) {
                when (val extraction = MhtHtmlExtractor.extractFromBytes(bytes)) {
                    is MhtHtmlExtractor.Extraction.Html -> extraction.html
                    is MhtHtmlExtractor.Extraction.Missing -> {
                        reportMhtExtractionFailed(extraction.reason, displayName)
                        return@launch
                    }
                }
            } else {
                decodePortalText(bytes)
            }
            if (html.isBlank()) {
                reportImportFileEmpty(displayName)
                return@launch
            }
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                PooyaHtmlParser.parsePortalHtml(html)
            }
            when (result) {
                is ImportResult.Success -> {
                    try {
                        repository.importPortalItems(result.items, clearExisting = clearExisting)
                        showInfo(
                            result.message +
                                " از تب «دروس» با وارد کردن کد درس، به دروس خود اضافه کنید."
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("CoursePlannerVM", "Portal import failed", e)
                        showError("درون‌ریزی کاتالوگ پرتال ناموفق بود؛ لطفاً دوباره تلاش کنید.")
                    }
                }
                is ImportResult.Failure -> {
                    showError(result.errorMessage)
                }
            }
        }
    }

    /**
     * Decodes a plain (non-MHTML) portal file the way the old text path did:
     * strict UTF-8 first, Windows-1256 fallback. MHTML archives never reach
     * this: [MhtHtmlExtractor] decodes each part with its own charset.
     */
    internal fun decodePortalText(bytes: ByteArray): String {
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            try {
                bytes.toString(charset("windows-1256"))
            } catch (_: Exception) {
                bytes.toString(Charsets.ISO_8859_1)
            }
        }
    }

    /** First bytes as text for MHTML sniffing only (never for parsing). */
    private fun decodeHeadForSniff(bytes: ByteArray): String {
        val head = bytes.take(4096).toByteArray()
        return try {
            head.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            head.toString(Charsets.ISO_8859_1)
        }
    }

    /**
     * Imports a saved university-portal HTML file (Pooya/Golestan/…) into the
     * hidden course catalog. Same-code courses are replaced, never duplicated.
     * Parsing runs off the main thread; the file can hold 200+ rows.
     *
     * Stages 3 and 4 (read OK → parser outcome) are reported by the parser:
     * "no courses found" and "import succeeded" stay distinguishable.
     */
    fun importPortalHtml(html: String, clearExisting: Boolean) {
        if (html.isBlank()) {
            // Defensive: callers distinguish read-failure/empty before this, but a
            // blank payload must never look like a successful import.
            reportImportFileEmpty()
            return
        }
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                PooyaHtmlParser.parsePortalHtml(html)
            }
            when (result) {
                is ImportResult.Success -> {
                    try {
                        repository.importPortalItems(result.items, clearExisting = clearExisting)
                        showInfo(
                            result.message +
                                " از تب «دروس» با وارد کردن کد درس، به دروس خود اضافه کنید."
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("CoursePlannerVM", "Portal import failed", e)
                        showError("درون‌ریزی کاتالوگ پرتال ناموفق بود؛ لطفاً دوباره تلاش کنید.")
                    }
                }
                is ImportResult.Failure -> {
                    showError(result.errorMessage)
                }
            }
        }
    }

    /**
     * Quick-add: moves a catalog course into "my courses" so it shows up in
     * the generator and the default list. Called from the code-search card.
     */
    fun addCatalogCourseToMine(courseId: Long) {
        launchDbWrite(onErrorMessage = { "افزودن درس به دروس من ناموفق بود." }) {
            val target = coursesWithSections.value.firstOrNull { it.course.id == courseId }
            repository.toggleCourseSelectedForGeneration(courseId, true)
            val alreadyPassed = target?.let {
                passedCourseCodes.value.contains(normalizeCode(it.course.code))
            } == true
            showInfo(
                when {
                    target == null -> "درس به دروس من اضافه شد."
                    alreadyPassed ->
                        "درس «${target.course.name}» اضافه شد؛ هشدار: این درس قبلاً در سوابق پاس‌شده ثبت شده است."
                    else -> "درس «${target.course.name}» به دروس من اضافه شد."
                }
            )
        }
    }

    fun loadSampleData(clearExisting: Boolean = true) {
        launchDbWrite(onErrorMessage = { "بارگذاری نمونه اطلاعات ناموفق بود." }) {
            repository.importItems(CourseImporter.getSampleCatalog(), clearExisting = clearExisting)
            showInfo("نمونه اطلاعات دروس دانشگاهی با موفقیت بارگذاری شد.")
        }
    }

    fun clearAllData() {
        // Kept as a thin alias for existing callers/tests; the advanced dialog
        // uses deleteSelectedData with an explicit selection.
        deleteSelectedData(DataDeletionSelection.ALL)
    }

    fun clearScheduleOnly() {
        launchDbWrite(onErrorMessage = { "خالی کردن برنامه هفتگی ناموفق بود." }) {
            repository.clearEnrollments()
            showInfo("برنامه هفتگی خالی شد.")
        }
    }

    fun onDocSearchQueryChange(query: String) {
        _docSearchQuery.value = query
    }

    fun onDocCourseFilterSelected(courseId: Long?) {
        _selectedDocCourseId.value = courseId
    }

    fun onDocCategoryFilterSelected(category: DocumentCategory?) {
        _selectedDocCategory.value = category
    }

    fun toggleOnlyBookmarkedDocs() {
        _onlyBookmarkedDocs.value = !_onlyBookmarkedDocs.value
    }

    fun navigateToCourseDocuments(courseId: Long) {
        _selectedDocCourseId.value = courseId
        _selectedDocCategory.value = null
        _docSearchQuery.value = ""
        _currentDestination.value = AppDestination.DOCUMENTS
    }

    fun addDocument(
        courseId: Long,
        title: String,
        category: DocumentCategory,
        notes: String,
        fileUri: String?,
        fileName: String?,
        fileSizeBytes: Long,
        isBookmarked: Boolean
    ) {
        launchDbWrite(onErrorMessage = { "ذخیره جزوه / سند ناموفق بود." }) {
            val doc = CourseDocument(
                courseId = courseId,
                title = title.trim(),
                category = category,
                contentNotes = notes.trim(),
                fileUri = fileUri,
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
                isBookmarked = isBookmarked
            )
            repository.insertDocument(doc)
            showInfo("جزوه / سند با موفقیت ذخیره شد.")
        }
    }

    fun updateDocument(doc: CourseDocument) {
        launchDbWrite(onErrorMessage = { "به‌روزرسانی جزوه ناموفق بود." }) {
            repository.updateDocument(doc)
            showInfo("اطلاعات جزوه به‌روزرسانی شد.")
        }
    }

    fun deleteDocument(id: Long) {
        launchDbWrite(onErrorMessage = { "حذف جزوه / سند ناموفق بود." }) {
            repository.deleteDocument(id)
            showInfo("جزوه / سند حذف گردید.")
        }
    }

    fun toggleDocumentBookmark(id: Long, current: Boolean) {
        launchDbWrite(onErrorMessage = { "تغییر وضعیت نشان‌گذاری جزوه ناموفق بود." }) {
            repository.toggleDocumentBookmark(id, !current)
        }
    }

    /**
     * The newer release to offer, or null when there is nothing to show.
     *
     * Only ever set by [checkForUpdateOnce]; every other path leaves it null so a
     * network failure can never surface as an error dialog or a snackbar.
     */
    private val _availableUpdate = MutableStateFlow<AvailableUpdate?>(null)
    val availableUpdate: StateFlow<AvailableUpdate?> = _availableUpdate.asStateFlow()

    /**
     * In-app download progress for the offered update.
     *
     * Lives in the ViewModel (not the dialog) so the transfer survives
     * recomposition and rotation: closing the dialog is the only thing that
     * cancels it, and it is cancelled explicitly via [cancelUpdateDownload] /
     * [dismissUpdatePrompt].
     */
    private val _updateDownload = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateDownload: StateFlow<UpdateDownloadState> = _updateDownload.asStateFlow()

    // The single in-flight transfer. Kept so a second tap cannot start a parallel
    // download, and so cancelling actually stops the network read.
    private var updateDownloadJob: Job? = null

    // Guards against a second check in the same process. The prompt is a
    // once-per-cold-start, one-shot operation — recomposition must never re-run it.
    private var updateCheckStarted = false

    /**
     * Asks GitHub whether a newer TermChin exists. Called once from MainActivity.
     *
     * The check is a cancellable background request, so it never blocks startup
     * and never delays the first frame.
     */
    fun checkForUpdateOnce() {
        if (updateCheckStarted) return
        updateCheckStarted = true
        viewModelScope.launch {
            // Cancellation must stay cancellable: a cancelled check dies here
            // instead of being converted into a silent "no update".
            val update = try {
                updateChecker.checkForUpdate()
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                null
            }
            if (update == null) return@launch
            // One prompt per published version: if this exact version was already
            // offered (and dismissed with "بعداً"), stay quiet until a newer one.
            val lastSeen = preferencesManager.lastPromptedUpdateVersion.first()
            if (UpdatePromptGate.shouldPrompt(update, lastSeen)) {
                _availableUpdate.value = update
            }
        }
    }

    /**
     * Dismisses the prompt without installing.
     *
     * The version is recorded as prompted, so "بعداً" really does mean "not again
     * for this release" and the dialog cannot become a startup annoyance. Any
     * in-flight download is cancelled and its partial file deleted, and a
     * finished archive is discarded too: the cache is not an install queue.
     */
    fun dismissUpdatePrompt() {
        cancelUpdateDownload()
        val update = _availableUpdate.value
        _availableUpdate.value = null
        if (update != null) markPromptSeen(update)
    }

    /**
     * Starts the in-app download of the offered release.
     *
     * The prompt stays on screen and turns into the progress view instead of
     * closing. The version is recorded as prompted immediately: a download that
     * outlives the process must not re-prompt the user on the next start.
     */
    fun startUpdateDownload() {
        val update = _availableUpdate.value ?: return
        if (updateDownloadJob?.isActive == true) return
        markPromptSeen(update)

        val target = updateApkFile(update)
        _updateDownload.value = UpdateDownloadState.Downloading(bytes = 0L, total = null)
        updateDownloadJob = viewModelScope.launch {
            try {
                val apk = apkDownloader.download(
                    url = update.downloadUrl,
                    expectedSha256 = update.sha256,
                    target = target
                ) { progress ->
                    _updateDownload.value =
                        UpdateDownloadState.Downloading(progress.bytes, progress.total)
                }
                val app = getApplication<Application>()
                val info = withContext(Dispatchers.IO) {
                    apkInspector.read(app, apk)
                }
                val failure = ApkVerifier.validate(
                    info = info,
                    expectedPackage = app.packageName,
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong()
                )
                if (failure != null) {
                    apk.delete()
                    _updateDownload.value = UpdateDownloadState.Failed(failure)
                    return@launch
                }
                _updateDownload.value = UpdateDownloadState.Ready(apk)
            } catch (cancellation: CancellationException) {
                // cancelUpdateDownload() owns the state transition.
                throw cancellation
            } catch (failure: DownloadException) {
                _updateDownload.value = UpdateDownloadState.Failed(failure.failure)
            } catch (failure: Exception) {
                _updateDownload.value = UpdateDownloadState.Failed(UpdateFailure.UNKNOWN)
            }
        }
    }

    /** Cancels an in-flight transfer and returns the dialog to its idle state. */
    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        discardDownloadedUpdate()
        _updateDownload.value = UpdateDownloadState.Idle
    }

    /**
     * Called after the system installer has been opened: the prompt has served
     * its purpose, so it closes.
     *
     * The verified APK is deliberately NOT deleted here. The installer reads the
     * `content://` URI asynchronously, so deleting the file right after
     * `startActivity` can make Android report a broken package. The cache entry
     * is replaced or dropped by the next download instead, and the OS may evict
     * it at any time.
     */
    fun onUpdateInstallerLaunched() {
        val update = _availableUpdate.value
        updateDownloadJob = null
        _updateDownload.value = UpdateDownloadState.Idle
        _availableUpdate.value = null
        if (update != null) markPromptSeen(update)
    }

    private fun discardDownloadedUpdate() {
        (_updateDownload.value as? UpdateDownloadState.Ready)?.apk?.delete()
    }

    /** Cache slot for the offered release; the FileProvider exposes this folder. */
    private fun updateApkFile(update: AvailableUpdate): File {
        val directory = File(getApplication<Application>().cacheDir, UPDATE_CACHE_DIR)
        val name = "TermChin-${update.version.versionName}.apk"
        // Drop archives from other versions/attempts, but keep this version's
        // `.part` file: a retry after a dropped connection resumes from it
        // instead of downloading the whole APK again.
        directory.listFiles()?.forEach { file ->
            if (!file.name.startsWith(name)) file.delete()
        }
        return File(directory, name)
    }

    private fun markPromptSeen(update: AvailableUpdate) {
        viewModelScope.launch {
            preferencesManager.setLastPromptedUpdateVersionSync(update.version.versionName)
        }
    }

    /**
     * Reports a message the user must actually see (an installer that could not
     * be opened). Same snackbar path as internal results, so the existing
     * flag-then-text ordering guarantee still applies.
     */
    fun showUserFacingMessage(message: String, isError: Boolean = false) {
        if (isError) showError(message) else showInfo(message)
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    companion object {
        /** Home dashboard clock cadence: countdowns stay minute-accurate. */
        const val DASHBOARD_TICK_MS = 30_000L

        /**
         * Cache folder holding the downloaded APK. Must stay in sync with
         * `res/xml/update_file_paths.xml`, the only path the FileProvider serves.
         */
        const val UPDATE_CACHE_DIR = "updates"
    }
}
