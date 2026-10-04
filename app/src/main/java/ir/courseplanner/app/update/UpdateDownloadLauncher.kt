package ir.courseplanner.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Builds the intent that hands the APK download to the user's browser.
 *
 * Deliberately just `ACTION_VIEW` + the HTTPS URL: TermChin never downloads,
 * stores, verifies or installs the file itself. The browser does the transfer and
 * Android's own package installer shows the system install screen, so the app
 * needs neither `REQUEST_INSTALL_PACKAGES` nor a `FileProvider`, and cannot
 * silently push an APK onto the device.
 *
 * Returns null when no browser can handle the URL, so the caller can degrade
 * gracefully instead of crashing with `ActivityNotFoundException`.
 */
fun downloadInBrowser(context: Context, url: String): Boolean {
    // NEW_TASK is required when the caller's context is the Application rather
    // than an Activity (which is the case from a ViewModel-driven prompt).
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}