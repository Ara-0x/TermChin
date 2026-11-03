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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ViewModel-level guard for the schedule generator result:
 * - a result that skipped a selected course is marked incomplete, the Apply
 *   button stays disabled (`canApplyCurrent == false`) and
 *   `applyCurrentGeneratedSchedule()` refuses to run,
 * - a complete result applies and replaces the program,
 * - refusals/errors reach the user instead of dying silently in a coroutine.
 *
 * Uses the real repository (in-memory Room) and the real view model, with an
 * unconfined main dispatcher so `viewModelScope` runs eagerly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GenerationApplyGuardTest {

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
        repository = CourseRepository(
            db,
            db.courseDao(),
            db.sectionDao(),
            db.documentDao(),
            db.passedCourseDao(),
            db.attendanceDao()
        )
        viewModel = CoursePlannerViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            repository = repository,
            preferencesManager = PreferencesManager(context)
        )
        // `stateIn(WhileSubscribed)` flows need a subscriber before their `.value`
        // reflects the database.
        collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        collectorScope.launch {
            launch { viewModel.coursesWithSections.collect { } }
            launch { viewModel.allSections.collect { } }
            launch { viewModel.enrolledSections.collect { } }
            launch { viewModel.generationState.collect { } }
        }
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
        Dispatchers.resetMain()
        db.close()
    }

    /** Inserts a course with one weekly group, or a selected course without any group. */
    private suspend fun seedCourse(code: String, name: String = "درس $code", day: Int = 0, withGroup: Boolean = true): Long {
        if (!withGroup) {
            return db.courseDao().insertCourse(
                Course(code = code, name = name, credits = 3, isSelectedForGeneration = true)
            )
        }
        repository.addManualCourse(
            Course(code = code, name = name, credits = 3, isSelectedForGeneration = true),
            CourseSection(courseId = 0, sectionCode = "01", instructor = "استاد"),
            listOf(ClassSession(sectionId = 0, dayOfWeek = day, startTime = "10:00", endTime = "12:00"))
        )
        val courseId = db.courseDao().getCourseByCode(code)!!.id
        return db.sectionDao().getSectionsByCourseId(courseId).single().let { section ->
            db.sectionDao().setSectionEnrolled(section.id, true)
            section.id
        }
    }

    /**
     * Waits until the ViewModel's cached flows actually reflect [expectedCourses]
     * rows (plus at least one group). Waiting for "non-empty" is not enough: the
     * generator reads `.value`, so a course inserted right after the first one
     * could still be missing and make the run look complete.
     */
    private suspend fun awaitWarmData(expectedCourses: Int) {
        withTimeout(10_000) { viewModel.coursesWithSections.first { it.size == expectedCourses } }
        withTimeout(10_000) { viewModel.allSections.first { it.isNotEmpty() } }
    }

    @Test
    fun `incomplete generation cannot be applied and keeps current enrollments`() = runBlocking {
        val enrolledSectionId = seedCourse("MATH101", name = "ریاضی ۱", day = 0)
        seedCourse("EMPTY", name = "بدون گروه", withGroup = false)
        awaitWarmData(expectedCourses = 2)

        viewModel.runScheduleGenerator()
        val state = withTimeout(10_000) { viewModel.generationState.first { it.isGenerated } }

        assertEquals(listOf("بدون گروه"), state.skippedCourses)
        assertFalse("a skipped course makes the result incomplete", state.isComplete)
        assertFalse("Apply must stay disabled", state.canApplyCurrent)
        assertTrue("the partial result is still shown", state.combinations.isNotEmpty())

        viewModel.applyCurrentGeneratedSchedule()
        assertTrue(viewModel.isErrorMessage.value)
        assertTrue(viewModel.userMessage.value!!.contains("کامل نیست"))
        assertEquals(
            "the student's existing program was not touched",
            listOf(enrolledSectionId),
            db.sectionDao().getEnrolledSections().first().map { it.section.id }
        )
    }

    @Test
    fun `complete generation is applicable and replaces the program`() = runBlocking {
        seedCourse("MATH101", name = "ریاضی ۱", day = 0)
        seedCourse("PHYS101", name = "فیزیک ۱", day = 1)
        awaitWarmData(expectedCourses = 2)

        viewModel.runScheduleGenerator()
        val state = withTimeout(10_000) { viewModel.generationState.first { it.isGenerated } }

        assertTrue(state.skippedCourses.isEmpty())
        assertTrue(state.isComplete)
        assertTrue(state.canApplyCurrent)

        viewModel.applyCurrentGeneratedSchedule()
        withTimeout(10_000) { viewModel.enrolledSections.first { it.size == 2 } }
        assertEquals(AppDestination.HOME, viewModel.currentDestination.value)
    }

    @Test
    fun `duplicate manual course is refused with a visible error`() = runBlocking {
        seedCourse("MATH101", name = "ریاضی ۱", day = 0)

        viewModel.addManualCourse(
            name = "ریاضی ۲",
            code = " math101 ",
            department = "",
            credits = 3,
            sectionCode = "01",
            instructor = "",
            examDate = "",
            examStartTime = "",
            examEndTime = "",
            sessions = listOf(ManualSessionInput(dayOfWeek = 0, startTime = "08:00", endTime = "10:00"))
        )

        // The write happens off the caller's stack: wait for the user-visible error.
        val message = withTimeout(10_000) { viewModel.userMessage.first { it != null } }
        assertTrue(viewModel.isErrorMessage.value)
        assertTrue(message!!.contains("قبلاً"))
        assertEquals("nothing was written", 1, db.courseDao().getCourseCount())
    }
}
