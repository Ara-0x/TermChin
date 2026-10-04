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
 * Only three fields are trusted:
 *  - `tag_name`               → the version to compare against
 *  - `assets[].browser_download_url` → the direct APK URL
 *  - `html_url`               → the human-readable release page (fallback)
 */
object GitHubReleaseParser {

    /** Only APKs are downloadable; anything else is ignored. */
    private fun isApk(assetName: String): Boolean =
        assetName.endsWith(".apk", ignoreCase = true)

    /**
     * Accepts only `https` URLs whose host is GitHub itself (release assets are
     * served from github.com and redirect to objects.githubusercontent.com).
     * This is the app's single outbound trust boundary: without it, a spoofed
     * response could hand the user a download link to any host on the internet.
     */
    private val TRUSTED_HOSTS = setOf(
        "github.com",
        "www.github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com"
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

        val downloadUrl = findApkAssetUrl(root) ?: releasePage.ifEmpty { return null }

        // Only a strictly newer version may ever prompt the user.
        if (!latest.isNewerThan(currentVersion)) return null

        return AvailableUpdate(
            version = latest,
            downloadUrl = downloadUrl,
            releasePageUrl = releasePage
        )
    }

    /**
     * Picks the APK asset to download. The release publishes two APKs:
     * a versioned one (`TermChin-v2.7.3.apk`) and a cache-busting one that also
     * carries the commit SHA (`TermChin-v2.7.3-3951ca2.apk`). The versioned name
     * is preferred because it is the stable, human-recognisable file; otherwise
     * the first trustworthy `.apk` is used.
     */
    internal fun findApkAssetUrl(root: Json.Obj): String? {
        val assets = root.array("assets") ?: return null
        var fallback: String? = null
        for (item in assets) {
            val asset = item as? Json.Obj ?: continue
            val name = asset.string("name")?.trim() ?: continue
            if (!isApk(name)) continue
            val url = asset.string("browser_download_url")?.trim() ?: continue
            if (!isTrustedDownloadUrl(url)) continue
            // The canonical asset carries no commit SHA after the version:
            // "TermChin-v2.7.3.apk" -> tail "2.7.3"; the cache-busting twin
            // "TermChin-v2.7.3-3951ca2.apk" -> tail "3951ca2".
            val tail = name.substringBeforeLast(".apk", name)
                .substringAfterLast('-')
                .trimStart('v', 'V')
            val isVersionedOnly = tail.matches(Regex("^\\d+(\\.\\d+)*$"))
            if (isVersionedOnly) return url
            if (fallback == null) fallback = url
        }
        return fallback
    }
}