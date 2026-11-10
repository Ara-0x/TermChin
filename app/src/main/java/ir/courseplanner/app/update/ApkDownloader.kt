package ir.courseplanner.app.update

import ir.courseplanner.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Properties

/** One progress sample: absolute bytes written so far and the expected total (if known). */
data class DownloadProgress(val bytes: Long, val total: Long?)

/**
 * Streams the release APK into the app's cache directory.
 *
 * This is the only place the app writes bytes from the network to disk, so the
 * rules are strict:
 *  - HTTPS only, and only GitHub-controlled hosts — every redirect is
 *    re-validated, so a `302` cannot downgrade the transfer or point it at an
 *    attacker's server;
 *  - bytes go to `<name>.part` and are only renamed after the size, the ZIP
 *    magic and (when the release publishes one) the SHA-256 match;
 *  - a dropped connection does not throw the transfer away: the partial file
 *    plus its URL/ETag are kept and the next attempt resumes with an HTTP
 *    `Range` request, so a 15 MB APK on a flaky network is not restarted from
 *    zero;
 *  - the total is capped, so a hostile/endless response cannot fill the disk;
 *  - explicit cancellation deletes the partial file.
 *
 * Nothing here installs anything: [UpdateInstaller] hands the finished file to
 * Android's own package installer.
 */
