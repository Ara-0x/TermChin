package ir.courseplanner.app.ui.components

/**
 * Turns a GitHub release body (Markdown) into the few plain-text lines the
 * update dialog can show without a Markdown renderer.
 *
 * The release template starts with a long compatibility warning and ends with
 * an English duplicate of it; both are blockquotes. The useful part is the
 * "جدید در این نسخه" (or "What's New") list in between, so when that heading is
 * present only the lines after it are kept. Everything is rendered as text, so
 * the input is treated as untrusted: images/links are unwrapped, emphasis and
 * code markers are dropped, and the result is capped.
 */
internal fun formatReleaseNotes(raw: String, maxChars: Int = MAX_RELEASE_NOTES_CHARS): String {
    if (raw.isBlank()) return ""

    val lines = raw.lineSequence().map { it.trim() }.toList()
    val start = lines.indexOfFirst { line ->
        val lower = line.lowercase()
        lower.contains("جدید در این نسخه") ||
            lower.contains("what's new") ||
            lower.contains("whats new")
    }
    val relevant = if (start >= 0) lines.drop(start + 1) else lines

    val out = StringBuilder()
    for (line in relevant) {
        if (line.isEmpty()) continue
        // Horizontal rules, headings and the release template's warning quotes
        // are not part of the change list.
        if (line == "---" || line.startsWith("#") || line.startsWith(">")) continue

        var text = line
        // Images first, then links: an image inside a link must not leave "[]".
        text = text.replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
        text = text.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        text = text.replace("**", "").replace("__", "").replace("`", "")
        text = text.replace(Regex("^[-*•]\\s+"), "• ")
        text = text.trim()
        if (text.isEmpty()) continue

        if (out.isNotEmpty()) out.append('\n')
        out.append(text)
        if (out.length >= maxChars) break
    }

    val formatted = out.toString().trim()
    return when {
        formatted.isEmpty() -> ""
        formatted.length > maxChars -> formatted.take(maxChars).trimEnd() + "…"
        else -> formatted
    }
}

/** Roughly one dialog panel of text; the dialog itself also scrolls. */
internal const val MAX_RELEASE_NOTES_CHARS = 1_400
