package ir.courseplanner.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.DataDeletionSelection
import ir.courseplanner.app.data.model.PassedCourse
import ir.courseplanner.app.data.model.WeekType
import kotlinx.coroutines.runBlocking
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
 * Selective deletion must remove exactly the selected domains. In particular:
 * - the schedule preset never deletes courses or history,
 * - "my courses" also removes enrolled-only courses (not just selected ones),
 * - catalog-only removal never touches the student's own term,
 * - deleting a course cascades its documents/attendance but not the transcript.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataDeletionTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CourseRepository
    private var enrolledOnlyId = 0L
    private var selectedId = 0L
    private var catalogId = 0L

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CourseRepository(
            db = db,
            courseDao = db.courseDao(),
            sectionDao = db.sectionDao(),
            documentDao = db.documentDao(),
            passedCourseDao = db.passedCourseDao(),
            attendanceDao = db.attendanceDao()
        )

        enrolledOnlyId = createCourse("ENROLLED1", selected = false)
        selectedId = createCourse("SELECTED2", selected = true)
        catalogId = createCourse("CATALOG3", selected = false)

        val enrolledSection = db.sectionDao().getSectionsByCourseId(enrolledOnlyId).single()
        db.sectionDao().setSectionEnrolled(enrolledSection.id, true)

        repository.insertDocument(CourseDocument(courseId = enrolledOnlyId, title = "جزوه"))
        repository.savePassedCourse(
            PassedCourse(code = "ENROLLED1", name = "درس گذرانده", credits = 3)
        )
        repository.markAttendance(
            sectionId = enrolledSection.id,
            dateEpochDay = 20_100L,
            startTime = "08:00",
            endTime = "10:00",
            status = AttendanceStatus.PRESENT
        )
        Unit
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun createCourse(code: String, selected: Boolean): Long {
        repository.addManualCourse(
            course = Course(
                code = code,
                name = "درس $code",
                credits = 3,
                isSelectedForGeneration = selected
            ),
            section = CourseSection(courseId = 0, sectionCode = "01"),
            sessions = listOf(
                ClassSession(
                    sectionId = 0,
                    dayOfWeek = 0,
                    startTime = "08:00",
                    endTime = "10:00",
                    weekType = WeekType.EVERY_WEEK
                )
            )
        )
        return db.courseDao().getCourseByCode(code)!!.id
    }

    @Test
    fun `an empty selection deletes nothing`() = runBlocking {
        repository.clearData(DataDeletionSelection())

        assertEquals(3, db.courseDao().getCourseCount())
        assertEquals(1, db.sectionDao().getEnrolledSectionCount())
        assertEquals(1, db.passedCourseDao().getPassedCourseCount())
        assertEquals(1, db.attendanceDao().getAttendanceCount())
        assertEquals(1, db.documentDao().countDocuments())
    }

    @Test
    fun `schedule preset clears only the weekly program`() = runBlocking {
        repository.clearData(DataDeletionSelection.SCHEDULE_ONLY)

        assertEquals(3, db.courseDao().getCourseCount())
        assertEquals(0, db.sectionDao().getEnrolledSectionCount())
        assertEquals(1, db.passedCourseDao().getPassedCourseCount())
        assertEquals(1, db.attendanceDao().getAttendanceCount())
        assertEquals(1, db.documentDao().countDocuments())
    }

    @Test
    fun `my courses removes selected and enrolled courses and cascades dependents`() = runBlocking {
        repository.clearData(DataDeletionSelection(myCourses = true))

        assertEquals("catalog-only course survives", 1, db.courseDao().getCourseCount())
        assertEquals(catalogId, db.courseDao().getAllCoursesOnce().single().id)
        assertEquals("course documents cascade", 0, db.documentDao().countDocuments())
        assertEquals("attendance cascades", 0, db.attendanceDao().getAttendanceCount())
        assertEquals("transcript is a separate table", 1, db.passedCourseDao().getPassedCourseCount())
    }

    @Test
    fun `schedule plus my courses also removes an enrolled-only course`() = runBlocking {
        // Regression: resolving enrolled ids AFTER clearAllEnrollments would
        // leave an enrolled-only course behind.
        repository.clearData(DataDeletionSelection(schedule = true, myCourses = true))

        assertEquals(1, db.courseDao().getCourseCount())
        assertEquals(catalogId, db.courseDao().getAllCoursesOnce().single().id)
        assertFalse(db.courseDao().getAllCoursesOnce().any { it.id == enrolledOnlyId })
        assertFalse(db.courseDao().getAllCoursesOnce().any { it.id == selectedId })
    }

    @Test
    fun `catalog-only removal leaves my courses and history intact`() = runBlocking {
        repository.clearData(DataDeletionSelection(catalog = true))

        val ids = db.courseDao().getAllCoursesOnce().map { it.id }.toSet()
        assertEquals(setOf(enrolledOnlyId, selectedId), ids)
        assertEquals(1, db.sectionDao().getEnrolledSectionCount())
        assertEquals(1, db.attendanceDao().getAttendanceCount())
        assertEquals(1, db.documentDao().countDocuments())
        assertEquals(1, db.passedCourseDao().getPassedCourseCount())
    }

    @Test
    fun `all preset clears every database domain`() = runBlocking {
        repository.clearData(DataDeletionSelection.ALL)

        assertEquals(0, db.courseDao().getCourseCount())
        assertEquals(0, db.sectionDao().getEnrolledSectionCount())
        assertEquals(0, db.passedCourseDao().getPassedCourseCount())
        assertEquals(0, db.attendanceDao().getAttendanceCount())
        assertEquals(0, db.documentDao().countDocuments())
    }

    @Test
    fun `selection defaults are opt-in and presets never include hidden extras`() {
        val empty = DataDeletionSelection()
        assertTrue(empty.isEmpty)
        assertFalse(empty.touchesDatabase)
        assertTrue(empty.summaryFa().isEmpty())

        val mine = DataDeletionSelection.MY_DATA
        assertTrue(mine.myCourses)
        assertTrue(mine.passedCourses)
        assertTrue(mine.attendance)
        assertFalse("catalog is never implied", mine.catalog)
        assertFalse("preferences need explicit consent", mine.preferences)

        val all = DataDeletionSelection.ALL
        assertTrue(all.preferences)
        assertTrue(all.catalog)
        assertTrue(all.summaryFa().any { it.contains("تنظیمات") })
    }
}
