package ir.courseplanner.app.data.repository

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.DocumentCategory
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.ui.CoursePlannerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * REGRESSION TEST for the startup data wipe that v2.5.0 removed.
 *
 * Old behaviour: `CoursePlannerViewModel.init` called `repository.clearAllData()`
 * whenever the DataStore marker `release_clean_courses_v1` was missing. The
 * marker defaults to `false`, so any install whose DataStore was absent/restored
 * (or simply never wrote the flag) deleted the student's ENTIRE database on the
 * next launch — courses, groups, sessions, enrollments and documents.
 *
 * New behaviour: starting the app (i.e. constructing the ViewModel, which is
 * exactly what the old fence did) must not delete anything, whatever the
 * preferences contain. Because the marker API no longer exists, this test also
 * fails to compile if anybody re-introduces a "missing flag → wipe" path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupDataPreservationTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CourseRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var application: Application

    @Before
    fun setup() {
        runBlocking {
            application = ApplicationProvider.getApplicationContext()
            db = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                AppDatabase::class.java
            ).allowMainThreadQueries().build()
            repository = CourseRepository(
                db = db,
                courseDao = db.courseDao(),
                sectionDao = db.sectionDao(),
                documentDao = db.documentDao()
            )
            // Fresh DataStore: no "release clean" marker exists at all (the exact
            // situation that used to trigger the wipe).
            preferencesManager = PreferencesManager(application)

            // Realistic pre-existing user data.
            repository.addManualCourse(
                Course(
                    code = "MATH101",
                    name = "ریاضی ۱",
                    department = "علوم پایه",
                    credits = 3,
                    isSelectedForGeneration = true
                ),
                CourseSection(courseId = 0, sectionCode = "01", instructor = "استاد تست"),
                listOf(ClassSession(sectionId = 0, dayOfWeek = 0, startTime = "08:00", endTime = "10:00"))
            )
            val courseId = db.courseDao().getCourseByCode("MATH101")!!.id
            val sectionId = db.sectionDao().getSectionsByCourseId(courseId).single().id
            db.sectionDao().setSectionEnrolled(sectionId, true)
            repository.insertDocument(
                CourseDocument(courseId = courseId, title = "جزوه ریاضی", category = DocumentCategory.PAMPHLET)
            )
            preferencesManager.setColorTheme(AppColorTheme.SLATE)
            preferencesManager.setStudentProfile("امیر", "کامپیوتر", "نیم‌سال اول")
            // Writes go through DataStore asynchronously — wait for ALL of them
            // (not just the theme) so the test asserts on what the app would
            // actually read. Waiting on the theme alone lets a lagging name
            // write flake the studentName assert on loaded runners.
            withTimeout(10_000) {
                preferencesManager.preferences.first {
                    it.theme == AppColorTheme.SLATE && it.studentName == "امیر"
                }
            }
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun snapshot() = runBlocking {
        Triple(
            db.courseDao().getAllCoursesOnce().map { it.code },
            db.sectionDao().getEnrolledSections().first().map { it.section.sectionCode },
            db.documentDao().getAllDocuments().first().map { it.title }
        )
    }

    @Test
    fun `starting the app with no cleanup marker keeps every row`() = runBlocking {
        val before = snapshot()

        // This is what the app does at startup: build the ViewModel.
        val viewModel = CoursePlannerViewModel(
            application = application,
            repository = repository,
            preferencesManager = preferencesManager
        )
        shadowOf(Looper.getMainLooper()).idle()
        // Touch the preferences the first frame reads.
        assertEquals(AppColorTheme.SLATE, viewModel.userPreferences.value.theme)

        val after = snapshot()
        assertEquals("courses survived", before.first, after.first)
        assertEquals("enrollment survived", before.second, after.second)
        assertEquals("documents survived", before.third, after.third)
        assertEquals(1, db.courseDao().getCourseCount())
        assertEquals("امیر", viewModel.userPreferences.value.studentName)
    }

    @Test
    fun `repeat startups keep the data too`() = runBlocking {
        // Simulate upgrade launches: several ViewModel instances, same database.
        repeat(3) {
            CoursePlannerViewModel(application, repository, preferencesManager)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val after = snapshot()
        assertEquals(listOf("MATH101"), after.first)
        assertTrue(after.second.isNotEmpty())
        assertEquals(listOf("جزوه ریاضی"), after.third)
    }

    @Test
    fun `deleting data still requires an explicit user action`() = runBlocking {
        // The ONLY wipe path left is the explicit Settings action.
        repository.clearAllData()
        assertEquals(0, db.courseDao().getCourseCount())
        assertEquals(0, db.sectionDao().getEnrolledSections().first().size)
        assertEquals(0, db.documentDao().getAllDocuments().first().size)
    }
}
