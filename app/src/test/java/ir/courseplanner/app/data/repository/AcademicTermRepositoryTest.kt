package ir.courseplanner.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.PassedCourse
import ir.courseplanner.app.data.model.TermCourseStatus
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
 * End-of-term invariants:
 * - the archive is written atomically and only after every status is supplied;
 * - the snapshot survives course/group deletion (no live FK);
 * - ending clears ONLY the active selection/enrollment flags;
 * - re-taking a course keeps both the old archive and the old passed result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicTermRepositoryTest {

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
            attendanceDao = db.attendanceDao(),
            academicTermDao = db.academicTermDao()
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun session(start: String = "08:00", end: String = "10:00") = ClassSession(
        sectionId = 0,
        dayOfWeek = 0,
        startTime = start,
        endTime = end,
        location = "کلاس ۱۰۱",
        weekType = WeekType.EVERY_WEEK
    )

    private suspend fun createActiveCourse(
        code: String = "MATH101",
        credits: Int = 3,
        selected: Boolean = true,
        enrolled: Boolean = true
    ): Pair<Long, Long> {
        repository.addManualCourse(
            course = Course(code = code, name = "درس $code", credits = credits),
            section = CourseSection(courseId = 0, sectionCode = "01", instructor = "استاد نمونه"),
            sessions = listOf(session())
        )
        val courseId = db.courseDao().getCourseByCode(code)!!.id
        val sectionId = db.sectionDao().getSectionsByCourseId(courseId).single().id
        db.courseDao().setCourseSelectedForGeneration(courseId, selected)
        db.sectionDao().setSectionEnrolled(sectionId, enrolled)
        return courseId to sectionId
    }

    private suspend fun endTerm(
        name: String,
        endEpochDay: Long,
        entries: Map<Long, CourseRepository.EndTermCourseEntry>
    ) = repository.endCurrentTerm(
        termName = name,
        startEpochDay = endEpochDay - 100,
        endEpochDay = endEpochDay,
        entries = entries
    )

    @Test
    fun `ending archives a full snapshot and clears only active flags`() = runBlocking {
        val (courseId, sectionId) = createActiveCourse()
        repository.markAttendance(
            sectionId = sectionId,
            dateEpochDay = 20_001L,
            startTime = "08:00",
            endTime = "10:00",
            status = AttendanceStatus.PRESENT
        )
        repository.markAttendance(
            sectionId = sectionId,
            dateEpochDay = 20_002L,
            startTime = "08:00",
            endTime = "10:00",
            status = AttendanceStatus.ABSENT
        )

        val result = endTerm(
            name = "نیم‌سال اول ۱۴۰۴-۱۴۰۵",
            endEpochDay = 20_100L,
            entries = mapOf(
                courseId to CourseRepository.EndTermCourseEntry(
                    status = TermCourseStatus.PASSED,
                    grade = " ۱۸٫۵ "
                )
            )
        )
        assertTrue(result is CourseRepository.EndTermResult.Success)

        val term = db.academicTermDao().getTermById((result as CourseRepository.EndTermResult.Success).termId)!!
        assertEquals("نیم‌سال اول ۱۴۰۴-۱۴۰۵", term.term.name)
        assertEquals(1, term.courses.size)

        val archived = term.courses.single().course
        assertEquals("MATH101", archived.code)
        assertEquals(3, archived.credits)
        assertEquals("01", archived.sectionCode)
        assertEquals("استاد نمونه", archived.instructor)
        assertEquals(TermCourseStatus.PASSED, archived.status)
        assertEquals("۱۸٫۵", archived.grade)
        assertEquals(1, archived.attendancePresent)
        assertEquals(1, archived.attendanceAbsent)

        val archivedSession = term.courses.single().sessions.single()
        assertEquals(0, archivedSession.dayOfWeek)
        assertEquals("08:00", archivedSession.startTime)
        assertEquals("کلاس ۱۰۱", archivedSession.location)

        // The LIVE rows are untouched; only the active flags were cleared.
        assertEquals(1, db.courseDao().getCourseCount())
        assertEquals(1, db.sectionDao().getSectionsByCourseId(courseId).size)
        assertFalse(db.courseDao().getCourseById(courseId)!!.isSelectedForGeneration)
        assertFalse(db.sectionDao().getSectionById(sectionId)!!.isEnrolled)
        assertEquals("attendance history survives", 2, db.attendanceDao().getAttendanceCount())

        // Passing the course also records it once in the academic record.
        val passed = db.passedCourseDao().getAllPassedCoursesOnce().single()
        assertEquals("MATH101", passed.code)
        assertEquals("۱۸٫۵", passed.grade)
        assertEquals("نیم‌سال اول ۱۴۰۴-۱۴۰۵", passed.termLabel)
    }

    @Test
    fun `missing statuses refuse the archive and change nothing`() = runBlocking {
        createActiveCourse()

        val result = endTerm(
            name = "ترم ناقص",
            endEpochDay = 20_100L,
            entries = emptyMap()
        )
        assertTrue(result is CourseRepository.EndTermResult.MissingStatuses)
        assertEquals(
            listOf("درس MATH101"),
            (result as CourseRepository.EndTermResult.MissingStatuses).courseNames
        )
        assertEquals(0, db.academicTermDao().getTermCount())
        assertEquals(0, db.academicTermDao().getArchivedCourseCount())
        assertTrue(db.courseDao().getCourseByCode("MATH101")!!.isSelectedForGeneration)
    }

    @Test
    fun `no current courses is refused without writing an empty term`() = runBlocking {
        createActiveCourse(selected = false, enrolled = false)

        val result = endTerm("ترم خالی", 20_100L, emptyMap())
        assertEquals(CourseRepository.EndTermResult.NoCourses, result)
        assertEquals(0, db.academicTermDao().getTermCount())
    }

    @Test
    fun `an earlier passed result is never overwritten by a later term`() = runBlocking {
        repository.savePassedCourse(
            PassedCourse(
                code = "math101",
                name = "ریاضی ۱ — نتیجهٔ قدیمی",
                credits = 3,
                grade = "10",
                termLabel = "ترم قبل"
            )
        )
        val (courseId, _) = createActiveCourse(code = "MATH101")

        val result = endTerm(
            name = "ترم جدید",
            endEpochDay = 20_200L,
            entries = mapOf(
                courseId to CourseRepository.EndTermCourseEntry(TermCourseStatus.PASSED, "20")
            )
        )
        assertTrue(result is CourseRepository.EndTermResult.Success)

        val passed = db.passedCourseDao().getAllPassedCoursesOnce().single()
        assertEquals("ریاضی ۱ — نتیجهٔ قدیمی", passed.name)
        assertEquals("10", passed.grade)
        assertEquals("ترم قبل", passed.termLabel)
        assertEquals(1, db.passedCourseDao().getPassedCourseCount())

        // The new attempt is still fully preserved in its own term archive.
        val term = db.academicTermDao().getAllTermsOnce().single()
        assertEquals(TermCourseStatus.PASSED, term.courses.single().course.status)
        assertEquals("20", term.courses.single().course.grade)
    }

    @Test
    fun `re-taking a course keeps both term archives`() = runBlocking {
        val (courseId, sectionId) = createActiveCourse()

        val first = endTerm(
            name = "ترم اول",
            endEpochDay = 20_100L,
            entries = mapOf(
                courseId to CourseRepository.EndTermCourseEntry(TermCourseStatus.FAILED, "8")
            )
        )
        assertTrue(first is CourseRepository.EndTermResult.Success)
        assertEquals("a failed course is not added to passed courses", 0, db.passedCourseDao().getPassedCourseCount())

        // The student re-takes the same catalog course in the next term.
        db.courseDao().setCourseSelectedForGeneration(courseId, true)
        db.sectionDao().setSectionEnrolled(sectionId, true)
        val second = endTerm(
            name = "ترم دوم",
            endEpochDay = 20_200L,
            entries = mapOf(
                courseId to CourseRepository.EndTermCourseEntry(TermCourseStatus.PASSED, "17")
            )
        )
        assertTrue(second is CourseRepository.EndTermResult.Success)

        val terms = db.academicTermDao().getAllTermsOnce()
        assertEquals(2, terms.size)
        assertEquals(listOf("ترم دوم", "ترم اول"), terms.map { it.term.name })
        assertEquals(TermCourseStatus.PASSED, terms[0].courses.single().course.status)
        assertEquals(TermCourseStatus.FAILED, terms[1].courses.single().course.status)
        assertEquals("one catalog row is reused", 1, db.courseDao().getCourseCount())
        assertEquals("only the passing attempt is in passed_courses", 1, db.passedCourseDao().getPassedCourseCount())
        assertEquals("17", db.passedCourseDao().getAllPassedCoursesOnce().single().grade)
    }

    @Test
    fun `passed credits ignore failed withdrawn and incomplete courses`() = runBlocking {
        val (passedId, _) = createActiveCourse(code = "A101", credits = 3)
        val (failedId, _) = createActiveCourse(code = "B102", credits = 4)
        val (withdrawnId, _) = createActiveCourse(code = "C103", credits = 2)
        val (incompleteId, _) = createActiveCourse(code = "D104", credits = 1)

        val result = endTerm(
            name = "ترم واحدها",
            endEpochDay = 20_300L,
            entries = mapOf(
                passedId to CourseRepository.EndTermCourseEntry(TermCourseStatus.PASSED),
                failedId to CourseRepository.EndTermCourseEntry(TermCourseStatus.FAILED),
                withdrawnId to CourseRepository.EndTermCourseEntry(TermCourseStatus.WITHDRAWN),
                incompleteId to CourseRepository.EndTermCourseEntry(TermCourseStatus.INCOMPLETE)
            )
        )
        assertTrue(result is CourseRepository.EndTermResult.Success)

        val term = db.academicTermDao().getAllTermsOnce().single()
        assertNotNull(term)
        assertEquals(4, term.courseCount)
        assertEquals(1, term.passedCount)
        assertEquals(10, term.totalCredits)
        assertEquals(3, term.passedCredits)
    }
}
