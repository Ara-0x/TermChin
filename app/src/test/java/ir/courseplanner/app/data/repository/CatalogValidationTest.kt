package ir.courseplanner.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Catalog invariants at the DATA layer (real in-memory Room database):
 * - course codes are unique in normalized form, creation and editing agree,
 * - blank codes are refused instead of being invented ("CRS-1234", "01"),
 * - section codes are unique inside their own course (but reusable across
 *   courses),
 * - a group may not contain internally overlapping sessions (parity-aware),
 * - applying a schedule can never silently drop a selected course.
 *
 * These are the guards the UI cannot provide: a repository/DAO caller (import,
 * future automation, tests) must hit the same wall.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogValidationTest {

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

    private fun course(code: String, name: String = "درس $code", selected: Boolean = true) = Course(
        code = code,
        name = name,
        department = "كامپيوتر",
        credits = 3,
        isSelectedForGeneration = selected
    )

    private fun section(code: String) = CourseSection(courseId = 0, sectionCode = code)

    private fun session(
        day: Int = 0,
        start: String = "08:00",
        end: String = "10:00",
        weekType: WeekType = WeekType.EVERY_WEEK
    ) = ClassSession(sectionId = 0, dayOfWeek = day, startTime = start, endTime = end, weekType = weekType)

    private suspend fun createCourse(code: String) =
        repository.addManualCourse(course(code), section("01"), listOf(session()))

    private suspend fun courseId(code: String): Long = db.courseDao().getCourseByCode(code)!!.id

    private suspend fun sectionId(courseId: Long, sectionCode: String): Long =
        db.sectionDao().getSectionsByCourseId(courseId).first { it.sectionCode == sectionCode }.id

    private suspend fun details(sectionId: Long): SectionWithDetails =
        db.sectionDao().getSectionWithDetailsById(sectionId)!!

    // ------------------------------------------------------------- course codes

    @Test
    fun `creating a course rejects a duplicate normalized code`() = runBlocking {
        assertTrue(createCourse("MATH101") is CourseRepository.AddCourseResult.Success)

        assertEquals(
            CourseRepository.AddCourseResult.DuplicateCode,
            repository.addManualCourse(course(" math101 "), section("01"), listOf(session()))
        )
        assertEquals(
            CourseRepository.AddCourseResult.DuplicateCode,
            repository.addManualCourse(course("Math101"), section("02"), listOf(session()))
        )
        assertEquals("only one MATH101 row", 1, db.courseDao().getCourseCount())
    }

    @Test
    fun `blank codes are refused instead of being invented`() = runBlocking {
        assertEquals(
            CourseRepository.AddCourseResult.BlankCode,
            repository.addManualCourse(course("   "), section("01"), listOf(session()))
        )
        assertEquals(
            CourseRepository.AddCourseResult.BlankSectionCode,
            repository.addManualCourse(course("MATH102"), section(""), listOf(session()))
        )
        assertEquals("nothing was written", 0, db.courseDao().getCourseCount())
    }

    @Test
    fun `editing keeps its own code but rejects another course's code`() = runBlocking {
        createCourse("MATH101")
        createCourse("PHYS101")
        val mathId = courseId("MATH101")
        val physId = courseId("PHYS101")

        // Same code, only surrounded by spaces → this is the course's own code.
        assertEquals(
            CourseRepository.UpdateCourseResult.Success,
            repository.updateCourseDetails(mathId, "ریاضی ۱", " MATH101 ", "علوم پایه", 4)
        )
        // Another course's code (case-insensitive) → rejected.
        assertEquals(
            CourseRepository.UpdateCourseResult.DuplicateCode,
            repository.updateCourseDetails(physId, "فیزیک", "math101", "", 3)
        )
        // Blank code → rejected (no silent fallback).
        assertEquals(
            CourseRepository.UpdateCourseResult.BlankCode,
            repository.updateCourseDetails(physId, "فیزیک", "  ", "", 3)
        )
        assertEquals("PHYS101 kept its own code", "PHYS101", db.courseDao().getCourseById(physId)!!.code)
        assertEquals("MATH101 kept its own code", "MATH101", db.courseDao().getCourseById(mathId)!!.code)
    }

    // ------------------------------------------------------------ section codes

    @Test
    fun `section code must be unique inside its course but may repeat across courses`() = runBlocking {
        createCourse("MATH101")
        createCourse("PHYS101")
        val mathId = courseId("MATH101")
        val physId = courseId("PHYS101")

        // Both courses own a group "01": the same number in another course is fine.
        assertEquals(
            listOf("01"),
            db.sectionDao().getSectionsByCourseId(physId).map { it.sectionCode }
        )

        // MATH101 already has group "01" from creation.
        assertEquals(
            CourseRepository.AddSectionResult.DuplicateCode,
            repository.addSectionToCourse(mathId, section("01"), listOf(session()))
        )
        assertEquals(
            CourseRepository.AddSectionResult.DuplicateCode,
            repository.addSectionToCourse(mathId, section(" 01 "), listOf(session()))
        )
        // A genuinely new group number is accepted.
        assertTrue(
            repository.addSectionToCourse(mathId, section("02"), listOf(session()))
                is CourseRepository.AddSectionResult.Success
        )
        assertEquals(2, db.sectionDao().getSectionsByCourseId(mathId).size)

        assertEquals(
            CourseRepository.AddSectionResult.BlankCode,
            repository.addSectionToCourse(mathId, section(""), listOf(session()))
        )
        assertEquals(
            CourseRepository.AddSectionResult.CourseNotFound,
            repository.addSectionToCourse(999_999L, section("07"), listOf(session()))
        )
    }

    // -------------------------------------------------------- session conflicts

    @Test
    fun `overlapping sessions are refused before they are persisted`() = runBlocking {
        createCourse("MATH101")
        val mathId = courseId("MATH101")

        // Saturday 08:00–10:00 vs Saturday 09:00–11:00, parity per row.
        val cases = listOf(
            WeekType.EVERY_WEEK to WeekType.EVERY_WEEK,
            WeekType.EVERY_WEEK to WeekType.ODD_WEEKS,
            WeekType.EVERY_WEEK to WeekType.EVEN_WEEKS,
            WeekType.ODD_WEEKS to WeekType.ODD_WEEKS,
            WeekType.EVEN_WEEKS to WeekType.EVEN_WEEKS
        )
        cases.forEachIndexed { index, (first, second) ->
            val result = repository.addSectionToCourse(
                mathId,
                section("1$index"),
                listOf(session(weekType = first), session(start = "09:00", end = "11:00", weekType = second))
            )
            assertEquals(
                "parity $first/$second must clash",
                CourseRepository.AddSectionResult.ConflictingSessions,
                result
            )
        }
        // Odd vs even weeks never meet → allowed.
        assertTrue(
            repository.addSectionToCourse(
                mathId,
                section("20"),
                listOf(
                    session(weekType = WeekType.ODD_WEEKS),
                    session(start = "09:00", end = "11:00", weekType = WeekType.EVEN_WEEKS)
                )
            ) is CourseRepository.AddSectionResult.Success
        )
        // Only the accepted group exists; no rejected row leaked in.
        assertEquals(
            listOf("01", "20"),
            db.sectionDao().getSectionsByCourseId(mathId).map { it.sectionCode }.sorted()
        )

        // Broken clock values are refused too (never stored as 00:00).
        assertEquals(
            CourseRepository.AddSectionResult.InvalidSessions,
            repository.addSectionToCourse(mathId, section("30"), listOf(session(start = "11:00", end = "09:00")))
        )
    }

    @Test
    fun `creating a course validates its first group's sessions`() = runBlocking {
        val result = repository.addManualCourse(
            course("MATH200"),
            section("01"),
            listOf(session(), session(start = "09:00", end = "11:00"))
        )
        assertEquals(CourseRepository.AddCourseResult.ConflictingSessions, result)
        assertEquals(0, db.courseDao().getCourseCount())
    }

    @Test
    fun `editing a group rejects self-conflicts and keeps enrollment otherwise`() = runBlocking {
        createCourse("MATH300")
        val mathId = courseId("MATH300")
        val secId = sectionId(mathId, "01")
        db.sectionDao().setSectionEnrolled(secId, true)

        // Conflicting sessions → refused, stored data untouched.
        assertEquals(
            CourseRepository.UpdateSectionResult.ConflictingSessions,
            repository.updateSectionDetails(
                sectionId = secId,
                sectionCode = "01",
                instructor = "استاد",
                examDate = "",
                examStartTime = "",
                examEndTime = "",
                sessions = listOf(session(), session(start = "09:00", end = "11:00"))
            )
        )
        assertEquals(
            CourseRepository.UpdateSectionResult.BlankCode,
            repository.updateSectionDetails(secId, "  ", "", "", "", "", listOf(session()))
        )
        assertEquals(
            CourseRepository.UpdateSectionResult.InvalidSessions,
            repository.updateSectionDetails(secId, "01", "", "", "", "", listOf(session(start = "10:00", end = "10:00")))
        )
        assertEquals("original session survived", "10:00", details(secId).sessions.single().endTime)

        // Valid edit → success, enrollment preserved, sessions replaced.
        assertEquals(
            CourseRepository.UpdateSectionResult.Success,
            repository.updateSectionDetails(
                sectionId = secId,
                sectionCode = "01",
                instructor = "استاد جدید",
                examDate = "1404/07/20",
                examStartTime = "08:00",
                examEndTime = "10:00",
                sessions = listOf(session(start = "13:00", end = "15:00"))
            )
        )
        val updated = details(secId)
        assertTrue(updated.section.isEnrolled)
        assertEquals("استاد جدید", updated.section.instructor)
        assertEquals("13:00", updated.sessions.single().startTime)
    }

    // ---------------------------------------------------------- applying a plan

    @Test
    fun `applying an incomplete plan is refused and keeps the current program`() = runBlocking {
        // Course A: two groups, the user is enrolled in "01".
        createCourse("MATH400")
        val mathId = courseId("MATH400")
        repository.addSectionToCourse(mathId, section("02"), listOf(session(start = "13:00", end = "15:00")))
        val enrolledId = sectionId(mathId, "01")
        db.sectionDao().setSectionEnrolled(enrolledId, true)

        // Course B is selected for the generator but has no usable group.
        db.courseDao().insertCourse(course("EMPTY500", name = "بدون گروه"))

        // The generator can only offer course A → partial plan.
        val partial = listOf(details(sectionId(mathId, "02")))
        val result = repository.applySchedule(partial)

        assertTrue(result is CourseRepository.ApplyScheduleResult.Incomplete)
        result as CourseRepository.ApplyScheduleResult.Incomplete
        assertEquals(listOf("بدون گروه"), result.missingCourseNames)
        // The user's existing enrollment was NOT wiped by the refused write.
        assertEquals(listOf(enrolledId), db.sectionDao().getEnrolledSections().first().map { it.section.id })

        // An empty plan is refused as well.
        assertEquals(CourseRepository.ApplyScheduleResult.Empty, repository.applySchedule(emptyList()))
        assertEquals(listOf(enrolledId), db.sectionDao().getEnrolledSections().first().map { it.section.id })
    }

    @Test
    fun `applying a complete plan replaces enrollments exactly`() = runBlocking {
        createCourse("MATH500")
        createCourse("PHYS500")
        val mathId = courseId("MATH500")
        val physId = courseId("PHYS500")
        val mathSection = details(sectionId(mathId, "01"))
        val physSection = details(sectionId(physId, "01"))
        val staleEnrolled = sectionId(physId, "01")
        db.sectionDao().setSectionEnrolled(staleEnrolled, true)

        val plan = listOf(mathSection, physSection)
        assertEquals(CourseRepository.ApplyScheduleResult.Success, repository.applySchedule(plan))
        assertEquals(
            setOf(mathSection.section.id, physSection.section.id),
            db.sectionDao().getEnrolledSections().first().map { it.section.id }.toSet()
        )
    }
}
