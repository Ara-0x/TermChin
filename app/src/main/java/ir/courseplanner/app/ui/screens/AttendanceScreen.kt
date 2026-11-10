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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
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
import ir.courseplanner.app.data.model.AttendanceRecord
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.components.AttendanceSheet
import ir.courseplanner.app.ui.components.formatEpochDayFa
import ir.courseplanner.app.ui.components.statusColor
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate

/** One course row on the attendance page; only absences are counted. */
private data class CourseAbsenceUi(
    val courseId: Long,
    val name: String,
    val code: String,
    /** Saved absences of the course, newest first. */
    val absences: List<AttendanceRecord>,
    val absenceCount: Int
)

/**
 * Editable absence page, opened from the Home attendance card.
 *
 * Deliberately absence-first: the only number aggregated here is the number of
 * recorded absences per course, next to the configured limit. There is no
 * "how many sessions should have happened" calculation — the student edits the
 * records that actually exist, and presence is never shown as a score.
 */
@Composable
fun AttendanceScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val enrolledSections by viewModel.enrolledSections.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()

    val sectionById = remember(enrolledSections) {
        enrolledSections.associateBy { it.section.id }
    }
    val courses = remember(enrolledSections, attendanceRecords) {
        buildCourseAbsenceUi(enrolledSections, attendanceRecords)
    }
    val totalAbsences = courses.sumOf { it.absenceCount }
    val coursesAtLimit = if (preferences.absenceLimit > 0) {
        courses.count { it.absenceCount > 0 && it.absenceCount >= preferences.absenceLimit }
    } else {
        courses.count { it.absenceCount > 0 }
    }

    var expandedCourseId by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<AttendanceRecord?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("attendance_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TermPageHeader(
                title = "حضور و غیاب",
                subtitle = "فقط غیبت‌ها، بدون شمارش حضور؛ هر غیبت را می‌توانی ویرایش کنی.",
                onBack = { viewModel.navigateTo(AppDestination.HOME) },
                backDescription = "بازگشت به خانه"
            )
        }

        item {
            AbsenceOverviewCard(
                totalAbsences = totalAbsences,
                absenceLimit = preferences.absenceLimit,
                coursesAtLimit = coursesAtLimit
            )
        }

        if (courses.isEmpty()) {
            item {
                EmptyAttendanceCard(
                    onOpenCourses = { viewModel.navigateTo(AppDestination.COURSES) }
                )
            }
        } else {
            items(courses, key = { it.courseId }) { course ->
                val expanded = expandedCourseId == course.courseId
                CourseAbsenceCard(
                    course = course,
                    absenceLimit = preferences.absenceLimit,
                    expanded = expanded,
                    onToggle = {
                        expandedCourseId = if (expanded) null else course.courseId
                    },
                    onEdit = { record -> editTarget = record }
                )
            }
        }

        item {
            Text(
                text = "برای ثبت یا تغییر وضعیت جلسه‌هایی که اینجا نیستند (حاضر/تأخیر)، " +
                    "روی همان جلسه در جدول هفتگی یا کارت «حضور امروز» بزن.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    editTarget?.let { record ->
        val section = sectionById[record.sectionId]
        if (section != null) {
            val session = section.sessions.firstOrNull { it.startTime == record.startTime }
                ?: ClassSession(
                    sectionId = record.sectionId,
                    dayOfWeek = JalaliDate.appDayIndexOfEpochDay(record.dateEpochDay),
                    startTime = record.startTime,
                    endTime = record.endTime
                )
            AttendanceSheet(
                section = section,
                session = session,
                occurrenceEpochDay = record.dateEpochDay,
                existing = record,
                sectionHistory = attendanceRecords
                    .filter { it.sectionId == section.section.id }
                    .sortedByDescending { it.dateEpochDay },
                canRecord = true,
                blockedReason = null,
                onDismiss = { editTarget = null },
                onSave = { epochDay, status, note ->
                    viewModel.markAttendance(
                        sectionId = section.section.id,
                        dateEpochDay = epochDay,
                        startTime = record.startTime,
                        endTime = record.endTime,
                        status = status,
                        note = note
                    )
                    editTarget = null
                },
                onClear = {
                    viewModel.clearAttendance(section.section.id, record.dateEpochDay, record.startTime)
                    editTarget = null
                },
                onDeleteHistory = { id ->
                    viewModel.deleteAttendance(id)
                    if (id == record.id) editTarget = null
                }
            )
        }
    }
}

