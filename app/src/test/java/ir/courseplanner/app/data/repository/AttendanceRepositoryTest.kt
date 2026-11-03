package ir.courseplanner.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.WeekType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Attendance keyed by (section, day, start time):
 * - re-marking the same occurrence updates instead of duplicating,
 * - editing a group (which replaces class_sessions) keeps history,
 * - deleting a group cascades its history rather than leaving orphans.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttendanceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CourseRepository

    @Before
    fun setup() {
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
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun course(code: String) = Course(code = code, name = "درس $code", credits = 3)

    private fun session(start: String = "08:00", end: String = "10:00") = ClassSession(
        sectionId = 0,
        dayOfWeek = 0,
        startTime = start,
        endTime = end,
        weekType = WeekType.EVERY_WEEK
    )

    private suspend fun createCourse(code: String = "MATH101"): Pair<Long, Long> {
        repository.addManualCourse(
            course = course(code),
            section = CourseSection(courseId = 0, sectionCode = "01"),
            sessions = listOf(session())
        )
        val courseId = db.courseDao().getCourseByCode(code)!!.id
        val sectionId = db.sectionDao().getSectionsByCourseId(courseId).single().id
        return courseId to sectionId
    }

    @Test
    fun `marking the same occurrence twice updates one row`() = runBlocking {
        val (_, sectionId) = createCourse()
        val day = 20_000L

        assertTrue(
            repository.markAttendance(
                sectionId = sectionId,
                dateEpochDay = day,
                startTime = "08:00",
                endTime = "10:00",
                status = AttendanceStatus.ABSENT,
                note = "پزشک"
            )
        )
        assertTrue(
            repository.markAttendance(
                sectionId = sectionId,
                dateEpochDay = day,
                startTime = "08:00",
                endTime = "10:00",
                status = AttendanceStatus.PRESENT,
                note = ""
            )
        )

        assertEquals(1, db.attendanceDao().getAttendanceCount())
        val stored = db.attendanceDao().getAttendanceForOccurrence(sectionId, day, "08:00")!!
        assertEquals(AttendanceStatus.PRESENT, stored.status)
        assertEquals("", stored.note)
    }

    @Test
    fun `editing the group keeps its attendance history`() = runBlocking {
        val (_, sectionId) = createCourse()
        val day = 20_001L
        repository.markAttendance(
            sectionId = sectionId,
            dateEpochDay = day,
            startTime = "08:00",
            endTime = "10:00",
            status = AttendanceStatus.LATE
        )

        // Updating the group replaces every class_sessions row (new ids), which
        // is exactly why attendance points at the section, not the session.
        assertEquals(
            CourseRepository.UpdateSectionResult.Success,
            repository.updateSectionDetails(
                sectionId = sectionId,
                sectionCode = "01",
                instructor = "استاد جدید",
                examDate = "",
                examStartTime = "",
                examEndTime = "",
                sessions = listOf(session(start = "13:00", end = "15:00"))
            )
        )

        assertEquals(1, db.attendanceDao().getAttendanceCount())
        val stored = db.attendanceDao().getAttendanceForOccurrence(sectionId, day, "08:00")
        assertNotNull(stored)
        assertEquals(sectionId, stored!!.sectionId)
        assertEquals(AttendanceStatus.LATE, stored.status)
    }

    @Test
    fun `deleting the group cascades its attendance history`() = runBlocking {
        val (_, sectionId) = createCourse()
        repository.markAttendance(
            sectionId = sectionId,
            dateEpochDay = 20_002L,
            startTime = "08:00",
            endTime = "10:00",
            status = AttendanceStatus.PRESENT
        )
        assertEquals(1, db.attendanceDao().getAttendanceCount())

        repository.deleteSection(sectionId)

        assertEquals("no orphan attendance rows", 0, db.attendanceDao().getAttendanceCount())
    }

    @Test
    fun `marking a deleted group is refused`() = runBlocking {
        val (_, sectionId) = createCourse()
        repository.deleteSection(sectionId)

        assertFalse(
            repository.markAttendance(
                sectionId = sectionId,
                dateEpochDay = 20_003L,
                startTime = "08:00",
                endTime = "10:00",
                status = AttendanceStatus.PRESENT
            )
        )
        assertEquals(0, db.attendanceDao().getAttendanceCount())
    }
}
