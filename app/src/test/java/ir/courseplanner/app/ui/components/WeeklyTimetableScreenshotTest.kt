package ir.courseplanner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.util.JalaliDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design captures of the home weekly calendar (day view, whole-week grid, and
 * the free-day state) in light and dark with a realistic multi-day timetable.
 *
 * The clock is pinned: [todayEpochDay] points at a real Saturday so the Jalali
 * day numbers under the strip are stable across runs — a real `Calendar`
 * lookup would make every re-record differ from the checked-in image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class WeeklyTimetableScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Saturday 2024-11-02 → ۱۲ آبان ۱۴۰۳, the anchor of the captured week. */
    private val saturdayEpochDay = JalaliDate.daysFromCivil(2024, 11, 2)

    @Test
    fun day_light() {
        render(dark = false) { timetable(selectedDay = 0) }
        captureNow("design_timetable_day_light")
    }

    @Test
    fun day_dark() {
        render(dark = true) { timetable(selectedDay = 0) }
        captureNow("design_timetable_day_dark")
    }

    @Test
    fun day_thursday_light() {
        render(dark = false) { timetable(selectedDay = 5) }
        captureNow("design_timetable_day_thursday_light")
    }

    @Test
    fun empty_day_light() {
        render(dark = false) { timetable(selectedDay = 4) }
        captureNow("design_timetable_empty_light")
    }

    @Test
    fun grid_light() {
        render(dark = false) { timetable(selectedDay = 0) }
        composeTestRule.onNodeWithTag("weekly_timetable_mode_grid").performClick()
        composeTestRule.waitForIdle()
        captureNow("design_timetable_grid_light")
    }

    @Test
    fun grid_dark() {
        render(dark = true) { timetable(selectedDay = 0) }
        composeTestRule.onNodeWithTag("weekly_timetable_mode_grid").performClick()
        composeTestRule.waitForIdle()
        captureNow("design_timetable_grid_dark")
    }

    /**
     * The whole week on one board, at a width where all six day columns fit.
     * The phone grid scrolls horizontally, so this is the only honest look at
     * the full matrix and the spacing between its columns. A wide window also
     * makes «whole week» the default view mode.
     */
    @Test
    @Config(qualifiers = "w900dp-h1300dp-mdpi")
    fun grid_wide_light() {
        render(dark = false, width = 860.dp) { timetable(selectedDay = 0) }
        captureNow("design_timetable_grid_wide_light")
    }

    @Composable
    private fun timetable(selectedDay: Int) {
        WeeklyTimetable(
            sections = sections,
            initialDay = selectedDay,
            highlightedDay = 0,
            todayEpochDay = saturdayEpochDay,
            currentParity = ScheduleEngine.WeekParity.ODD,
            showHeader = true,
            onOpenAttendance = {}
        )
    }

    private fun render(
        dark: Boolean,
        width: Dp = 360.dp,
        content: @Composable () -> Unit
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                MyApplicationTheme(darkTheme = dark, colorTheme = AppColorTheme.INDIGO) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(width)
                                .wrapContentHeight()
                                .padding(4.dp)
                                .testTag(CAPTURE_TAG)
                        ) {
                            content()
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun captureNow(name: String) {
        composeTestRule.onNodeWithTag(CAPTURE_TAG)
            .captureRoboImage("src/test/screenshots/$name.png")
    }

    /**
     * A believable third-semester week: four courses on Saturday (two sharing
     * the 10–12 slot), a two-hour Thursday lab plus an even-week class that must
     * be dimmed this week, and Wednesday left free for the rest-day state.
     */
    private val sections: List<SectionWithDetails> = listOf(
        section(
            id = 1, code = "10103", name = "ریاضی عمومی ۱", credits = 3,
            instructor = "دکتر رضایی",
            sessions = listOf(
                ClassSession(1, sectionId = 1, dayOfWeek = 0, startTime = "08:00", endTime = "09:30", location = "کلاس ۲۰۴"),
                ClassSession(2, sectionId = 1, dayOfWeek = 2, startTime = "10:00", endTime = "11:30", location = "کلاس ۲۰۴")
            )
        ),
        section(
            id = 2, code = "10201", name = "برنامه‌سازی پیشرفته", credits = 3,
            instructor = "دکتر موسوی",
            sessions = listOf(
                ClassSession(3, sectionId = 2, dayOfWeek = 0, startTime = "10:00", endTime = "11:30", location = "آزمایشگاه ۲"),
                ClassSession(4, sectionId = 2, dayOfWeek = 0, startTime = "14:00", endTime = "15:30", location = "آزمایشگاه ۲")
            )
        ),
        section(
            id = 3, code = "10302", name = "ساختمان داده", credits = 3,
            instructor = "دکتر کریمی",
            sessions = listOf(
                ClassSession(5, sectionId = 3, dayOfWeek = 0, startTime = "10:00", endTime = "11:30", location = "کلاس ۱۰۱"),
                ClassSession(6, sectionId = 3, dayOfWeek = 1, startTime = "16:00", endTime = "17:30", location = "کلاس ۱۰۱", weekType = WeekType.ODD_WEEKS)
            )
        ),
        section(
            id = 4, code = "10404", name = "مدارهای الکتریکی", credits = 3,
            instructor = "دکتر احمدی",
            sessions = listOf(
                ClassSession(7, sectionId = 4, dayOfWeek = 1, startTime = "08:00", endTime = "09:30", location = "کلاس ۳۰۲")
            )
        ),
        section(
            id = 5, code = "10505", name = "آزمایشگاه فیزیک ۲", credits = 1,
            instructor = "دکتر نوری",
            sessions = listOf(
                ClassSession(8, sectionId = 5, dayOfWeek = 5, startTime = "10:00", endTime = "12:00", location = "آزمایشگاه فیزیک")
            )
        ),
        section(
            id = 6, code = "10606", name = "زبان تخصصی", credits = 2,
            instructor = "دکتر صادقی",
            sessions = listOf(
                ClassSession(9, sectionId = 6, dayOfWeek = 3, startTime = "12:00", endTime = "13:30", location = "کلاس ۴۰۵"),
                // Even-week class: this capture is recorded in an odd week, so
                // it must render dimmed and prove the parity caption.
                ClassSession(10, sectionId = 6, dayOfWeek = 5, startTime = "16:00", endTime = "17:30", location = "کلاس ۴۰۵", weekType = WeekType.EVEN_WEEKS)
            )
        )
    )

    private fun section(
        id: Long,
        code: String,
        name: String,
        credits: Int,
        instructor: String,
        sessions: List<ClassSession>
    ): SectionWithDetails = SectionWithDetails(
        section = CourseSection(
            id = id,
            courseId = id,
            sectionCode = "1",
            instructor = instructor,
            examDate = "۱۴۰۳/۰۹/۱۲",
            examStartTime = "10:00",
            examEndTime = "12:00"
        ),
        course = Course(id = id, code = code, name = name, credits = credits),
        sessions = sessions
    )

    private companion object {
        const val CAPTURE_TAG = "timetable_capture"
    }
}
