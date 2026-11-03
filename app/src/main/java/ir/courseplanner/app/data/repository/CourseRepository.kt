package ir.courseplanner.app.data.repository

import androidx.room.withTransaction
import ir.courseplanner.app.data.importer.ImportItem
import ir.courseplanner.app.data.local.AcademicTermDao
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.local.AttendanceDao
import ir.courseplanner.app.data.local.CourseDao
import ir.courseplanner.app.data.local.CourseDocumentDao
import ir.courseplanner.app.data.local.PassedCourseDao
import ir.courseplanner.app.data.local.SectionDao
import ir.courseplanner.app.data.model.AcademicTerm
import ir.courseplanner.app.data.model.AcademicTermWithCourses
import ir.courseplanner.app.data.model.ArchivedClassSession
import ir.courseplanner.app.data.model.ArchivedCourse
import ir.courseplanner.app.data.model.AttendanceRecord
import ir.courseplanner.app.data.model.AttendanceStats
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.DataCounts
import ir.courseplanner.app.data.model.DataDeletionSelection
import ir.courseplanner.app.data.model.PassedCourse
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.TermCourseStatus
import ir.courseplanner.app.data.model.normalizeCode
import ir.courseplanner.app.engine.SectionSessionValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class CourseRepository(
    private val db: AppDatabase,
    private val courseDao: CourseDao,
    private val sectionDao: SectionDao,
    private val documentDao: CourseDocumentDao,
    private val passedCourseDao: PassedCourseDao,
    private val attendanceDao: AttendanceDao,
    private val academicTermDao: AcademicTermDao = db.academicTermDao()
) {
    val allCourses: Flow<List<Course>> = courseDao.getAllCourses()
    val coursesWithSections: Flow<List<CourseWithSections>> = courseDao.getCoursesWithSections()
    val selectedForGeneration: Flow<List<CourseWithSections>> = courseDao.getCoursesSelectedForGeneration()
    val enrolledSections: Flow<List<SectionWithDetails>> = sectionDao.getEnrolledSections()
    val allSectionsWithDetails: Flow<List<SectionWithDetails>> = sectionDao.getAllSectionsWithDetails()
    val allDocuments: Flow<List<CourseDocument>> = documentDao.getAllDocuments()
    val passedCourses: Flow<List<PassedCourse>> = passedCourseDao.getAllPassedCourses()
    val attendanceRecords: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendance()
    val academicTerms: Flow<List<AcademicTermWithCourses>> = academicTermDao.getAllTerms()

    /**
     * Live row counts for the advanced-deletion dialog. `myCourses` mirrors
     * the Courses tab definition (selected for the generator OR enrolled), so
     * the number the user sees is exactly the number about to be deleted.
     * Catalog count is the complement inside the same snapshot.
     */
    val dataCounts: Flow<DataCounts> = combine(
        courseDao.getCoursesWithSections(),
        documentDao.getAllDocuments(),
        passedCourseDao.getAllPassedCourses(),
        attendanceDao.getAllAttendance()
    ) { courses, docs, passed, attendance ->
        var enrolledSections = 0
        var myCourses = 0
        var catalogCourses = 0
        for (cws in courses) {
            val enrolled = cws.sections.count { it.section.isEnrolled }
            enrolledSections += enrolled
            if (cws.course.isSelectedForGeneration || enrolled > 0) myCourses++ else catalogCourses++
        }
        DataCounts(
            enrolledSections = enrolledSections,
            myCourses = myCourses,
            catalogCourses = catalogCourses,
            documents = docs.size,
            passedCourses = passed.size,
            attendanceRecords = attendance.size
        )
    }

    fun getDocumentsByCourse(courseId: Long): Flow<List<CourseDocument>> {
        return documentDao.getDocumentsByCourse(courseId)
    }

    suspend fun insertDocument(document: CourseDocument): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: CourseDocument) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(id: Long) {
        documentDao.deleteDocumentById(id)
    }

    suspend fun toggleDocumentBookmark(id: Long, isBookmarked: Boolean) {
        documentDao.setBookmarked(id, isBookmarked)
    }

    fun searchCourses(query: String): Flow<List<Course>> {
        return courseDao.searchCourses(query)
    }

    suspend fun getCourseCount(): Int {
        return courseDao.getCourseCount()
    }

    /**
     * JSON/CSV import (restoring a backup). Runs in ONE transaction:
     * all-or-nothing, never partial.
     *
     * Re-import is UPSERT rather than append: a course already present under the
     * same NORMALIZED code is refreshed instead of duplicated. Previously this
     * path called [insertAll] unconditionally, so importing a file twice (or
     * importing a backup whose codes differed from the stored ones only by case
     * or spacing) silently doubled every course. Catalog fields are refreshed
     * while the user's own state — generator ticks, enrollment, documents — is
     * kept, exactly like [importPortalItems].
     */
    suspend fun importItems(items: List<ImportItem>, clearExisting: Boolean = false) {
        db.withTransaction {
            if (clearExisting) {
                clearAllData()
            }
            for (item in items) {
                syncCourse(item)
            }
        }
    }

    /**
     * Portal catalog import with UPSERT sync per course code (one transaction):
     * - New course → plain insert.
     * - Known course → catalog fields updated, but user-owned state
     *   (`isSelectedForGeneration`, section `isEnrolled`) is preserved, so a
     *   re-import never wipes enrollments, picks, or documents (ids are kept).
     * - Imported section matched by sectionCode → fields updated, sessions replaced.
     * - Stale sections (gone from the portal) are deleted ONLY when not enrolled;
     *   an enrolled section the user chose is always kept.
     */
    suspend fun importPortalItems(items: List<ImportItem>, clearExisting: Boolean = false) {
        db.withTransaction {
            if (clearExisting) {
                clearAllData()
                insertAll(items)
                return@withTransaction
            }
            for (item in items) {
                syncCourse(item)
            }
        }
    }

    private suspend fun insertAll(items: List<ImportItem>) {
        for (item in items) {
            val courseId = courseDao.insertCourse(item.course)
            for (secItem in item.sections) {
                val secWithCourse = secItem.section.copy(courseId = courseId)
                val sectionId = sectionDao.insertSection(secWithCourse)
                val sessionsWithSec = secItem.sessions.map { it.copy(sectionId = sectionId) }
                sectionDao.insertSessions(sessionsWithSec)
            }
        }
    }

    private suspend fun syncCourse(item: ImportItem) {
        // Matched in NORMALIZED form (trim + upper-case), so " math101 " and
        // "MATH101" are one course. The exact `code =` lookup used before
        // silently created a second row whenever the imported code differed
        // from the stored one only by case or spacing — even though the manual
        // create/edit paths already forbid that duplicate.
        val existing = findCourseByNormalizedCode(item.course.code)
        if (existing == null) {
            insertAll(listOf(item))
            return
        }
        // Catalog fields refresh; the user's own flags survive the re-import.
        courseDao.updateCourse(
            item.course.copy(
                id = existing.id,
                isSelectedForGeneration = existing.isSelectedForGeneration
            )
        )
        // Sections are matched in NORMALIZED form too, for the same reason:
        // uniqueness of a group code inside a course is defined by
        // `normalizeCode`, so an exact-string match here could insert a second
        // row that the create/edit paths would have refused.
        val existingSections = sectionDao.getSectionsByCourseId(existing.id)
            .associateBy { normalizeCode(it.sectionCode) }
        val importedCodes = mutableSetOf<String>()
        for (secItem in item.sections) {
            val importedCode = normalizeCode(secItem.section.sectionCode)
            importedCodes.add(importedCode)
            val old = existingSections[importedCode]
            if (old == null) {
                val sectionId = sectionDao.insertSection(secItem.section.copy(courseId = existing.id))
                sectionDao.insertSessions(secItem.sessions.map { it.copy(sectionId = sectionId) })
            } else {
                sectionDao.updateSection(
                    secItem.section.copy(
                        id = old.id,
                        courseId = existing.id,
                        isEnrolled = old.isEnrolled
                    )
                )
                sectionDao.deleteSessionsBySectionId(old.id)
                sectionDao.insertSessions(secItem.sessions.map { it.copy(sectionId = old.id) })
            }
        }
        // Drop stale catalog-only sections; never touch the user's enrolled pick.
        for ((normalizedCode, old) in existingSections) {
            if (normalizedCode !in importedCodes && !old.isEnrolled) {
                sectionDao.deleteSectionById(old.id)
            }
        }
    }

    /**
     * Inserts a manually created course together with its first section and
     * class sessions, in ONE transaction.
     *
     * Data-integrity rules (never "fix up" the input, never insert a bad row):
     * - blank course code / blank section code → rejected ([AddCourseResult.BlankCode] /
     *   [AddCourseResult.BlankSectionCode]); a course code is meaningful domain
     *   data, so it is never synthesized (the old code generated a random
     *   `CRS-1234` code, which silently produced unmatchable courses);
     * - the normalized course code must be unique in the catalog;
     * - the first section's sessions may not overlap each other.
     */
    suspend fun addManualCourse(
        course: Course,
        section: CourseSection,
        sessions: List<ClassSession>
    ): AddCourseResult = db.withTransaction {
        if (course.code.isBlank()) return@withTransaction AddCourseResult.BlankCode
        if (section.sectionCode.isBlank()) return@withTransaction AddCourseResult.BlankSectionCode
        if (findCourseByNormalizedCode(course.code) != null) {
            return@withTransaction AddCourseResult.DuplicateCode
        }
        when (SectionSessionValidator.validate(sessions)) {
            is SectionSessionValidator.Result.InvalidTime -> return@withTransaction AddCourseResult.InvalidSessions
            is SectionSessionValidator.Result.Overlap -> return@withTransaction AddCourseResult.ConflictingSessions
            SectionSessionValidator.Result.Valid -> Unit
        }
        val courseId = courseDao.insertCourse(course.copy(code = course.code.trim()))
        val sectionId = sectionDao.insertSection(
            section.copy(courseId = courseId, sectionCode = section.sectionCode.trim())
        )
        val sessionsWithSec = sessions.map { it.copy(sectionId = sectionId) }
        sectionDao.insertSessions(sessionsWithSec)
        AddCourseResult.Success(courseId)
    }

    /** Course whose code equals [code] after [normalizeCode], or null. */
    private suspend fun findCourseByNormalizedCode(code: String, excludingCourseId: Long? = null): Course? {
        val wanted = normalizeCode(code)
        return courseDao.getAllCoursesOnce()
            .firstOrNull { it.id != excludingCourseId && normalizeCode(it.code) == wanted }
    }

    /**
     * Adds a manual section/group to an existing course.
     *
     * Enforces the group invariants at the DATA layer (the dialogs check them
     * too for a nicer error, but the repository is the one that must hold):
     * - the code may not be blank (never invent a group number);
     * - the code must be unique inside this course (`MATH101` may have one and
     *   only one group "01"); the same code under a different course is fine;
     * - the sessions of the new group may not contradict each other.
     */
    suspend fun addSectionToCourse(
        courseId: Long,
        section: CourseSection,
        sessions: List<ClassSession>
    ): AddSectionResult = db.withTransaction {
        if (section.sectionCode.isBlank()) return@withTransaction AddSectionResult.BlankCode
        courseDao.getCourseById(courseId) ?: return@withTransaction AddSectionResult.CourseNotFound
        val newCode = normalizeCode(section.sectionCode)
        val duplicate = sectionDao.getSectionsByCourseId(courseId)
            .any { normalizeCode(it.sectionCode) == newCode }
        if (duplicate) return@withTransaction AddSectionResult.DuplicateCode
        when (SectionSessionValidator.validate(sessions)) {
            is SectionSessionValidator.Result.InvalidTime -> return@withTransaction AddSectionResult.InvalidSessions
            is SectionSessionValidator.Result.Overlap -> return@withTransaction AddSectionResult.ConflictingSessions
            SectionSessionValidator.Result.Valid -> Unit
        }
        val sectionId = sectionDao.insertSection(section.copy(courseId = courseId, sectionCode = section.sectionCode.trim()))
        val sessionsWithSec = sessions.map { it.copy(sectionId = sectionId) }
        sectionDao.insertSessions(sessionsWithSec)
        AddSectionResult.Success(sectionId)
    }

    suspend fun deleteCourse(courseId: Long) {
        courseDao.deleteCourseById(courseId)
    }

    sealed interface AddCourseResult {
        data class Success(val courseId: Long) : AddCourseResult
        /** Another course already uses this code (compared with [normalizeCode]). */
        data object DuplicateCode : AddCourseResult
        data object BlankCode : AddCourseResult
        data object BlankSectionCode : AddCourseResult
        /** Two sessions of the new group overlap each other. */
        data object ConflictingSessions : AddCourseResult
        /** Unparseable time or start >= end. */
        data object InvalidSessions : AddCourseResult
    }

    sealed interface AddSectionResult {
        data class Success(val sectionId: Long) : AddSectionResult
        /** This course already has a group with the same normalized code. */
        data object DuplicateCode : AddSectionResult
        data object BlankCode : AddSectionResult
        data object CourseNotFound : AddSectionResult
        data object ConflictingSessions : AddSectionResult
        data object InvalidSessions : AddSectionResult
    }

    sealed interface ApplyScheduleResult {
        data object Success : ApplyScheduleResult
        /** Nothing to apply (empty list). */
        data object Empty : ApplyScheduleResult
        /**
         * The candidate schedule does not cover every course currently selected
         * for generation, so applying it would DROP those courses from the user's
         * program. The write is refused and [missingCourseNames] is reported.
         */
        data class Incomplete(val missingCourseNames: List<String>) : ApplyScheduleResult
    }

    sealed interface UpdateCourseResult {
        data object Success : UpdateCourseResult
        data object DuplicateCode : UpdateCourseResult
        data object NotFound : UpdateCourseResult
        /** A course must always have a code — no silent "CRS-1234" fallback. */
        data object BlankCode : UpdateCourseResult
    }

    /**
     * Updates a course's own fields (name/code/department/credits) in place.
     * Sections, enrollments, generator flag and documents are untouched, so the
     * user never has to delete and re-add a course to fix a typo. Runs in a
     * transaction together with the duplicate-code check.
     *
     * Invariant parity with creation: a blank code is refused and the code is
     * compared in normalized form (same rule as [addManualCourse]), so editing
     * cannot produce the duplicate the create path already prevents.
     */
    suspend fun updateCourseDetails(
        courseId: Long,
        name: String,
        code: String,
        department: String,
        credits: Int
    ): UpdateCourseResult = db.withTransaction {
        if (code.isBlank()) return@withTransaction UpdateCourseResult.BlankCode
        val current = courseDao.getCourseById(courseId) ?: return@withTransaction UpdateCourseResult.NotFound
        if (findCourseByNormalizedCode(code, excludingCourseId = courseId) != null) {
            return@withTransaction UpdateCourseResult.DuplicateCode
        }
        courseDao.updateCourse(
            current.copy(name = name, code = code.trim(), department = department, credits = credits)
        )
        UpdateCourseResult.Success
    }

    suspend fun deleteSection(sectionId: Long) {
        sectionDao.deleteSectionById(sectionId)
    }

    sealed interface UpdateSectionResult {
        data object Success : UpdateSectionResult
        data object DuplicateCode : UpdateSectionResult
        data object NotFound : UpdateSectionResult
        data object BlankCode : UpdateSectionResult
        data object ConflictingSessions : UpdateSectionResult
        data object InvalidSessions : UpdateSectionResult
    }

    /**
     * Updates a section/group in place: code, instructor, exam fields plus the
     * full session list (replaced atomically). Enrollment flag is preserved so
     * editing an enrolled group never drops it from the weekly program.
     *
     * Same invariants as [addSectionToCourse]: non-blank unique code inside its
     * course, and a self-consistent session list.
     */
    suspend fun updateSectionDetails(
        sectionId: Long,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ClassSession>
    ): UpdateSectionResult = db.withTransaction {
        if (sectionCode.isBlank()) return@withTransaction UpdateSectionResult.BlankCode
        val current = sectionDao.getSectionById(sectionId)
            ?: return@withTransaction UpdateSectionResult.NotFound
        val newCode = normalizeCode(sectionCode)
        val clash = sectionDao.getSectionsByCourseId(current.courseId)
            .any { it.id != sectionId && normalizeCode(it.sectionCode) == newCode }
        if (clash) {
            return@withTransaction UpdateSectionResult.DuplicateCode
        }
        when (SectionSessionValidator.validate(sessions)) {
            is SectionSessionValidator.Result.InvalidTime -> return@withTransaction UpdateSectionResult.InvalidSessions
            is SectionSessionValidator.Result.Overlap -> return@withTransaction UpdateSectionResult.ConflictingSessions
            SectionSessionValidator.Result.Valid -> Unit
        }
        sectionDao.updateSection(
            current.copy(
                sectionCode = sectionCode.trim(),
                instructor = instructor.trim(),
                examDate = examDate.trim(),
                examStartTime = examStartTime.trim(),
                examEndTime = examEndTime.trim()
            )
        )
        sectionDao.deleteSessionsBySectionId(sectionId)
        sectionDao.insertSessions(sessions.map { it.copy(id = 0, sectionId = sectionId) })
        UpdateSectionResult.Success
    }

    suspend fun toggleCourseSelectedForGeneration(courseId: Long, isSelected: Boolean) {
        courseDao.setCourseSelectedForGeneration(courseId, isSelected)
    }

    suspend fun setSectionEnrolled(section: SectionWithDetails, isEnrolled: Boolean) {
        db.withTransaction {
            if (isEnrolled) {
                // Unenroll other sections of the same course so only 1 section is enrolled per course
                sectionDao.unenrollAllForCourse(section.course.id)
                sectionDao.setSectionEnrolled(section.section.id, true)
            } else {
                sectionDao.setSectionEnrolled(section.section.id, false)
            }
        }
    }

    /**
     * Replaces the weekly program with [sections] (the user's pick from the
     * generator or from a group).
     *
     * SAFETY: a partial result must never overwrite a complete program. The
     * candidate is rejected with [ApplyScheduleResult.Incomplete] when it does
     * not cover every course currently selected for generation — applying it
     * would silently remove those courses (the generator reports such courses in
     * `ScheduleSearchResult.skippedCourses` and refuses to apply them).
     */
    suspend fun applySchedule(sections: List<SectionWithDetails>): ApplyScheduleResult = db.withTransaction {
        if (sections.isEmpty()) return@withTransaction ApplyScheduleResult.Empty
        val selected = courseDao.getCoursesSelectedForGenerationOnce()
        val coveredCourseIds = sections.map { it.course.id }.toSet()
        val missing = selected.filterNot { coveredCourseIds.contains(it.id) }.map { it.name }
        if (missing.isNotEmpty()) {
            return@withTransaction ApplyScheduleResult.Incomplete(missing)
        }
        sectionDao.clearAllEnrollments()
        for (sec in sections) {
            sectionDao.setSectionEnrolled(sec.section.id, true)
        }
        ApplyScheduleResult.Success
    }

    suspend fun clearEnrollments() {
        sectionDao.clearAllEnrollments()
    }

    // ---------------------------------------------------------------------
    // Passed courses (academic record)
    // ---------------------------------------------------------------------

    sealed interface PassedCourseResult {
        data class Success(val id: Long) : PassedCourseResult
        data object BlankCode : PassedCourseResult
        data object BlankName : PassedCourseResult
        data object InvalidCredits : PassedCourseResult
        /** Another passed course already uses this normalized code. */
        data object DuplicateCode : PassedCourseResult
        data object NotFound : PassedCourseResult
    }

    /**
     * Inserts (id == 0) or updates a passed course. The code is compared with
     * [normalizeCode], so " math101 " cannot be recorded twice.
     */
    suspend fun savePassedCourse(course: PassedCourse): PassedCourseResult = db.withTransaction {
        val code = course.code.trim()
        val name = course.name.trim()
        if (code.isBlank()) return@withTransaction PassedCourseResult.BlankCode
        if (name.isBlank()) return@withTransaction PassedCourseResult.BlankName
        if (course.credits !in 0..30) return@withTransaction PassedCourseResult.InvalidCredits
        val wanted = normalizeCode(code)
        val duplicate = passedCourseDao.getAllPassedCoursesOnce()
            .any { it.id != course.id && normalizeCode(it.code) == wanted }
        if (duplicate) return@withTransaction PassedCourseResult.DuplicateCode
        val existing = if (course.id == 0L) null else passedCourseDao.getPassedCourseById(course.id)
        if (course.id != 0L && existing == null) {
            return@withTransaction PassedCourseResult.NotFound
        }
        val id = if (existing == null) {
            passedCourseDao.insertPassedCourse(
                course.copy(
                    id = 0,
                    code = code,
                    name = name,
                    grade = course.grade?.trim()?.ifBlank { null },
                    termLabel = course.termLabel?.trim()?.ifBlank { null },
                    note = course.note.trim(),
                    recordedAt = System.currentTimeMillis()
                )
            )
        } else {
            passedCourseDao.updatePassedCourse(
                course.copy(
                    code = code,
                    name = name,
                    grade = course.grade?.trim()?.ifBlank { null },
                    termLabel = course.termLabel?.trim()?.ifBlank { null },
                    note = course.note.trim()
                )
            )
            course.id
        }
        PassedCourseResult.Success(id)
    }

    suspend fun deletePassedCourse(id: Long) {
        passedCourseDao.deletePassedCourseById(id)
    }

    // ---------------------------------------------------------------------
    // Attendance
    // ---------------------------------------------------------------------

    /**
     * Records (or updates) attendance for one class occurrence.
     *
     * The logical key is (section, epoch day, start time): marking the same
     * class again updates the existing row instead of duplicating it.
     * Returns false when the section no longer exists (deleted group), so the
     * UI can report a real error instead of writing an orphan row.
     */
    suspend fun markAttendance(
        sectionId: Long,
        dateEpochDay: Long,
        startTime: String,
        endTime: String,
        status: AttendanceStatus,
        note: String = ""
    ): Boolean = db.withTransaction {
        if (sectionDao.getSectionById(sectionId) == null) return@withTransaction false
        val existing = attendanceDao.getAttendanceForOccurrence(sectionId, dateEpochDay, startTime)
        attendanceDao.upsertAttendance(
            AttendanceRecord(
                id = existing?.id ?: 0,
                sectionId = sectionId,
                dateEpochDay = dateEpochDay,
                startTime = startTime,
                endTime = endTime,
                status = status,
                note = note.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        true
    }

    suspend fun clearAttendance(sectionId: Long, dateEpochDay: Long, startTime: String) {
        attendanceDao.deleteAttendanceForOccurrence(sectionId, dateEpochDay, startTime)
    }

    suspend fun deleteAttendance(id: Long) {
        attendanceDao.deleteAttendanceById(id)
    }

    // ---------------------------------------------------------------------
    // End of term archive
    // ---------------------------------------------------------------------

    /** What the student decided for one course in the end-term form. */
    data class EndTermCourseEntry(
        val status: TermCourseStatus,
        val grade: String? = null
    )

    sealed interface EndTermResult {
        data class Success(
            val termId: Long,
            val archivedCourses: Int,
            val addedPassedCourses: Int
        ) : EndTermResult

        /** The form was submitted before every course had a status. */
        data class MissingStatuses(val courseNames: List<String>) : EndTermResult

        /** Nothing is selected or enrolled, so there is no term to archive. */
        data object NoCourses : EndTermResult
    }

    /**
     * Archives the current term in ONE transaction:
     * 1. snapshot the term + every current course (selected for the generator
     *    OR enrolled in a group) with its final status/grade/attendance stats;
     * 2. snapshot the enrolled group's class times;
     * 3. add passed courses to the academic record WITHOUT overwriting an
     *    earlier result for the same normalized code;
     * 4. only then clear the "active" flags (generator ticks + enrollments).
     *
     * Catalog rows, documents and attendance history are NEVER deleted: the
     * archive is an additional snapshot, so an interrupted operation can only
     * roll back completely. The DataStore "term active" flag is written by the
     * caller after this returns, so a failure here cannot mark a term finished.
     */
    suspend fun endCurrentTerm(
        termName: String,
        startEpochDay: Long?,
        endEpochDay: Long,
        entries: Map<Long, EndTermCourseEntry>
    ): EndTermResult = db.withTransaction {
        val snapshot = courseDao.getCoursesWithSectionsOnce()
        val currentCourses = snapshot.filter { cws ->
            cws.course.isSelectedForGeneration || cws.sections.any { it.section.isEnrolled }
        }
        if (currentCourses.isEmpty()) return@withTransaction EndTermResult.NoCourses

        val missing = currentCourses
            .filter { entries[it.course.id] == null }
            .map { it.course.name }
        if (missing.isNotEmpty()) return@withTransaction EndTermResult.MissingStatuses(missing)

        val attendanceBySection = attendanceDao.getAllAttendanceOnce().groupBy { it.sectionId }
        val archivedAt = System.currentTimeMillis()
        val safeTermName = termName.trim().ifBlank { "ترم پایان‌یافته" }

        val termId = academicTermDao.insertTerm(
            AcademicTerm(
                name = safeTermName,
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay,
                endedAt = archivedAt
            )
        )

        // Build the archive in memory first so a single insert sets every id,
        // then attach the group's sessions to the real archived-course ids.
        val pendingCourses = currentCourses.map { cws ->
            val entry = entries.getValue(cws.course.id)
            val group = cws.sections.firstOrNull { it.section.isEnrolled }
            val records = cws.sections
                .flatMap { section -> attendanceBySection[section.section.id].orEmpty() }
            val stats = AttendanceStats.from(records)
            val archivedCourse = ArchivedCourse(
                termId = termId,
                courseId = cws.course.id,
                code = cws.course.code,
                name = cws.course.name,
                credits = cws.course.credits,
                department = cws.course.department,
                sectionCode = group?.section?.sectionCode.orEmpty(),
                instructor = group?.section?.instructor.orEmpty(),
                status = entry.status,
                grade = entry.grade?.trim()?.ifBlank { null },
                attendancePresent = stats.present,
                attendanceAbsent = stats.absent,
                attendanceLate = stats.late,
                attendanceExcused = stats.excused,
                archivedAt = archivedAt
            )
            val sessions = group?.sessions.orEmpty().map { session ->
                ArchivedClassSession(
                    archivedCourseId = 0,
                    dayOfWeek = session.dayOfWeek,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    location = session.location,
                    weekType = session.weekType
                )
            }
            archivedCourse to sessions
        }

        val archivedIds = academicTermDao.insertArchivedCourses(pendingCourses.map { it.first })
        val sessionsToInsert = pendingCourses.flatMapIndexed { index, (_, sessions) ->
            sessions.map { it.copy(archivedCourseId = archivedIds[index]) }
        }
        if (sessionsToInsert.isNotEmpty()) {
            academicTermDao.insertArchivedSessions(sessionsToInsert)
        }

        // Academic record: only add what is not there yet. An older result for
        // the same normalized code is history and must never be rewritten.
        val existingPassedCodes = passedCourseDao.getAllPassedCoursesOnce()
            .map { normalizeCode(it.code) }
            .toMutableSet()
        var addedPassed = 0
        for (cws in currentCourses) {
            val entry = entries.getValue(cws.course.id)
            if (entry.status != TermCourseStatus.PASSED) continue
            if (cws.course.code.isBlank() || cws.course.name.isBlank()) continue
            if (!existingPassedCodes.add(normalizeCode(cws.course.code))) continue
            passedCourseDao.insertPassedCourse(
                PassedCourse(
                    code = cws.course.code,
                    name = cws.course.name,
                    credits = cws.course.credits,
                    grade = entry.grade?.trim()?.ifBlank { null },
                    termLabel = safeTermName,
                    note = "ثبت خودکار در پایان ترم",
                    recordedAt = archivedAt
                )
            )
            addedPassed++
        }

        // Leave the active term only after the snapshot is safely written.
        courseDao.clearAllSelectedForGeneration()
        sectionDao.clearAllEnrollments()

        EndTermResult.Success(
            termId = termId,
            archivedCourses = pendingCourses.size,
            addedPassedCourses = addedPassed
        )
    }

    // ---------------------------------------------------------------------
    // Selective data deletion
    // ---------------------------------------------------------------------

    /**
     * Deletes exactly the domains the user selected, in one transaction.
     *
     * CASCADE rules make the dependency explicit: removing a course also
     * removes its groups, sessions, documents and attendance history. The UI
     * shows that dependency before confirming. Preferences live in DataStore
     * and are reset by the ViewModel, not here.
     */
    suspend fun clearData(selection: DataDeletionSelection) {
        if (!selection.touchesDatabase) return
        db.withTransaction {
            // «دروس من» is resolved BEFORE clearing enrollments: a course that
            // is enrolled but not marked for generation must still be deleted
            // when both flags are selected (ALL preset).
            if (selection.myCourses) {
                val selectedIds = courseDao.getCoursesSelectedForGenerationOnce().map { it.id }
                val enrolledIds = sectionDao.getEnrolledCourseIdsOnce()
                val ids = (selectedIds + enrolledIds).distinct()
                if (ids.isNotEmpty()) courseDao.deleteCoursesByIds(ids)
            }
            if (selection.schedule) {
                sectionDao.clearAllEnrollments()
            }
            if (selection.catalog) {
                // Recomputed after "my courses" removal: only rows that are
                // neither selected nor enrolled may be dropped.
                val enrolledIds = sectionDao.getEnrolledCourseIdsOnce().toSet()
                val catalogIds = courseDao.getAllCoursesOnce()
                    .filter { !it.isSelectedForGeneration && it.id !in enrolledIds }
                    .map { it.id }
                if (catalogIds.isNotEmpty()) courseDao.deleteCoursesByIds(catalogIds)
            }
            if (selection.documents) documentDao.deleteAllDocuments()
            if (selection.passedCourses) passedCourseDao.deleteAllPassedCourses()
            if (selection.attendance) attendanceDao.deleteAllAttendance()
        }
    }

    /**
     * Catalog restore only: clears courses/sections/sessions/documents.
     * The academic record (separate table) survives on purpose. Attendance
     * rows follow their group through CASCADE: once the group is gone there is
     * no occurrence left to attribute them to. Full user-data removal is only
     * done through [clearData] with an explicit selection.
     */
    suspend fun clearAllData() {
        db.withTransaction {
            documentDao.deleteAllDocuments()
            sectionDao.deleteAllSessions()
            sectionDao.deleteAllSections()
            courseDao.deleteAllCourses()
        }
    }
}
