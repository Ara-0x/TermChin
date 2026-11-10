package ir.courseplanner.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun `keeps only the change list from a release body`() {
        val body = """
            ## 🎓 TermChin — نسخه 2.21.0

            > ## ⚠️ اطلاعیهٔ مهم
            > متن هشدار که نباید در دیالوگ بیاید.

            ### ✨ جدید در این نسخه:
            - 🗂 **پایان ترم:** آرشیو اتمیک
            - 📚 `سوابق` تحصیلی
            - [جزئیات](https://example.com)

            > بعد از نصب، نسخه را ببینید.
        """.trimIndent()

        assertEquals(
            "• 🗂 پایان ترم: آرشیو اتمیک\n• 📚 سوابق تحصیلی\n• جزئیات",
            formatReleaseNotes(body)
        )
    }

    @Test
    fun `supports an english whats new heading`() {
        val body = """
            ## Release

            ## What's New
            - Faster sync
            - Fixes a crash
        """.trimIndent()

        assertEquals("• Faster sync\n• Fixes a crash", formatReleaseNotes(body))
    }

    @Test
    fun `blank bodies produce no text`() {
        assertEquals("", formatReleaseNotes(""))
        assertEquals("", formatReleaseNotes("   \n  "))
    }

    @Test
    fun `body without a whats new heading keeps its bullet lines`() {
        assertEquals("• یک تغییر", formatReleaseNotes("- یک تغییر"))
    }

    @Test
    fun `long notes are truncated`() {
        val body = buildString {
            appendLine("### جدید در این نسخه:")
            repeat(200) { appendLine("- خط شمارهٔ $it") }
        }
        val formatted = formatReleaseNotes(body, maxChars = 120)
        assertTrue("expected a cap, got ${formatted.length}", formatted.length <= 121)
        assertTrue(formatted.endsWith("…"))
    }
}
