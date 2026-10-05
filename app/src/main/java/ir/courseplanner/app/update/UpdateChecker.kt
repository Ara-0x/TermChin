package ir.courseplanner.app.update

import ir.courseplanner.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs the single outbound request the app is allowed to make: a GET against
 * GitHub's public "latest release" endpoint. No auth, no user data, no cookies,
 * no telemetry.
 *
 * All three collaborators (current version, dispatcher, fetcher) are constructor
 * parameters so the whole decision chain — fetch, parse, compare — is testable
 * without a live network. The app uses the no-argument constructor.
 *
 * The transport itself ([httpGet]) is tested separately with a fake connection
 * via its own `openConnection` seam, so no test ever touches the network.
 */
@Singleton
class UpdateChecker(
    private val currentVersion: ReleaseVersion?,
    private val dispatcher: CoroutineDispatcher,
    private val fetcher: (String) -> String
) {

    @Inject
    constructor() : this(
        currentVersion = ReleaseVersion.parse(BuildConfig.VERSION_NAME),
        dispatcher = Dispatchers.IO,
        fetcher = ::httpGet
    )

    /**
     * Returns the newer release, or null when the app is up to date or when
     * anything at all goes wrong (no network, rate limit, malformed payload).
     * Failures are deliberately silent: no dialog, no snackbar, no error state —
     * the app behaves exactly as if no update exists, and the check is retried on
     * the next cold start.
     *
     * Cancellation is never swallowed: a [CancellationException] propagates so
     * the surrounding scope can actually cancel the check.
     */
    suspend fun checkForUpdate(): AvailableUpdate? {
        val current = currentVersion ?: return null
        return withContext(dispatcher) {
            val body = try {
                fetcher(LATEST_RELEASE_URL)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                return@withContext null
            }
            GitHubReleaseParser.parseLatestRelease(body, current)
        }
    }
}

/** GitHub's public, unauthenticated latest-release endpoint. */
private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/Ara-0x/TermChin/releases/latest"

private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 8_000

/**
 * Reads at most [MAX_RESPONSE_BYTES] of the response body.
 *
 * The cap is a hardening measure: a hostile or misconfigured endpoint cannot make
 * the app allocate unbounded memory on a cold start. It is far above the real
 * payload (a two-asset release is roughly 2 KB).
 */
private const val MAX_RESPONSE_BYTES = 512 * 1024

/**
 * HTTPS-only GET returning the body as text, or throwing on any failure.
 *
 * Redirects are followed manually and re-validated against HTTPS, so a
 * `302` to `http://` cannot silently downgrade the connection. Any failure
 * propagates to [UpdateChecker], which treats it as "no update known".
 *
 * [openConnection] is a seam for tests: production opens a real connection,
 * tests inject a fake so this transport layer is verifiable without a network.
 */
internal fun httpGet(
    url: String,
    openConnection: (URL) -> HttpURLConnection = { target ->
        target.openConnection() as HttpURLConnection
    }
): String {
    require(GitHubReleaseParser.isTrustedDownloadUrl(url)) {
        "Refusing to fetch a non-HTTPS or non-GitHub URL: $url"
    }
    var connection: HttpURLConnection? = null
    var redirects = 0
    var target = url
    try {
        while (true) {
            connection = openConnection(URL(target)).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                // GitHub rejects requests without a User-Agent.
                setRequestProperty("User-Agent", "TermChin-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("Redirect $status without a Location header")
                check(redirects++ < 5) { "Too many redirects" }
                target = URL(URL(target), location).toString()
                check(GitHubReleaseParser.isTrustedDownloadUrl(target)) {
                    "Refusing to follow a redirect off HTTPS/GitHub: $target"
                }
                connection.disconnect()
                connection = null
                continue
            }
            check(status == HttpURLConnection.HTTP_OK) { "Unexpected HTTP status $status" }
            // Only the 200 path touches the body stream. Non-200 responses never
            // reach it (the check above throws first), so the error stream is
            // intentionally never read: failures are silent by design.
            val stream = connection.inputStream
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                val buffer = CharArray(MAX_RESPONSE_BYTES)
                val out = StringBuilder()
                while (out.length < buffer.size) {
                    val read = reader.read(buffer)
                    if (read == -1) break
                    out.appendRange(buffer, 0, read)
                }
                return out.toString()
            }
        }
    } finally {
        connection?.disconnect()
    }
}