open class ApkDownloader(
    private val openConnection: (URL) -> HttpURLConnection = { target ->
        target.openConnection() as HttpURLConnection
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Downloads [url] to [target] and returns the finished file.
     *
     * [onProgress] is invoked on the IO thread, throttled to roughly every
     * [PROGRESS_INTERVAL_MS], and always once with the final byte count.
     */
    open suspend fun download(
        url: String,
        expectedSha256: String?,
        target: File,
        onProgress: (DownloadProgress) -> Unit = {}
    ): File = withContext(ioDispatcher) {
        if (!GitHubReleaseParser.isTrustedDownloadUrl(url)) {
            throw DownloadException(UpdateFailure.NETWORK)
        }
        val directory = target.parentFile
            ?: throw DownloadException(UpdateFailure.STORAGE)
        if (!directory.exists() && !directory.mkdirs()) {
            throw DownloadException(UpdateFailure.STORAGE)
        }
        val part = File(directory, target.name + ".part")
        val meta = File(directory, target.name + ".part.json")

        // A stale Range response or a server-side change can invalidate the
        // partial file; one clean restart is cheaper than failing the update.
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return@withContext transfer(url, expectedSha256, target, part, meta, onProgress)
            } catch (restart: RestartDownload) {
                discard(part, meta)
                if (attempt == MAX_ATTEMPTS - 1) {
                    throw DownloadException(UpdateFailure.NETWORK, restart)
                }
            }
        }
        throw DownloadException(UpdateFailure.NETWORK)
    }

    private suspend fun transfer(
        url: String,
        expectedSha256: String?,
        target: File,
        part: File,
        meta: File,
        onProgress: (DownloadProgress) -> Unit
    ): File {
        val saved = readMetadata(meta)
        val resumable = saved.getProperty("url") == url && part.exists()
        var resumeFrom = if (resumable) part.length() else 0L
        val etag = if (resumable) saved.getProperty("etag").orEmpty() else ""

        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(url, resumeFrom, etag)
            val status = connection.responseCode
            if (status == HTTP_RANGE_NOT_SATISFIABLE) throw RestartDownload()
            if (status != HttpURLConnection.HTTP_OK && status != HTTP_PARTIAL) {
                throw DownloadException(UpdateFailure.NETWORK)
            }

            val isResume = status == HTTP_PARTIAL
            if (isResume) {
                if (resumeFrom <= 0L ||
                    !contentRangeStartsAt(connection.getHeaderField("Content-Range"), resumeFrom)
                ) {
                    throw RestartDownload()
                }
            } else {
                // The server ignored the range and is sending the whole file.
                resumeFrom = 0L
            }

            val contentRange = connection.getHeaderField("Content-Range")
            val total = contentRangeTotal(contentRange)
                ?: connection.contentLengthLong
                    .takeIf { it > 0L }
                    ?.let { resumeFrom + it }
            if (total != null && total > MAX_APK_BYTES) {
                throw DownloadException(UpdateFailure.STORAGE)
            }
            writeMetadata(meta, url, connection.getHeaderField("ETag"), total)

            var received = 0L
            var lastReport = 0L
            onProgress(DownloadProgress(resumeFrom, total))

            FileOutputStream(part, isResume).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        received += read
                        if (resumeFrom + received > MAX_APK_BYTES) {
                            throw DownloadException(UpdateFailure.STORAGE)
                        }
                        output.write(buffer, 0, read)

                        val now = System.nanoTime()
                        if (now - lastReport >= PROGRESS_INTERVAL_NANOS) {
                            lastReport = now
                            onProgress(DownloadProgress(resumeFrom + received, total))
                        }
                    }
                }
            }

            val complete = resumeFrom + received
            if (total != null && complete != total) {
                // Keep the partial: the next attempt resumes from `complete`.
                throw DownloadException(UpdateFailure.NETWORK)
            }
            if (!looksLikeApk(part)) {
                throw DownloadException(UpdateFailure.NOT_AN_APK)
            }
            if (expectedSha256 != null && !sha256(part).equals(expectedSha256, ignoreCase = true)) {
                throw DownloadException(UpdateFailure.CHECKSUM)
            }
            onProgress(DownloadProgress(complete, total ?: complete))

            target.delete()
            if (!part.renameTo(target)) {
                throw DownloadException(UpdateFailure.STORAGE)
            }
            meta.delete()
            return target
        } catch (cancellation: CancellationException) {
            discard(part, meta)
            throw cancellation
        } catch (restart: RestartDownload) {
            discard(part, meta)
            throw restart
        } catch (failure: DownloadException) {
            // A network failure keeps the bytes for the next attempt; anything
            // that means the bytes themselves are wrong is deleted outright.
            if (failure.failure != UpdateFailure.NETWORK) discard(part, meta)
            throw failure
        } catch (failure: IOException) {
            throw DownloadException(UpdateFailure.NETWORK, failure)
        } catch (failure: Throwable) {
            discard(part, meta)
            throw DownloadException(UpdateFailure.UNKNOWN, failure)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Opens the URL, following at most [MAX_REDIRECTS] GitHub redirects. The
     * initial URL is validated by the caller; every hop is validated here again,
     * and the resume headers are re-applied to each hop (a redirect drops them).
     */
    private fun openFollowingRedirects(
        url: String,
        rangeFrom: Long,
        etag: String
    ): HttpURLConnection {
        var target = url
        var redirects = 0
        while (true) {
            val connection = openConnection(URL(target))
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                // GitHub rejects requests without a User-Agent, and a binary
                // asset must not be transparently decompressed: the SHA-256 is
                // published over the stored bytes.
                connection.setRequestProperty("User-Agent", "TermChin-Android/${BuildConfig.VERSION_NAME}")
                connection.setRequestProperty("Accept", "application/octet-stream")
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (rangeFrom > 0L) {
                    connection.setRequestProperty("Range", "bytes=$rangeFrom-")
                    if (etag.isNotEmpty()) connection.setRequestProperty("If-Range", etag)
                }

                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw DownloadException(UpdateFailure.NETWORK)
                    if (redirects++ >= MAX_REDIRECTS) {
                        throw DownloadException(UpdateFailure.NETWORK)
                    }
                    val next = URL(URL(target), location).toString()
                    if (!GitHubReleaseParser.isTrustedDownloadUrl(next)) {
                        throw DownloadException(UpdateFailure.NETWORK)
                    }
                    target = next
                    connection.disconnect()
                    continue
                }
                return connection
            } catch (failure: Throwable) {
                connection.disconnect()
                throw failure
            }
        }
    }

    /**
     * `bytes 100-999/1000` → resumes at 100. Anything else (a server that
     * ignored `If-Range`, a different range, garbage) forces a clean restart.
     */
    private fun contentRangeStartsAt(header: String?, expected: Long): Boolean {
        val value = header?.trim().orEmpty()
        val match = CONTENT_RANGE.matchEntire(value) ?: return false
        return match.groupValues[1].toLongOrNull() == expected
    }

    private fun contentRangeTotal(header: String?): Long? {
        val value = header?.trim().orEmpty()
        val match = CONTENT_RANGE.matchEntire(value) ?: return null
        return match.groupValues[3].toLongOrNull()
    }

    private fun looksLikeApk(file: File): Boolean {
        if (file.length() < APK_MAGIC.size) return false
        val header = ByteArray(APK_MAGIC.size)
        return runCatching {
            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read == -1) return false
                    offset += read
                }
            }
            header.contentEquals(APK_MAGIC)
        }.getOrDefault(false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun readMetadata(meta: File): Properties = Properties().apply {
        if (meta.exists()) {
            runCatching { meta.inputStream().use { load(it) } }
        }
    }

    private fun writeMetadata(meta: File, url: String, etag: String?, total: Long?) {
        runCatching {
            Properties().apply {
                setProperty("url", url)
                setProperty("etag", etag.orEmpty())
                setProperty("total", (total ?: 0L).toString())
            }.store(meta.outputStream(), null)
        }
    }

    private fun discard(vararg files: File) {
        files.forEach { runCatching { it.delete() } }
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xFF
            append(HEX[value shr 4])
            append(HEX[value and 0x0F])
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_REDIRECTS = 5
        const val MAX_ATTEMPTS = 2
        const val HTTP_PARTIAL = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val PROGRESS_INTERVAL_MS = 150L
        val PROGRESS_INTERVAL_NANOS = PROGRESS_INTERVAL_MS * 1_000_000L

        /** Far above a real TermChin APK (~15 MB) and still bounded. */
        const val MAX_APK_BYTES = 200L * 1024 * 1024

        val APK_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        val CONTENT_RANGE = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$")

        val HEX = "0123456789abcdef".toCharArray()
    }

    /**
     * Internal control-flow signal: the saved partial file cannot be reused, so
     * the transfer restarts from byte zero instead of surfacing an error.
     */
    private class RestartDownload : Exception()
}
