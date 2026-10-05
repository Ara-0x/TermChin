package ir.courseplanner.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Transport-level tests for [httpGet]: the one function the whole suite used to
 * skip (every other test injects a fake fetcher). A missing `return` here once
 * turned the HTTP-200 success path into an unbounded request loop, so these
 * tests pin the transport contract with fake connections — no sockets involved.
 */
class HttpGetTransportTest {

    private val trustedUrl =
        "https://api.github.com/repos/Ara-0x/TermChin/releases/latest"

    /** A scripted connection: serves one status, then (on 200) one body. */
    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val location: String?,
        private val bodyText: String,
        val openedUrls: MutableList<String>
    ) : HttpURLConnection(url) {

        var opens = 0
        var disconnected = false

        override fun getResponseCode(): Int {
            opens++
            openedUrls.add(url.toString())
            return status
        }

        override fun getHeaderField(name: String): String? =
            location?.takeIf { name == "Location" }

        override fun getInputStream(): InputStream {
            check(status == HTTP_OK) { "no body for status $status" }
            return ByteArrayInputStream(bodyText.toByteArray(StandardCharsets.UTF_8))
        }

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false
        override fun connect() {}
    }

    private val connections = mutableListOf<FakeConnection>()

    private fun httpGetWith(
        url: String = trustedUrl,
        status: Int = 200,
        location: String? = null,
        bodyText: String = """{"tag_name":"v9.9.9"}"""
    ): String {
        val openedUrls = mutableListOf<String>()
        return httpGet(url) { target ->
            FakeConnection(target, status, location, bodyText, openedUrls)
                .also { connections.add(it) }
        }
    }

    @Test
    fun `a 200 response is returned exactly once`() {
        val body = httpGetWith()
        assertEquals("""{"tag_name":"v9.9.9"}""", body)
        // The success path must not re-request: one connection, then done.
        assertEquals(1, connections.size)
        assertEquals(listOf(trustedUrl), connections.single().openedUrls)
    }

    @Test
    fun `a redirect to a trusted url is followed once`() {
        val target = "https://api.github.com/repos/Ara-0x/TermChin/releases/123"
        val openedUrls = mutableListOf<String>()
        var calls = 0
        val body = httpGet(trustedUrl) { url ->
            calls++
            if (calls == 1) {
                FakeConnection(url, 302, target, "", openedUrls)
                    .also { connections.add(it) }
            } else {
                FakeConnection(url, 200, null, "ok", openedUrls)
                    .also { connections.add(it) }
            }
        }
        assertEquals("ok", body)
        assertEquals(listOf(trustedUrl, target), openedUrls)
    }

    @Test
    fun `a redirect off github is refused`() {
        try {
            httpGetWith(status = 302, location = "https://evil.example.com/a.apk")
            fail("expected an off-GitHub redirect to be refused")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("HTTPS/GitHub"))
        }
    }

    @Test
    fun `a redirect to plain http is refused`() {
        try {
            httpGetWith(
                status = 302,
                location = "http://github.com/Ara-0x/TermChin/releases/latest"
            )
            fail("expected an http downgrade redirect to be refused")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("HTTPS/GitHub"))
        }
    }

    @Test
    fun `a redirect without a location header fails`() {
        try {
            httpGetWith(status = 302, location = null)
            fail("expected a location-less redirect to fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Location"))
        }
    }

    @Test
    fun `more than five redirects fail`() {
        val openedUrls = mutableListOf<String>()
        try {
            httpGet(trustedUrl) { url ->
                // No manual add here: FakeConnection records each responseCode()
                // call in openedUrls exactly once.
                FakeConnection(url, 302, trustedUrl, "", openedUrls)
                    .also { connections.add(it) }
            }
            fail("expected a redirect loop to fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Too many redirects"))
        }
        // 1 initial + 5 followed redirects = 6 connections before the guard trips.
        assertEquals(6, openedUrls.size)
    }

    @Test
    fun `a non-200 status throws`() {
        for (status in listOf(404, 500, 403)) {
            try {
                httpGetWith(status = status)
                fail("expected HTTP $status to throw")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message!!.contains("$status"))
            }
        }
    }

    @Test
    fun `an untrusted starting url is refused before any connection`() {
        var opened = 0
        try {
            httpGet("http://github.com/Ara-0x/TermChin/releases/latest") {
                opened++
                throw AssertionError("must never open a connection")
            }
            fail("expected an http start URL to be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("non-HTTPS"))
        }
        assertEquals(0, opened)
        try {
            httpGet("https://evil.example.com/a.apk") {
                throw AssertionError("must never open a connection")
            }
            fail("expected an off-GitHub start URL to be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("non-GitHub"))
        }
    }

    @Test
    fun `the request carries identification headers and no redirect following`() {
        httpGetWith()
        val connection = connections.single()
        assertEquals("GET", connection.requestMethod)
        assertTrue(
            connection.getRequestProperty("User-Agent")!!.startsWith("TermChin-Android/")
        )
        assertEquals("application/vnd.github+json", connection.getRequestProperty("Accept"))
        assertEquals(false, connection.instanceFollowRedirects)
    }

}
