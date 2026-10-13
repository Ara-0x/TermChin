package ir.courseplanner.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.engine.CourseSuggestionRanker
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric Compose tests for the discovery screen contract:
 * header + search are visible, ranked suggestions render with their reason,
 * the full catalog list is reachable, and the empty catalog points to the
 * real import path. Pure rendering (no ViewModel): the screen reads plain
 * props so the tests stay hermetic.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AddCourseScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun suggestion(
        code: String,
        name: String,
        status: CourseSuggestionRanker.SuggestionStatus,
        reason: String
    ) = CourseSuggestionRanker.CourseSuggestion(
        course = Course(id = code.hashCode().toLong(), code = code, name = name, credits = 3),
        groups = emptyList(),
        bestGroup = null,
        status = status,
        alreadyAdded = false,
        reasons = listOf(reason)
    )

    private fun setScreenContent(
        suggestions: List<CourseSuggestionRanker.CourseSuggestion>,
        query: String = ""
    ) {
        val top = suggestions.take(1)
        val topIds = top.map { it.course.id }.toSet()
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(Modifier.fillMaxSize()) {
                    AddCourseDiscoveryContent(
                        query = query,
                        onQueryChange = {},
                        addableCount = suggestions.size,
                        hasCatalog = suggestions.isNotEmpty(),
                        hasVisible = suggestions.isNotEmpty(),
                        topSuggestions = top,
                        rest = suggestions.filter { it.course.id !in topIds },
                        visible = suggestions,
                        onBack = {},
                        onOpenManualAdd = {},
                        onOpenSettings = {},
                        onClearSearch = {},
                        onAdd = {},
                        onShowGroups = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    @Test
    fun headerSearchAndSuggestionAreVisible() {
        setScreenContent(
            listOf(
                suggestion("MATH101", "ریاضی ۱", CourseSuggestionRanker.SuggestionStatus.COMPATIBLE, "بدون تداخل"),
                suggestion("PHYS204", "فیزیک عمومی", CourseSuggestionRanker.SuggestionStatus.NO_COMPATIBLE_GROUP, "تداخل")
            )
        )

        composeTestRule.onNodeWithText("افزودن درس").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_course_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithText("پیشنهادهای مناسب برای برنامه شما").assertIsDisplayed()
        composeTestRule.onNodeWithText("همه درس‌های قابل افزودن").assertIsDisplayed()
        composeTestRule.onNodeWithText("ریاضی ۱").assertIsDisplayed()
        composeTestRule.onNodeWithText("فیزیک عمومی").assertIsDisplayed()
        composeTestRule.onNodeWithText("بدون تداخل").assertIsDisplayed()
    }

    @Test
    fun emptyCatalogPointsToSettings() {
        setScreenContent(emptyList())

        composeTestRule.onNodeWithText("کاتالوگ درسی برای کشف وجود ندارد").assertIsDisplayed()
        composeTestRule.onNodeWithTag("discovery_open_settings_button").assertIsDisplayed()
    }

    @Test
    fun searchFieldAcceptsInput() {
        setScreenContent(
            listOf(suggestion("MATH101", "ریاضی ۱", CourseSuggestionRanker.SuggestionStatus.COMPATIBLE, "بدون تداخل")),
            query = "ریاضی"
        )

        composeTestRule.onNodeWithTag("add_course_search_input").performTextInput("۱")
        composeTestRule.onNodeWithTag("add_course_search_input").assertIsDisplayed()
    }

    @Test
    fun addButtonIsClickable() {
        setScreenContent(
            listOf(suggestion("MATH101", "ریاضی ۱", CourseSuggestionRanker.SuggestionStatus.COMPATIBLE, "بدون تداخل"))
        )

        composeTestRule.onNodeWithTag("discovery_add_MATH101").performClick()
        composeTestRule.onNodeWithTag("discovery_card_MATH101").assertIsDisplayed()
    }
}
