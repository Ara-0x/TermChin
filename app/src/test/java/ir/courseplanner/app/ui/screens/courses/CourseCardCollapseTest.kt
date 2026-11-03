package ir.courseplanner.app.ui.screens.courses

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.SectionWithSessions
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The group drawer must start closed for every course (including an enrolled
 * one) and open only after the student explicitly taps the bar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CourseCardCollapseTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun course(sections: List<SectionWithDetails>): CourseWithSections {
        val first = sections.first()
        return CourseWithSections(
            course = first.course,
            sections = sections.map { SectionWithSessions(it.section, it.sessions) }
        )
    }

    private fun section(
        code: String,
        instructor: String,
        enrolled: Boolean = false
    ): SectionWithDetails {
        val course = Course(id = 1, code = "MATH101", name = "ریاضی ۱")
        return SectionWithDetails(
            section = CourseSection(
                id = code.toLongOrNull() ?: 1,
                courseId = course.id,
                sectionCode = code,
                instructor = instructor,
                isEnrolled = enrolled
            ),
            course = course,
            sessions = listOf(
                ClassSession(
                    id = 1,
                    sectionId = 1,
                    dayOfWeek = 0,
                    startTime = "08:00",
                    endTime = "10:00",
                    location = "کلاس ۱۰۱"
                )
            )
        )
    }

    @Test
    fun `groups start collapsed and open on tap`() {
        val sections = listOf(section("01", "استاد الف"), section("02", "استاد ب"))
        composeTestRule.setContent {
            MyApplicationTheme {
                CourseCard(
                    courseWithSections = course(sections),
                    sections = sections,
                    enrolledKey = "",
                    onToggleSelectedForGeneration = {},
                    onSelectSection = { _, _ -> },
                    onAddSectionClick = {},
                    onEditCourseClick = {},
                    onDeleteCourseClick = {},
                    onDeleteSectionClick = {},
                    onEditSectionClick = {},
                    checkConflict = { null }
                )
            }
        }

        composeTestRule.onNodeWithText("گروه ۰۱").assertDoesNotExist()
        composeTestRule.onNodeWithText("گروه ۰۲").assertDoesNotExist()

        composeTestRule.onNodeWithTag("course_sections_toggle_MATH101").performClick()

        composeTestRule.onNodeWithText("گروه ۰۱").assertExists()
        composeTestRule.onNodeWithText("گروه ۰۲").assertExists()
    }

    @Test
    fun `an enrolled group does not auto-open the drawer`() {
        val sections = listOf(
            section("01", "استاد الف", enrolled = true),
            section("02", "استاد ب")
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                CourseCard(
                    courseWithSections = course(sections),
                    sections = sections,
                    enrolledKey = "01",
                    onToggleSelectedForGeneration = {},
                    onSelectSection = { _, _ -> },
                    onAddSectionClick = {},
                    onEditCourseClick = {},
                    onDeleteCourseClick = {},
                    onDeleteSectionClick = {},
                    onEditSectionClick = {},
                    checkConflict = { null }
                )
            }
        }

        composeTestRule.onNodeWithText("گروه ۰۱").assertDoesNotExist()

        composeTestRule.onNodeWithTag("course_sections_toggle_MATH101").performClick()

        composeTestRule.onNodeWithText("گروه ۰۱").assertExists()
    }
}
