package ir.courseplanner.app.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.preferences.TimetableDensity
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.util.JalaliDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual baselines for the Settings screen, section by section. The clock is
 * pinned so the profile's «شروع کلاس‌ها» and «هفته جاری آموزشی» rows stay
 * byte-identical across re-records; categories are reached through the real
 * quick-nav tiles, which also exercises the jump targets.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var viewModel: CoursePlannerViewModel
    private lateinit var preferences: PreferencesManager
    private lateinit var collectorScope: CoroutineScope

    /** Saturday 2024-11-09 → ۱۹ آبان ۱۴۰۳; semester start six weeks earlier. */
    private val pinnedToday = JalaliDate.daysFromCivil(2024, 11, 9)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = CourseRepository(
            db,
            db.courseDao(),
            db.sectionDao(),
            db.documentDao(),
            db.passedCourseDao(),
            db.attendanceDao()
        )
        preferences = PreferencesManager(context)
        viewModel = CoursePlannerViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            repository = repository,
            preferencesManager = preferences
        )
        collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        collectorScope.launch {
            launch { viewModel.enrolledSections.collect { } }
            launch { viewModel.coursesWithSections.collect { } }
            launch { viewModel.userPreferences.collect { } }
            launch { viewModel.dataCounts.collect { } }
            launch { viewModel.optimizationPreference.collect { } }
            launch { viewModel.currentTermCourseCount.collect { } }
        }
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun top_light() = runBlocking { capture("design_settings_top_light") }

    @Test
    fun top_dark() = runBlocking { capture("design_settings_top_dark", dark = true) }

    @Test
    fun timetable_light() = runBlocking {
        capture("design_settings_timetable_light", navIndex = 1)
    }

    @Test
    fun timetable_dark() = runBlocking {
        capture("design_settings_timetable_dark", dark = true, navIndex = 1)
    }

    @Test
    fun selection_light() = runBlocking {
        capture("design_settings_selection_light", navIndex = 2)
    }

    @Test
    fun selection_dark() = runBlocking {
        capture("design_settings_selection_dark", dark = true, navIndex = 2)
    }

    @Test
    fun profile_light() = runBlocking {
        capture("design_settings_profile_light", navIndex = 3)
    }

    @Test
    fun profile_dark() = runBlocking {
        capture("design_settings_profile_dark", dark = true, navIndex = 3)
    }

    @Test
    fun data_light() = runBlocking {
        capture("design_settings_data_light", navIndex = 4)
    }

    @Test
    fun data_dark() = runBlocking {
        capture("design_settings_data_dark", dark = true, navIndex = 4)
    }

    @Test
    fun export_light() = runBlocking {
        capture("design_settings_export_light", scrollToTag = "settings_section_export")
    }

    @Test
    fun cleanup_light() = runBlocking {
        capture("design_settings_cleanup_light", scrollToTag = "settings_section_cleanup")
    }

    @Test
    fun about_light() = runBlocking {
        capture("design_settings_about_light", scrollToTag = "settings_section_about")
    }

    private suspend fun capture(
        name: String,
        dark: Boolean = false,
        navIndex: Int? = null,
        scrollToTag: String? = null
    ) {
        seed()
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                MyApplicationTheme(darkTheme = dark, colorTheme = AppColorTheme.INDIGO) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        SettingsScreen(viewModel = viewModel, todayEpochDay = pinnedToday)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        navIndex?.let { index ->
            composeTestRule.onNodeWithTag("settings_nav_$index").performClick()
            composeTestRule.waitForIdle()
        }
        scrollToTag?.let { tag ->
            composeTestRule.onNodeWithTag(tag).performScrollTo()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private suspend fun seed() {
        preferences.setStudentProfile(
            name = "امیررضا",
            major = "مهندسی کامپیوتر",
            semester = "نیم‌سال اول ۱۴۰۴"
        )
        preferences.setSemesterStartEpochDay(pinnedToday - 42)
        preferences.setCreditTarget(20)
        preferences.setAbsenceLimit(3)
        preferences.setShowThursday(true)
        preferences.setTimetableDensity(TimetableDensity.STANDARD)
        withTimeout(10_000) {
            preferences.preferences.first {
                it.studentName == "امیررضا" && it.semesterStartEpochDay != null
            }
        }
        seedCourse(code = "10103", name = "ریاضی عمومی ۱", day = 0, start = "10:00", end = "11:30")
        seedCourse(code = "10201", name = "برنامه‌سازی پیشرفته", day = 2, start = "13:30", end = "15:00")
    }

    private suspend fun seedCourse(code: String, name: String, day: Int, start: String, end: String) {
        val courseId = db.courseDao().insertCourse(
            Course(code = code, name = name, credits = 3, isSelectedForGeneration = true)
        )
        val sectionId = db.sectionDao().insertSection(
            CourseSection(
                courseId = courseId,
                sectionCode = "1",
                instructor = "دکتر نمونه",
                isEnrolled = true
            )
        )
        db.sectionDao().insertSession(
            ClassSession(
                sectionId = sectionId,
                dayOfWeek = day,
                startTime = start,
                endTime = end,
                location = "کلاس ۲۰۴"
            )
        )
    }
}
