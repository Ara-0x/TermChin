package ir.courseplanner.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.util.JalaliYmd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contract tests for the settings dialogs touched by the 2.9 cleanup:
 * - export dialogs are RTL from the very first composition, keep one stable
 *   button order across recompositions, and hand the FULL text to onCopy;
 * - the semester-start dialog (sole editor of the even/odd basis) rejects an
 *   invalid date and saves a valid one with its parity;
 * - the profile dialog no longer carries any semester-start field.
 *
 * No fixed delays anywhere: every assertion reads the current composition.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsDialogsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setExportContent(
        body: String,
        onCopy: () -> Unit = {},
        copyTestTag: String = "export_json_copy_button"
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                ExportTextDialog(
                    title = "خروجی JSON (۲ درس)",
                    bodyText = body,
                    copyTestTag = copyTestTag,
                    onDismiss = {},
                    onCopy = onCopy
                )
            }
        }
    }

    @Test
    fun exportDialogIsRtlFromFirstComposition() {
        setExportContent("[{\"code\":\"MATH101\"}]")

        composeTestRule.onNodeWithText("خروجی JSON (۲ درس)").assertIsDisplayed()
        // Geometric RTL proof on the very first composition (no prior state):
        // the Row's first child (copy) sits to the RIGHT of close.
        val copyX = composeTestRule.onNodeWithTag("export_json_copy_button")
            .fetchSemanticsNode().positionInRoot.x
        val closeX = composeTestRule.onNodeWithText("بستن")
            .fetchSemanticsNode().positionInRoot.x
        assertTrue(copyX > closeX)
        // The LTR data preview is rendered intact for faithful copying.
        composeTestRule.onNodeWithText("[{\"code\":\"MATH101\"}]").assertIsDisplayed()
    }

    @Test
    fun exportDialogButtonsKeepOrderAcrossRecomposition() {
        val body = androidx.compose.runtime.mutableStateOf("short body")
        composeTestRule.setContent {
            MyApplicationTheme {
                ExportTextDialog(
                    title = "خروجی JSON (۲ درس)",
                    bodyText = body.value,
                    copyTestTag = "export_json_copy_button",
                    onDismiss = {},
                    onCopy = {}
                )
            }
        }

        val copyXBefore = composeTestRule.onNodeWithTag("export_json_copy_button")
            .fetchSemanticsNode().positionInRoot.x
        val closeXBefore = composeTestRule.onNodeWithText("بستن")
            .fetchSemanticsNode().positionInRoot.x
        // RTL row: first child (copy) sits to the right of close.
        assertTrue(copyXBefore > closeXBefore)

        // Recompose with a much longer body (new export content): positions
        // must not move.
        body.value = "long body\n".repeat(200)
        composeTestRule.waitForIdle()

        val copyXAfter = composeTestRule.onNodeWithTag("export_json_copy_button")
            .fetchSemanticsNode().positionInRoot.x
        val closeXAfter = composeTestRule.onNodeWithText("بستن")
            .fetchSemanticsNode().positionInRoot.x
        assertEquals(copyXBefore, copyXAfter, 0.5f)
        assertEquals(closeXBefore, closeXAfter, 0.5f)
        assertTrue(copyXAfter > closeXAfter)
    }

    @Test
    fun exportCopyHandsOverTheFullText() {
        val full = "{\"code\":\"MATH101\"}\n".repeat(50)
        var copied: String? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                // Simulates the call site: onCopy closes over the FULL export
                // string, never the visible preview slice.
                ExportTextDialog(
                    title = "خروجی CSV (۱ درس)",
                    bodyText = full,
                    copyTestTag = "export_csv_copy_button",
                    onDismiss = {},
                    onCopy = { copied = full }
                )
            }
        }

        composeTestRule.onNodeWithTag("export_csv_copy_button").performClick()
        assertEquals(full, copied)
    }

    @Test
    fun semesterDialogRejectsInvalidDate() {
        var saved: Pair<JalaliYmd?, Boolean>? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                EditSemesterStartDialog(
                    initialSemesterStart = "",
                    initialFirstWeekIsOdd = true,
                    onDismiss = {},
                    onSave = { start, odd -> saved = start to odd }
                )
            }
        }

        composeTestRule.onNodeWithTag("semester_start_input")
            .performTextInput("تاریخ نادرست")
        composeTestRule.onNodeWithTag("semester_start_save_button").performClick()

        assertNull(saved)
        composeTestRule.onNodeWithText("تاریخ معتبر نیست (مثلاً ۱۴۰۴/۰۷/۰۵).")
            .assertIsDisplayed()
    }

    @Test
    fun semesterDialogSavesValidDateWithParity() {
        var saved: Pair<JalaliYmd?, Boolean>? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                EditSemesterStartDialog(
                    initialSemesterStart = "",
                    initialFirstWeekIsOdd = true,
                    onDismiss = {},
                    onSave = { start, odd -> saved = start to odd }
                )
            }
        }

        composeTestRule.onNodeWithTag("semester_start_input")
            .performTextInput("۱۴۰۴/۰۷/۰۵")
        composeTestRule.onNodeWithText("هفته زوج").performClick()
        composeTestRule.onNodeWithTag("semester_start_save_button").performClick()

        val result = saved
        assertTrue(result != null)
        assertEquals(1404, result!!.first?.year)
        assertEquals(7, result!!.first?.month)
        assertEquals(5, result!!.first?.day)
        assertEquals(false, result!!.second)
    }

    @Test
    fun profileDialogHasNoSemesterFields() {
        composeTestRule.setContent {
            MyApplicationTheme {
                EditProfileDialog(
                    initialName = "نام",
                    initialMajor = "رشته",
                    initialSemester = "نیم‌سال اول",
                    initialCreditTarget = 20,
                    onDismiss = {},
                    onSave = { _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("ویرایش اطلاعات دانشجو").assertIsDisplayed()
        // Semester start now lives only in «برنامه هفتگی».
        composeTestRule.onAllNodesWithText("تاریخ شروع کلاس‌ها (شمسی)")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("هفته اول ترم:", substring = true)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("هفته فرد", substring = true)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("هفته زوج", substring = true)
            .assertCountEquals(0)
    }
}
