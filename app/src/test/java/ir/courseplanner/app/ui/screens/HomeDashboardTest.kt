package ir.courseplanner.app.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.engine.ScheduleEngine.UpcomingSession
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.util.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar
import java.util.TimeZone

/**
 * Dashboard contracts for the Home redesign: deterministic clock derivation,
 * now/next resolution (ongoing vs upcoming vs future day vs none), parity
 * honesty, zero-target credit safety, attention prioritization, and the key
 * rendered states. Time-dependent logic runs on explicit inputs only — no
 * real-elapsed-time flakes.
 */
class HomeDashboardPureTest {

    private val tehran = TimeZone.getTimeZone("Asia/Tehran")

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long {
        val cal = Calendar.getInstance(tehran).apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun details(
        courseName: String = "ریاضی ۱",
        day: Int = 3,
        start: String = "10:00",
        end: String = "12:00",
        weekType: WeekType = WeekType.EVERY_WEEK
    ): SectionWithDetails {
        val section = CourseSection(courseId = 1, sectionCode = "01", instructor = "استاد")
        return SectionWithDetails(
            section = section,
            course = Course(id = 1, code = "MATH101", name = courseName),
            sessions = listOf(
                ClassSession(
                    sectionId = 1,
                    dayOfWeek = day,
                    startTime = start,
                    endTime = end,
                    weekType = weekType
                )
            )
        )
    }

    // -- clock ---------------------------------------------------------------

    @Test
    fun `dashboardNow derives weekday minutes and epoch coherently`() {
        // Tuesday 1404/… 10:30 Asia/Tehran.
        val now = dashboardNow(millis(2025, 10, 14, 10, 30), tehran)

        assertEquals(3, now.appDayIdx) // Tuesday
        assertEquals(630, now.nowMinutes)
        // Epoch and weekday must agree with each other.
        assertEquals(now.appDayIdx, JalaliDate.appDayIndexOfEpochDay(now.epochDay))
        val ymd = JalaliDate.fromEpochDay(now.epochDay)!!
        assertEquals(1404, ymd.year)
        assertEquals(7, ymd.month)
    }

    @Test
    fun `midnight rollover moves day and epoch together`() {
        val before = dashboardNow(millis(2025, 10, 14, 23, 59), tehran)
        val after = dashboardNow(millis(2025, 10, 15, 0, 1), tehran)

        assertEquals(before.epochDay + 1, after.epochDay)
        assertEquals((before.appDayIdx + 1) % 7, after.appDayIdx)
        assertEquals(1, after.nowMinutes)
    }

    // -- now/next ------------------------------------------------------------

    private fun upcoming(
        day: Int,
        start: String,
        end: String,
        ongoing: Boolean
    ): UpcomingSession {
        val sec = details(day = day, start = start, end = end)
        return UpcomingSession(sec, sec.sessions.single(), day, ongoing)
    }

    @Test
    fun `ongoing class reports remaining live-window minutes`() {
        // 10:00-12:00 at 10:20 → live window ends 11:30 → 70 left.
        val state = resolveNowNext(upcoming(3, "10:00", "12:00", true), 3, 620)

        assertTrue(state is NowNextState.Ongoing)
        assertEquals(70, (state as NowNextState.Ongoing).minutesLeft)
    }

    @Test
    fun `upcoming class today reports minutes to start`() {
        val state = resolveNowNext(upcoming(3, "10:00", "12:00", false), 3, 565)

        assertTrue(state is NowNextState.UpcomingToday)
        assertEquals(35, (state as NowNextState.UpcomingToday).minutesToStart)
    }

    @Test
    fun `future-day class is never reported as today`() {
        val state = resolveNowNext(upcoming(4, "08:00", "10:00", false), 3, 700)

        assertTrue(state is NowNextState.LaterDay)
        assertEquals(4, (state as NowNextState.LaterDay).item.dayOfWeek)
    }

    @Test
    fun `null next means none scheduled`() {
        assertEquals(NowNextState.NoneScheduled, resolveNowNext(null, 3, 700))
    }

    @Test
    fun `past sessions are skipped by the engine lookup`() {
        // 08:00-10:00 already over at 11:00; 14:00-16:00 is next.
        val sec = SectionWithDetails(
            section = CourseSection(courseId = 1, sectionCode = "01"),
            course = Course(id = 1, code = "C1", name = "درس"),
            sessions = listOf(
                ClassSession(sectionId = 1, dayOfWeek = 2, startTime = "08:00", endTime = "10:00"),
                ClassSession(sectionId = 1, dayOfWeek = 2, startTime = "14:00", endTime = "16:00")
            )
        )
        val next = ScheduleEngine.nextUpcomingSession(listOf(sec), 2, 660)

        assertEquals("14:00", next!!.session.startTime)
        assertEquals(false, next.ongoing)
    }

    @Test
    fun `odd-week session is not next in an even week`() {
        val sec = details(weekType = WeekType.ODD_WEEKS)
        val next = ScheduleEngine.nextUpcomingSession(
            sections = listOf(sec),
            dayOfWeek = 3,
            nowMinutes = 500,
            todayParity = ScheduleEngine.WeekParity.EVEN
        )

        assertNull(next)
    }

    @Test
    fun `unknown parity keeps every session visible`() {
        val sec = details(weekType = WeekType.ODD_WEEKS)
        val todays = ScheduleEngine.sessionsOnDay(listOf(sec), 3, null)
        val next = ScheduleEngine.nextUpcomingSession(listOf(sec), 3, 500, null)

        assertEquals(1, todays.size)
        assertTrue(next != null)
    }

    // -- countdown / credits / gaps ------------------------------------------

    @Test
    fun `countdown uses Persian digits`() {
        assertEquals("۳۵ دقیقه تا شروع کلاس", countdownFa(35))
        assertEquals("کمتر از یک دقیقه تا شروع کلاس", countdownFa(0))
    }

    @Test
    fun `credit progress is null for zero target`() {
        assertNull(creditProgressRatio(12, 0))
        assertNull(creditProgressRatio(12, -3))
        assertEquals(0.6f, creditProgressRatio(12, 20)!!, 0.001f)
        assertEquals(1f, creditProgressRatio(25, 20)!!, 0.001f)
    }

    @Test
    fun `gap formatting is human Persian`() {
        assertEquals("بدون گپ", formatGapFa(0))
        assertEquals("۴۵ دقیقه", formatGapFa(45))
        assertEquals("۲ ساعت", formatGapFa(120))
        assertEquals("۲ ساعت و ۳۰ دقیقه", formatGapFa(150))
    }

    // -- attention -----------------------------------------------------------

    @Test
    fun `no enrolled courses means no attention items`() {
        assertTrue(
            buildAttentionItems(
                classConflicts = 2,
                examConflicts = 1,
                examWarnings = 1,
                parityKnown = false,
                hasEnrolled = false
            ).isEmpty()
        )
    }

    @Test
    fun `attention prioritizes class over exam over missing start over warnings`() {
        val items = buildAttentionItems(
            classConflicts = 1,
            examConflicts = 1,
            examWarnings = 2,
            parityKnown = false,
            hasEnrolled = true
        )

        assertEquals(
            listOf(
                AttentionKind.CLASS_CONFLICT,
                AttentionKind.EXAM_CONFLICT,
                AttentionKind.MISSING_SEMESTER_START
            ),
            items.take(3).map { it.kind }
        )
    }

    @Test
    fun `known parity drops the missing-start item`() {
        val items = buildAttentionItems(
            classConflicts = 0,
            examConflicts = 0,
            examWarnings = 1,
            parityKnown = true,
            hasEnrolled = true
        )

        assertEquals(listOf(AttentionKind.EXAM_WARNING), items.map { it.kind })
    }

    @Test
    fun `clean program yields no attention card`() {
        assertTrue(
            buildAttentionItems(0, 0, 0, parityKnown = true, hasEnrolled = true).isEmpty()
        )
    }

    // -- Jalali helpers ------------------------------------------------------

    @Test
    fun `month names and fa digits`() {
        assertEquals("مهر", JalaliDate.monthNameFa(7))
        assertEquals("اسفند", JalaliDate.monthNameFa(12))
        assertEquals("", JalaliDate.monthNameFa(13))
        assertEquals("۱۲۳", JalaliDate.toFaDigits(123))
        assertEquals("کد ۱۰۱", JalaliDate.toFaDigits("کد 101"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeDashboardUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun details(
        courseName: String = "ریاضی ۱",
        day: Int = 3,
        start: String = "10:00",
        end: String = "12:00",
        location: String = "کلاس ۱۰۱"
    ): SectionWithDetails {
        val section = CourseSection(courseId = 1, sectionCode = "01", instructor = "استاد")
        return SectionWithDetails(
            section = section,
            course = Course(id = 1, code = "MATH101", name = courseName),
            sessions = listOf(
                ClassSession(
                    sectionId = 1,
                    dayOfWeek = day,
                    startTime = start,
                    endTime = end,
                    location = location
                )
            )
        )
    }

    private fun upcoming(
        day: Int,
        start: String,
        end: String,
        ongoing: Boolean
    ): UpcomingSession {
        val sec = details(day = day, start = start, end = end)
        return UpcomingSession(sec, sec.sessions.single(), day, ongoing)
    }

    @Test
    fun ongoingCardShowsStatusAndCountdown() {
        val item = upcoming(3, "10:00", "12:00", true)
        composeTestRule.setContent {
            MyApplicationTheme {
                NowNextCard(
                    todayIdx = 3,
                    todayClasses = listOf(item),
                    state = NowNextState.Ongoing(item, 70),
                    weekLabel = null,
                    nowMinutes = 620,
                    onOpenWeeklyTimetable = {},
                    onManageCourses = {}
                )
            }
        }

        composeTestRule.onNodeWithText("در حال برگزاری").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_now_next_headline")
            .assertTextEquals("ریاضی ۱")
        composeTestRule.onNodeWithTag("home_now_next_countdown").assertIsDisplayed()
        composeTestRule.onNodeWithText("۷۰ دقیقه مانده", substring = true).assertIsDisplayed()
    }

    @Test
    fun upcomingCardShowsCountdownToStart() {
        val item = upcoming(3, "10:00", "12:00", false)
        composeTestRule.setContent {
            MyApplicationTheme {
                NowNextCard(
                    todayIdx = 3,
                    todayClasses = listOf(item),
                    state = NowNextState.UpcomingToday(item, 35),
                    weekLabel = null,
                    nowMinutes = 565,
                    onOpenWeeklyTimetable = {},
                    onManageCourses = {}
                )
            }
        }

        composeTestRule.onNodeWithText("کلاس بعدی").assertIsDisplayed()
        composeTestRule.onNodeWithText("۳۵ دقیقه تا شروع کلاس").assertIsDisplayed()
    }

    @Test
    fun noneScheduledShowsManageAction() {
        composeTestRule.setContent {
            MyApplicationTheme {
                NowNextCard(
                    todayIdx = 3,
                    todayClasses = emptyList(),
                    state = NowNextState.NoneScheduled,
                    weekLabel = null,
                    nowMinutes = 700,
                    onOpenWeeklyTimetable = {},
                    onManageCourses = {}
                )
            }
        }

        composeTestRule.onNodeWithText("امروز کلاسی نداری.").assertIsDisplayed()
        composeTestRule.onNodeWithText("تکمیل ساعت کلاس‌ها").assertIsDisplayed()
    }

    @Test
    fun attentionCardListsIssuesWithActions() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AttentionCard(
                    items = listOf(
                        AttentionItem(
                            AttentionKind.CLASS_CONFLICT,
                            "تداخل ساعت کلاسی",
                            "۱ مورد"
                        ),
                        AttentionItem(
                            AttentionKind.MISSING_SEMESTER_START,
                            "شروع ترم تنظیم نشده",
                            "…"
                        )
                    ),
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("home_attention_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("تداخل ساعت کلاسی").assertIsDisplayed()
        composeTestRule.onNodeWithText("شروع ترم تنظیم نشده").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_attention_action_CLASS_CONFLICT").assertIsDisplayed()
    }

    @Test
    fun emptyAttentionListRendersNothing() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AttentionCard(items = emptyList(), onAction = {})
            }
        }

        composeTestRule.onAllNodesWithText("نیاز به توجه", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun overviewWithZeroTargetShowsCreditsWithoutBar() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OverviewCard(
                    credits = 12,
                    target = 0,
                    activeDays = 3,
                    gapMinutes = 45,
                    courseCount = 4,
                    onOptimize = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("home_overview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_overview_credits_value")
            .assertTextEquals("۱۲ واحد ثبت شده")
    }

    @Test
    fun overviewOffersOptimizeForSubstantialGaps() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OverviewCard(
                    credits = 12,
                    target = 20,
                    activeDays = 3,
                    gapMinutes = 150,
                    courseCount = 4,
                    onOptimize = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("home_optimize_schedule_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("۲ ساعت و ۳۰ دقیقه").assertIsDisplayed()
    }

    @Test
    fun overviewHidesOptimizeForSmallGaps() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OverviewCard(
                    credits = 12,
                    target = 20,
                    activeDays = 3,
                    gapMinutes = 30,
                    courseCount = 4,
                    onOptimize = {}
                )
            }
        }

        composeTestRule.onNodeWithText("۳۰ دقیقه").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("بازبینی در برنامه‌ساز", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun headerShowsGreetingDateAndWeek() {
        val now = DashboardNow(epochDay = JalaliDate.todayEpochDay(), appDayIdx = 3, nowMinutes = 600)
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardHeader(
                    studentName = "امیر",
                    semesterName = "نیم‌سال اول",
                    major = "کامپیوتر",
                    credits = 12,
                    creditTarget = 20,
                    now = now,
                    weekInfo = ScheduleEngine.AcademicWeek(4, ScheduleEngine.WeekParity.ODD)
                )
            }
        }

        composeTestRule.onNodeWithText("سلام، امیر").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_header_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("هفته ۴ (فرد)", substring = true).assertIsDisplayed()
    }

    @Test
    fun guidedEmptyStateShowsThreeDistinctSteps() {
        composeTestRule.setContent {
            MyApplicationTheme {
                GuidedEmptyState(onOpenSettings = {}, onOpenCourses = {})
            }
        }

        composeTestRule.onNodeWithTag("home_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("ورود اطلاعات درسی").assertIsDisplayed()
        composeTestRule.onNodeWithText("انتخاب درس‌ها").assertIsDisplayed()
        composeTestRule.onNodeWithText("ساخت برنامه هوشمند").assertIsDisplayed()
    }
}
