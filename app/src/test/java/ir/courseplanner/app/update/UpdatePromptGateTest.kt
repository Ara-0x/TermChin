package ir.courseplanner.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "don't be annoying" contract, in isolation.
 *
 * Requirement: the prompt appears once per published version. Choosing "بعداً"
 * (Later) must silence that same version forever, while a genuinely newer release
 * must still be announced.
 */
class UpdatePromptGateTest {

    private fun update(version: String) = AvailableUpdate(
        version = ReleaseVersion.parse(version)!!,
        downloadUrl = "https://github.com/Ara-0x/TermChin/releases/download/v$version/TermChin-v$version.apk",
        releasePageUrl = "https://github.com/Ara-0x/TermChin/releases/tag/v$version"
    )

    @Test
    fun `prompts when an update exists and none was seen`() {
        assertTrue(UpdatePromptGate.shouldPrompt(update("2.7.4"), lastPromptedVersion = null))
    }

    @Test
    fun `never prompts twice for the same version`() {
        // First launch shows it; "بعداً" stores "2.7.4"; the next cold start must
        // stay quiet even though the update is still newer than the installed one.
        assertTrue(UpdatePromptGate.shouldPrompt(update("2.7.4"), null))
        assertFalse(UpdatePromptGate.shouldPrompt(update("2.7.4"), "2.7.4"))
        assertFalse(UpdatePromptGate.shouldPrompt(update("2.7.4"), "2.7.4"))
    }

    @Test
    fun `prompts again once a newer version is published`() {
        assertFalse(UpdatePromptGate.shouldPrompt(update("2.7.4"), "2.7.4"))
        assertTrue(UpdatePromptGate.shouldPrompt(update("2.7.5"), "2.7.4"))
    }

    @Test
    fun `never prompts without an update`() {
        assertFalse(UpdatePromptGate.shouldPrompt(update = null, lastPromptedVersion = null))
        assertFalse(UpdatePromptGate.shouldPrompt(update = null, lastPromptedVersion = "2.7.4"))
    }
}