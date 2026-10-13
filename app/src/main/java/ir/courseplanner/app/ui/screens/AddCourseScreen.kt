package ir.courseplanner.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Conflict
import ir.courseplanner.app.data.model.ConflictType
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.engine.CourseSuggestionRanker
import ir.courseplanner.app.engine.CourseSuggestionRanker.GroupVerdict
import ir.courseplanner.app.engine.CourseSuggestionRanker.SuggestionStatus
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.ManualSessionInput
import ir.courseplanner.app.ui.components.AddCourseDialog

/**
 * Smart course discovery ("افزودن درس"): header, local search, ranked
 * suggestions matched to the current program, and the full addable catalog
 * below them — so a smart ranking never hides the other courses.
 */
@Composable
fun AddCourseScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val query by viewModel.addCourseQuery.collectAsStateWithLifecycle()
    val suggestions by viewModel.addCourseSuggestions.collectAsStateWithLifecycle()
    val addableCount by viewModel.addableCourseCount.collectAsStateWithLifecycle()

    var showManualAddDialog by remember { mutableStateOf(false) }
    var groupPickerFor by remember {
        mutableStateOf<CourseSuggestionRanker.CourseSuggestion?>(null)
    }

    val visible = remember(suggestions) {
        suggestions.filter { !it.alreadyAdded }
    }
    val topSuggestions = remember(visible) {
        visible.filter { it.status == SuggestionStatus.COMPATIBLE }.take(5)
    }
    val rest = remember(visible, topSuggestions) {
        val topIds = topSuggestions.map { it.course.id }.toSet()
        visible.filter { it.course.id !in topIds }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header, search, suggestions, full list and empty states all live in
        // the testable content below; this screen only wires the ViewModel.
        AddCourseDiscoveryContent(
            modifier = Modifier.weight(1f),
            query = query,
            onQueryChange = { viewModel.onAddCourseQueryChange(it) },
            addableCount = addableCount,
            hasCatalog = suggestions.isNotEmpty(),
            hasVisible = visible.isNotEmpty(),
            topSuggestions = topSuggestions,
            rest = rest,
            visible = visible,
            onBack = { viewModel.navigateTo(AppDestination.COURSES) },
            onOpenManualAdd = { showManualAddDialog = true },
            onOpenSettings = { viewModel.navigateTo(AppDestination.SETTINGS) },
            onClearSearch = { viewModel.onAddCourseQueryChange("") },
            onAdd = { viewModel.addCatalogCourseToMine(it) },
            onShowGroups = { groupPickerFor = it }
        )

        groupPickerFor?.let { suggestion ->
            GroupPickerDialog(
                suggestion = suggestion,
                checkConflict = { viewModel.checkConflictForCandidate(it) },
                onEnroll = { viewModel.toggleSectionEnrolled(it, true) },
                onDismiss = { groupPickerFor = null }
            )
        }

        // Manual add stays reachable from the discovery screen as well.
        if (showManualAddDialog) {
            AddCourseDialog(
                onDismiss = { showManualAddDialog = false },
                onConfirm = { name, code, dept, credits, secCode, instructor, examDate, examStart, examEnd, sessions ->
                    viewModel.addManualCourse(
                        name = name,
                        code = code,
                        department = dept,
                        credits = credits,
                        sectionCode = secCode,
                        instructor = instructor,
                        examDate = examDate,
                        examStartTime = examStart,
                        examEndTime = examEnd,
                        sessions = sessions
                    )
                    showManualAddDialog = false
                }
            )
        }
    }
}

/**
 * Pure rendering of the discovery list (no ViewModel): the header owns the
 * back/search/count actions through callbacks, so the layout contract —
 * header, search, suggestions, full list, empty states — stays testable
 * without a database.
 */
