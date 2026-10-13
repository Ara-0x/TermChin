package ir.courseplanner.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contract for the unified bottom navigation: all five destinations render
 * with stable tags and labels, taps route to the right destination, and the
 * bar carries no hidden sixth entry.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TermChinBottomBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setBar(
        current: AppDestination = AppDestination.HOME,
        onNavigate: (AppDestination) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                TermChinBottomBar(current = current, onNavigate = onNavigate)
            }
        }
    }

    @Test
    fun allFiveDestinationsAreVisible() {
        setBar()

        composeTestRule.onNodeWithTag("main_bottom_nav").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_courses").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_schedule").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_documents").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("خانه").assertIsDisplayed()
        composeTestRule.onNodeWithText("دروس").assertIsDisplayed()
        composeTestRule.onNodeWithText("برنامه‌ساز").assertIsDisplayed()
        composeTestRule.onNodeWithText("جزوات").assertIsDisplayed()
        composeTestRule.onNodeWithText("تنظیمات").assertIsDisplayed()
    }

    @Test
    fun tapsRouteToTheRightDestination() {
        var destination: AppDestination? = null
        setBar { destination = it }

        composeTestRule.onNodeWithTag("nav_item_schedule").performClick()
        assertEquals(AppDestination.SCHEDULE, destination)

        composeTestRule.onNodeWithTag("nav_item_documents").performClick()
        assertEquals(AppDestination.DOCUMENTS, destination)

        composeTestRule.onNodeWithTag("nav_item_home").performClick()
        assertEquals(AppDestination.HOME, destination)
    }
}
