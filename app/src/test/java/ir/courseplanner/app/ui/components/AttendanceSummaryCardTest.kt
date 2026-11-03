package ir.courseplanner.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.courseplanner.app.data.model.AttendanceStats
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The absence summary is the first thing a student checks, so its warning must
 * be exact: a zero limit with no absences is not a warning, and a zero limit
 * with an absence must still be flagged even though "0 از 0" is meaningless.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AttendanceSummaryCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun section(): SectionWithDetails {
        val course = Course(id = 7, code = "MATH101", name = "ریاضی ۱")
        return SectionWithDetails(
            section = CourseSection(
                id = 11,
                courseId = course.id,
                sectionCode = "01",
                instructor = "استاد الف",
                isEnrolled = true
            ),
            course = course,
            sessions = listOf(
                ClassSession(
                    id = 1,
                    sectionId = 11,
                    dayOfWeek = 0,
                    startTime = "08:00",
                    endTime = "10:00"
                )
            )
        )
    }

    @Test
    fun `zero limit without absences does not warn`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceSummaryCard(
                    enrolledSections = listOf(section()),
                    statsByCourse = mapOf(7L to AttendanceStats(present = 2)),
                    absenceLimit = 0
                )
            }
        }

        composeTestRule.onNodeWithText("غیبت ثبت شده").assertDoesNotExist()
        composeTestRule.onNodeWithText("۰ از ۰").assertDoesNotExist()
        composeTestRule.onNodeWithText("حاضر").assertIsDisplayed()
    }

    @Test
    fun `reaching the limit shows the exact absence count`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceSummaryCard(
                    enrolledSections = listOf(section()),
                    statsByCourse = mapOf(7L to AttendanceStats(absent = 3)),
                    absenceLimit = 3
                )
            }
        }

        composeTestRule.onNodeWithText("۳ از ۳").assertIsDisplayed()
    }

    @Test
    fun `excused absences get their own pill`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceSummaryCard(
                    enrolledSections = listOf(section()),
                    statsByCourse = mapOf(7L to AttendanceStats(absent = 3, excused = 2)),
                    absenceLimit = 3
                )
            }
        }

        composeTestRule.onNodeWithText("موجه").assertIsDisplayed()
        composeTestRule.onNodeWithText("۳ از ۳").assertIsDisplayed()
    }

    @Test
    fun `without a schedule action the header is not clickable`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceSummaryCard(
                    enrolledSections = listOf(section()),
                    statsByCourse = emptyMap(),
                    absenceLimit = 3
                )
            }
        }

        // No dangling chevron promises navigation that will never happen.
        composeTestRule.onNodeWithContentDescription("باز کردن برنامه").assertDoesNotExist()
    }

    @Test
    fun `with a schedule action the header navigates`() {
        var opened = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceSummaryCard(
                    enrolledSections = listOf(section()),
                    statsByCourse = emptyMap(),
                    absenceLimit = 3,
                    onOpenSchedule = { opened++ }
                )
            }
        }

        composeTestRule.onNodeWithText("حضور و غیاب").performClick()
        assertEquals(1, opened)
        composeTestRule.onNodeWithContentDescription("باز کردن برنامه").assertIsDisplayed()
    }
}
