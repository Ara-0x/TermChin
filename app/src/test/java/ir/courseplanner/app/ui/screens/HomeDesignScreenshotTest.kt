package ir.courseplanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import ir.courseplanner.app.data.model.AttendanceStats
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.ui.components.AttendanceSummaryCard
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.util.JalaliDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class HomeDesignScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Sunday 2024-11-03 → یکشنبه ۱۳ آبان ۱۴۰۳. A real epoch day, so the Jalali
     * day/month half of the date line renders instead of collapsing to the
     * weekday name alone. The clock is pinned so re-recording is deterministic.
     */
    private val sundayEpochDay = JalaliDate.daysFromCivil(2024, 11, 3)
    private val now = DashboardNow(
        epochDay = sundayEpochDay,
        appDayIdx = JalaliDate.appDayIndexOfEpochDay(sundayEpochDay),
        nowMinutes = 585 // 09:45 → صبح بخیر
    )
    private val week = ScheduleEngine.AcademicWeek(7, ScheduleEngine.WeekParity.ODD)

    @Test
    fun header_light() {
        captureHeader(dark = false, name = "design_header_light") {
            header()
        }
    }

    @Test
    fun header_dark() {
        captureHeader(dark = true, name = "design_header_dark") {
            header()
        }
    }

    /** No name and no major yet: the greeting falls back to the brand name. */
    @Test
    fun header_no_profile_light() {
        captureHeader(dark = false, name = "design_header_no_profile_light") {
            header(studentName = "", major = "", credits = 0, creditTarget = 0, weekInfo = null)
        }
    }

    /** After «ثبت پایان ترم»: term menu flips to «شروع ترم جدید». */
    @Test
    fun header_term_ended_light() {
        captureHeader(dark = false, name = "design_header_term_ended_light") {
            header(isTermActive = false, hasCurrentTermCourses = true)
        }
    }

    /** 200% text on a narrow 320dp window: the chips must wrap, not clip. */
    @Test
    fun header_large_font_light() {
        captureHeader(
            dark = false,
            name = "design_header_large_font_light",
            fontScale = 2f,
            width = 320.dp
        ) {
            header()
        }
    }

    @Test
    fun now_next_ongoing_light() {
        capture(dark = false, name = "design_now_next_light") {
            NowNextCard(
                todayIdx = 1,
                todayClasses = listOf(
                    upcoming(ongoing = true),
                    upcoming(ongoing = false, start = "12:00", end = "13:30", courseName = "برنامه‌سازی پیشرفته")
                ),
                state = NowNextState.Ongoing(upcoming(ongoing = true), 50),
                nowMinutes = 640,
                onManageCourses = {}
            )
        }
    }

    @Test
    fun now_next_ongoing_dark() {
        capture(dark = true, name = "design_now_next_dark") {
            NowNextCard(
                todayIdx = 1,
                todayClasses = listOf(
                    upcoming(ongoing = true),
                    upcoming(ongoing = false, start = "12:00", end = "13:30", courseName = "برنامه‌سازی پیشرفته")
                ),
                state = NowNextState.Ongoing(upcoming(ongoing = true), 50),
                nowMinutes = 640,
                onManageCourses = {}
            )
        }
    }

    @Test
    fun attendance_light() {
        capture(dark = false, name = "design_attendance_light") {
            AttendanceSummaryCard(
                enrolledSections = listOf(section()),
                statsByCourse = mapOf(7L to AttendanceStats(absent = 2)),
                absenceLimit = 3,
                onOpenAttendance = {}
            )
        }
    }

    /**
     * Header captures target the card node itself so the review image is the
     * card, not a full phone screen with 80% empty background.
     */
    private fun captureHeader(
        dark: Boolean,
        name: String,
        fontScale: Float = 1f,
        width: Dp = 360.dp,
        content: @Composable () -> Unit
    ) {
        capture(
            dark = dark,
            name = name,
            fontScale = fontScale,
            width = width,
            tag = "home_header_card",
            content = content
        )
    }

    @Composable
    private fun header(
        studentName: String = "امیررضا",
        semesterName: String = "نیم‌سال اول ۱۴۰۴",
        major: String = "مهندسی کامپیوتر",
        credits: Int = 17,
        creditTarget: Int = 20,
        weekInfo: ScheduleEngine.AcademicWeek? = week,
        isTermActive: Boolean = true,
        hasCurrentTermCourses: Boolean = true
    ) {
        DashboardHeader(
            studentName = studentName,
            semesterName = semesterName,
            major = major,
            credits = credits,
            creditTarget = creditTarget,
            weekInfo = weekInfo,
            now = now,
            isTermActive = isTermActive,
            hasCurrentTermCourses = hasCurrentTermCourses
        )
    }

    private fun capture(
        dark: Boolean,
        name: String,
        fontScale: Float = 1f,
        width: Dp = 360.dp,
        tag: String? = null,
        content: @Composable () -> Unit
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = density, fontScale = fontScale)
            ) {
                MyApplicationTheme(darkTheme = dark, colorTheme = AppColorTheme.INDIGO) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.width(width)) {
                            content()
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val image = if (tag == null) {
            composeTestRule.onRoot()
        } else {
            composeTestRule.onNodeWithTag(tag)
        }
        image.captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun section(): SectionWithDetails {
        val course = Course(id = 7, code = "10103", name = "ریاضی عمومی ۱", credits = 3)
        return SectionWithDetails(
            section = CourseSection(id = 1, courseId = 7, sectionCode = "1", instructor = "دکتر رضایی"),
            course = course,
            sessions = listOf(
                ClassSession(
                    sectionId = 1,
                    dayOfWeek = 1,
                    startTime = "10:00",
                    endTime = "11:30",
                    location = "کلاس ۲۰۴"
                )
            )
        )
    }

    private fun upcoming(
        ongoing: Boolean,
        start: String = "10:00",
        end: String = "11:30",
        courseName: String = "ریاضی عمومی ۱"
    ): ScheduleEngine.UpcomingSession {
        val base = section()
        val course = base.course.copy(name = courseName)
        val session = base.sessions.first().copy(startTime = start, endTime = end)
        return ScheduleEngine.UpcomingSession(
            section = base.copy(course = course, sessions = listOf(session)),
            session = session,
            dayOfWeek = 1,
            ongoing = ongoing
        )
    }
}
