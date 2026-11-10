package ir.courseplanner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.ui.theme.tinted
import ir.courseplanner.app.update.AvailableUpdate
import ir.courseplanner.app.update.UpdateDownloadState
import ir.courseplanner.app.update.UpdateFailure
import ir.courseplanner.app.util.JalaliDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One-shot "a new version exists" prompt, shown at most once per released version.
 *
 * The layout follows the update prompt users already know from other Android
 * apps: a branded header, a current → new version comparison, and the release
 * notes in a compact scroll well, so the user can decide before anything is
 * downloaded.
 *
 * The download happens inside the app ([UpdateDownloadState]) with live
 * progress, a checksum check and a package check; the finished file is then
 * handed to Android's own installer, which asks for the final confirmation.
 * "بعداً" dismisses silently and cancels any running transfer — there is no nag
 * and no manual check button.
 */
@Composable
fun UpdateAvailableDialog(
    update: AvailableUpdate,
    currentVersionName: String,
    downloadState: UpdateDownloadState,
    installPermissionGranted: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onGrantInstallPermission: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = {
            // Back while a transfer is running means "stop", not "hide it":
            // a hidden download would keep a spinnerless job alive.
            if (downloadState is UpdateDownloadState.Downloading) onCancelDownload() else onLater()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        UpdateDialogContent(
            update = update,
            currentVersionName = currentVersionName,
            downloadState = downloadState,
            installPermissionGranted = installPermissionGranted,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onInstall = onInstall,
            onGrantInstallPermission = onGrantInstallPermission,
            onLater = onLater,
            modifier = modifier
        )
    }
}

/**
 * The dialog body without the window wrapper, so it can be previewed and
 * screenshot-tested in isolation.
 */