@Composable
internal fun AddCourseDiscoveryContent(
    query: String,
    onQueryChange: (String) -> Unit,
    addableCount: Int,
    hasCatalog: Boolean,
    hasVisible: Boolean,
    topSuggestions: List<CourseSuggestionRanker.CourseSuggestion>,
    rest: List<CourseSuggestionRanker.CourseSuggestion>,
    visible: List<CourseSuggestionRanker.CourseSuggestion>,
    onBack: () -> Unit,
    onOpenManualAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearSearch: () -> Unit,
    onAdd: (Long) -> Unit,
    onShowGroups: (CourseSuggestionRanker.CourseSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    // Header: standard back + title + explainer + count.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("add_course_back_button")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "بازگشت به دروس من",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "افزودن درس",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "پیشنهادهای متناسب با برنامه فعلی شما",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = TermChinDesign.PillShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 2.dp
        ) {
            Text(
                text = "$addableCount درس قابل افزودن",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Local search: name, code, department and instructor.
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_course_search_input"),
        placeholder = {
            Text(
                "جست‌وجوی نام درس، کد، دانشکده یا استاد...",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "جست‌وجوی درس",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Clear, contentDescription = "پاک کردن جست‌وجو")
                    }
                }
                // The manual form stays one tap away.
                IconButton(
                    onClick = onOpenManualAdd,
                    modifier = Modifier.testTag("add_course_manual_button")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "افزودن دستی درس",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        shape = TermChinDesign.CardShape
    )

    Spacer(modifier = Modifier.height(8.dp))

    when {
        !hasCatalog -> AddCourseEmptyState(onOpenSettings = onOpenSettings)
        !hasVisible -> AddCourseNoMatchState(onClearSearch = onClearSearch)
        else -> LazyColumn(
            modifier = modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Suggestions first, then the full addable catalog: ranking never
            // hides a course. Keys are prefixed so the two sections can never
            // collide. When every visible course is already a top suggestion
            // (rest is empty) the second section is omitted so no card renders
            // twice.
            if (query.isBlank() && topSuggestions.isNotEmpty() && rest.isNotEmpty()) {
                item(key = "header_suggestions") {
                    SectionHeader(
                        title = "پیشنهادهای مناسب برای برنامه شما",
                        subtitle = "سازگار با گروه‌های ثبت‌نام‌شده، از کم‌اثرترین تا پراثرترین"
                    )
                }
                items(topSuggestions, key = { "sug_${it.course.id}" }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        highlighted = true,
                        onAdd = { onAdd(suggestion.course.id) },
                        onShowGroups = { onShowGroups(suggestion) }
                    )
                }
                item(key = "header_all") {
                    SectionHeader(
                        title = "همه درس‌های قابل افزودن",
                        subtitle = "بقیه گزینه‌ها — از جمله درس‌های دارای تداخل، با دلیل مشخص"
                    )
                }
                items(rest, key = { "all_${it.course.id}" }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onAdd = { onAdd(suggestion.course.id) },
                        onShowGroups = { onShowGroups(suggestion) }
                    )
                }
            } else {
                // Single flat list (search results, or everything is already a
                // top suggestion): every visible course exactly once.
                val flat = if (query.isBlank() && topSuggestions.isNotEmpty()) topSuggestions else visible
                val topIds = topSuggestions.map { it.course.id }.toSet()
                items(flat, key = { "all_${it.course.id}" }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        highlighted = suggestion.course.id in topIds,
                        onAdd = { onAdd(suggestion.course.id) },
                        onShowGroups = { onShowGroups(suggestion) }
                    )
                }
            }
        }
    }
}



/** Small bold section header inside the discovery list. */
@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/**
 * One ranked course: full name (wraps), code, credits, department, group
 * count, compatibility status, best group and the computed reason. The
 * action row keeps «افزودن» (catalog → my courses) and «گروه‌ها» (real
 * groups with conflict state and enrollment) apart.
 */
