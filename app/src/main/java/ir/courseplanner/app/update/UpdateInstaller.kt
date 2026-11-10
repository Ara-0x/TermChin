package ir.courseplanner.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a verified APK to Android's own package installer, and nothing more.
 *
 * The app never installs silently: it opens the system installer UI, which shows
 * the app name/permissions and asks for confirmation. On Android 8+ the user
 * must also allow this app to install unknown apps once; [canInstall] reports
 * that, and [openInstallPermissionSettings] takes the user straight to the
 * per-app toggle instead of a generic settings maze.
 */
object UpdateInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** True when the system will let this app hand an APK to the installer. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Opens the "install unknown apps" toggle for this app. */
    fun openInstallPermissionSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    /**
     * Opens the system installer for [apk]. Returns false when no installer
     * activity could be launched, so the caller can show a message instead of
     * crashing.
     */
    fun install(context: Context, apk: File): Boolean = runCatching {
        // The provider authority is derived from the runtime package name, so a
        // renamed/suffixed build still resolves its own FileProvider.
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        context.startActivity(installIntent(uri))
    }.isSuccess

    internal fun installIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