@Composable
internal fun UpdateDialogContent(
    update: AvailableUpdate,
    currentVersionName: String,
    downloadState: UpdateDownloadState,
    installPermissionGranted: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onGrantInstallPermission: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val noteLines = remember(update.changelog) {
        formatReleaseNotes(update.changelog)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
    // A dialog must never clip: the card scrolls instead of pushing its own
    // buttons off a short screen or a large font scale.
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .padding(horizontal = 20.dp)
            .heightIn(max = maxCardHeight)
            .testTag("update_dialog"),
        // The palette's own surface, not the library's default container tint:
        // a dialog is a card, and cards here are surface + hairline border.
        shape = TermChinDesign.HeroShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(TermChinDesign.BrandMarkSize)
                        .clip(TermChinDesign.BrandShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "نسخهٔ جدید آماده است",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ترم‌چین ${update.version.versionName} منتشر شد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = TermChinDesign.RowShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    VersionSide(
                        label = "نسخهٔ فعلی",
                        version = currentVersionName,
                        highlighted = false,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    VersionSide(
                        label = "نسخهٔ جدید",
                        version = update.version.versionName,
                        highlighted = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "تازه‌های این نسخه",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = TermChinDesign.RowShape,
                color = MaterialTheme.colorScheme.tinted(
                    MaterialTheme.colorScheme.surfaceVariant,
                    0.55f
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    // The notes grow with their content and the CARD scrolls when
                    // it must: a fixed-height inner well used to cut the last
                    // bullet mid-line, which reads as a rendering bug.
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (noteLines.isEmpty()) {
                        Text(
                            text = if (update.releasePageUrl.isNotEmpty()) {
                                "جزئیات کامل این نسخه در صفحهٔ انتشار گیت‌هاب آمده است."
                            } else {
                                "این نسخه شامل بهبودهای عملکرد و رفع خطاهای گزارش‌شده است."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Start
                        )
                    } else {
                        noteLines.forEach { line ->
                            NoteRow(line)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (downloadState) {
                UpdateDownloadState.Idle -> IdleActions(
                    hasApkAsset = update.hasApkAsset,
                    onDownload = onDownload,
                    onLater = onLater
                )

                is UpdateDownloadState.Downloading -> DownloadingActions(
                    state = downloadState,
                    onCancel = onCancelDownload
                )

                is UpdateDownloadState.Ready -> ReadyActions(
                    installPermissionGranted = installPermissionGranted,
                    onInstall = onInstall,
                    onGrantInstallPermission = onGrantInstallPermission,
                    onLater = onLater
                )

                is UpdateDownloadState.Failed -> FailedActions(
                    failure = downloadState.failure,
                    onRetry = onDownload,
                    onLater = onLater
                )
            }
        }
    }
}

@Composable
private fun IdleActions(
    hasApkAsset: Boolean,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    Text(
        text = if (hasApkAsset) {
            "دانلود داخل برنامه انجام می‌شود و پس از پایان، نصب را خود اندروید تأیید می‌کند. داده‌های شما (دروس، برنامهٔ هفتگی و جزوات) دست‌نخورده می‌ماند."
        } else {
            "در این نسخه فایل APK منتشر نشده است؛ صفحهٔ رسمی انتشار در مرورگر باز می‌شود."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp
    )
    Spacer(modifier = Modifier.height(14.dp))
    Button(
        onClick = onDownload,
        shape = TermChinDesign.ControlShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(TermChinDesign.PrimaryButtonHeight)
            .testTag("update_dialog_download")
    ) {
        Icon(
            if (hasApkAsset) Icons.Default.Download else Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(TermChinDesign.IconSm)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (hasApkAsset) "دانلود و نصب" else "مشاهدهٔ صفحهٔ انتشار",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
    SecondaryTextButton(text = "بعداً", onClick = onLater)
}

@Composable
private fun DownloadingActions(
    state: UpdateDownloadState.Downloading,
    onCancel: () -> Unit
) {
    val fraction = state.fraction
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "در حال دانلود به‌روزرسانی",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (fraction != null) {
            Text(
                // Persian percent follows the number: «۴۴٪», not «٪۴۴».
                text = "${JalaliDate.toFaDigits((fraction * 100).roundToInt())}٪",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    UpdateProgressBar(fraction)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = progressCaption(state.bytes, state.total),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    SecondaryTextButton(text = "لغو دانلود", onClick = onCancel)
}

/**
 * The transfer bar: fills from the **right** in this RTL layout.
 *
 * Material's [LinearProgressIndicator] always draws left→right regardless of
 * layout direction, which read backwards next to Persian status labels. A usual
 * Box with `CenterStart` alignment resolves "start" to the right edge here, so
 * the fill grows toward the percent label. The indeterminate variant stays with
 * Material: it is a moving sweep, not a fill worth mirroring.
 */
@Composable
private fun UpdateProgressBar(fraction: Float?) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val shape = TermChinDesign.MicroShape
    if (fraction == null) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(shape),
            color = fill,
            trackColor = track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(track),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(fill)
        )
    }
}

@Composable
private fun ReadyActions(
    installPermissionGranted: Boolean,
    onInstall: () -> Unit,
    onGrantInstallPermission: () -> Unit,
    onLater: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(TermChinDesign.IconMd)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "دانلود کامل شد",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (installPermissionGranted) {
                    "برای نصب، دکمهٔ زیر را بزنید؛ اندروید تأیید نهایی را می‌پرسد."
                } else {
                    "برای نصب، اجازهٔ «نصب برنامه‌های ناشناس» را به ترم‌چین بدهید."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    if (installPermissionGranted) {
        Button(
            onClick = onInstall,
            shape = TermChinDesign.ControlShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(TermChinDesign.PrimaryButtonHeight)
                .testTag("update_dialog_install")
        ) {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(TermChinDesign.IconSm)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "نصب به‌روزرسانی",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Button(
            onClick = onGrantInstallPermission,
            shape = TermChinDesign.ControlShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(TermChinDesign.PrimaryButtonHeight)
                .testTag("update_dialog_install_permission")
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(TermChinDesign.IconSm)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "اجازهٔ نصب برنامه",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
    SecondaryTextButton(text = "بستن", onClick = onLater)
}

@Composable
private fun FailedActions(
    failure: UpdateFailure,
    onRetry: () -> Unit,
    onLater: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(TermChinDesign.IconMd)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "دانلود ناموفق بود",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = failureMessage(failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    Button(
        onClick = onRetry,
        shape = TermChinDesign.ControlShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(TermChinDesign.PrimaryButtonHeight)
            .testTag("update_dialog_retry")
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(TermChinDesign.IconSm)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "تلاش دوباره",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
    SecondaryTextButton(text = "بعداً", onClick = onLater)
}

@Composable
private fun SecondaryTextButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TermChinDesign.MinTouchHeight)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One release-note line: a small neutral dot plus the text, so wrapped lines
 * hang under the text instead of under the bullet.
 */
@Composable
private fun NoteRow(line: String) {
    val isBullet = line.startsWith("•")
    val text = if (isBullet) line.removePrefix("•").trimStart() else line
    Row(modifier = Modifier.fillMaxWidth()) {
        if (isBullet) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun VersionSide(
    label: String,
    version: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (highlighted) {
            Surface(
                shape = TermChinDesign.PillShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        } else {
            Text(
                text = version,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** "۱۲٫۴ از ۲۸٫۱ مگابایت" — or an honest line when the server sent no length. */
internal fun progressCaption(bytes: Long, total: Long?): String = if (total != null && total > 0L) {
    "${megabytes(bytes)} از ${megabytes(total)} مگابایت"
} else {
    "${megabytes(bytes)} مگابایت دریافت شد"
}

private fun megabytes(bytes: Long): String {
    val value = bytes / 1024.0 / 1024.0
    // One decimal up to 10 MB, whole numbers above: "۳٫۴" is useful, "۱۷٫۲" is noise.
    val pattern = if (value >= 10) "%.0f" else "%.1f"
    return JalaliDate.toFaDigits(String.format(Locale.US, pattern, value)).replace('.', '٫')
}

/** One actionable sentence per failure; never a raw exception message. */
internal fun failureMessage(failure: UpdateFailure): String = when (failure) {
    UpdateFailure.NETWORK ->
        "اتصال برقرار نشد یا دانلود قطع شد. اینترنت را بررسی کنید و دوباره تلاش کنید."
    UpdateFailure.CHECKSUM ->
        "فایل دریافتی با نسخهٔ رسمی مطابقت نداشت و برای امنیت شما حذف شد."
    UpdateFailure.NOT_AN_APK ->
        "فایل دریافتی یک بستهٔ معتبر اندروید نبود و نصب نشد."
    UpdateFailure.WRONG_PACKAGE ->
        "این بسته به ترم‌چین تعلق ندارد و نصب نمی‌شود."
    UpdateFailure.OLD_VERSION ->
        "این بسته از نسخهٔ نصب‌شده قدیمی‌تر است."
    UpdateFailure.STORAGE ->
        "حافظهٔ دستگاه برای ذخیرهٔ فایل کافی نبود. کمی فضا آزاد کنید."
    UpdateFailure.UNKNOWN ->
        "مشکلی پیش آمد. دوباره تلاش کنید."
}
