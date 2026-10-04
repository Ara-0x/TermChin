package ir.courseplanner.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.courseplanner.app.update.AvailableUpdate

/**
 * One-shot "a new version exists" prompt, shown at most once per released version.
 *
 * Copy is deliberately short and non-technical: it names the new version, says
 * the download happens in the browser (so nobody expects an in-app installer),
 * and warns about the two things that actually surprise users — leaving the app,
 * and the fact that their data stays untouched by an upgrade.
 *
 * "بعداً" (Later) dismisses silently; there is no nag and no manual check button.
 */
@Composable
fun UpdateAvailableDialog(
    update: AvailableUpdate,
    currentVersionName: String,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onLater,
        modifier = modifier.testTag("update_dialog"),
        title = {
            Text(
                text = "نسخهٔ جدید موجود است",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "ترم‌چین ${update.version.versionName} منتشر شده است و شما نسخهٔ $currentVersionName را دارید.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "با زدن «دانلود»، صفحهٔ رسمی انتشار در مرورگر باز می‌شود و نصب را خودِ اندروید تأیید می‌کند. داده‌های شما (دروس، برنامهٔ هفتگی و جزوات) دست‌نخورده باقی می‌ماند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDownload,
                modifier = Modifier.testTag("update_dialog_download")
            ) {
                Text("دانلود", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onLater,
                modifier = Modifier.testTag("update_dialog_later")
            ) {
                Text("بعداً")
            }
        }
    )
}