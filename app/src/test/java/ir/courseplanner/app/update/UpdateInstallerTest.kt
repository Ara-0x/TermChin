package ir.courseplanner.app.update

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hand-off to Android's package installer. The intent must be a one-shot,
 * read-only grant on a `content://` URI — never a file path or a silent install.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateInstallerTest {

    private val apkUri = Uri.parse(
        "content://ir.courseplanner.app.fileprovider/updates/TermChin-2.22.0.apk"
    )

    @Test
    fun `the install intent opens the package installer with read-only access`() {
        val intent = UpdateInstaller.installIntent(apkUri)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals(apkUri, intent.data)
        assertNotEquals("a raw file path must never be exposed", "file", apkUri.scheme)
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK,
            intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK
        )
        // Read-only means exactly read: no write grant is ever attached.
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}
