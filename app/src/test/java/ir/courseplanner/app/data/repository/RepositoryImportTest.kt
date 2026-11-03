package ir.courseplanner.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.importer.ImportItem
import ir.courseplanner.app.data.importer.ImportSectionItem
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.DocumentCategory
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
 * Phase 2: import atomicity and re-import user-state preservation,
 * against a real (in-memory) Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryImportTest {

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

    private fun portalItem(
        code: String,
        sections: List<String>,
        instructor: String = "استاد",
        selected: Boolean = false
    ): ImportItem {
        return ImportItem(
            course = Course(
                code = code,
                name = "درس $code",
                department = "كامپيوتر",
                credits = 3,
                isSelectedForGeneration = selected
            ),
            sections = sections.map { secCode ->
                ImportSectionItem(
                    section = CourseSection(
                        courseId = 0,
                        sectionCode = secCode,
                        instructor = instructor,
                        capacity = 30
                    ),
                    sessions = listOf(
                        ClassSession(
                            sectionId = 0,
                            dayOfWeek = 0,
                            startTime = "08:00",
                            endTime = "10:00",
                            location = "کلاس ۱"
                        )
                    )
                )
            }
        )
    }

    @Test
    fun `reimport preserves enrollment selected flag and documents`() = runBlocking {
        repository.importPortalItems(listOf(portalItem("10103", listOf("1", "2"))))

        // User state: pick the course, enroll section "2", attach a document.
        val courseId = db.courseDao().getCourseByCode("10103")!!.id
        repository.toggleCourseSelectedForGeneration(courseId, true)
        val sec2 = db.sectionDao().getSectionsByCourseId(courseId).first { it.sectionCode == "2" }
        db.sectionDao().setSectionEnrolled(sec2.id, true)
        db.documentDao().insertDocument(
            CourseDocument(
                courseId = courseId,
                title = "جزوه ریاضی",
                category = DocumentCategory.PAMPHLET
            )
        )

        // Same file re-imported with a changed instructor (catalog refresh).
        repository.importPortalItems(listOf(portalItem("10103", listOf("1", "2"), instructor = "استاد جدید")))

        val after = db.courseDao().getCourseByCode("10103")!!
        assertEquals(courseId, after.id)
        assertTrue(after.isSelectedForGeneration)

        val sections = db.sectionDao().getSectionsByCourseId(courseId)
        assertEquals(2, sections.size)
        assertEquals("استاد جدید", sections.first { it.sectionCode == "1" }.instructor)
        assertTrue(sections.first { it.sectionCode == "2" }.isEnrolled)

        val docs = db.documentDao().getDocumentsByCourse(courseId).first()
        assertEquals(1, docs.size)
        assertEquals("جزوه ریاضی", docs[0].title)
    }

    @Test
    fun `reimport drops stale unenrolled sections but keeps enrolled ones`() = runBlocking {
        repository.importPortalItems(listOf(portalItem("10103", listOf("1", "2"))))
        val courseId = db.courseDao().getCourseByCode("10103")!!.id
        val sec2 = db.sectionDao().getSectionsByCourseId(courseId).first { it.sectionCode == "2" }
        db.sectionDao().setSectionEnrolled(sec2.id, true)

        // Portal no longer lists group "1" (unenrolled) nor "2" (enrolled).
        repository.importPortalItems(listOf(portalItem("10103", listOf("3"))))

        val codes = db.sectionDao().getSectionsByCourseId(courseId).map { it.sectionCode }.toSet()
        assertEquals(setOf("2", "3"), codes)
    }

    @Test
    fun `reimport never duplicates courses`() = runBlocking {
        val item = portalItem("10103", listOf("1"))
        repository.importPortalItems(listOf(item))
        repository.importPortalItems(listOf(item))
        repository.importPortalItems(listOf(item))
        assertEquals(1, db.courseDao().getCourseCount())
        val courseId = db.courseDao().getCourseByCode("10103")!!.id
        assertEquals(1, db.sectionDao().getSectionsByCourseId(courseId).size)
    }

    @Test
    fun `updateCourseDetails edits fields but keeps sections enrollments and docs`() = runBlocking {
        repository.importPortalItems(listOf(portalItem("10103", listOf("1", "2"))))
        val courseId = db.courseDao().getCourseByCode("10103")!!.id
        val sec2 = db.sectionDao().getSectionsByCourseId(courseId).first { it.sectionCode == "2" }
        db.sectionDao().setSectionEnrolled(sec2.id, true)
        db.documentDao().insertDocument(
            CourseDocument(courseId = courseId, title = "جزوه", category = DocumentCategory.PAMPHLET)
        )

        val result = repository.updateCourseDetails(
            courseId = courseId, name = "ریاضی مهندسی", code = "10103",
            department = "علوم پایه", credits = 4
        )
        assertEquals(CourseRepository.UpdateCourseResult.Success, result)

        val after = db.courseDao().getCourseById(courseId)!!
        assertEquals("ریاضی مهندسی", after.name)
        assertEquals("علوم پایه", after.department)
        assertEquals(4, after.credits)
        // Sections, enrollment and documents are untouched.
        assertEquals(2, db.sectionDao().getSectionsByCourseId(courseId).size)
        assertTrue(db.sectionDao().getSectionsByCourseId(courseId).first { it.sectionCode == "2" }.isEnrolled)
        assertEquals(1, db.documentDao().getDocumentsByCourse(courseId).first().size)
    }

    @Test
    fun `updateCourseDetails rejects duplicate code and missing course`() = runBlocking {
        repository.importPortalItems(
            listOf(portalItem("10103", listOf("1")), portalItem("10104", listOf("1")))
        )
        val firstId = db.courseDao().getCourseByCode("10103")!!.id

        assertEquals(
            CourseRepository.UpdateCourseResult.DuplicateCode,
            repository.updateCourseDetails(firstId, "x", "10104", "", 3)
        )
        // Same code on the same course is fine (no-op clash).
        assertEquals(
            CourseRepository.UpdateCourseResult.Success,
            repository.updateCourseDetails(firstId, "x", "10103", "", 3)
        )
        assertEquals(
            CourseRepository.UpdateCourseResult.NotFound,
            repository.updateCourseDetails(999_999L, "x", "10103", "", 3)
        )
    }

    @Test
    fun `applySchedule enrolls exactly the given sections`() = runBlocking {
        repository.importPortalItems(listOf(portalItem("10103", listOf("1", "2"))))
        val all = db.sectionDao().getAllSectionsWithDetails().first()
        val target = all.first { it.section.sectionCode == "1" }
        repository.applySchedule(listOf(target))

        val enrolled = db.sectionDao().getEnrolledSections().first()
        assertEquals(1, enrolled.size)
        assertEquals("1", enrolled[0].section.sectionCode)
    }

    // ---------------------------------------------------------------- importItems
    // The JSON/CSV restore path (`importItems`) had no re-import coverage; only
    // the portal path was tested, so a duplicate-on-reimport regression there
    // would have gone unnoticed.

    @Test
    fun `importing the same payload twice does not duplicate courses or groups`() = runBlocking {
        val payload = listOf(portalItem("10103", listOf("1", "2")))

        repository.importItems(payload, clearExisting = false)
        repository.importItems(payload, clearExisting = false)
        repository.importItems(payload, clearExisting = false)

        assertEquals(1, db.courseDao().getCourseCount())
        val courseId = db.courseDao().getCourseByCode("10103")!!.id
        assertEquals(2, db.sectionDao().getSectionsByCourseId(courseId).size)
        assertEquals(2, db.sectionDao().getAllSectionsWithDetails().first().size)
    }

    @Test
    fun `import matches course codes in normalized form`() = runBlocking {
        repository.importItems(listOf(portalItem(" MATH101 ", listOf("1"))))
        repository.importItems(listOf(portalItem("math101", listOf("1"))))
        repository.importItems(listOf(portalItem("  Math101", listOf("1"))))

        assertEquals(
            "case/spacing variants are one course, not three",
            1,
            db.courseDao().getCourseCount()
        )
    }

    @Test
    fun `reimport keeps the generator tick when the file is restored again`() = runBlocking {
        repository.importItems(listOf(portalItem("10103", listOf("1"))))
        val courseId = db.courseDao().getCourseByCode("10103")!!.id
        repository.toggleCourseSelectedForGeneration(courseId, true)

        repository.importItems(listOf(portalItem("10103", listOf("1"))))

        assertTrue(db.courseDao().getCourseById(courseId)!!.isSelectedForGeneration)
        assertEquals(1, db.courseDao().getCourseCount())
    }
}
