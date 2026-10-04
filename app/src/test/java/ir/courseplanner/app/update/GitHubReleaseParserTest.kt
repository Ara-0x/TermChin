package ir.courseplanner.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JUnit: the release payload is read by the project's own parser, so no
 * Robolectric (and no device) is needed here.
 *
 * The payloads below are trimmed from the real response of
 * `GET /repos/Ara-0x/TermChin/releases/latest` (tag v2.7.3, two APK assets), so
 * the shape under test is the shape the app actually receives.
 */
class GitHubReleaseParserTest {

    private val current = ReleaseVersion.parse("2.7.3")!!

    private fun releaseJson(
        tag: String = "v2.7.4",
        assets: String = """
            [
              {"name":"TermChin-v2.7.4-abcdef1.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4-abcdef1.apk"},
              {"name":"TermChin-v2.7.4.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk"}
            ]
        """.trimIndent()
    ): String = """
        {
          "tag_name": "$tag",
          "name": "TermChin $tag",
          "html_url": "https://github.com/Ara-0x/TermChin/releases/tag/$tag",
          "assets": $assets
        }
    """.trimIndent()

    @Test
    fun `a newer release yields the versioned apk download url`() {
        val update = GitHubReleaseParser.parseLatestRelease(releaseJson(), current)
        assertEquals("2.7.4", update?.version?.versionName)
        assertEquals(
            "https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk",
            update?.downloadUrl
        )
    }

    @Test
    fun `prefers the plain versioned apk over the cache-busting twin`() {
        // Real release publishes both: the versioned file AND a SHA-suffixed twin.
        // Order in the payload must not matter — the canonical file still wins.
        val reversed = releaseJson(assets = """
            [
              {"name":"TermChin-v2.7.4.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk"},
              {"name":"TermChin-v2.7.4-abcdef1.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4-abcdef1.apk"}
            ]
        """.trimIndent())
        assertEquals(
            "https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk",
            GitHubReleaseParser.parseLatestRelease(reversed, current)?.downloadUrl
        )
    }

    @Test
    fun `falls back to the sha-suffixed twin when only it exists`() {
        val json = releaseJson(assets = """
            [
              {"name":"TermChin-v2.7.4-abcdef1.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4-abcdef1.apk"}
            ]
        """.trimIndent())
        assertEquals(
            "https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4-abcdef1.apk",
            GitHubReleaseParser.parseLatestRelease(json, current)?.downloadUrl
        )
    }

    @Test
    fun `equal or older releases never prompt`() {
        assertNull(GitHubReleaseParser.parseLatestRelease(releaseJson(tag = "v2.7.3"), current))
        assertNull(GitHubReleaseParser.parseLatestRelease(releaseJson(tag = "v2.7.2"), current))
        assertNull(GitHubReleaseParser.parseLatestRelease(releaseJson(tag = "v1.0.0"), current))
    }

    @Test
    fun `malformed payloads are silent no-ops`() {
        assertNull(GitHubReleaseParser.parseLatestRelease("", current))
        assertNull(GitHubReleaseParser.parseLatestRelease("not json at all", current))
        // GitHub's rate-limit and error bodies are valid JSON without a tag.
        assertNull(GitHubReleaseParser.parseLatestRelease("""{"message":"API rate limit exceeded"}""", current))
        // A proxy serving an HTML error page must not be parsed.
        assertNull(GitHubReleaseParser.parseLatestRelease("<html><body>502</body></html>", current))
        // A tag that is not a version.
        assertNull(GitHubReleaseParser.parseLatestRelease(releaseJson(tag = "nightly"), current))
        assertNull(GitHubReleaseParser.parseLatestRelease(releaseJson(tag = ""), current))
    }

    @Test
    fun `a release without assets falls back to the release page`() {
        val json = releaseJson(assets = "[]")
        val update = GitHubReleaseParser.parseLatestRelease(json, current)
        assertEquals(
            "https://github.com/Ara-0x/TermChin/releases/tag/v2.7.4",
            update?.downloadUrl
        )
    }

    @Test
    fun `an apk hosted somewhere else is refused`() {
        val json = releaseJson(assets = """
            [
              {"name":"TermChin-v2.7.4.apk","browser_download_url":"https://evil.example.com/TermChin-v2.7.4.apk"}
            ]
        """.trimIndent())
        // Not an APK we may download -> no asset, so fall back to the GitHub page.
        assertEquals(
            "https://github.com/Ara-0x/TermChin/releases/tag/v2.7.4",
            GitHubReleaseParser.parseLatestRelease(json, current)?.downloadUrl
        )
    }

    @Test
    fun `non apk assets are ignored`() {
        val json = releaseJson(assets = """
            [
              {"name":"sha256sums.txt","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/sha256sums.txt"},
              {"name":"TermChin-v2.7.4.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk"}
            ]
        """.trimIndent())
        assertEquals(
            "https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk",
            GitHubReleaseParser.parseLatestRelease(json, current)?.downloadUrl
        )
    }

    @Test
    fun `trusts only https github urls`() {
        assertTrue(
            GitHubReleaseParser.isTrustedDownloadUrl(
                "https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk"
            )
        )
        assertTrue(
            GitHubReleaseParser.isTrustedDownloadUrl(
                "https://objects.githubusercontent.com/github-production-release-asset/1234"
            )
        )
        // Downgrades and look-alike hosts must fail.
        assertFalse(
            GitHubReleaseParser.isTrustedDownloadUrl(
                "http://github.com/Ara-0x/TermChin/releases/download/v2.7.4/a.apk"
            )
        )
        assertFalse(
            GitHubReleaseParser.isTrustedDownloadUrl("https://evil.example.com/a.apk")
        )
        // Suffix-confusion: "github.com.evil.com" is not GitHub.
        assertFalse(
            GitHubReleaseParser.isTrustedDownloadUrl("https://github.com.evil.example/a.apk")
        )
        assertFalse(GitHubReleaseParser.isTrustedDownloadUrl("not a url"))
        assertFalse(GitHubReleaseParser.isTrustedDownloadUrl(""))
    }
}