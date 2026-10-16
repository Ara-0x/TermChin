package ir.courseplanner.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.engine.OptimizationPreference
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.components.WeeklyTimetable
import ir.courseplanner.app.ui.theme.BookmarkAmberDark
import ir.courseplanner.app.ui.theme.BookmarkAmberLight
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.ui.theme.isDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isDark = isDarkTheme()
    val optimizationPref by viewModel.optimizationPreference.collectAsStateWithLifecycle()
    val coursesWithSections by viewModel.coursesWithSections.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val selectedCourses = coursesWithSections.filter { it.course.isSelectedForGeneration }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Surface(
            shape = TermChinDesign.CardShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "برنامه‌ساز هوشمند و بهینه‌ساز",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "تولید ترکیب‌های بدون تداخل، حذف گپ‌های خالی و چینش بهینه کلاسی",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Optimization Preference Selector — one row per strategy, the selected
        // row highlighted and explained in place, so the choice reads as a
        // decision rather than a strip of anonymous chips.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = TermChinDesign.CardShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = TermChinDesign.CardElevation,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "اولویت و استراتژی بهینه‌سازی",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                OptimizationPreference.values().forEach { pref ->
                    val isSelected = optimizationPref == pref
                    Surface(
                        shape = TermChinDesign.RowShape,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TermChinDesign.RowShape)
                            .clickable { viewModel.setOptimizationPreference(pref) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pref.titleFa,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Text(
                                        text = pref.descriptionFa,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Target Courses Summary Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = TermChinDesign.CardShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = TermChinDesign.CardElevation,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "دروس هدف برای برنامه‌ساز:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = TermChinDesign.ChipShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${selectedCourses.size} درس (${selectedCourses.sumOf { it.course.credits }} واحد)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedCourses.isEmpty()) {
                    Text(
                        text = "هنوز درسی برای ساخت برنامه انتخاب نشده است. از تب «دروس»، تیک «تولید خودکار» را برای درس‌های مورد نظرتان فعال کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(AppDestination.COURSES) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = TermChinDesign.RowShape
                    ) {
                        Text("مشاهده لیست و انتخاب دروس", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedCourses.forEach { cws ->
                            Surface(
                                shape = TermChinDesign.ControlShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${cws.course.name}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${cws.sections.size} گروه ارائه شده",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.runScheduleGenerator() },
                        // Disabled while searching: the previous result stays on
                        // screen and the label says what is happening, so the
                        // screen neither looks frozen nor accepts a second run.
                        enabled = !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("generate_schedules_button"),
                        shape = TermChinDesign.RowShape
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isGenerating) "در حال محاسبهٔ برنامه…" else "ساخت و رتبه‌بندی برنامه‌های بهینه",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Generator Result & Recommendations
        if (generationState.isGenerated) {
            val scoredList = generationState.combinations
            val count = scoredList.size

            if (count == 0) {
                // No conflict-free schedules found
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = TermChinDesign.CardShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = generationState.message ?: "هیچ ترکیبی بدون تداخل یافت نشد. گروه‌های بیشتری اضافه کنید یا درسی را تغییر دهید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                val currentScored = scoredList[generationState.currentIndex]
                val isBest = generationState.currentIndex == 0
                var showBreakdown by remember(generationState.currentIndex) { mutableStateOf(false) }
                val totalCredits = currentScored.schedule.sumOf { it.course.credits }
                val currentWarnings = remember(currentScored) {
                    ir.courseplanner.app.engine.ScheduleEngine.findExamWarnings(currentScored.schedule)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(TermChinDesign.CardElevation, TermChinDesign.CardShape),
                    shape = TermChinDesign.CardShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Navigation Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.previousCombination() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برنامهٔ قبلی")
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isBest) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (isDark) BookmarkAmberDark else BookmarkAmberLight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = if (isBest) "پیشنهاد برتر" else "پیشنهاد ${generationState.currentIndex + 1} از $count",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }

                                Text(
                                    text = "$totalCredits واحد • ${currentScored.schedule.size} درس • ${currentScored.activeDaysCount} روز",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "امتیاز ${currentScored.score}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                    TextButton(onClick = { showBreakdown = true }) {
                                        Text(
                                            "چرا این برنامه؟",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { viewModel.nextCombination() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "برنامهٔ بعدی")
                            }
                        }

                        // Skipped courses and search-cap notes are reported here, never hidden.
                        val noticeText = buildString {
                            if (generationState.skippedCourses.isNotEmpty()) {
                                append("این دروس گروه قابل‌استفاده‌ای نداشتند و لحاظ نشدند: ")
                                append(generationState.skippedCourses.joinToString("، "))
                            }
                            if (generationState.truncated) {
                                if (isNotEmpty()) append(" ")
                                append("جست‌وجو به سقف امن رسید؛ بهترین‌های یافت‌شده نمایش داده می‌شود.")
                            }
                        }
                        if (noticeText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = TermChinDesign.ControlShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = noticeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // The weekly schedule itself is the hero of this screen.
                        val resultParity = remember(
                            preferences.semesterStartEpochDay,
                            preferences.firstWeekIsOdd
                        ) {
                            ir.courseplanner.app.engine.ScheduleEngine.academicWeek(
                                ir.courseplanner.app.util.JalaliDate.todayEpochDay(),
                                preferences.semesterStartEpochDay,
                                preferences.firstWeekIsOdd
                            )?.parity
                        }
                        WeeklyTimetable(
                            sections = currentScored.schedule,
                            showThursday = preferences.showThursday,
                            density = preferences.timetableDensity,
                            currentParity = resultParity,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (currentWarnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = TermChinDesign.ControlShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "${currentWarnings.size} هشدار امتحانی (ساعت نامشخص) — مانع برنامه نیست، ولی بررسی کن:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    currentWarnings.forEach { w ->
                                        Text(
                                            text = "• ${w.descriptionFa}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Smart Optimization Badges
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            currentScored.tags.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(TermChinDesign.ChipShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Key Metrics Row (Gaps, Days, Hours)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricBox(
                                title = "گپ کلاسی",
                                value = if (currentScored.totalGapMinutes == 0) "۰ دقیقه" else "${ir.courseplanner.app.util.JalaliDate.toFaDigits(currentScored.totalGapMinutes)} دقیقه",
                                subtitle = if (currentScored.totalGapMinutes == 0) "پشت سر هم" else "زمان انتظار",
                                icon = Icons.Default.HourglassEmpty,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                title = "روزهای حضور",
                                value = "${ir.courseplanner.app.util.JalaliDate.toFaDigits(currentScored.activeDaysCount)} روز",
                                subtitle = "در طول هفته",
                                icon = Icons.Default.CalendarMonth,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                title = "ساعت کلاس",
                                value = ir.courseplanner.app.util.JalaliDate.toFaDigits(
                                    String.format("%.1f", currentScored.totalWeeklyHours).replace('.', '٫')
                                ),
                                subtitle = "ساعت در هفته",
                                icon = Icons.Default.Schedule,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Apply Button — disabled while the result is incomplete
                        // (some selected course has no usable group): applying it
                        // would replace a complete program with a partial one.
                        Button(
                            onClick = { viewModel.applyCurrentGeneratedSchedule() },
                            enabled = generationState.canApplyCurrent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("apply_generated_schedule_button"),
                            shape = TermChinDesign.RowShape
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "اعمال این برنامه بر روی تقویم هفتگی من",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }

                        if (!generationState.isComplete) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "اعمال غیرفعال است: این برنامه همهٔ دروس انتخاب‌شده را پوشش نمی‌دهد.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                    }
                }

                if (showBreakdown) {
                    ModalBottomSheet(
                        onDismissRequest = { showBreakdown = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ) {
                        ScoreBreakdownSheet(scored = currentScored)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * "Why this schedule?" — every score component on its own line so the
 * ranking is understandable instead of a magic number.
 */
@Composable
private fun ScoreBreakdownSheet(scored: ir.courseplanner.app.engine.ScoredSchedule) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "چرا این برنامه پیشنهاد شده؟",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(6.dp))
        scored.breakdown.forEach { component ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (component.delta >= 0) "✓ ${component.labelFa}" else "− ${component.labelFa}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (component.delta >= 0) "+${component.delta}" else "${component.delta}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (component.delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "امتیاز نهایی",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${scored.score}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = TermChinDesign.RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}
