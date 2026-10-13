package ir.courseplanner.app.ui

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.engine.CourseSuggestionRanker.SuggestionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


/**
 * End-to-end ViewModel coverage for the discovery flow, on the real
 * repository (in-memory Room) and the real ViewModel:
 * - FAB opens the discovery sub-screen and back returns to «دروس من»;
 * - the catalog search sees every catalog course;
 * - adding a course lands it in «دروس من» without duplicates;
 * - incompatible choices are still reported honestly;
 * - the manual-add path keeps working.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddCourseFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CourseRepository
    private lateinit var viewModel: CoursePlannerViewModel
    private lateinit var collectorScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CourseRepository(db, db.courseDao(), db.sectionDao(), db.documentDao())
        viewModel = CoursePlannerViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            repository = repository,
            preferencesManager = PreferencesManager(context)
        )
        // `stateIn(WhileSubscribed)` flows need a subscriber before `.value`
        // reflects the database.
        collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        collectorScope.launch {
            launch { viewModel.coursesWithSections.collect { } }
            launch { viewModel.sectionsByCourse.collect { } }
            launch { viewModel.enrolledSections.collect { } }
            launch { viewModel.addCourseSuggestions.collect { } }
            launch { viewModel.addableCourseCount.collect { } }
        }
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
        Dispatchers.resetMain()
        db.close()
    }

    // __HELPERS__
    private suspend fun seedCatalog() {
        val mathId = db.courseDao().insertCourse(
            Course(code = "MATH101", name = "ریاضی ۱", department = "علوم پایه", credits = 3)
        )
        val physId = db.courseDao().insertCourse(
            Course(code = "PHYS204", name = "فیزیک عمومی", department = "علوم پایه", credits = 3)
        )
        val mathSec = db.sectionDao().insertSection(
            CourseSection(courseId = mathId, sectionCode = "01", instructor = "دکتر تست")
        )
        db.sectionDao().insertSessions(
            listOf(
                ClassSession(sectionId = mathSec, dayOfWeek = 0, startTime = "08:00", endTime = "10:00"),
                ClassSession(sectionId = mathSec, dayOfWeek = 2, startTime = "08:00", endTime = "10:00")
            )
        )
        val physSec = db.sectionDao().insertSection(
            CourseSection(courseId = physId, sectionCode = "01", instructor = "دکتر تست‌چی")
        )
        db.sectionDao().insertSessions(
            listOf(ClassSession(sectionId = physSec, dayOfWeek = 1, startTime = "10:00", endTime = "12:00"))
        )
        // Let every WhileSubscribed flow catch up with the seeded rows.
        withTimeout(10_000) { viewModel.coursesWithSections.first { it.size == 2 } }
        withTimeout(10_000) { viewModel.addCourseSuggestions.first { it.size == 2 } }
    }

    @Test
    fun openAddCourseNavigatesToDiscovery() = kotlinx.coroutines.runBlocking {
        seedCatalog()

        viewModel.openAddCourse()
        assertEquals(AppDestination.ADD_COURSE, viewModel.currentDestination.value)

        viewModel.navigateTo(AppDestination.COURSES)
        assertEquals(AppDestination.COURSES, viewModel.currentDestination.value)
    }

    @Test
    fun discoverySearchFindsEveryCatalogCourse() = kotlinx.coroutines.runBlocking {
        seedCatalog()

        viewModel.onAddCourseQueryChange("فیزیک")
        // Wait for the FILTERED emission (size 1): the previous unfiltered
        // list also contains PHYS204, so matching on membership alone would
        // accept a stale emission.
        withTimeout(10_000) {
            viewModel.addCourseSuggestions.first { list ->
                list.size == 1 && list.any { it.course.code == "PHYS204" }
            }
        }
        assertEquals(1, viewModel.addCourseSuggestions.value.size)
        assertEquals("PHYS204", viewModel.addCourseSuggestions.value.single().course.code)

        viewModel.onAddCourseQueryChange("علوم پایه")
        withTimeout(10_000) { viewModel.addCourseSuggestions.first { it.size == 2 } }

        // Courses-tab search stays independent from the discovery search.
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun addingCourseLandsInMyCoursesExactlyOnce() = kotlinx.coroutines.runBlocking {
        seedCatalog()
        val before = viewModel.coursesWithSections.value.size

        viewModel.addCatalogCourseToMine(
            viewModel.coursesWithSections.value.first { it.course.code == "MATH101" }.course.id
        )
        withTimeout(10_000) {
            viewModel.coursesWithSections.first { list ->
                list.any { it.course.code == "MATH101" && it.course.isSelectedForGeneration }
            }
        }
        // Toggle API is idempotent: a second call does not duplicate the row.
        viewModel.addCatalogCourseToMine(
            viewModel.coursesWithSections.value.first { it.course.code == "MATH101" }.course.id
        )
        withTimeout(10_000) { viewModel.addCourseSuggestions.first { it.isNotEmpty() } }

        val mathRows = viewModel.coursesWithSections.value.count { it.course.code == "MATH101" }
        assertEquals(1, mathRows)
        assertEquals(before, viewModel.coursesWithSections.value.size)

        val suggestion = viewModel.addCourseSuggestions.value
            .first { it.course.code == "MATH101" }
        assertEquals(SuggestionStatus.ALREADY_ADDED, suggestion.status)
        assertTrue(suggestion.alreadyAdded)
        assertEquals(1, viewModel.addableCourseCount.value)
    }

    @Test
    fun conflictingGroupIsReportedAndEnrollmentStillWorks() = kotlinx.coroutines.runBlocking {
        seedCatalog()

        // Enroll the MATH101 group first (Saturday 08:00-10:00).
        val mathDetails = viewModel.sectionsByCourse.value.values.flatten()
            .first { it.course.code == "MATH101" }
        viewModel.toggleSectionEnrolled(mathDetails, true)
        withTimeout(10_000) { viewModel.enrolledSections.first { it.isNotEmpty() } }

        // Add a catalog course whose only group clashes with it.
        val clashCourseId = db.courseDao().insertCourse(
            Course(code = "CLASH1", name = "درس متداخل", credits = 2)
        )
        val clashSec = db.sectionDao().insertSection(
            CourseSection(courseId = clashCourseId, sectionCode = "01")
        )
        db.sectionDao().insertSessions(
            listOf(ClassSession(sectionId = clashSec, dayOfWeek = 0, startTime = "08:30", endTime = "09:30"))
        )
        withTimeout(10_000) {
            viewModel.addCourseSuggestions.first { list ->
                // Sections and their sessions arrive over separate Room flows:
                // wait until the clash group's class time is actually loaded,
                // otherwise the verdict is honestly NO_TIME_DATA, not a clash.
                list.any { suggestion ->
                    suggestion.course.code == "CLASH1" &&
                        suggestion.groups.any { it.section.sessions.isNotEmpty() }
                }
            }
        }

        val clash = viewModel.addCourseSuggestions.value.first { it.course.code == "CLASH1" }
        assertEquals(SuggestionStatus.NO_COMPATIBLE_GROUP, clash.status)

        // The clash is explainable through the existing conflict API.
        val conflict = viewModel.checkConflictForCandidate(clash.groups.single().section)
        assertTrue(conflict != null)

        // Enrolling an already-enrolled section keeps a single enrollment.
        viewModel.toggleSectionEnrolled(mathDetails, true)
        withTimeout(10_000) { viewModel.enrolledSections.first { it.size == 1 } }
        assertEquals(1, viewModel.enrolledSections.value.size)
    }

    @Test
    fun manualAddKeepsWorkingAfterDiscoveryFlow() = kotlinx.coroutines.runBlocking {
        viewModel.addManualCourse(
            name = "درس دستی",
            code = "MAN999",
            department = "دستی",
            credits = 2,
            sectionCode = "01",
            instructor = "",
            examDate = "",
            examStartTime = "",
            examEndTime = "",
            sessions = listOf(
                ManualSessionInput(dayOfWeek = 3, startTime = "10:00", endTime = "12:00")
            )
        )
        withTimeout(10_000) {
            viewModel.coursesWithSections.first { list -> list.any { it.course.code == "MAN999" } }
        }
        assertTrue(
            viewModel.coursesWithSections.value.any {
                it.course.code == "MAN999" && it.course.isSelectedForGeneration
            }
        )
    }
}
