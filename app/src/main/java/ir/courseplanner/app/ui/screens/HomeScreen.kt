package ir.courseplanner.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.AttendanceRecord
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.ConflictType
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.components.AttendanceSheet
import ir.courseplanner.app.ui.components.AttendanceSummaryCard
import ir.courseplanner.app.ui.components.EndTermConfirmDialog
import ir.courseplanner.app.ui.components.TimetableItem
import ir.courseplanner.app.ui.components.WeeklyTimetable
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate
import ir.courseplanner.app.util.TimetableExporter
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
    val attendanceRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val attendanceByCourse by viewModel.attendanceByCourse.collectAsStateWithLifecycle()
    val passedCourses by viewModel.passedCourses.collectAsStateWithLifecycle()
    val currentTermCourseCount by viewModel.currentTermCourseCount.collectAsStateWithLifecycle()
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
    var showExamSchedule by remember { mutableStateOf(false) }
    var showEndTermConfirm by remember { mutableStateOf(false) }
    var attendanceTarget by remember { mutableStateOf<ScheduleEngine.UpcomingSession?>(null) }
    var attendanceTimetableTarget by remember { mutableStateOf<TimetableItem?>(null) }

    val scrollToTimetable: () -> Unit = {
        homeScope.launch { weeklyTimetableRequester.bringIntoView() }
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
        // A. Personal context, then the time facts, then the one decision that
        // matters now — three calm surfaces, one consistent card scale.
        DashboardHeader(
            studentName = preferences.studentName,
            semesterName = preferences.semesterName,
            major = preferences.major,
            credits = metrics.totalCredits,
            creditTarget = preferences.creditTarget,
            weekInfo = weekInfo,
            now = now,
            isTermActive = preferences.isTermActive,
            hasCurrentTermCourses = currentTermCourseCount > 0,
            onEndTerm = { showEndTermConfirm = true },
            onAcademicRecords = { viewModel.openAcademicRecords() },
            onStartNewTerm = { viewModel.requestStartNewTerm() },
            modifier = Modifier.dashboardEntrance(0)
        )

        if (!preferences.isTermActive) {
            InactiveTermCard(
                archivedRecordsAvailable = true,
                onStartNewTerm = { viewModel.requestStartNewTerm() },
                onOpenRecords = { viewModel.openAcademicRecords() },
                modifier = Modifier.dashboardEntrance(1)
            )
        }

        // B. Primary hero: now and next, with a live countdown.
        if (hasEnrolled) {
            NowNextCard(
                todayIdx = now.appDayIdx,
                todayClasses = todaysClasses,
                state = nowNextState,
                nowMinutes = now.nowMinutes,
                onManageCourses = { viewModel.navigateTo(AppDestination.COURSES) },
                modifier = Modifier.dashboardEntrance(2)
            )
        }

        // Today's attendance: only classes that belong to today (parity-aware
        // via todaysClasses). A class can be marked once it has started.
        if (todaysClasses.isNotEmpty()) {
            TodayAttendanceCard(
                classes = todaysClasses,
                recordsToday = attendanceRecords.filter { it.dateEpochDay == now.epochDay },
                nowMinutes = now.nowMinutes,
                onMark = { attendanceTarget = it },
                modifier = Modifier.dashboardEntrance(3)
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
                },
                modifier = Modifier.dashboardEntrance(3)
            )
        }

        // E. Contextual next step: only when the semester setup is genuinely
        // unfinished (courses still unfinalized). Never duplicates the bottom
        // navigation destinations (Courses / Schedule Builder live there).
        if (hasEnrolled && enrolledSections.any { it.sessions.isEmpty() }) {
            ContextualSetupCard(
                onOpenCourses = { viewModel.navigateTo(AppDestination.COURSES) },
                onOpenSchedule = { viewModel.navigateTo(AppDestination.SCHEDULE) },
                modifier = Modifier.dashboardEntrance(5)
            )
        }

        // First-run guided sequence: import → pick courses → build. Each step
        // exists in the app; nothing here is decorative.
        if (!hasEnrolled && preferences.isTermActive) {
            GuidedEmptyState(
                onOpenSettings = { viewModel.navigateTo(AppDestination.SETTINGS) },
                onOpenCourses = { viewModel.navigateTo(AppDestination.COURSES) },
                modifier = Modifier.dashboardEntrance(5)
            )
        } else {
            // Exam Schedule Section (expandable, conflict-aware as before).
            val examList = enrolledSections.filter { it.section.examDate.isNotBlank() }
                .sortedBy { it.section.examDate }

            if (examList.isNotEmpty()) {
                val hasExamConflict = conflicts.any { it.type == ConflictType.EXAM_OVERLAP }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dashboardEntrance(6)
                        .bringIntoViewRequester(examSectionRequester),
                    shape = TermChinDesign.CardShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = TermChinDesign.CardElevation,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (hasExamConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.semantics { heading() }
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
                                        shape = TermChinDesign.ControlShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics(mergeDescendants = true) {}
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
                                                    text = "گروه ${JalaliDate.toFaDigits(sec.section.sectionCode)} • استاد: ${sec.section.instructor.ifBlank { "نامشخص" }}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Surface(
                                                shape = TermChinDesign.ChipShape,
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    Text(
                                                        text = sec.section.examDate,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (sec.section.examStartTime.isNotBlank()) {
                                                        Text(
                                                            text = "${JalaliDate.toFaDigits(sec.section.examStartTime)} تا ${JalaliDate.toFaDigits(sec.section.examEndTime)}",
                                                            style = MaterialTheme.typography.labelSmall,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashboardEntrance(7),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // The section title now sits OUTSIDE the card (the component's
                // own in-card title is off via showHeader = false), so the
                // section is named only once instead of twice.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تقویم برنامه هفتگی",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Export lives as bare icons right beside the timetable it
                    // acts on, not as a full-width card at the page's end.
                    IconButton(
                        onClick = { TimetableExporter.copyToClipboard(context, enrolledSections) },
                        modifier = Modifier.testTag("home_export_copy_button")
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "کپی متن برنامه",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TermChinDesign.IconMd)
                        )
                    }
                    IconButton(
                        onClick = { TimetableExporter.shareSchedule(context, enrolledSections) },
                        modifier = Modifier.testTag("home_export_share_button")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "اشتراک‌گذاری برنامه",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TermChinDesign.IconMd)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(weeklyTimetableRequester)
                        .testTag("home_weekly_timetable_section")
                ) {
                    WeeklyTimetable(
                        sections = enrolledSections,
                        initialDay = now.appDayIdx,
                        highlightedDay = now.appDayIdx,
                        showThursday = preferences.showThursday,
                        density = preferences.timetableDensity,
                        currentParity = weekInfo?.parity,
                        showHeader = false,
                        onOpenAttendance = { item -> attendanceTimetableTarget = item },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Documents are useful context, but never outrank today's class,
        // planning warnings, or the timetable itself.
        if (hasEnrolled && visibleDocumentCount > 0) {
            SecondaryDocumentsLine(
                count = visibleDocumentCount,
                onOpen = { viewModel.navigateTo(AppDestination.DOCUMENTS) },
                modifier = Modifier.dashboardEntrance(8)
            )
        }

        if (hasEnrolled) {
            AttendanceSummaryCard(
                enrolledSections = enrolledSections,
                statsByCourse = attendanceByCourse,
                absenceLimit = preferences.absenceLimit,
                onOpenSchedule = { viewModel.navigateTo(AppDestination.SCHEDULE) },
                modifier = Modifier.dashboardEntrance(8)
            )
        }

        PassedCoursesShortcutCard(
            courseCount = passedCourses.size,
            totalCredits = passedCourses.sumOf { it.credits },
            onOpen = { viewModel.openPassedCourses() },
            modifier = Modifier.dashboardEntrance(9)
        )

        // C. Compact academic overview, demoted to the very bottom: it is
        // reference material ("how full is my term?"), not today's plan, so it
        // never pushes the timetable below the fold.
        if (hasEnrolled) {
            OverviewCard(
                credits = metrics.totalCredits,
                target = preferences.creditTarget,
                activeDays = metrics.activeDaysCount,
                gapMinutes = metrics.totalGapMinutes,
                courseCount = metrics.totalCourses,
                onOptimize = { viewModel.navigateTo(AppDestination.SCHEDULE) },
                modifier = Modifier.dashboardEntrance(10)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Attendance sheet for today's class rows.
    attendanceTarget?.let { target ->
        val section = target.section
        val session = target.session
        val occurrence = now.epochDay
        val existing = attendanceRecords.firstOrNull {
            it.sectionId == section.section.id &&
                it.dateEpochDay == occurrence &&
                it.startTime == session.startTime
        }
        val started = now.nowMinutes >= session.startMinutes
        AttendanceSheet(
            section = section,
            session = session,
            occurrenceEpochDay = occurrence,
            existing = existing,
            sectionHistory = attendanceRecords
                .filter { it.sectionId == section.section.id }
                .sortedByDescending { it.dateEpochDay },
            canRecord = started,
            blockedReason = if (!started) {
                "کلاس هنوز شروع نشده است؛ پس از شروع می‌توانید حضور را ثبت کنید."
            } else null,
            onDismiss = { attendanceTarget = null },
            onSave = { epochDay, status, note ->
                viewModel.markAttendance(
                    sectionId = section.section.id,
                    dateEpochDay = epochDay,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    status = status,
                    note = note
                )
                attendanceTarget = null
            },
            onClear = {
                viewModel.clearAttendance(section.section.id, occurrence, session.startTime)
                attendanceTarget = null
            },
            onDeleteHistory = { viewModel.deleteAttendance(it) }
        )
    }

    // Attendance sheet opened from the weekly timetable detail dialog. The
    // occurrence is resolved inside the current week only: past days can be
    // marked, future days show a reason instead of writing a record.
    attendanceTimetableTarget?.let { item ->
        val section = item.section
        val session = item.session
        val day = session.dayOfWeek
        val occurrence = if (day <= now.appDayIdx) {
            now.epochDay - (now.appDayIdx - day)
        } else {
            now.epochDay + (day - now.appDayIdx)
        }
        val occurs = ScheduleEngine.occursInWeek(session, weekInfo?.parity)
        val dateIsPast = occurrence < now.epochDay
        val startedToday = occurrence == now.epochDay && now.nowMinutes >= session.startMinutes
        val canRecord = occurs && (dateIsPast || startedToday)
        val reason = when {
            !occurs -> "این جلسه در هفتهٔ جاری (${weekInfo?.parity?.titleFa ?: "نامشخص"}) تشکیل نمی‌شود."
            occurrence > now.epochDay -> "این کلاس در روزهای آیندهٔ همین هفته برگزار می‌شود."
            occurrence == now.epochDay && !startedToday -> "کلاس هنوز شروع نشده است."
            else -> null
        }
        val existing = attendanceRecords.firstOrNull {
            it.sectionId == section.section.id &&
                it.dateEpochDay == occurrence &&
                it.startTime == session.startTime
        }
        AttendanceSheet(
            section = section,
            session = session,
            occurrenceEpochDay = occurrence,
            existing = existing,
            sectionHistory = attendanceRecords
                .filter { it.sectionId == section.section.id }
                .sortedByDescending { it.dateEpochDay },
            canRecord = canRecord,
            blockedReason = reason,
            onDismiss = { attendanceTimetableTarget = null },
            onSave = { epochDay, status, note ->
                viewModel.markAttendance(
                    sectionId = section.section.id,
                    dateEpochDay = epochDay,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    status = status,
                    note = note
                )
                attendanceTimetableTarget = null
            },
            onClear = {
                viewModel.clearAttendance(section.section.id, occurrence, session.startTime)
                attendanceTimetableTarget = null
            },
            onDeleteHistory = { viewModel.deleteAttendance(it) }
        )
    }

    if (showEndTermConfirm) {
        EndTermConfirmDialog(
            courseCount = currentTermCourseCount,
            onDismiss = { showEndTermConfirm = false },
            onConfirm = {
                showEndTermConfirm = false
                viewModel.openEndTerm()
            }
        )
    }
}

/**
 * Today's classes as attendance rows. Only classes whose start time has
 * passed are actionable; earlier records show their saved status instead.
 */
@Composable
private fun TodayAttendanceCard(
    classes: List<ScheduleEngine.UpcomingSession>,
    recordsToday: List<AttendanceRecord>,
    nowMinutes: Int,
    onMark: (ScheduleEngine.UpcomingSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val byKey = remember(recordsToday) {
        recordsToday.associateBy { "${it.sectionId}|${it.startTime}" }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_today_attendance"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(TermChinDesign.IconMd)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "حضور امروز",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "برای ثبت لمس کنید",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            classes.forEach { item ->
                val record = byKey["${item.section.section.id}|${item.session.startTime}"]
                val started = nowMinutes >= item.session.startMinutes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TermChinDesign.MicroShape)
                        .clickable(enabled = started) { onMark(item) }
                        .background(
                            if (record != null) {
                                when (record.status) {
                                    AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                }
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.section.courseName,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${JalaliDate.toFaDigits(item.session.startTime)} تا ${JalaliDate.toFaDigits(item.session.endTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = when {
                            record != null -> record.status.titleFa
                            !started -> "شروع نشده"
                            else -> "ثبت حضور"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            record?.status == AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error
                            record != null -> MaterialTheme.colorScheme.primary
                            !started -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

/** Compact entry point to the academic record, always available. */
@Composable
private fun PassedCoursesShortcutCard(
    courseCount: Int,
    totalCredits: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(TermChinDesign.CardShape)
            .clickable(onClick = onOpen)
            .testTag("home_passed_courses"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(TermChinDesign.ControlShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "دروس پاس‌شده",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (courseCount == 0) {
                        "سابقهٔ دروس گذرانده‌شده را ثبت کنید"
                    } else {
                        "${JalaliDate.toFaDigits(courseCount)} درس • ${JalaliDate.toFaDigits(totalCredits)} واحد"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "باز کردن دروس پاس‌شده",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Shown only while no term is active. It states the fact plainly and offers
 * exactly the two useful next steps: open the new term, or read the archive.
 */
@Composable
private fun InactiveTermCard(
    archivedRecordsAvailable: Boolean,
    onStartNewTerm: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_inactive_term"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(TermChinDesign.IconMd)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ترم فعالی ندارید",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ترم قبلی آرشیو شده و اطلاعاتش در سوابق تحصیلی محفوظ است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartNewTerm,
                    modifier = Modifier
                        .weight(1f)
                        .height(TermChinDesign.PrimaryButtonHeight)
                        .testTag("home_start_new_term_button"),
                    shape = TermChinDesign.ControlShape
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(TermChinDesign.IconSm)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("شروع ترم جدید", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (archivedRecordsAvailable) {
                    OutlinedButton(
                        onClick = onOpenRecords,
                        modifier = Modifier
                            .weight(1f)
                            .height(TermChinDesign.PrimaryButtonHeight),
                        shape = TermChinDesign.ControlShape
                    ) { Text("سوابق تحصیلی", fontSize = 13.sp) }
                }
            }
        }
    }
}

/**
 * Staggered entrance for the dashboard blocks: each section fades in and
 * lifts a few pixels, offset by [index], so Home assembles itself instead of
 * snapping into place. Runs once (finite, no infinite transitions) and only
 * changes the visual transform — layout and semantics stay put.
 */
@Composable
private fun Modifier.dashboardEntrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (index > 0) delay(index * 45L)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
        )
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/**
 * A. Personal context and time facts in a single block: the greeting, the
 * Jalali date and the academic week all answer "where am I right now", so
 * they share one card instead of stacking two near-identical surfaces.
 */
@Composable
internal fun DashboardHeader(
    studentName: String,
    semesterName: String,
    major: String,
    credits: Int,
    creditTarget: Int,
    weekInfo: ScheduleEngine.AcademicWeek?,
    now: DashboardNow,
    isTermActive: Boolean = true,
    hasCurrentTermCourses: Boolean = false,
    onEndTerm: () -> Unit = {},
    onAcademicRecords: () -> Unit = {},
    onStartNewTerm: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showTermMenu by remember { mutableStateOf(false) }
    val todayJalali = remember(now.epochDay) { JalaliDate.fromEpochDay(now.epochDay) }
    val dateLabel = buildString {
        append(ClassSession.getDayName(now.appDayIdx))
        if (todayJalali != null) {
            append(" ")
            append(JalaliDate.toFaDigits(todayJalali.day))
            append(" ")
            append(JalaliDate.monthNameFa(todayJalali.month))
        }
    }
    val weekLabel = if (weekInfo != null) {
        "هفته ${JalaliDate.toFaDigits(weekInfo.number)} (${weekInfo.parity.titleFa})"
    } else {
        "هفته نامشخص"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_header_card"),
        shape = TermChinDesign.CardShape,
        // A confident colour block instead of a faint tint: the greeting is the
        // first thing the student sees, so it owns the theme's primary colour.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = greetingFa(now.nowMinutes, studentName),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = buildString {
                            append(semesterName)
                            if (major.isNotBlank()) {
                                append(" • ")
                                append(major)
                            }
                            append(" • ")
                            append(JalaliDate.toFaDigits(credits))
                            append(" واحد")
                            if (creditTarget > 0) {
                                append(" از ")
                                append(JalaliDate.toFaDigits(creditTarget))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box {
                    IconButton(
                        onClick = { showTermMenu = true },
                        modifier = Modifier
                            .size(TermChinDesign.IconButtonSize)
                            .testTag("home_term_menu_button")
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "مدیریت ترم",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showTermMenu,
                        onDismissRequest = { showTermMenu = false }
                    ) {
                        if (isTermActive) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (hasCurrentTermCourses) "ثبت پایان ترم"
                                        else "ثبت پایان ترم (بدون درس)"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                enabled = hasCurrentTermCourses,
                                onClick = {
                                    showTermMenu = false
                                    onEndTerm()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("شروع ترم جدید") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    showTermMenu = false
                                    onStartNewTerm()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("سوابق تحصیلی") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showTermMenu = false
                                onAcademicRecords()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Date + academic week: the two time facts the standalone date card
            // used to carry, folded into the greeting block.
            Row(
                modifier = Modifier.testTag("home_header_date_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = TermChinDesign.PillShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = TermChinDesign.PillShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = weekLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
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
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
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
                        shape = TermChinDesign.ControlShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp,
                            vertical = 6.dp
                        ),
                        modifier = Modifier.height(TermChinDesign.MinTouchHeight)
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
                modifier = Modifier.size(TermChinDesign.IconMd)
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
                    shape = TermChinDesign.ControlShape,
                    modifier = Modifier.height(TermChinDesign.MinTouchHeight)
                ) {
                    Text("\u062f\u0631\u0648\u0633", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenSchedule,
                    shape = TermChinDesign.ControlShape,
                    modifier = Modifier.height(TermChinDesign.MinTouchHeight)
                ) {
                    Text("\u0628\u0631\u0646\u0627\u0645\u0647\u200c\u0633\u0627\u0632", style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
                modifier = Modifier.size(TermChinDesign.IconSm)
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
            Text("\u0645\u0634\u0627\u0647\u062f\u0647 \u062c\u0632\u0648\u0627\u062a", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}


