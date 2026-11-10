package ir.courseplanner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.update.AvailableUpdate
import ir.courseplanner.app.update.ReleaseVersion
import ir.courseplanner.app.update.UpdateDownloadState
import ir.courseplanner.app.update.UpdateFailure
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Design captures for all five update-prompt states (idle, downloading, ready,
 * permission needed, failed) in light and dark. These are visual verification
 * artifacts, not assertions: Roborazzi fails the build only if the image cannot
 * be written.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class UpdateDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val update = AvailableUpdate(
        version = ReleaseVersion.parse("2.22.0")!!,
        downloadUrl = "https://github.com/Ara-0x/TermChin/releases/download/v2.22.0/TermChin-v2.22.0.apk",
        releasePageUrl = "https://github.com/Ara-0x/TermChin/releases/tag/v2.22.0",
        sha256 = "ab".repeat(32),
        changelog = """
            ## 🎓 TermChin | ترم‌چین — نسخه 2.22.0

            > ## ⚠️ اطلاعیهٔ مهم
            > این متن نباید در دیالوگ دیده شود.

            ### ✨ جدید در این نسخه:
            - 🗓 **برنامه‌ساز سریع‌تر:** بررسی هوشمندانه‌تر ترکیب‌ها و نمایش شفاف امتیاز هر برنامه.
            - 📚 **دروس پاس‌شده:** ثبت نمره و افزودن خودکار به سوابق تحصیلی.
            - 🎨 **ظاهر یکدست:** کارت‌های جمع‌شونده، رنگ‌بندی تازه و حالت تاریک کامل.
            - 🛠 **زیرساخت:** به‌روزرسانی کتابخانه‌ها و رفع چند خطای گزارش‌شده.

            > بعد از نصب، در تنظیمات نسخه را ببینید.
        """.trimIndent()
    )

    @Test
    fun update_dialog_idle_light() {
        capture("design_update_dialog_light", UpdateDownloadState.Idle)
    }

    @Test
    fun update_dialog_idle_dark() {
        capture("design_update_dialog_dark", UpdateDownloadState.Idle, dark = true)
    }

    @Test
    fun update_dialog_downloading_light() {
        capture(
            "design_update_dialog_downloading",
            UpdateDownloadState.Downloading(
                bytes = (12.4 * 1024 * 1024).toLong(),
                total = (28.1 * 1024 * 1024).toLong()
            )
        )
    }

    @Test
    fun update_dialog_ready_light() {
        capture("design_update_dialog_ready", UpdateDownloadState.Ready(File("TermChin-2.22.0.apk")))
    }

    @Test
    fun update_dialog_permission_light() {
        capture(
            "design_update_dialog_permission",
            UpdateDownloadState.Ready(File("TermChin-2.22.0.apk")),
            installPermissionGranted = false
        )
    }

    @Test
    fun update_dialog_failed_light() {
        capture(
            "design_update_dialog_failed",
            UpdateDownloadState.Failed(UpdateFailure.CHECKSUM)
        )
    }

    private fun capture(
        name: String,
        state: UpdateDownloadState,
        dark: Boolean = false,
        installPermissionGranted: Boolean = true
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme(darkTheme = dark, colorTheme = AppColorTheme.INDIGO) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        UpdateDialogContent(
                            update = update,
                            currentVersionName = "2.21.0",
                            downloadState = state,
                            installPermissionGranted = installPermissionGranted,
                            onDownload = {},
                            onCancelDownload = {},
                            onInstall = {},
                            onGrantInstallPermission = {},
                            onLater = {}
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
