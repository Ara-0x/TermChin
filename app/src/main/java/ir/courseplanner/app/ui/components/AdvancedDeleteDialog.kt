package ir.courseplanner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.courseplanner.app.data.model.DataCounts
import ir.courseplanner.app.data.model.DataDeletionSelection
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate

/**
 * Advanced deletion dialog: every domain is an independent switch, and the
 * presets only pre-fill those switches. Nothing is ever deleted by opening the
 * dialog; the destructive button stays disabled until something is selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedDeleteDialog(
    counts: DataCounts,
    hasProfile: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (DataDeletionSelection) -> Unit
) {
    var selection by remember { mutableStateOf(DataDeletionSelection()) }

    // An empty domain is not deletable; dropping its flag keeps presets and the
    // switch list in sync with what actually exists.
    fun clearEmptyDomains(value: DataDeletionSelection): DataDeletionSelection = value.copy(
        schedule = value.schedule && counts.enrolledSections > 0,
        myCourses = value.myCourses && counts.myCourses > 0,
        catalog = value.catalog && counts.catalogCourses > 0,
        documents = value.documents && counts.documents > 0,
        passedCourses = value.passedCourses && counts.passedCourses > 0,
        attendance = value.attendance && counts.attendanceRecords > 0
    )

    fun toggle(update: (DataDeletionSelection) -> DataDeletionSelection) {
        selection = update(selection)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پاک‌سازی پیشرفته", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "دقیقاً همان داده‌هایی حذف می‌شوند که انتخاب می‌کنید. سایر بخش‌ها دست‌نخورده می‌مانند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { selection = clearEmptyDomains(DataDeletionSelection.SCHEDULE_ONLY) },
                        label = { Text("فقط برنامه هفتگی", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = { selection = clearEmptyDomains(DataDeletionSelection.MY_DATA) },
                        label = { Text("داده‌های من", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = { selection = clearEmptyDomains(DataDeletionSelection.ALL) },
                        label = { Text("همه چیز", fontSize = 11.sp) }
                    )
                }

                DeleteSwitchRow(
                    title = "برنامه هفتگی",
                    subtitle = "${JalaliDate.toFaDigits(counts.enrolledSections)} گروه انتخاب‌شده",
                    checked = selection.schedule,
                    enabled = counts.enrolledSections > 0,
                    onCheckedChange = { checked -> toggle { it.copy(schedule = checked) } }
                )
                DeleteSwitchRow(
                    title = "دروس من",
                    subtitle = "${JalaliDate.toFaDigits(counts.myCourses)} درس (جزوات و سوابق حضور وابسته هم حذف می‌شوند)",
                    checked = selection.myCourses,
                    enabled = counts.myCourses > 0,
                    onCheckedChange = { checked -> toggle { it.copy(myCourses = checked) } }
                )
                DeleteSwitchRow(
                    title = "کاتالوگ پورتال",
                    subtitle = "${JalaliDate.toFaDigits(counts.catalogCourses)} درس اضافه‌نشده",
                    checked = selection.catalog,
                    enabled = counts.catalogCourses > 0,
                    onCheckedChange = { checked -> toggle { it.copy(catalog = checked) } }
                )
                DeleteSwitchRow(
                    title = "جزوات و اسناد",
                    subtitle = "${JalaliDate.toFaDigits(counts.documents)} مورد",
                    checked = selection.documents,
                    enabled = counts.documents > 0,
                    onCheckedChange = { checked -> toggle { it.copy(documents = checked) } }
                )
                DeleteSwitchRow(
                    title = "دروس پاس‌شده",
                    subtitle = "${JalaliDate.toFaDigits(counts.passedCourses)} درس",
                    checked = selection.passedCourses,
                    enabled = counts.passedCourses > 0,
                    onCheckedChange = { checked -> toggle { it.copy(passedCourses = checked) } }
                )
                DeleteSwitchRow(
                    title = "سوابق حضور و غیاب",
                    subtitle = "${JalaliDate.toFaDigits(counts.attendanceRecords)} ثبت",
                    checked = selection.attendance,
                    enabled = counts.attendanceRecords > 0,
                    onCheckedChange = { checked -> toggle { it.copy(attendance = checked) } }
                )
                DeleteSwitchRow(
                    title = "تنظیمات و پروفایل",
                    subtitle = if (hasProfile) "نام، ترم، تم، حد غیبت و سایر تنظیمات" else "بازگشت تنظیمات به مقادیر پیش‌فرض",
                    checked = selection.preferences,
                    enabled = true,
                    onCheckedChange = { checked -> toggle { it.copy(preferences = checked) } }
                )

                if (!selection.isEmpty) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TermChinDesign.RowShape)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.width(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "این موارد حذف می‌شوند:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        selection.summaryFa().forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "این عمل قابل بازگشت نیست.",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selection) },
                enabled = !selection.isEmpty,
                modifier = Modifier.testTag("confirm_advanced_delete"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = TermChinDesign.RowShape
            ) {
                Text(
                    text = if (selection.isEmpty) "چیزی انتخاب نشده" else "حذف انتخاب‌شده‌ها",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun DeleteSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TermChinDesign.RowShape)
            .background(
                when {
                    checked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                }
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f)
            )
            Text(
                text = if (enabled) subtitle else "موردی برای حذف نیست",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            // The whole row is the touch target; the switch only renders state.
            onCheckedChange = null
        )
    }
}
