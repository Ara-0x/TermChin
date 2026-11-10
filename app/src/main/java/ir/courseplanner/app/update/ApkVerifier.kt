package ir.courseplanner.app.update

import android.content.Context
import java.io.File

/** The two fields of a downloaded archive that matter before installing it. */
data class InstalledApkInfo(val packageName: String, val versionCode: Long)

/**
 * Refuses an archive that is not this app's own, newer build.
 *
 * The installer would also reject a foreign or older APK, but only after the
 * user has already tapped through the system prompt. Checking first turns that
 * dead end into one clear sentence inside the dialog. Signature verification is
 * deliberately left to Android: the installer compares certificates and will
 * not install over a differently-signed copy of the same package.
 */
object ApkVerifier {

    /** Returns the reason the archive is unusable, or null when it is fine. */
    fun validate(
        info: InstalledApkInfo?,
        expectedPackage: String,
        currentVersionCode: Long
    ): UpdateFailure? = when {
        info == null -> UpdateFailure.NOT_AN_APK
        info.packageName != expectedPackage -> UpdateFailure.WRONG_PACKAGE
        info.versionCode <= currentVersionCode -> UpdateFailure.OLD_VERSION
        else -> null
    }

    /** Reads package metadata through the platform's own archive parser. */
    @Suppress("DEPRECATION")
    fun read(context: Context, apk: File): InstalledApkInfo? = runCatching {
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return null
        InstalledApkInfo(
            packageName = archive.packageName ?: "",
            versionCode = archive.versionCode.toLong()
        )
    }.getOrNull()
}