private fun buildCourseAbsenceUi(
    sections: List<SectionWithDetails>,
    records: List<AttendanceRecord>
): List<CourseAbsenceUi> {
    return sections.groupBy { it.course.id }.map { (courseId, courseSections) ->
        val ids = courseSections.map { it.section.id }.toSet()
        val absences = records
            .filter { it.sectionId in ids && it.status == AttendanceStatus.ABSENT }
            .sortedWith(compareByDescending<AttendanceRecord> { it.dateEpochDay }.thenByDescending { it.startTime })
        CourseAbsenceUi(
            courseId = courseId,
            name = courseSections.first().courseName,
            code = courseSections.first().courseCode,
            absences = absences,
            absenceCount = absences.size
        )
    }.sortedBy { it.name }
}

@Composable
private fun AbsenceOverviewCard(
    totalAbsences: Int,
    absenceLimit: Int,
    coursesAtLimit: Int
) {
    // Neutral when nothing is recorded, error-tinted once absences exist: the
    // card only raises its voice when there is something to look at.
    val hasAbsences = totalAbsences > 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_overview_card"),
        shape = TermChinDesign.CardShape,
        color = if (hasAbsences) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (hasAbsences) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = if (hasAbsences) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TermChinDesign.IconMd)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "غیبت‌های ثبت‌شده",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = JalaliDate.toFaDigits(totalAbsences),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (hasAbsences) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = buildString {
                    append("حد مجاز غیبت هر درس: ")
                    append(JalaliDate.toFaDigits(absenceLimit))
                    if (coursesAtLimit > 0) {
                        append(" • ")
                        append(JalaliDate.toFaDigits(coursesAtLimit))
                        append(" درس به حد رسیده")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CourseAbsenceCard(
    course: CourseAbsenceUi,
    absenceLimit: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (AttendanceRecord) -> Unit
) {
    val atLimit = course.absenceCount > 0 && course.absenceCount >= absenceLimit
    // Any recorded absence earns the error accent; reaching the limit also
    // thickens the card border, so the escalation is visible at a glance.
    val accent = if (course.absenceCount > 0) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_course_${course.courseId}"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (atLimit) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TermChinDesign.MicroShape)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = JalaliDate.toFaDigits(course.absenceCount),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = accent
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("غیبت")
                            if (course.code.isNotBlank()) {
                                append(" • کد ")
                                append(JalaliDate.toFaDigits(course.code))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                if (atLimit) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = "رسیدن به حد غیبت",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp)
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "بستن جلسات" else "نمایش جلسات",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (course.absences.isEmpty()) {
                        Text(
                            text = "غیبتی برای این درس ثبت نشده است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        course.absences.forEach { record ->
                            AttendanceRecordRow(record = record, onClick = { onEdit(record) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceRecordRow(
    record: AttendanceRecord,
    onClick: () -> Unit
) {
    val color = statusColor(record.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TermChinDesign.RowShape)
            .clickable(onClick = onClick)
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatEpochDayFa(record.dateEpochDay)} • " +
                    "${JalaliDate.toFaDigits(record.startTime)} تا ${JalaliDate.toFaDigits(record.endTime)}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
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
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(
            Icons.Default.ChevronLeft,
            contentDescription = "ویرایش این ثبت",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun EmptyAttendanceCard(onOpenCourses: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "هنوز درسی در «دروس من» نداری.",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "اول درس‌های ترم را اضافه کن؛ بعد از ثبت حضور، غیبت‌ها همین‌جا برای ویرایش می‌آیند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenCourses,
                shape = TermChinDesign.RowShape
            ) {
                Text("رفتن به دروس", fontWeight = FontWeight.Bold)
            }
        }
    }
}
