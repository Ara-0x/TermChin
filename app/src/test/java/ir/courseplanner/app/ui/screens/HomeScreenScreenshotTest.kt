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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.AttendanceRecord
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.theme.MyApplicationTheme
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var viewModel: CoursePlannerViewModel
    private lateinit var preferences: PreferencesManager
    private lateinit var collectorScope: CoroutineScope

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
            launch { viewModel.attendanceRecords.collect { } }
            launch { viewModel.documentsWithCourse.collect { } }
            launch { viewModel.passedCourses.collect { } }
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
    fun home_light() {
        runBlocking { seed(captureName = "design_home_light") }
    }

    @Test
    fun home_dark() {
        runBlocking { seed(captureName = "design_home_dark", dark = true) }
    }

    @Test
    fun home_empty_light() {
        runBlocking { seed(captureName = "design_home_empty_light", withCourses = false) }
    }

    @Test
    fun home_empty_dark() {
        runBlocking {
            seed(captureName = "design_home_empty_dark", dark = true, withCourses = false)
        }
    }

    /** The weekly calendar as it appears on the scrolled home page. */
    @Test
    fun home_calendar_light() {
        runBlocking { seed(captureName = "design_home_calendar_light", scrollToCalendar = true) }
    }

    @Test
    fun home_calendar_dark() {
        runBlocking {
            seed(captureName = "design_home_calendar_dark", dark = true, scrollToCalendar = true)
        }
    }

    private suspend fun seed(
        captureName: String,
        dark: Boolean = false,
        withCourses: Boolean = true,
        scrollToCalendar: Boolean = false
    ) {
        val now = dashboardNow(System.currentTimeMillis())
        val today = now.epochDay
        preferences.setStudentProfile(
            name = "امیررضا",
            major = "مهندسی کامپیوتر",
            semester = "نیم‌سال اول ۱۴۰۴"
        )
        preferences.setSemesterStartEpochDay(today - 42)
        preferences.setCreditTarget(20)
        withTimeout(10_000) {
            preferences.preferences.first { it.studentName == "امیررضا" }
        }

        if (!withCourses) {
            render(captureName = captureName, dark = dark, scrollToCalendar = scrollToCalendar)
            return
        }

        val ongoing = seedCourse(
            code = "10103",
            name = "ریاضی عمومی ۱",
            day = now.appDayIdx,
            start = minutes(now.nowMinutes - 30),
            end = minutes(now.nowMinutes + 60),
            location = "کلاس ۲۰۴"
        )
        seedCourse(
            code = "10201",
            name = "برنامه‌سازی پیشرفته",
            day = now.appDayIdx,
            start = minutes(now.nowMinutes + 120),
            end = minutes(now.nowMinutes + 210),
            location = "آزمایشگاه ۲"
        )
        seedCourse(
            code = "10302",
            name = "ساختمان داده",
            day = now.appDayIdx,
            start = minutes(now.nowMinutes - 240),
            end = minutes(now.nowMinutes - 150),
            location = "کلاس ۱۰۱"
        )
        seedCourse(
            code = "10404",
            name = "مدارهای الکتریکی",
            day = (now.appDayIdx + 1) % 6,
            start = "08:00",
            end = "09:30",
            location = "کلاس ۳۰۲"
        )

        listOf(7L, 14L).forEach { daysAgo ->
            db.attendanceDao().upsertAttendance(
                AttendanceRecord(
                    sectionId = ongoing,
                    dateEpochDay = today - daysAgo,
                    startTime = "10:00",
                    endTime = "11:30",
                    status = AttendanceStatus.ABSENT
                )
            )
        }

        render(captureName = captureName, dark = dark, scrollToCalendar = scrollToCalendar)
    }

    private fun render(captureName: String, dark: Boolean, scrollToCalendar: Boolean = false) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                // The app shell forces RTL; screenshots must match it or every
                // leading icon and chip renders on the wrong side.
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                MyApplicationTheme(darkTheme = dark, colorTheme = AppColorTheme.INDIGO) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        HomeScreen(viewModel = viewModel)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        if (scrollToCalendar) {
            composeTestRule.onNodeWithTag("home_weekly_timetable_section").performScrollTo()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/$captureName.png")
    }

    private suspend fun seedCourse(
        code: String,
        name: String,
        day: Int,
        start: String,
        end: String,
        location: String
    ): Long {
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
                location = location
            )
        )
        return sectionId
    }

    private fun minutes(value: Int): String {
        val wrapped = ((value % 1440) + 1440) % 1440
        return "%02d:%02d".format(wrapped / 60, wrapped % 60)
    }
}
