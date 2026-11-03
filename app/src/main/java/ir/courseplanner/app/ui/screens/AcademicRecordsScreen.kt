package ir.courseplanner.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.AcademicTermWithCourses
import ir.courseplanner.app.data.model.ArchivedCourseWithSessions
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.TermCourseStatus
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate

/**
 * Independent academic-record page: every finished term with its courses,
 * final statuses, grades and passed credits. Purely read-only — history is
 * never edited from here, and current-term operations cannot touch it.
 */
@Composable
fun AcademicRecordsScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val terms by viewModel.academicTerms.collectAsStateWithLifecycle()
    val summary by viewModel.academicRecordsSummary.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TermPageHeader(
                title = "سوابق تحصیلی",
                subtitle = "ترم‌های پایان‌یافته؛ مستقل از ترم فعال.",
                onBack = { viewModel.navigateTo(AppDestination.HOME) },
                backDescription = "بازگشت به خانه"
            )
        }
        item {
            RecordsSummaryCard(
                termCount = summary.termCount,
                courseCount = summary.courseCount,
                passedCredits = summary.passedCredits
            )
        }

        if (terms.isEmpty()) {
            item { EmptyRecords() }
        } else {
            itemsIndexed(terms, key = { _, term -> term.term.id }) { index, term ->
                AcademicTermCard(term = term, initiallyExpanded = index == 0)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun RecordsSummaryCard(
    termCount: Int,
    courseCount: Int,
    passedCredits: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(TermChinDesign.IconMd)
            )
            RecordStat(label = "ترم", value = termCount)
            RecordStat(label = "درس", value = courseCount)
            RecordStat(label = "واحد گذرانده", value = passedCredits)
        }
    }
}

@Composable
private fun RecordStat(label: String, value: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = JalaliDate.toFaDigits(value),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun AcademicTermCard(
    term: AcademicTermWithCourses,
    initiallyExpanded: Boolean
) {
    var expanded by remember(term.term.id) { mutableStateOf(initiallyExpanded) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("academic_term_${term.term.id}"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(TermChinDesign.CourseAvatarSize)
                        .clip(TermChinDesign.ControlShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(TermChinDesign.IconMd)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = term.term.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(formatEpochDay(term.term.startEpochDay) ?: "شروع نامشخص")
                            append(" تا ")
                            append(formatEpochDay(term.term.endEpochDay) ?: "—")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = buildString {
                            append(JalaliDate.toFaDigits(term.courseCount))
                            append(" درس  •  ")
                            append(JalaliDate.toFaDigits(term.passedCount))
                            append(" پاس‌شده  •  ")
                            append(JalaliDate.toFaDigits(term.passedCredits))
                            append(" واحد گذرانده")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "بستن جزئیات ترم" else "نمایش جزئیات ترم",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArchivedCourseRowHeader()
                    term.courses.forEach { archived ->
                        ArchivedCourseRow(archived)
                    }
                    if (term.courses.isEmpty()) {
                        Text(
                            "درسی برای این ترم ثبت نشده است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedCourseRowHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "درس",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "وضعیت / نمره",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArchivedCourseRow(archived: ArchivedCourseWithSessions) {
    val course = archived.course
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("archived_course_${course.id}"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(JalaliDate.toFaDigits(course.code))
                        append(" • ")
                        append(JalaliDate.toFaDigits(course.credits))
                        append(" واحد")
                        if (course.sectionCode.isNotBlank()) {
                            append(" • گروه ")
                            append(JalaliDate.toFaDigits(course.sectionCode))
                        }
                        if (course.instructor.isNotBlank()) {
                            append(" • ")
                            append(course.instructor)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(course.status)
                course.grade?.takeIf { it.isNotBlank() }?.let { grade ->
                    Text(
                        text = "نمره: ${JalaliDate.toFaDigits(grade)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (archived.sessions.isNotEmpty()) {
            Text(
                text = archived.sessions.sortedWith(
                    compareBy({ it.dayOfWeek }, { it.startTime })
                ).joinToString("  •  ") { session ->
                    buildString {
                        append(ClassSession.getDayName(session.dayOfWeek))
                        append(" ")
                        append(JalaliDate.toFaDigits(session.startTime))
                        append("-")
                        append(JalaliDate.toFaDigits(session.endTime))
                        if (session.location.isNotBlank()) {
                            append(" (")
                            append(session.location)
                            append(")")
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val attendance = course.attendanceTotal
        if (attendance > 0) {
            Text(
                text = buildString {
                    append("حضور: ")
                    append(JalaliDate.toFaDigits(course.attendancePresent))
                    append("  •  غیبت: ")
                    append(JalaliDate.toFaDigits(course.attendanceAbsent))
                    append("  •  تأخیر: ")
                    append(JalaliDate.toFaDigits(course.attendanceLate))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyRecords() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                "هنوز ترمی آرشیو نشده است.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "پس از «ثبت پایان ترم»، سوابق هر ترم با وضعیت، نمره و واحدهای گذرانده اینجا نمایش داده می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatEpochDay(epochDay: Long?): String? {
    if (epochDay == null) return null
    return JalaliDate.fromEpochDay(epochDay)?.let { JalaliDate.toFaDigits(it.toString()) }
}