@Composable
private fun SuggestionCard(
    suggestion: CourseSuggestionRanker.CourseSuggestion,
    highlighted: Boolean = false,
    onAdd: () -> Unit,
    onShowGroups: () -> Unit
) {
    val course = suggestion.course
    val best = suggestion.bestGroup
    val compatible = suggestion.status == SuggestionStatus.COMPATIBLE
    // Top suggestions share one tonal wash (same accent, no extra badges or
    // shadows) so they read as a group without visual noise.
    val tonalWash = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovery_card_${course.code}"),
        shape = TermChinDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) tonalWash
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            1.dp,
            if (compatible) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Name owns the full first line and may wrap to a second one.
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discovery_name_${course.code}")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append("کد: ${course.code}  •  ${course.credits} واحد")
                        if (course.department.isNotBlank()) append("  •  ${course.department}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                CompatibilityChip(status = suggestion.status)
            }

            Text(
                text = "${suggestion.groups.size} گروه در کاتالوگ" +
                    (if (best != null) "  •  بهترین گروه: ${best.section.section.sectionCode}" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Computed reasons only — never a faked score.
            suggestion.reasons.take(3).forEach { reason ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        when {
                            compatible -> Icons.Default.Check
                            suggestion.status == SuggestionStatus.NO_COMPATIBLE_GROUP -> Icons.Default.WarningAmber
                            else -> Icons.AutoMirrored.Filled.MenuBook
                        },
                        contentDescription = null,
                        tint = when {
                            compatible -> MaterialTheme.colorScheme.primary
                            suggestion.status == SuggestionStatus.NO_COMPATIBLE_GROUP ->
                                MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("discovery_add_${course.code}")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onShowGroups,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("گروه‌ها", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


/** Compact compatibility badge: the course verdict at a glance. */
@Composable
private fun CompatibilityChip(status: SuggestionStatus) {
    val label = when (status) {
        SuggestionStatus.COMPATIBLE -> "سازگار"
        SuggestionStatus.NO_COMPATIBLE_GROUP -> "بدون گروه سازگار"
        SuggestionStatus.NO_TIME_DATA -> "بدون ساعت ثبت‌شده"
        SuggestionStatus.NO_GROUPS -> "بدون گروه"
        SuggestionStatus.ALREADY_ADDED -> "در دروس من"
    }
    val container = when (status) {
        SuggestionStatus.COMPATIBLE -> MaterialTheme.colorScheme.primaryContainer
        SuggestionStatus.NO_COMPATIBLE_GROUP -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (status) {
        SuggestionStatus.COMPATIBLE -> MaterialTheme.colorScheme.onPrimaryContainer
        SuggestionStatus.NO_COMPATIBLE_GROUP -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}


/**
 * Real groups of the course with their verdicts: compatible groups can be
 * enrolled directly (one group per course, per the repository rule);
 * conflicting groups show their reason and stay disabled as the top
 * recommendation; incomplete-time groups explain themselves.
 */
@Composable
private fun GroupPickerDialog(
    suggestion: CourseSuggestionRanker.CourseSuggestion,
    checkConflict: (SectionWithDetails) -> Conflict?,
    onEnroll: (SectionWithDetails) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "گروه‌های «${suggestion.course.name}»",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestion.groups, key = { it.section.section.id }) { group ->
                    val conflict = remember(group) {
                        if (group.verdict == GroupVerdict.COMPATIBLE) null
                        else group.conflict ?: checkConflict(group.section)
                    }
                    GroupRow(
                        group = group,
                        conflict = conflict,
                        enabled = group.verdict == GroupVerdict.COMPATIBLE,
                        onEnroll = {
                            onEnroll(group.section)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}


@Composable
private fun GroupRow(
    group: CourseSuggestionRanker.GroupAssessment,
    conflict: Conflict?,
    enabled: Boolean,
    onEnroll: () -> Unit
) {
    val section = group.section.section
    Surface(
        shape = TermChinDesign.RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "گروه ${section.sectionCode}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                GroupVerdictChip(verdict = group.verdict)
            }
            val preview = group.section.sessions
                .sortedBy { it.dayOfWeek * 1440 + it.startMinutes }
                .take(2)
                .joinToString(" • ") { sess ->
                    val parity = when (sess.weekType) {
                        WeekType.EVERY_WEEK -> ""
                        WeekType.EVEN_WEEKS -> " (زوج)"
                        WeekType.ODD_WEEKS -> " (فرد)"
                    }
                    "${ClassSession.getDayName(sess.dayOfWeek)} ${sess.startTime} تا ${sess.endTime}$parity"
                }
            if (preview.isNotBlank()) {
                Text(
                    text = preview.ifBlank { "بدون ساعت کلاسی ثبت‌شده" } +
                        (if (section.instructor.isNotBlank()) "  •  ${section.instructor}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "بدون ساعت کلاسی ثبت‌شده" +
                        (if (section.instructor.isNotBlank()) "  •  ${section.instructor}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                group.verdict == GroupVerdict.COMPATIBLE && group.gapDeltaMinutes > 0 ->
                    Text(
                        text = "افزودن این گروه حدود ${group.gapDeltaMinutes} دقیقه به زمان انتظار اضافه می‌کند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                conflict != null -> Text(
                    text = conflict.descriptionFa,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                group.verdict == GroupVerdict.INCOMPLETE_TIME_DATA -> Text(
                    text = "ساعت کلاسی این گروه ثبت یا کامل نیست؛ سازگاری آن بررسی نمی‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onEnroll,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    if (enabled) "ثبت‌نام در این گروه" else "قابل انتخاب نیست",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GroupVerdictChip(verdict: GroupVerdict) {
    val label = when (verdict) {
        GroupVerdict.COMPATIBLE -> "سازگار"
        GroupVerdict.CONFLICTING -> "دارای تداخل"
        GroupVerdict.INCOMPLETE_TIME_DATA -> "نامشخص"
    }
    val container = when (verdict) {
        GroupVerdict.COMPATIBLE -> MaterialTheme.colorScheme.primaryContainer
        GroupVerdict.CONFLICTING -> MaterialTheme.colorScheme.errorContainer
        GroupVerdict.INCOMPLETE_TIME_DATA -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (verdict) {
        GroupVerdict.COMPATIBLE -> MaterialTheme.colorScheme.onPrimaryContainer
        GroupVerdict.CONFLICTING -> MaterialTheme.colorScheme.onErrorContainer
        GroupVerdict.INCOMPLETE_TIME_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}


/**
 * Empty catalog: nothing can be discovered yet. Points to the real import
 * path instead of faking results.
 */
@Composable
private fun AddCourseEmptyState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = "کاتالوگ درسی برای کشف وجود ندارد",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "ابتدا از بخش تنظیمات، فایل HTML/MHT پرتال یا فایل JSON/CSV را وارد کنید تا درس‌ها برای پیشنهاد آماده شوند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("discovery_open_settings_button")
        ) {
            Text("رفتن به تنظیمات و ورود اطلاعات")
        }
    }
}

/** Search matched nothing: keep the full list one tap away. */
@Composable
private fun AddCourseNoMatchState(onClearSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = "درسی با این مشخصات پیدا نشد",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "عبارت جست‌وجو را تغییر دهید یا آن را پاک کنید تا همه درس‌های قابل افزودن دیده شوند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = onClearSearch,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("نمایش همه درس‌ها", fontWeight = FontWeight.Bold)
        }
    }
}
