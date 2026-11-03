package ir.courseplanner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.courseplanner.app.data.model.AttendanceRecord
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate

/**
 * Bottom sheet for one class occurrence.
 *
 * [canRecord] is computed by the caller from the academic week/parity and the
 * current date: attendance may only be written for an occurrence that has
 * already happened in the current week. History stays visible and editable
 * even when today's occurrence is not markable.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AttendanceSheet(
    section: SectionWithDetails,
    session: ClassSession,
    occurrenceEpochDay: Long,
    existing: AttendanceRecord?,
    sectionHistory: List<AttendanceRecord>,
    canRecord: Boolean,
    blockedReason: String?,
    onDismiss: () -> Unit,
    onSave: (epochDay: Long, status: AttendanceStatus, note: String) -> Unit,
    onClear: () -> Unit,
    onDeleteHistory: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var status by remember(existing?.id, occurrenceEpochDay) {
        mutableStateOf(existing?.status ?: AttendanceStatus.PRESENT)
    }
    var note by remember(existing?.id, occurrenceEpochDay) { mutableStateOf(existing?.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = TermChinDesign.HeroShape,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (canRecord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.courseName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${ClassSession.getDayName(session.dayOfWeek)} • گروه ${JalaliDate.toFaDigits(section.sectionCode)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = TermChinDesign.RowShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoLine(
                        icon = Icons.Default.Schedule,
                        text = "${JalaliDate.toFaDigits(session.startTime)} تا ${JalaliDate.toFaDigits(session.endTime)}"
                    )
                    if (section.instructor.isNotBlank()) {
                        InfoLine(icon = Icons.Default.Person, text = section.instructor)
                    }
                    InfoLine(
                        icon = Icons.Default.EventBusy,
                        text = formatEpochDayFa(occurrenceEpochDay)
                    )
                }
            }

            if (!canRecord) {
                Text(
                    text = blockedReason ?: "ثبت حضور برای این جلسه در دسترس نیست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = if (existing == null) "وضعیت این جلسه" else "ویرایش وضعیت ثبت‌شده",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttendanceStatus.values().forEach { option ->
                    val selected = status == option
                    FilterChip(
                        selected = selected,
                        enabled = canRecord,
                        onClick = { status = option },
                        label = { Text(option.titleFa, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = statusColor(option).copy(alpha = 0.18f),
                            selectedLabelColor = statusColor(option),
                            selectedLeadingIconColor = statusColor(option)
                        )
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("یادداشت (اختیاری)") },
                enabled = canRecord,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = TermChinDesign.CardShape
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSave(occurrenceEpochDay, status, note) },
                    enabled = canRecord,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("attendance_save_button"),
                    shape = TermChinDesign.RowShape
                ) {
                    Text(if (existing == null) "ثبت حضور" else "به‌روزرسانی", fontWeight = FontWeight.Bold)
                }
                if (existing != null) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.height(46.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("حذف ثبت", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (sectionHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "تاریخچهٔ این گروه",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                sectionHistory.take(20).forEach { record ->
                    HistoryRow(record = record, onDelete = { onDeleteHistory(record.id) })
                }
            }
        }
    }
}

@Composable
private fun InfoLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HistoryRow(record: AttendanceRecord, onDelete: () -> Unit) {
    Surface(
        shape = TermChinDesign.RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(formatEpochDayFa(record.dateEpochDay))
                        append(" • ")
                        append(JalaliDate.toFaDigits(record.startTime))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (record.note.isNotBlank()) {
                    Text(
                        text = record.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = record.status.titleFa,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = statusColor(record.status)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "حذف سابقهٔ حضور",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TermChinDesign.IconSm)
                )
            }
        }
    }
}

@Composable
fun statusColor(status: AttendanceStatus): androidx.compose.ui.graphics.Color = when (status) {
    AttendanceStatus.PRESENT -> MaterialTheme.colorScheme.primary
    AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error
    AttendanceStatus.LATE -> MaterialTheme.colorScheme.tertiary
    AttendanceStatus.EXCUSED -> MaterialTheme.colorScheme.secondary
}

fun formatEpochDayFa(epochDay: Long): String {
    val ymd = JalaliDate.fromEpochDay(epochDay) ?: return JalaliDate.toFaDigits(epochDay.toString())
    return buildString {
        append(ClassSession.getDayName(JalaliDate.appDayIndexOfEpochDay(epochDay)))
        append(" ")
        append(JalaliDate.toFaDigits(ymd.day))
        append(" ")
        append(JalaliDate.monthNameFa(ymd.month))
    }
}
