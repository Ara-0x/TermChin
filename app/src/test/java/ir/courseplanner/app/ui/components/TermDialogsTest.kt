package ir.courseplanner.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.util.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contract tests for the end-of-term dialogs:
 * - nothing is archived when the course list is empty;
 * - the student sees and can change the term name before any write, and a
 *   blank name never reaches the repository;
 * - the next-term dialog refuses an invalid start date but treats an empty one
 *   as "unknown week", which is a valid choice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TermDialogsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `end term confirm is blocked while there are no courses`() {
        var confirmed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                EndTermConfirmDialog(
                    courseCount = 0,
                    onDismiss = {},
                    onConfirm = { confirmed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("ادامه").assertIsNotEnabled()
        composeTestRule.onNodeWithText(
            "در ترم فعلی درسی انتخاب نشده است؛ برای ثبت پایان ترم ابتدا از «دروس» درس‌هایتان را اضافه کنید."
        ).assertIsDisplayed()
        assertFalse(confirmed)
    }

    @Test
    fun `end term confirm continues with a non empty course list`() {
        var confirmed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                EndTermConfirmDialog(
                    courseCount = 3,
                    onDismiss = {},
                    onConfirm = { confirmed = true }
                )
            }
        }

        composeTestRule.onNodeWithText(
            "با ادامه، ترم فعلی با ۳ درس پایان‌یافته و آرشیو می‌شود."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("ادامه").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun `archive name dialog suggests the current term and returns the edited name`() {
        var confirmedName: String? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                TermArchiveNameDialog(
                    initialName = "نیم‌سال اول ۱۴۰۴-۱۴۰۵",
                    onDismiss = {},
                    onConfirm = { confirmedName = it }
                )
            }
        }

        composeTestRule.onNodeWithText("نیم‌سال اول ۱۴۰۴-۱۴۰۵").assertIsDisplayed()
        composeTestRule.onNodeWithTag("archive_term_name")
            .performTextReplacement("  نیم‌سال دوم ۱۴۰۴-۱۴۰۵  ")
        composeTestRule.onNodeWithText("آرشیو ترم").performClick()

        assertEquals("نیم‌سال دوم ۱۴۰۴-۱۴۰۵", confirmedName)
    }

    @Test
    fun `archive name dialog rejects a blank name without confirming`() {
        var confirmedName: String? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                TermArchiveNameDialog(
                    initialName = "ترم جاری",
                    onDismiss = {},
                    onConfirm = { confirmedName = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("archive_term_name").performTextReplacement("   ")
        composeTestRule.onNodeWithText("آرشیو ترم").performClick()

        composeTestRule.onNodeWithText("نام ترم الزامی است.").assertIsDisplayed()
        assertNull(confirmedName)
    }

    @Test
    fun `dismissing the archive name dialog writes nothing`() {
        var dismissed = false
        var confirmedName: String? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                TermArchiveNameDialog(
                    initialName = "ترم جاری",
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmedName = it }
                )
            }
        }

        composeTestRule.onNodeWithText("انصراف").performClick()

        assertTrue(dismissed)
        assertNull(confirmedName)
    }

    @Test
    fun `start new term accepts a valid jalali date`() {
        var confirmedName: String? = null
        var confirmedStart: Long? = null
        var wasConfirmed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                StartNewTermDialog(
                    onDismiss = {},
                    onConfirm = { name, start ->
                        wasConfirmed = true
                        confirmedName = name
                        confirmedStart = start
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("new_term_name").performTextReplacement("  نیم‌سال دوم  ")
        composeTestRule.onNodeWithTag("new_term_start_date").performTextReplacement("۱۴۰۴/۰۷/۰۵")
        composeTestRule.onNodeWithText("شروع ترم").performClick()

        assertTrue(wasConfirmed)
        assertEquals("نیم‌سال دوم", confirmedName)
        assertEquals(JalaliDate.toEpochDay(1404, 7, 5), confirmedStart)
    }

    @Test
    fun `start new term treats an empty date as unknown`() {
        var confirmedStart: Long? = -1L
        var wasConfirmed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                StartNewTermDialog(
                    onDismiss = {},
                    onConfirm = { _, start ->
                        wasConfirmed = true
                        confirmedStart = start
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("new_term_name").performTextReplacement("ترم بدون تاریخ")
        composeTestRule.onNodeWithTag("new_term_start_date").performTextReplacement("")
        composeTestRule.onNodeWithText("شروع ترم").performClick()

        assertTrue(wasConfirmed)
        assertNull(confirmedStart)
    }

    @Test
    fun `start new term rejects an impossible date`() {
        var wasConfirmed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                StartNewTermDialog(
                    onDismiss = {},
                    onConfirm = { _, _ -> wasConfirmed = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("new_term_name").performTextReplacement("ترم نامعتبر")
        composeTestRule.onNodeWithTag("new_term_start_date").performTextReplacement("1404/13/40")
        composeTestRule.onNodeWithText("شروع ترم").performClick()

        composeTestRule.onNodeWithText("تاریخ شروع معتبر نیست؛ نمونه: ۱۴۰۴/۰۷/۰۵")
            .assertIsDisplayed()
        assertFalse(wasConfirmed)
    }
}
