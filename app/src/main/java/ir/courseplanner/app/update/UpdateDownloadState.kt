package ir.courseplanner.app.update

import java.io.File

/**
 * Why an in-app update download could not finish.
 *
 * Each case maps to one honest Persian sentence in the update dialog; the enum
 * exists so the messages live in the UI layer and the download layer stays free
 * of copy. Nothing here is "unknown failure" by accident: [UNKNOWN] is only for
 * genuinely unexpected exceptions.
 */
enum class UpdateFailure {
    /** Connection refused, DNS failure, timeout, HTTP error, truncated body. */
    NETWORK,

    /** The bytes did not match the SHA-256 published with the release. */
    CHECKSUM,

    /** The payload was not a well-formed APK/ZIP archive. */
    NOT_AN_APK,

    /** The archive installs a different application. */
    WRONG_PACKAGE,

    /** The archive is not newer than the running build. */
    OLD_VERSION,

    /** No space, or the cache directory is not writable. */
    STORAGE,

    /** Anything unexpected; the UI offers a retry. */
    UNKNOWN
}

/** Thrown by [ApkDownloader]; carries the reason the UI should show. */
class DownloadException(
    val failure: UpdateFailure,
    cause: Throwable? = null
) : java.io.IOException("APK download failed: $failure", cause)

/**
 * The state of the in-app update download, owned by the ViewModel so it
 * survives recomposition, the dialog being recreated, and rotation.
 */
sealed interface UpdateDownloadState {

    /** Nothing downloaded yet; the dialog offers the download button. */
    data object Idle : UpdateDownloadState

    /**
     * Bytes are arriving. [total] is null when the server sent no
     * `Content-Length`, in which case the UI shows an indeterminate bar.
     */
    data class Downloading(
        val bytes: Long,
        val total: Long?
    ) : UpdateDownloadState {
        val fraction: Float?
            get() = total
                ?.takeIf { it > 0L }
                ?.let { (bytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** Fully downloaded, checksum/package verified, waiting for the user to install. */
    data class Ready(val apk: File) : UpdateDownloadState

    /** Stopped with a reason the user can act on. */
    data class Failed(val failure: UpdateFailure) : UpdateDownloadState
}
