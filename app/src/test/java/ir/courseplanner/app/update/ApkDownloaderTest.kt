package ir.courseplanner.app.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

/**
 * Transport tests for the in-app APK downloader, with a scripted connection —
 * no sockets, no GitHub, no device. Pins the promises that make the feature
 * trustworthy: GitHub-only redirects, a checksum before install, resumable
 * transfers that never lose good bytes, and no half-file handed to the
 * installer.
 */
class ApkDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val apkUrl =
        "https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk"

    /** A minimal ZIP/APK header plus a recognisable body. */
    private val apkBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04) +
        ByteArray(4096) { it.toByte() }

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val location: String?,
        private val body: InputStream,
        private val declaredLength: Long,
        private val headers: Map<String, String> = emptyMap()
    ) : HttpURLConnection(url) {

        var disconnected = false

        /** Records the request headers the downloader sets (Range/If-Range/...). */
        private val requestHeaders = mutableMapOf<String, String>()

        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }

        override fun getRequestProperty(key: String): String? = requestHeaders[key]

        override fun getResponseCode(): Int = status

        override fun getHeaderField(name: String): String? =
            if (name == "Location") location else headers[name]

        override fun getContentLengthLong(): Long = declaredLength

        override fun getInputStream(): InputStream = body

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false
        override fun connect() {}
    }

    private val connections = mutableListOf<FakeConnection>()

    private fun downloader(
        status: Int = 200,
        location: String? = null,
        body: ByteArray = apkBytes,
        declaredLength: Long = body.size.toLong(),
        failAfter: Int? = null,
        headers: Map<String, String> = emptyMap()
    ): ApkDownloader = ApkDownloader(
        openConnection = { url ->
            val stream = if (failAfter != null) {
                TruncatingStream(body, failAfter)
            } else {
                ByteArrayInputStream(body)
            }
            FakeConnection(url, status, location, stream, declaredLength, headers)
                .also { connections.add(it) }
        }
    )

    // A fresh, uniquely named folder per call: one test asserts several HTTP
    // statuses in a loop, so a fixed name would collide on the second iteration.
    private fun targetFile(): File = File(temporaryFolder.newFolder(), "update.apk")

    @Test
    fun `a 200 response is written to the target and progress ends at the total`() = runBlocking {
        val target = targetFile()
        val samples = mutableListOf<DownloadProgress>()
        val file = downloader().download(apkUrl, null, target) { samples.add(it) }

        assertEquals(target, file)
        assertTrue(file.exists())
        assertTrue(file.readBytes().contentEquals(apkBytes))
        assertFalse("the .part file must be renamed away", part(target).exists())
        assertEquals(apkBytes.size.toLong(), samples.last().bytes)
        assertEquals(apkBytes.size.toLong(), samples.last().total)
    }

    @Test
    fun `a matching sha256 is accepted`() = runBlocking {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(apkBytes)
            .joinToString("") { "%02x".format(it) }
        val file = downloader().download(apkUrl, digest.uppercase(), targetFile())
        assertTrue(file.exists())
    }

    @Test
    fun `a mismatching sha256 deletes the download`() = runBlocking {
        val target = targetFile()
        try {
            downloader().download(apkUrl, "0".repeat(64), target)
            fail("expected a checksum mismatch")
        } catch (expected: DownloadException) {
            assertEquals(UpdateFailure.CHECKSUM, expected.failure)
        }
        assertFalse(target.exists())
        assertFalse(part(target).exists())
    }

    @Test
    fun `a payload that is not a zip is rejected`() = runBlocking {
        val target = targetFile()
        try {
            downloader(body = "<html>404</html>".toByteArray()).download(apkUrl, null, target)
            fail("expected the non-APK payload to be rejected")
        } catch (expected: DownloadException) {
            assertEquals(UpdateFailure.NOT_AN_APK, expected.failure)
        }
        assertFalse(target.exists())
    }

    @Test
    fun `an untrusted start url is refused before any connection`() = runBlocking {
        var opened = 0
        val downloader = ApkDownloader(openConnection = {
            opened++
            throw AssertionError("must never connect")
        })
        try {
            downloader.download("https://evil.example.com/a.apk", null, targetFile())
            fail("expected the URL to be refused")
        } catch (expected: DownloadException) {
            assertEquals(UpdateFailure.NETWORK, expected.failure)
        }
        assertEquals(0, opened)
    }

    @Test
    fun `a redirect off github is refused`() = runBlocking {
        try {
            downloader(status = 302, location = "https://evil.example.com/a.apk")
                .download(apkUrl, null, targetFile())
            fail("expected an off-GitHub redirect to be refused")
        } catch (expected: DownloadException) {
            assertEquals(UpdateFailure.NETWORK, expected.failure)
        }
    }

    @Test
    fun `a redirect to the release CDN is followed`() = runBlocking {
        // GitHub serves release bytes from release-assets.githubusercontent.com.
        val cdn = "https://release-assets.githubusercontent.com/github-production-release-asset/1"
        val opened = mutableListOf<String>()
        val downloader = ApkDownloader(openConnection = { url ->
            opened.add(url.toString())
            if (opened.size == 1) {
                FakeConnection(url, 302, cdn, ByteArrayInputStream(ByteArray(0)), -1L)
            } else {
                FakeConnection(url, 200, null, ByteArrayInputStream(apkBytes), apkBytes.size.toLong())
            }
        })
        assertTrue(downloader.download(apkUrl, null, targetFile()).exists())
        assertEquals(listOf(apkUrl, cdn), opened)
    }

    @Test
    fun `an http error is a network failure`() = runBlocking {
        for (status in listOf(403, 404, 500)) {
            try {
                downloader(status = status).download(apkUrl, null, targetFile())
                fail("expected HTTP $status to fail")
            } catch (expected: DownloadException) {
                assertEquals(UpdateFailure.NETWORK, expected.failure)
            }
        }
    }

    @Test
    fun `a truncated body keeps the bytes so the next attempt can resume`() = runBlocking {
        val target = targetFile()
        try {
            downloader(declaredLength = apkBytes.size + 100L).download(apkUrl, null, target)
            fail("expected the short body to be rejected")
        } catch (expected: DownloadException) {
            assertEquals(UpdateFailure.NETWORK, expected.failure)
        }
        assertFalse(target.exists())
        assertEquals(apkBytes.size.toLong(), part(target).length())
    }

    @Test
    fun `a mid-stream failure keeps the partial file`() = runBlocking {
        val target = targetFile()
        try {
            downloader(failAfter = 1024).download(apkUrl, null, target)
            fail("expected the broken stream to fail")
        } catch (expected: DownloadException) {
            assertEquals(UpdateFailure.NETWORK, expected.failure)
        }
        assertFalse(target.exists())
        assertEquals(1024L, part(target).length())
    }

    @Test
    fun `a saved partial is resumed with a range request`() = runBlocking {
        val target = targetFile()
        val resumeAt = 1000L
        val etag = "\"abc123\""
        val part = part(target)
        part.parentFile?.mkdirs()
        part.writeBytes(apkBytes.copyOfRange(0, resumeAt.toInt()))
        target.parentFile?.let { dir ->
            Properties().apply {
                setProperty("url", apkUrl)
                setProperty("etag", etag)
                setProperty("total", apkBytes.size.toString())
            }.store(File(dir, "${target.name}.part.json").outputStream(), null)
        }

        val remaining = apkBytes.copyOfRange(resumeAt.toInt(), apkBytes.size)
        val client = downloader(
            status = 206,
            body = remaining,
            declaredLength = remaining.size.toLong(),
            headers = mapOf(
                "Content-Range" to "bytes $resumeAt-${apkBytes.size - 1}/${apkBytes.size}",
                "ETag" to etag
            )
        )
        val file = client.download(apkUrl, null, target)

        assertTrue(file.exists())
        assertTrue("resumed bytes must join the saved prefix", file.readBytes().contentEquals(apkBytes))
        val request = connections.single()
        assertEquals("bytes=$resumeAt-", request.getRequestProperty("Range"))
        assertEquals(etag, request.getRequestProperty("If-Range"))
        assertFalse(part.exists())
        assertFalse(File("${target.path}.part.json").exists())
    }

    @Test
    fun `a partial from a different url is not reused`() = runBlocking {
        val target = targetFile()
        val part = part(target)
        part.parentFile?.mkdirs()
        part.writeBytes(byteArrayOf(1, 2, 3))
        Properties().apply {
            setProperty("url", "https://github.com/Ara-0x/TermChin/releases/download/v0.0.1/old.apk")
            setProperty("etag", "\"old\"")
        }.store(File(target.parentFile!!, "${target.name}.part.json").outputStream(), null)

        val file = downloader().download(apkUrl, null, target)
        assertTrue(file.readBytes().contentEquals(apkBytes))
        assertNull(connections.single().getRequestProperty("Range"))
    }

    @Test
    fun `a 416 response restarts the transfer from zero`() = runBlocking {
        val target = targetFile()
        val part = part(target)
        part.parentFile?.mkdirs()
        part.writeBytes(ByteArray(64) { 7 })
        Properties().apply {
            setProperty("url", apkUrl)
            setProperty("etag", "\"abc\"")
        }.store(File(target.parentFile!!, "${target.name}.part.json").outputStream(), null)

        var calls = 0
        val client = ApkDownloader(openConnection = { url ->
            calls++
            if (calls == 1) {
                FakeConnection(url, 416, null, ByteArrayInputStream(ByteArray(0)), -1L)
            } else {
                FakeConnection(url, 200, null, ByteArrayInputStream(apkBytes), apkBytes.size.toLong())
            }
        })
        val file = client.download(apkUrl, null, target)
        assertTrue(file.readBytes().contentEquals(apkBytes))
        assertEquals(2, calls)
    }

    @Test
    fun `a suspiciously large body is refused before a byte is written`() = runBlocking {
        val target = targetFile()
        try {
            downloader(declaredLength = 300L * 1024 * 1024).download(apkUrl, null, target)
            fail("expected the size cap to reject the transfer")
        } catch (expected: DownloadException) {
            assertEquals(UpdateFailure.STORAGE, expected.failure)
        }
        assertFalse(target.exists())
        assertFalse("nothing may be left on disk", part(target).exists())
    }

    @Test
    fun `a range response that does not match the partial restarts the transfer`() = runBlocking {
        val target = targetFile()
        val part = part(target)
        part.parentFile?.mkdirs()
        // 1000 bytes exist locally, but the server answers with a range that
        // starts somewhere else: the prefix cannot be trusted.
        part.writeBytes(apkBytes.copyOfRange(0, 1000))
        Properties().apply {
            setProperty("url", apkUrl)
            setProperty("etag", "\"abc\"")
        }.store(File(target.parentFile!!, "${target.name}.part.json").outputStream(), null)

        var calls = 0
        val client = ApkDownloader(openConnection = { url ->
            calls++
            if (calls == 1) {
                FakeConnection(
                    url,
                    206,
                    null,
                    ByteArrayInputStream(apkBytes),
                    apkBytes.size.toLong(),
                    mapOf("Content-Range" to "bytes 0-${apkBytes.size - 1}/${apkBytes.size}")
                )
            } else {
                FakeConnection(url, 200, null, ByteArrayInputStream(apkBytes), apkBytes.size.toLong())
            }
        })
        val file = client.download(apkUrl, null, target)
        assertTrue("a clean restart must still produce the whole file", file.readBytes().contentEquals(apkBytes))
        assertEquals(2, calls)
    }

    @Test
    fun `cancellation deletes the partial file`() = runBlocking {
        val target = targetFile()
        try {
            downloader().download(apkUrl, null, target) { throw CancellationException("stop") }
            fail("expected cancellation to propagate")
        } catch (expected: CancellationException) {
            // expected
        }
        assertFalse(target.exists())
        assertFalse(part(target).exists())
    }

    private fun part(target: File): File = File(target.parentFile, "${target.name}.part")

    /** Serves [payload] and then fails, simulating a dropped connection. */
    private class TruncatingStream(
        private val payload: ByteArray,
        private val failAfter: Int
    ) : InputStream() {
        private var position = 0

        override fun read(): Int {
            if (position >= failAfter) throw IOException("connection dropped")
            return payload[position++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= failAfter) throw IOException("connection dropped")
            val count = minOf(length, failAfter - position, payload.size - position)
            if (count <= 0) throw IOException("connection dropped")
            System.arraycopy(payload, position, buffer, offset, count)
            position += count
            return count
        }
    }
}
