package ir.courseplanner.app.update

/**
 * Decides whether the update prompt may be shown.
 *
 * The rule the product owner asked for is deliberately strict: **one prompt per
 * published version, ever.** Choosing "بعداً" (Later) records the version as seen
 * so the dialog cannot reappear on every single cold start and become the thing
 * users learn to tap away without reading. A genuinely newer release carries a
 * different version, so the prompt returns exactly once per new release.
 */
object UpdatePromptGate {

    /**
     * @param lastPromptedVersion version whose prompt was already shown (or
     *   dismissed); null when the user has never seen a prompt.
     */
    fun shouldPrompt(
        update: AvailableUpdate?,
        lastPromptedVersion: String?
    ): Boolean {
        if (update == null) return false
        // Re-prompting for a version already seen would be nagging.
        if (lastPromptedVersion != null &&
            lastPromptedVersion == update.version.versionName
        ) {
            return false
        }
        return true
    }
}