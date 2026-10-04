package ir.courseplanner.app.update

/**
 * A dotted release version such as `2.7.3`, parsed from a GitHub tag (`v2.7.3`)
 * or from `BuildConfig.VERSION_NAME`.
 *
 * TermChin compares **versionName**, not versionCode, because GitHub's
 * `releases/latest` API exposes no `versionCode` field: the only machine-readable
 * version the API guarantees is the tag / `versionName` pair. The release tag is
 * forced to equal `versionName` by the CI "Version regression gate", so the tag
 * is an authoritative mirror of the shipped version.
 *
 * Parsing is deliberately strict: anything that is not a leading `v` followed by
 * 1–4 numeric components returns null, so a renamed repository, a hijacked
 * mirror response or an HTML error page can never be read as "there is an update".
 */
class ReleaseVersion private constructor(
    val versionName: String,
    private val parts: List<Int>
) : Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int {
        // Pad to the longer component list: 2.7 == 2.7.0 so a shortened tag
        // never looks like a downgrade (which would nag forever).
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val mine = parts.getOrElse(index) { 0 }
            val theirs = other.parts.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return 0
    }

    /** True only for a strictly newer release than [current]. */
    fun isNewerThan(current: ReleaseVersion): Boolean = this > current

    override fun toString(): String = versionName

    // Not a data class: the component list must never be settable after parsing,
    // and a private constructor + generated copy() is a Kotlin warning.
    override fun equals(other: Any?): Boolean =
        other is ReleaseVersion && versionName == other.versionName

    override fun hashCode(): Int = versionName.hashCode()

    companion object {
        private val PATTERN = Regex("^v?(\\d{1,9})(?:\\.(\\d{1,9}))?(?:\\.(\\d{1,9}))?(?:\\.(\\d{1,9}))?$")

        /** Parses `v2.7.3`, `2.7.3`, `2.7`, `2` — or returns null if malformed. */
        fun parse(raw: String?): ReleaseVersion? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val match = PATTERN.matchEntire(trimmed) ?: return null
            // A group that did not participate in the match is null, not 0 —
            // normalise so 2.7 does not become [2,7,null,null].
            val numbers = (match.groupValues.drop(1).map { it })
                .filter { it.isNotEmpty() }
                .map { it.toInt() }
            if (numbers.isEmpty()) return null
            return ReleaseVersion(
                versionName = trimmed.removePrefix("v").removePrefix("V"),
                parts = numbers
            )
        }
    }
}