package ir.courseplanner.app.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * `UpdateChecker` end-to-end with a fake fetcher: no sockets, no Robolectric.
 *
 * This is the layer the user actually experiences — the app must stay completely
 * silent on every failure mode, and must prompt exactly once per newer version.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {

    private val newerJson = """
        {
          "tag_name": "v2.7.4",
          "html_url": "https://github.com/Ara-0x/TermChin/releases/tag/v2.7.4",
          "assets": [
            {"name":"TermChin-v2.7.4.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk"}
          ]
        }
    """.trimIndent()

    private fun checker(
        current: String = "2.7.3",
        fetcher: (String) -> String
    ) = UpdateChecker(
        currentVersion = ReleaseVersion.parse(current),
        dispatcher = UnconfinedTestDispatcher(),
        fetcher = fetcher
    )

    @Test
    fun `returns the update when the release is newer`() = runTest {
        val update = checker { newerJson }.checkForUpdate()
        assertEquals("2.7.4", update?.version?.versionName)
        assertEquals(
            "https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk",
            update?.downloadUrl
        )
    }

    @Test
    fun `queries the public github endpoint with no credentials`() = runTest {
        var requested: String? = null
        checker { url ->
            requested = url
            newerJson
        }.checkForUpdate()
        assertEquals(
            "https://api.github.com/repos/Ara-0x/TermChin/releases/latest",
            requested
        )
    }

    @Test
    fun `stays silent when the app is already current`() = runTest {
        val sameVersion = newerJson.replace("v2.7.4", "v2.7.3")
        assertNull(checker { sameVersion }.checkForUpdate())
    }

    @Test
    fun `stays silent when the network fails`() = runTest {
        assertNull(checker { throw IOException("no connectivity") }.checkForUpdate())
    }

    @Test
    fun `stays silent when the payload is not json`() = runTest {
        assertNull(checker { "<html>502 Bad Gateway</html>" }.checkForUpdate())
    }

    @Test
    fun `stays silent when the installed version cannot be parsed`() = runTest {
        val broken = UpdateChecker(
            currentVersion = ReleaseVersion.parse("not-a-version"),
            dispatcher = UnconfinedTestDispatcher(),
            fetcher = { newerJson }
        )
        assertNull(broken.checkForUpdate())
    }

    @Test
    fun `cancellation propagates instead of becoming silent null`() = runTest {
        val checker = UpdateChecker(
            currentVersion = ReleaseVersion.parse("2.7.3"),
            dispatcher = UnconfinedTestDispatcher(),
            fetcher = { throw CancellationException("scope cancelled") }
        )
        var thrown: Throwable? = null
        try {
            checker.checkForUpdate()
        } catch (cancellation: CancellationException) {
            thrown = cancellation
        }
        assertNotNull(
            "a cancelled check must stay cancellable, not turn into 'no update'",
            thrown
        )
    }

    @Test
    fun `checker works end to end over the real transport with a fake connection`() =
        runTest {
            var connectionCount = 0
            val checker = UpdateChecker(
                currentVersion = ReleaseVersion.parse("2.7.3"),
                dispatcher = UnconfinedTestDispatcher(),
                fetcher = { url ->
                    httpGet(url) { target ->
                        connectionCount++
                        object : HttpURLConnection(target) {
                            override fun getResponseCode(): Int = HTTP_OK
                            override fun getInputStream(): InputStream =
                                ByteArrayInputStream(
                                    newerJson.toByteArray(StandardCharsets.UTF_8)
                                )
                            override fun disconnect() {}
                            override fun usingProxy(): Boolean = false
                            override fun connect() {}
                        }
                    }
                }
            )
            val update = checker.checkForUpdate()
            assertEquals("2.7.4", update?.version?.versionName)
            // Exactly one connection: the transport must return after reading,
            // never loop back for another request.
            assertEquals(1, connectionCount)
        }
}