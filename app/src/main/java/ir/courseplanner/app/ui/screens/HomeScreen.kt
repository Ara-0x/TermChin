package ir.courseplanner.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.ConflictType
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.components.WeeklyTimetable
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate
import ir.courseplanner.app.util.TimetableExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Academic dashboard: one clock sample per frame drives the header date, the
 * now/next hero, parity filtering and the timetable highlight together, so
 * time-sensitive rows can never disagree. Pure decisions (states, attention
 * list, progress safety) live in HomeDashboard.kt on explicit inputs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val enrolledSections by viewModel.enrolledSections.collectAsStateWithLifecycle()
    val conflicts by viewModel.enrolledConflicts.collectAsStateWithLifecycle()
    val examWarnings by viewModel.enrolledExamWarnings.collectAsStateWithLifecycle()
    val metrics by viewModel.enrolledMetrics.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val allDocs by viewModel.documentsWithCourse.collectAsStateWithLifecycle()
    // Lifecycle-aware clock: ticks only while this screen is collected, so the
    // countdown and day rollover stay correct without leaking background jobs.
    val tick by viewModel.dashboardTick.collectAsStateWithLifecycle()
    // The Documents screen intentionally exposes material only for finalized
    // courses, so the dashboard must use that same scope for its badge.
    val visibleDocumentCount = remember(allDocs, enrolledSections) {
        val enrolledCourseIds = enrolledSections.map { it.course.id }.toSet()
        allDocs.count { it.course.id in enrolledCourseIds }
    }
    val scrollState = rememberScrollState()

    // The timetable button in the hero scrolls to the real weekly timetable
    // further down this screen instead of opening the builder; exam actions
    // scroll to the exam section the same way.
    val weeklyTimetableRequester = remember { BringIntoViewRequester() }
    val examSectionRequester = remember { BringIntoViewRequester() }
    val homeScope = rememberCoroutineScope()
    val timetableGlow = remember { Animatable(0f) }
    var timetableGlowJob by remember { mutableStateOf<Job?>(null) }
    var showExamSchedule by remember { mutableStateOf(false) }

    val scrollToTimetable: () -> Unit = {
        timetableGlowJob?.cancel()
        timetableGlowJob = homeScope.launch {
            weeklyTimetableRequester.bringIntoView()
            timetableGlow.animateTo(1f, tween(220))
            delay(1_200)
            timetableGlow.animateTo(0f, tween(520))
        }
    }
    val scrollToExams: () -> Unit = {
        showExamSchedule = true
        homeScope.launch { examSectionRequester.bringIntoView() }
    }

    // One clock sample per frame: day index, minutes and epoch day always move
    // together, so midnight and week boundaries resolve consistently.
    val now = remember(tick) { dashboardNow(tick) }
    val weekInfo = remember(
        now.epochDay,
        preferences.semesterStartEpochDay,
        preferences.firstWeekIsOdd
    ) {
        ScheduleEngine.academicWeek(
            now.epochDay,
            preferences.semesterStartEpochDay,
            preferences.firstWeekIsOdd
        )
    }
    val hasEnrolled = enrolledSections.isNotEmpty()
    // Parity-aware lookups (engine rules): a biweekly session never counts for
    // a day/week it does not meet in; unknown parity keeps everything visible.
    val todaysClasses = remember(enrolledSections, weekInfo, now.appDayIdx) {
        if (!hasEnrolled) emptyList()
        else ScheduleEngine.sessionsOnDay(enrolledSections, now.appDayIdx, weekInfo?.parity)
    }
    val nextClass = remember(enrolledSections, weekInfo, now, preferences) {
        if (!hasEnrolled) {
            null
        } else {
            ScheduleEngine.nextUpcomingSession(
                sections = enrolledSections,
                dayOfWeek = now.appDayIdx,
                nowMinutes = now.nowMinutes,
                todayParity = weekInfo?.parity,
                parityForOffset = if (weekInfo == null) null else { offsetDays: Int ->
                    val startEpoch = preferences.semesterStartEpochDay
                    if (startEpoch == null) null else {
                        val futureWeekNumber =
                            ((now.epochDay - startEpoch + offsetDays) / 7 + 1).toInt()
                        val isEven = if (preferences.firstWeekIsOdd) futureWeekNumber % 2 == 0
                        else futureWeekNumber % 2 == 1
                        if (isEven) ScheduleEngine.WeekParity.EVEN
                        else ScheduleEngine.WeekParity.ODD
                    }
                }
            )
        }
    }
    val nowNextState = remember(nextClass, now) {
        resolveNowNext(nextClass, now.appDayIdx, now.nowMinutes)
    }
    val attentionItems = remember(conflicts, examWarnings, weekInfo, hasEnrolled) {
        buildAttentionItems(
            classConflicts = conflicts.count { it.type == ConflictType.CLASS_TIME_OVERLAP },
            examConflicts = conflicts.count { it.type == ConflictType.EXAM_OVERLAP },
            examWarnings = examWarnings.size,
            parityKnown = weekInfo != null,
            hasEnrolled = hasEnrolled
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = TermChinDesign.ScreenMargin, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // A. Compact personalized header (date, week, offline pill, credits).
        DashboardHeader(
            studentName = preferences.studentName,
            semesterName = preferences.semesterName,
            major = preferences.major,
            credits = metrics.totalCredits,
            creditTarget = preferences.creditTarget,
            now = now,
            weekInfo = weekInfo
        )

        // B. Primary hero: now and next, with a live countdown.
        if (hasEnrolled) {
            NowNextCard(
                todayIdx = now.appDayIdx,
                todayClasses = todaysClasses,
                state = nowNextState,
                weekLabel = weekInfo?.let {
                    "هفته ${JalaliDate.toFaDigits(it.number)} • ${it.parity.titleFa}"
                },
                nowMinutes = now.nowMinutes,
                onOpenWeeklyTimetable = scrollToTimetable,
                onManageCourses = { viewModel.navigateTo(AppDestination.COURSES) }
            )
        }

        // D. Needs attention — rendered only when something is actionable.
        if (attentionItems.isNotEmpty()) {
            AttentionCard(
                items = attentionItems,
                onAction = { kind ->
                    when (kind) {
                        AttentionKind.CLASS_CONFLICT -> scrollToTimetable()
                        AttentionKind.EXAM_CONFLICT,
                        AttentionKind.EXAM_WARNING -> scrollToExams()
                        AttentionKind.MISSING_SEMESTER_START ->
                            viewModel.navigateTo(AppDestination.SETTINGS)
                    }
                }
            )
        }

        // C. Compact academic overview (safe credit progress, no color noise).
        if (hasEnrolled) {
            OverviewCard(
                credits = metrics.totalCredits,
                target = preferences.creditTarget,
                activeDays = metrics.activeDaysCount,
                gapMinutes = metrics.totalGapMinutes,
                courseCount = metrics.totalCourses,
                onOptimize = { viewModel.navigateTo(AppDestination.SCHEDULE) }
            )
        }

        // E. Contextual next step: only when the semester setup is genuinely
        // unfinished (courses still unfinalized). Never duplicates the bottom
        // navigation destinations (Courses / Schedule Builder live there).
        if (hasEnrolled && enrolledSections.any { it.sessions.isEmpty() }) {
            ContextualSetupCard(
                onOpenCourses = { viewModel.navigateTo(AppDestination.COURSES) },
                onOpenSchedule = { viewModel.navigateTo(AppDestination.SCHEDULE) }
            )
        }

        // G. Secondary: upcoming exams (real data only) and documents live
        // where they belong — the bottom navigation owns the Documents
        // destination, so Home shows no shortcut card duplicating it.
        if (hasEnrolled && visibleDocumentCount > 0) {
            SecondaryDocumentsLine(
                count = visibleDocumentCount,
                onOpen = { viewModel.navigateTo(AppDestination.DOCUMENTS) }
            )
        }

        // First-run guided sequence: import → pick courses → build. Each step
        // exists in the app; nothing here is decorative.
        if (!hasEnrolled) {
            GuidedEmptyState(
                onOpenSettings = { viewModel.navigateTo(AppDestination.SETTINGS) },
                onOpenCourses = { viewModel.navigateTo(AppDestination.COURSES) }
            )
        } else {
            // Share & Copy Timetable Action Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = TermChinDesign.CardShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "خروجی و اشتراک‌گذاری برنامه:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { TimetableExporter.copyToClipboard(context, enrolledSections) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("کپی متن", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { TimetableExporter.shareSchedule(context, enrolledSections) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اشتراک‌گذاری", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Exam Schedule Section (expandable, conflict-aware as before).
            val examList = enrolledSections.filter { it.section.examDate.isNotBlank() }
                .sortedBy { it.section.examDate }

            if (examList.isNotEmpty()) {
                val hasExamConflict = conflicts.any { it.type == ConflictType.EXAM_OVERLAP }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(examSectionRequester),
                    shape = TermChinDesign.CardShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        if (hasExamConflict) 1.5.dp else 1.dp,
                        if (hasExamConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExamSchedule = !showExamSchedule },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = null,
                                    tint = if (hasExamConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "برنامه زمانی امتحانات پایان‌ترم",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = buildString {
                                            append("${JalaliDate.toFaDigits(examList.size)} آزمون ثبت شده")
                                            if (hasExamConflict) {
                                                append(" (همراه با تداخل آزمون!)")
                                            } else if (examWarnings.isNotEmpty()) {
                                                append(" (${JalaliDate.toFaDigits(examWarnings.size)} هشدار ساعت نامشخص)")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasExamConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Icon(
                                if (showExamSchedule) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showExamSchedule,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                examList.forEach { sec ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sec.course.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "گروه ${sec.section.sectionCode} • استاد: ${sec.section.instructor.ifBlank { "نامشخص" }}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    Text(
                                                        text = sec.section.examDate,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (sec.section.examStartTime.isNotBlank()) {
                                                        Text(
                                                            text = "${sec.section.examStartTime} تا ${sec.section.examEndTime}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // F. Weekly timetable preview: the real component, headed and
        // parity-labelled, shown only when there is a program to preview.
        if (hasEnrolled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "برنامه هفتگی",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (weekInfo != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "هفته ${JalaliDate.toFaDigits(weekInfo.number)} (${weekInfo.parity.titleFa})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(weeklyTimetableRequester)
                        .testTag("home_weekly_timetable_section")
                        .clip(TermChinDesign.CardShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f * timetableGlow.value)
                        )
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.15f + 0.75f * timetableGlow.value
                            ),
                            shape = TermChinDesign.CardShape
                        )
                ) {
                    WeeklyTimetable(
                        sections = enrolledSections,
                        showThursday = preferences.showThursday,
                        density = preferences.timetableDensity,
                        currentParity = weekInfo?.parity,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * A. Compact header: greeting, semester line, Persian date + academic week,
 * a secondary offline pill, and a zero-safe credit progress row.
 */
@Composable
internal fun DashboardHeader(
    studentName: String,
    semesterName: String,
    major: String,
    credits: Int,
    creditTarget: Int,
    now: DashboardNow,
    weekInfo: ScheduleEngine.AcademicWeek?,
    modifier: Modifier = Modifier
) {
    val todayJalali = remember(now.epochDay) { JalaliDate.fromEpochDay(now.epochDay) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_header_card"),
        shape = TermChinDesign.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (studentName.isNotBlank()) "سلام، $studentName" else "ترم‌چین",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(semesterName)
                            if (major.isNotBlank()) {
                                append(" • ")
                                append(major)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val dateLine = todayJalali?.let { j ->
                        buildString {
                            append(ClassSession.getDayName(now.appDayIdx))
                            append(" ")
                            append(JalaliDate.toFaDigits(j.day))
                            append(" ")
                            append(JalaliDate.monthNameFa(j.month))
                            if (weekInfo != null) {
                                append(" • هفته ")
                                append(JalaliDate.toFaDigits(weekInfo.number))
                                append(" (")
                                append(weekInfo.parity.titleFa)
                                append(")")
                            }
                        }
                    }
                    if (dateLine != null) {
                        Text(
                            text = dateLine,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Secondary offline pill: the app works offline; the pill must
                // never read as a warning or a connectivity requirement.
                Surface(
                    shape = TermChinDesign.CardShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = "برنامه کاملاً آفلاین کار می‌کند",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "آفلاین",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val ratio = creditProgressRatio(credits, creditTarget)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "واحدهای اخذشده",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (creditTarget > 0) {
                            "${JalaliDate.toFaDigits(credits)} از ${JalaliDate.toFaDigits(creditTarget)} واحد"
                        } else {
                            "${JalaliDate.toFaDigits(credits)} واحد ثبت شده"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // A zero/unset target renders no bar: never divide by zero,
                // never display a NaN progress value.
                if (ratio != null) {
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * First-run guided sequence. Every step maps to a real destination; the third
 * step is guidance text (no dead button) until courses exist.
 */
@Composable
internal fun GuidedEmptyState(
    onOpenSettings: () -> Unit,
    onOpenCourses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_empty_state"),
        shape = TermChinDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(TermChinDesign.EmptyArt)
                        )
            }

            Text(
                text = "برنامه کلاسی شما خالی است",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            GuidedStepRow(
                number = "۱",
                title = "ورود اطلاعات درسی",
                subtitle = "فایل پرتال، JSON یا CSV را وارد کنید",
                actionLabel = "تنظیمات و ورود داده",
                onAction = onOpenSettings
            )
            GuidedStepRow(
                number = "۲",
                title = "انتخاب درس‌ها",
                subtitle = "درس‌ها را به برنامه اضافه کنید",
                actionLabel = "مدیریت درس‌ها",
                onAction = onOpenCourses
            )
            GuidedStepRow(
                number = "۳",
                title = "ساخت برنامه هوشمند",
                subtitle = "بعد از انتخاب درس، برنامه هفتگی‌تان را می‌سازید",
                actionLabel = null,
                onAction = null
            )
        }
    }
}

@Composable
private fun GuidedStepRow(
    number: String,
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = TermChinDesign.RowShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onAction,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp,
                            vertical = 6.dp
                        ),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Contextual next step shown only while the semester setup is unfinished
 * (enrolled courses exist but none is finalized yet). Points at the two real
 * destinations that finish the job — Courses and Schedule Builder — without
 * duplicating the bottom navigation as a permanent shortcut row.
 */
@Composable
internal fun ContextualSetupCard(
    onOpenCourses: () -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TermChinDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\u0627\u062a\u0645\u0627\u0645 \u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0646\u06cc\u0645\u200c\u0633\u0627\u0644",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2
                )
                Text(
                    text = "\u062f\u0631\u0633\u200c\u0647\u0627 \u0627\u0646\u062a\u062e\u0627\u0628 \u0648 \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0646\u0647\u0627\u06cc\u06cc \u06a9\u0646\u06cc\u062f",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenCourses,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("\u062f\u0631\u0648\u0633", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenSchedule,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("\u0628\u0631\u0646\u0627\u0645\u0647\u200c\u0633\u0627\u0632", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Quiet one-line documents summary. The Documents destination owns browsing;
 * Home only notes the real count with a single text action.
 */
@Composable
internal fun SecondaryDocumentsLine(
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = JalaliDate.toFaDigits(count) + " \u062c\u0632\u0648\u0647 \u062b\u0628\u062a \u0634\u062f\u0647",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onOpen) {
            Text("\u0645\u0634\u0627\u0647\u062f\u0647 \u062c\u0632\u0648\u0627\u062a", fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
