package ir.courseplanner.app.update

/**
 * Parses one payload of GitHub's "latest release" endpoint
 * (`GET /repos/{owner}/{repo}/releases/latest`).
 *
 * The JSON underneath is read by the project's own tiny [JsonParser] — not
 * `org.json`, which is only a runtime stub on the JVM test classpath ("not
 * mocked"), and not a new dependency for one 2 KB response.
 *
 * Everything here is defensive on purpose. The payload comes from the network, so
 * a proxy error page, a rate-limit JSON object, a renamed repository or a
 * hijacked mirror must all resolve to null ("no update known") rather than to a
 * crash, a bogus version, or a download link pointing somewhere unexpected.
 *
 * Only a few fields are trusted:
 *  - `tag_name`               → the version to compare against
 *  - `assets[].browser_download_url` → the direct APK URL
 *  - `assets[].digest`        → optional SHA-256 of that exact APK
 *  - `html_url`               → the human-readable release page (fallback)
 */
object GitHubReleaseParser {

    /** Only APKs are downloadable; anything else is ignored. */
    private fun isApk(assetName: String): Boolean =
        assetName.endsWith(".apk", ignoreCase = true)

    /**
     * Accepts only `https` URLs whose host is GitHub itself. Release assets are
     * served from github.com and redirect to GitHub's CDN
     * (objects/release-assets.githubusercontent.com). This is the app's single
     * outbound trust boundary: without it, a spoofed response could hand the
     * user a download link to any host on the internet.
     */
    private val TRUSTED_HOSTS = setOf(
        "github.com",
        "www.github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    fun isTrustedDownloadUrl(url: String): Boolean {
        // java.net.URI (not android.net.Uri) keeps this validator pure JVM code,
        // so the trust boundary is unit-testable without a device or Robolectric.
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return TRUSTED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Returns the update to offer, or null when the response is unusable or not
     * newer than [currentVersion].
     */
    fun parseLatestRelease(
        json: String,
        currentVersion: ReleaseVersion
    ): AvailableUpdate? {
        val root = JsonParser.parse(json) as? Json.Obj ?: return null
        // Rate limits and errors are valid JSON without a tag; opt out early.
        val tag = root.string("tag_name")?.trim() ?: return null
        val latest = ReleaseVersion.parse(tag) ?: return null

        val htmlUrl = root.string("html_url")?.trim().orEmpty()
        val releasePage = if (isTrustedDownloadUrl(htmlUrl)) htmlUrl else ""

        val asset = findApkAsset(root)
        val downloadUrl = asset?.url ?: releasePage.ifEmpty { return null }

        // Only a strictly newer version may ever prompt the user.
        if (!latest.isNewerThan(currentVersion)) return null

        return AvailableUpdate(
            version = latest,
            downloadUrl = downloadUrl,
            releasePageUrl = releasePage,
            // The notes are only ever rendered as plain text; the cap keeps a
            // hostile/oversized payload from bloating the in-memory release.
            changelog = root.string("body")?.trim()?.take(MAX_CHANGELOG_CHARS).orEmpty(),
            sha256 = asset?.sha256
        )
    }

    /** A release body longer than this is truncated; the dialog scrolls anyway. */
    private const val MAX_CHANGELOG_CHARS = 6_000

    /** The APK chosen for download plus its published checksum, if any. */
    internal data class ApkAsset(val url: String, val sha256: String?)

    /**
     * Picks the APK asset to download. The release publishes two APKs:
     * a versioned one (`TermChin-v2.7.3.apk`) and a cache-busting one that also
     * carries the commit SHA (`TermChin-v2.7.3-3951ca2.apk`). The versioned name
     * is preferred because it is the stable, human-recognisable file; otherwise
     * the first trustworthy `.apk` is used.
     */
    internal fun findApkAsset(root: Json.Obj): ApkAsset? {
        val assets = root.array("assets") ?: return null
        var fallback: ApkAsset? = null
        for (item in assets) {
            val asset = item as? Json.Obj ?: continue
            val name = asset.string("name")?.trim() ?: continue
            if (!isApk(name)) continue
            val url = asset.string("browser_download_url")?.trim() ?: continue
            if (!isTrustedDownloadUrl(url)) continue
            val candidate = ApkAsset(url = url, sha256 = parseDigest(asset.string("digest")))
            // The canonical asset carries no commit SHA after the version:
            // "TermChin-v2.7.3.apk" -> tail "2.7.3"; the cache-busting twin
            // "TermChin-v2.7.3-3951ca2.apk" -> tail "3951ca2".
            val tail = name.substringBeforeLast(".apk", name)
                .substringAfterLast('-')
                .trimStart('v', 'V')
            val isVersionedOnly = tail.matches(Regex("^\\d+(\\.\\d+)*$"))
            if (isVersionedOnly) return candidate
            if (fallback == null) fallback = candidate
        }
        return fallback
    }

    private val SHA256_DIGEST = Regex("^sha256:([0-9a-fA-F]{64})$")

    /**
     * GitHub reports `asset.digest` as `"sha256:<hex>"`. Anything else (an
     * unknown algorithm, a malformed value, a missing field) yields null, which
     * simply skips the checksum comparison rather than blocking the update.
     */
    internal fun parseDigest(digest: String?): String? {
        val hex = digest?.trim()?.let { SHA256_DIGEST.matchEntire(it)?.groupValues?.get(1) }
            ?: return null
        return hex.lowercase()
    }

    /** Kept for callers that only need the URL. */
    internal fun findApkAssetUrl(root: Json.Obj): String? = findApkAsset(root)?.url
}
