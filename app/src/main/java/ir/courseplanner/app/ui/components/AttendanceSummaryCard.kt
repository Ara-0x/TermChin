package ir.courseplanner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.courseplanner.app.data.model.AttendanceStats
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate

/**
 * Compact per-course attendance summary. A row turns red as soon as the
 * student reaches the configured absence limit, so the warning exists before
 * the next (possibly disastrous) absence.
 */
@Composable
fun AttendanceSummaryCard(
    enrolledSections: List<SectionWithDetails>,
    statsByCourse: Map<Long, AttendanceStats>,
    absenceLimit: Int,
    onOpenSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val courses = enrolledSections
        .distinctBy { it.course.id }
        .sortedBy { it.course.name }
    if (courses.isEmpty()) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("attendance_summary_card"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TermChinDesign.MicroShape)
                    .then(
                        if (onOpenSchedule != null) {
                            Modifier.clickable(onClick = onOpenSchedule)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Icon(
                    Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(TermChinDesign.IconMd)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "حضور و غیاب",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "حد غیبت: ${JalaliDate.toFaDigits(absenceLimit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onOpenSchedule != null) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "باز کردن برنامه",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(18.dp)
                    )
                }
            }

            courses.forEach { section ->
                val stats = statsByCourse[section.course.id] ?: AttendanceStats()
                CourseAttendanceRow(
                    name = section.courseName,
                    stats = stats,
                    absenceLimit = absenceLimit
                )
            }

            Text(
                text = "برای ثبت، روی جلسهٔ کلاس در خانه یا جدول هفتگی بزنید.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TermChinDesign.MicroShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun CourseAttendanceRow(
    name: String,
    stats: AttendanceStats,
    absenceLimit: Int
) {
    // A zero limit should only warn once an absence actually exists; comparing
    // 0 >= 0 would otherwise flag every course from the first day.
    val atLimit = stats.absenceCount > 0 && stats.absenceCount >= absenceLimit
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        StatPill("حاضر", stats.present, MaterialTheme.colorScheme.primary)
        StatPill("غایب", stats.absent, if (atLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (stats.late > 0) StatPill("تأخیر", stats.late, MaterialTheme.colorScheme.tertiary)
        if (stats.excused > 0) StatPill("موجه", stats.excused, MaterialTheme.colorScheme.secondary)
        if (atLimit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = "رسیدن به حد غیبت",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = if (absenceLimit > 0) {
                        "${JalaliDate.toFaDigits(stats.absenceCount)} از ${JalaliDate.toFaDigits(absenceLimit)}"
                    } else {
                        "غیبت ثبت شده"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = color,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = JalaliDate.toFaDigits(value),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = color,
            maxLines = 1
        )
    }
}
