package ir.courseplanner.app.update

import android.content.Context
import java.io.File

/**
 * Reads the package metadata of a downloaded archive.
 *
 * This is a class rather than a top-level function so the download flow can be
 * tested offline: unit tests override [read] and return canned metadata instead
 * of asking the platform to parse the file.
 */
open class ApkInspector {
    open fun read(context: Context, file: File): InstalledApkInfo? =
        ApkVerifier.read(context, file)
}
