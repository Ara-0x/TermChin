package ir.courseplanner.app.update

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The "دانلود" button must hand the APK to a browser — nothing more.
 *
 * TermChin deliberately has no `REQUEST_INSTALL_PACKAGES` and no `FileProvider`:
 * both the download and the install belong to the browser and to Android's system
 * installer, so the app can never push an APK onto the device by itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateDownloadLauncherTest {

    private val url =
        "https://github.com/Ara-0x/TermChin/releases/download/v2.7.4/TermChin-v2.7.4.apk"

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `reports success and opens the download url in a browser`() {
        assertTrue(downloadInBrowser(app, url))

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.data?.toString())
    }

    @Test
    fun `never asks to install the apk itself`() {
        downloadInBrowser(app, url)
        val intent = shadowOf(app).nextStartedActivity

        // No install-package permission is declared anywhere in the manifest, so
        // the app must not create an installer intent either.
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(
            "Expected a plain browser hand-off, got ${intent.component}",
            intent.component == null
        )
    }
}