package ir.courseplanner.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.AttendanceStats
import ir.courseplanner.app.data.model.TermCourseStatus
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.EndTermCourseItem
import ir.courseplanner.app.ui.EndTermUiState
import ir.courseplanner.app.ui.components.TermArchiveNameDialog
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate

/**
 * End-of-term flow:
 * form (status + optional grade per course) → review/summary → atomic archive
 * → success with «شروع ترم جدید». Leaving before the final confirmation writes
 * nothing; the ViewModel keeps the draft across rotation.
 */
@Composable
fun EndTermScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.endTermState.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    var showNameDialog by remember { mutableStateOf(false) }

    // Inside the form, system back is a true cancel (no writes). On the review
    // step it returns to editing instead of discarding the whole draft.
    BackHandler(enabled = state.showSummary && state.archivedTermId == null) {
        if (showNameDialog) showNameDialog = false else viewModel.editEndTerm()
    }

    if (showNameDialog && state.archivedTermId == null) {
        TermArchiveNameDialog(
            initialName = preferences.semesterName,
            onDismiss = { showNameDialog = false },
            onConfirm = { termName ->
                showNameDialog = false
                viewModel.confirmEndTerm(termName)
            }
        )
    }

    when {
        state.archivedTermId != null -> EndTermSuccess(
            state = state,
            onStartNewTerm = viewModel::requestStartNewTerm,
            onOpenRecords = viewModel::openAcademicRecords,
            onHome = { viewModel.navigateTo(AppDestination.HOME) },
            modifier = modifier
        )
        state.showSummary -> EndTermReview(
            state = state,
            termName = preferences.semesterName,
            onEdit = viewModel::editEndTerm,
            onConfirm = { showNameDialog = true },
            modifier = modifier
        )
        else -> EndTermForm(
            state = state,
            termName = preferences.semesterName,
            onBack = { viewModel.navigateTo(AppDestination.HOME) },
            onStatus = viewModel::setEndTermStatus,
            onGrade = viewModel::setEndTermGrade,
            onReview = viewModel::reviewEndTerm,
            onOpenCourses = { viewModel.navigateTo(AppDestination.COURSES) },
            modifier = modifier
        )
    }
}

