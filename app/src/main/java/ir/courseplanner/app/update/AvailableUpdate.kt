package ir.courseplanner.app.update

/**
 * A newer published release, already resolved to a direct APK download URL.
 *
 * [downloadUrl] always points at a concrete asset from the release
 * (`.../releases/download/<tag>/<asset>.apk`), never at a "latest" shortcut:
 * GitHub does not expose a stable `latest` filename here, and the versioned
 * asset name is what the release actually publishes.
 */
data class AvailableUpdate(
    val version: ReleaseVersion,
    val downloadUrl: String,
    val releasePageUrl: String,
    /** Release notes (GitHub release body), already trimmed to a display cap. */
    val changelog: String = "",
    /**
     * Lowercase hex SHA-256 of the APK as published by GitHub (`asset.digest`),
     * or null when the API response carried no digest. When present the
     * downloaded bytes are verified against it before the file may be installed.
     */
    val sha256: String? = null
) {
    /** True when the release published a downloadable APK (not just a page). */
    val hasApkAsset: Boolean
        get() = downloadUrl.endsWith(".apk", ignoreCase = true)
}
