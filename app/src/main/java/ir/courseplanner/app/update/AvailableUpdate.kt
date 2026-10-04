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
    val releasePageUrl: String
)