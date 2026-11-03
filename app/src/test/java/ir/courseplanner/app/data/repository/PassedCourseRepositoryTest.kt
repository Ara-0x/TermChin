package ir.courseplanner.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.PassedCourse
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
 * Academic-record invariants: one passed course per normalized code, blank
 * input refused, edit-in-place allowed, and a delete that touches nothing else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PassedCourseRepositoryTest {

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

    private fun passed(code: String, name: String = "درس $code", credits: Int = 3) =
        PassedCourse(code = code, name = name, credits = credits)

    @Test
    fun `saving a duplicate normalized code is refused`() = runBlocking {
        val first = repository.savePassedCourse(passed("MATH101"))
        assertTrue(first is CourseRepository.PassedCourseResult.Success)

        assertEquals(
            CourseRepository.PassedCourseResult.DuplicateCode,
            repository.savePassedCourse(passed(" math101 "))
        )
        assertEquals(
            CourseRepository.PassedCourseResult.DuplicateCode,
            repository.savePassedCourse(passed("Math101"))
        )
        assertEquals("one row only", 1, db.passedCourseDao().getPassedCourseCount())
        assertEquals("MATH101", db.passedCourseDao().getAllPassedCoursesOnce().single().code)
    }

    @Test
    fun `blank values and invalid credits are refused`() = runBlocking {
        assertEquals(
            CourseRepository.PassedCourseResult.BlankCode,
            repository.savePassedCourse(passed("   "))
        )
        assertEquals(
            CourseRepository.PassedCourseResult.BlankName,
            repository.savePassedCourse(passed("MATH102", name = "  "))
        )
        assertEquals(
            CourseRepository.PassedCourseResult.InvalidCredits,
            repository.savePassedCourse(passed("MATH103", credits = 31))
        )
        assertEquals(0, db.passedCourseDao().getPassedCourseCount())
    }

    @Test
    fun `editing keeps the same row and its own code`() = runBlocking {
        val created = repository.savePassedCourse(passed("PHYS101")) as
            CourseRepository.PassedCourseResult.Success
        repository.savePassedCourse(passed("MATH101"))

        val edited = repository.savePassedCourse(
            PassedCourse(
                id = created.id,
                code = " phys101 ",
                name = "فیزیک ۱",
                credits = 4,
                grade = " 18.5 ",
                termLabel = " نیم‌سال اول ",
                note = " بازخوانی "
            )
        )
        assertTrue(edited is CourseRepository.PassedCourseResult.Success)

        val stored = db.passedCourseDao().getPassedCourseById(created.id)!!
        assertEquals("phys101", stored.code)
        assertEquals("فیزیک ۱", stored.name)
        assertEquals(4, stored.credits)
        assertEquals("18.5", stored.grade)
        assertEquals("نیم‌سال اول", stored.termLabel)
        assertEquals("بازخوانی", stored.note)
        assertEquals("no duplicate inserted", 2, db.passedCourseDao().getPassedCourseCount())
    }

    @Test
    fun `deleting a passed course leaves ordinary courses untouched`() = runBlocking {
        repository.addManualCourse(
            ir.courseplanner.app.data.model.Course(code = "MATH200", name = "ریاضی ۲"),
            ir.courseplanner.app.data.model.CourseSection(courseId = 0, sectionCode = "01"),
            emptyList()
        )
        val created = repository.savePassedCourse(passed("MATH200")) as
            CourseRepository.PassedCourseResult.Success

        repository.deletePassedCourse(created.id)

        assertEquals(0, db.passedCourseDao().getPassedCourseCount())
        assertEquals("the catalog row survives", 1, db.courseDao().getCourseCount())
        assertEquals(
            CourseRepository.PassedCourseResult.NotFound,
            repository.savePassedCourse(
                PassedCourse(id = created.id, code = "MATH200", name = "ریاضی ۲")
            )
        )
    }
}