@Composable
private fun EndTermForm(
    state: EndTermUiState,
    termName: String,
    onBack: () -> Unit,
    onStatus: (Long, TermCourseStatus) -> Unit,
    onGrade: (Long, String) -> Unit,
    onReview: () -> Unit,
    onOpenCourses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TermPageHeader(
                    title = "ثبت پایان ترم",
                    subtitle = termName,
                    onBack = onBack,
                    backDescription = "انصراف و بازگشت به خانه"
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = TermChinDesign.CardShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "وضعیت نهایی هر درس را انتخاب کنید.",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "ثبت نمره اختیاری و مستقل از وضعیت است. تا قبل از تأیید نهایی می‌توانید بدون هیچ تغییری خارج شوید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.courses.isEmpty()) {
                item {
                    EmptyEndTerm(onOpenCourses = onOpenCourses)
                }
            } else {
                items(state.courses, key = { it.courseId }) { course ->
                    EndTermCourseCard(
                        course = course,
                        onStatus = { onStatus(course.courseId, it) },
                        onGrade = { onGrade(course.courseId, it) }
                    )
                }
            }

            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (state.courses.isEmpty()) {
                        "درسی برای پایان ترم انتخاب نشده است."
                    } else {
                        "${JalaliDate.toFaDigits(state.courses.count { it.status != null })}" +
                            " از ${JalaliDate.toFaDigits(state.courses.size)} درس مشخص شده است."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onReview,
                    enabled = state.isComplete && !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TermChinDesign.PrimaryButtonHeight)
                        .testTag("end_term_review_button"),
                    shape = TermChinDesign.ControlShape
                ) {
                    Text("مرور و تأیید نهایی", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EndTermCourseCard(
    course: EndTermCourseItem,
    onStatus: (TermCourseStatus) -> Unit,
    onGrade: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("end_term_course_${course.courseId}"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = course.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("کد: ")
                            append(JalaliDate.toFaDigits(course.code))
                            append("  •  ")
                            append(JalaliDate.toFaDigits(course.credits))
                            append(" واحد")
                            if (course.sectionCode.isNotBlank()) {
                                append("  •  گروه ")
                                append(JalaliDate.toFaDigits(course.sectionCode))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (course.instructor.isNotBlank()) {
                        Text(
                            text = course.instructor,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            StatusChipRow(
                selected = course.status,
                onSelect = onStatus,
                courseId = course.courseId
            )

            OutlinedTextField(
                value = course.grade,
                onValueChange = onGrade,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("end_term_grade_${course.courseId}"),
                label = { Text("نمرهٔ نهایی (اختیاری)", fontSize = 12.sp) },
                placeholder = { Text("مثلاً ۱۸٫۵ یا A", fontSize = 12.sp) },
                singleLine = true,
                shape = TermChinDesign.ControlShape
            )

            AttendanceSnapshotLine(stats = course.attendance)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChipRow(
    selected: TermCourseStatus?,
    onSelect: (TermCourseStatus) -> Unit,
    courseId: Long
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TermCourseStatus.values().forEach { status ->
            val accent = statusColor(status)
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = {
                    Text(
                        status.titleFa,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected == status) FontWeight.Bold else FontWeight.Normal
                    )
                },
                shape = TermChinDesign.ChipShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.18f),
                    selectedLabelColor = accent
                ),
                modifier = Modifier.testTag("end_term_status_${courseId}_${status.name}")
            )
        }
    }
}

@Composable
private fun AttendanceSnapshotLine(stats: AttendanceStats) {
    if (stats.total == 0) return
    Text(
        text = buildString {
            append("حضور: ")
            append(JalaliDate.toFaDigits(stats.present))
            append("  •  غیبت: ")
            append(JalaliDate.toFaDigits(stats.absent))
            append("  •  تأخیر: ")
            append(JalaliDate.toFaDigits(stats.late))
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EmptyEndTerm(onOpenCourses: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
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
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                "در ترم فعلی درسی انتخاب نشده است.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "برای ثبت پایان ترم، ابتدا از «دروس» درس‌های این ترم را اضافه کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onOpenCourses) { Text("رفتن به دروس") }
        }
    }
}

@Composable
private fun EndTermReview(
    state: EndTermUiState,
    termName: String,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TermPageHeader(
                    title = "خلاصهٔ پایان ترم",
                    subtitle = termName,
                    onBack = onEdit,
                    backDescription = "بازگشت و ویرایش"
                )
            }
            item {
                SummaryStatsCard(state)
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = TermChinDesign.CardShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "نتیجهٔ هر درس",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        state.courses.forEach { course ->
                            ReviewCourseRow(course)
                        }
                    }
                }
            }
            item {
                Text(
                    "با تأیید نهایی، نام ترم را مشخص می‌کنید و سپس این ترم آرشیو و از حالت فعال خارج می‌شود. " +
                        "اطلاعات و برنامهٔ آن حذف نمی‌شود و در «سوابق تحصیلی» باقی می‌ماند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .height(TermChinDesign.PrimaryButtonHeight),
                    shape = TermChinDesign.ControlShape
                ) { Text("بازگشت") }
                Button(
                    onClick = onConfirm,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(TermChinDesign.PrimaryButtonHeight)
                        .testTag("end_term_confirm_button"),
                    shape = TermChinDesign.ControlShape
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("تأیید و آرشیو ترم", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryStatsCard(state: EndTermUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(TermChinDesign.IconMd)
                )
                TermStat(label = "درس", value = state.courses.size)
                TermStat(label = "واحد کل", value = state.totalCredits)
                TermStat(label = "واحد گذرانده", value = state.passedCredits)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TermCourseStatus.values().forEach { status ->
                    Surface(
                        shape = TermChinDesign.PillShape,
                        color = statusColor(status).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${status.titleFa}: ${JalaliDate.toFaDigits(state.countOf(status))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor(status),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermStat(label: String, value: Int) {
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
private fun ReviewCourseRow(course: EndTermCourseItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(JalaliDate.toFaDigits(course.code))
                    append(" • ")
                    append(JalaliDate.toFaDigits(course.credits))
                    append(" واحد")
                    if (!course.grade.isNullOrBlank()) {
                        append(" • نمره: ")
                        append(JalaliDate.toFaDigits(course.grade))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        course.status?.let { StatusBadge(it) }
    }
}

@Composable
private fun EndTermSuccess(
    state: EndTermUiState,
    onStartNewTerm: () -> Unit,
    onOpenRecords: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(92.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "ترم با موفقیت آرشیو شد",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("end_term_success")
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "«${state.archivedTermName}» در سوابق تحصیلی ثبت شد.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = TermChinDesign.CardShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TermStat(label = "درس", value = state.archivedCourseCount)
                TermStat(label = "پاس‌شده", value = state.archivedPassedCount)
                TermStat(label = "واحد گذرانده", value = state.archivedPassedCredits)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onStartNewTerm,
            modifier = Modifier
                .fillMaxWidth()
                .height(TermChinDesign.PrimaryButtonHeight)
                .testTag("end_term_start_new_button"),
            shape = TermChinDesign.ControlShape
        ) { Text("شروع ترم جدید", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenRecords,
            modifier = Modifier
                .fillMaxWidth()
                .height(TermChinDesign.PrimaryButtonHeight),
            shape = TermChinDesign.ControlShape
        ) { Text("مشاهده سوابق تحصیلی") }
        TextButton(onClick = onHome) { Text("بازگشت به خانه") }
        Text(
            "اطلاعات و برنامهٔ ترم قبلی حفظ شده است.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun TermPageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    backDescription: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(TermChinDesign.MinTouchHeight)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StatusBadge(status: TermCourseStatus) {
    val accent = statusColor(status)
    Surface(
        shape = TermChinDesign.PillShape,
        color = accent.copy(alpha = 0.14f)
    ) {
        Text(
            text = status.titleFa,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun statusColor(status: TermCourseStatus): Color = when (status) {
    TermCourseStatus.PASSED -> Color(0xFF1B7F4B)
    TermCourseStatus.FAILED -> MaterialTheme.colorScheme.error
    TermCourseStatus.WITHDRAWN -> MaterialTheme.colorScheme.onSurfaceVariant
    TermCourseStatus.INCOMPLETE -> MaterialTheme.colorScheme.tertiary
}
