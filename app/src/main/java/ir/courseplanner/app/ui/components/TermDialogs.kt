package ir.courseplanner.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.courseplanner.app.util.JalaliDate

/**
 * Shown BEFORE the end-term form. It explains exactly what will happen so the
 * student can still cancel with no side effects: nothing has been written yet.
 */
@Composable
fun EndTermConfirmDialog(
    courseCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("end_term_confirm_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("ثبت پایان ترم", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (courseCount > 0) {
                        "با ادامه، ترم فعلی با ${JalaliDate.toFaDigits(courseCount)} درس پایان‌یافته و آرشیو می‌شود."
                    } else {
                        "در ترم فعلی درسی انتخاب نشده است؛ برای ثبت پایان ترم ابتدا از «دروس» درس‌هایتان را اضافه کنید."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("• وضعیت نهایی همهٔ درس‌ها را مشخص می‌کنید.", style = MaterialTheme.typography.bodySmall)
                Text("• ترم فعلی از حالت فعال خارج می‌شود.", style = MaterialTheme.typography.bodySmall)
                Text("• امکان شروع ترم جدید فراهم می‌شود.", style = MaterialTheme.typography.bodySmall)
                Text(
                    "• اطلاعات و برنامهٔ ترم قبلی حذف نمی‌شود و در «سوابق تحصیلی» قابل مشاهده است.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = courseCount > 0
            ) { Text("ادامه") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/**
 * Final name confirmation, shown after «تأیید و آرشیو ترم» and before any
 * write. The suggested value is the current term name, but the student can
 * label the archived term exactly as they want it to appear in the record.
 */
@Composable
fun TermArchiveNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (termName: String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        modifier = Modifier.testTag("term_archive_name_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("نام ترم برای آرشیو", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "این نام در «سوابق تحصیلی» ثبت می‌شود. می‌توانید آن را تغییر دهید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("نام ترم *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("archive_term_name")
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) error = "نام ترم الزامی است."
                    else onConfirm(name.trim())
                }
            ) { Text("آرشیو ترم") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/**
 * Creates the next term. Choosing courses is optional here; the dialog only
 * moves the "current term" label and (optionally) its start date forward.
 */
@Composable
fun StartNewTermDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, startEpochDay: Long?) -> Unit
) {
    val todayLabel = remember {
        JalaliDate.fromEpochDay(JalaliDate.todayEpochDay())?.toString().orEmpty()
    }
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(todayLabel) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        modifier = Modifier.testTag("start_new_term_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("شروع ترم جدید", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "نام ترم جدید را وارد کنید. انتخاب درس‌ها اجباری نیست و هر زمان از «دروس» می‌توانید آن‌ها را اضافه کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("نام ترم جدید *") },
                    placeholder = { Text("مثلاً نیم‌سال دوم ۱۴۰۴-۱۴۰۵") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_term_name")
                )
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it; error = null },
                    label = { Text("تاریخ شروع ترم (اختیاری)") },
                    placeholder = { Text("۱۴۰۴/۰۷/۰۵") },
                    supportingText = { Text("خالی بماند، هفتهٔ آموزشی نامشخص می‌ماند.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_term_start_date")
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        name.isBlank() -> error = "نام ترم جدید الزامی است."
                        else -> {
                            val parsed = if (startDate.isBlank()) null else JalaliDate.parse(startDate)
                            if (startDate.isNotBlank() && parsed == null) {
                                error = "تاریخ شروع معتبر نیست؛ نمونه: ۱۴۰۴/۰۷/۰۵"
                            } else {
                                val epochDay = parsed?.let {
                                    JalaliDate.toEpochDay(it.year, it.month, it.day)
                                }
                                onConfirm(name.trim(), epochDay)
                            }
                        }
                    }
                }
            ) { Text("شروع ترم") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
