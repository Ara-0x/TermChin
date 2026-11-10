package ir.courseplanner.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.data.preferences.TimetableDensity
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.ui.theme.CourseColorListDark
import ir.courseplanner.app.ui.theme.CourseColorListLight
import ir.courseplanner.app.ui.theme.TermChinDesign

data class TimetableItem(
    val section: SectionWithDetails,
    val session: ClassSession,
    val color: Color
)

/** Uniform 2-hour university blocks used by the weekly calendar. */
internal val TimetableTwoHourSlots = listOf(
    "08:00" to "10:00",
    "10:00" to "12:00",
    "12:00" to "14:00",
    "14:00" to "16:00",
    "16:00" to "18:00",
    "18:00" to "20:00"
)

@Composable
fun WeeklyTimetable(
    sections: List<SectionWithDetails>,
    modifier: Modifier = Modifier,
    initialDay: Int = 0,
    /** Day to mark as «today» in the dashboard; omitted in the builder preview. */
    highlightedDay: Int? = null,
    /**
     * Epoch day that [highlightedDay] represents, used to print Jalali day
     * numbers under the weekday names. Home passes the same clock it uses for
     * the rest of the dashboard so the strip cannot disagree with «today» after
     * midnight. Callers that do not show «today» (builder preview) keep the
     * real clock as a harmless default.
     */
    todayEpochDay: Long = ir.courseplanner.app.util.JalaliDate.todayEpochDay(),
    showThursday: Boolean = true,
    density: TimetableDensity = TimetableDensity.STANDARD,
    /**
     * Academic week parity right now (null when the semester start is unset).
     * Sessions that do not meet this week are dimmed, never hidden.
     */
    currentParity: ScheduleEngine.WeekParity? = null,
    /**
     * Set false when the caller prints the section title itself above the card
     * (Home does, so the same section is never named twice: once outside the
     * card and once inside it).
     */
    showHeader: Boolean = true,
    /**
     * Optional hook that opens the attendance sheet for a class occurrence.
     * Null keeps the timetable read-only (previews/tests) and hides the button.
     */
    onOpenAttendance: ((TimetableItem) -> Unit)? = null
) {
    // A six-day grid needs horizontal scrolling on a phone. Start with the
    // focused day view there, while retaining the full grid as one tap away.
    val isCompactScreen = LocalConfiguration.current.screenWidthDp < 600
    var selectedDay by remember(initialDay) { mutableStateOf(initialDay) }
    // Keep the internal selection in sync when the caller moves the focused
    // day (e.g. midnight rollover on Home) — otherwise the strip and the
    // day view below could disagree on first paint.
    LaunchedEffect(initialDay) {
        selectedDay = initialDay
    }
    var viewMode by remember(isCompactScreen) {
        mutableStateOf(if (isCompactScreen) "day" else "grid")
    } // "grid" or "day"
    var inspectItem by remember { mutableStateOf<TimetableItem?>(null) }

    // Map each course code to a stable distinct color. Memoized so large
    // timetables are not rebuilt on every recomposition.
    val isDark = ir.courseplanner.app.ui.theme.isDarkTheme()
    val fallbackColor = MaterialTheme.colorScheme.secondary
    val allItems = remember(sections, isDark, fallbackColor) {
        val courseColors = if (isDark) CourseColorListDark else CourseColorListLight
        val uniqueCodes = sections.map { it.courseCode }.distinct()
        val colorMap = uniqueCodes.mapIndexed { idx, code ->
            code to courseColors[idx % courseColors.size]
        }.toMap()
        sections.flatMap { sec ->
            sec.sessions.map { sess ->
                TimetableItem(
                    section = sec,
                    session = sess,
                    color = colorMap[sec.courseCode] ?: fallbackColor
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Keep orientation and the view switch on separate rows. On a phone,
        // this avoids squeezing Persian labels into a fragile single line.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (showHeader) 8.dp else 10.dp)
        ) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                }
            }

            Surface(
                shape = TermChinDesign.ControlShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    val isGrid = viewMode == "grid"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(TermChinDesign.MicroShape)
                            .background(
                                if (isGrid) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable(role = Role.RadioButton) { viewMode = "grid" }
                            .heightIn(min = TermChinDesign.MinTouchHeight)
                            .testTag("weekly_timetable_mode_grid"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarViewWeek,
                                contentDescription = null,
                                tint = if (isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "نمای کل هفته",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isGrid) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isGrid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(TermChinDesign.MicroShape)
                            .background(
                                if (!isGrid) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable(role = Role.RadioButton) { viewMode = "day" }
                            .heightIn(min = TermChinDesign.MinTouchHeight)
                            .testTag("weekly_timetable_mode_day"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarViewDay,
                                contentDescription = null,
                                tint = if (!isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تفکیک روزانه",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (!isGrid) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (!isGrid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (currentParity != null) {
            Spacer(modifier = Modifier.height(8.dp))
            // One compact status line instead of a sentence that wrapped and
            // left a single orphan word on narrow phones. The parity is the
            // fact worth reading; the explanation is deliberately quiet.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = TermChinDesign.PillShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "هفتهٔ ${currentParity.titleFa}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = "کلاس‌های کم‌رنگ این هفته برگزار نمی‌شوند",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (sections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(TermChinDesign.CardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), TermChinDesign.CardShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "هنوز کلاسی برای نمایش در جدول هفتگی انتخاب نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TimetableMode"
            ) { mode ->
                if (mode == "grid") {
                    TimetableGridView(
                        items = allItems,
                        showThursday = showThursday,
                        density = density,
                        currentParity = currentParity,
                        highlightedDay = highlightedDay,
                        onItemClick = { inspectItem = it }
                    )
                } else {
                    TimetableDayView(
                        items = allItems,
                        selectedDay = selectedDay,
                        highlightedDay = highlightedDay,
                        todayEpochDay = todayEpochDay,
                        showThursday = showThursday,
                        currentParity = currentParity,
                        onDayChange = { selectedDay = it },
                        onItemClick = { inspectItem = it }
                    )
                }
            }
        }
    }

    // Polished Detail dialog when clicking a class card
    inspectItem?.let { item ->
        AlertDialog(
            onDismissRequest = { inspectItem = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(item.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.section.courseName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = TermChinDesign.ControlShape,
                        color = item.color.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, item.color.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "کد درس: ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.section.courseCode)}  •  تعداد واحد: ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.section.credits)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "گروه: ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.section.sectionCode)}  •  استاد: ${item.section.instructor.ifBlank { "نامشخص" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = item.color, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "زمان کلاس: ${ClassSession.getDayName(item.session.dayOfWeek)} ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.session.startTime)} تا ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.session.endTime)}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (item.session.weekType != WeekType.EVERY_WEEK) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(TermChinDesign.MicroShape)
                                        .background(item.color.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "برگزاری: ${item.session.weekType.titleFa}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = item.color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (item.session.location.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "محل تشکیل: ${item.session.location}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (item.section.examDate.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تاریخ امتحان: ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.section.examDate)}  (${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.section.examTimeRange)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (onOpenAttendance != null) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    inspectItem = null
                                    onOpenAttendance(item)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TermChinDesign.MinTouchHeight),
                                shape = TermChinDesign.RowShape
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(TermChinDesign.IconSm)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ثبت حضور / تاریخچه", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectItem = null }) {
                    Text("بستن", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun TimetableGridView(
    items: List<TimetableItem>,
    showThursday: Boolean = true,
    density: TimetableDensity = TimetableDensity.STANDARD,
    currentParity: ScheduleEngine.WeekParity? = null,
    highlightedDay: Int? = null,
    onItemClick: (TimetableItem) -> Unit
) {
    val hasThursdayClasses = items.any { it.session.dayOfWeek == 5 }
    val days = if (showThursday || hasThursdayClasses) (0..5).toList() else (0..4).toList()
    val scrollState = rememberScrollState()

    val timeSlots = TimetableTwoHourSlots

    val baseCellHeight = when (density) {
        TimetableDensity.COMPACT -> 76.dp
        TimetableDensity.STANDARD -> 92.dp
        TimetableDensity.SPACIOUS -> 110.dp
    }

    // One height per 2-hour row, shared by the pinned hour gutter and the
    // scrolling day columns so the two can never drift apart.
    val slotHeights = timeSlots.map { (slotStart, slotEnd) ->
        val slotStartMin = ClassSession.timeToMinutes(slotStart)
        val slotEndMin = ClassSession.timeToMinutes(slotEnd)
        val hasMultipleInSlot = days.any { dayIdx ->
            items.count { item ->
                item.session.dayOfWeek == dayIdx &&
                    item.session.startMinutes >= slotStartMin &&
                    item.session.startMinutes < slotEndMin
            } > 1
        }
        if (hasMultipleInSlot) baseCellHeight + 28.dp else baseCellHeight
    }

    val timeColumnWidth = 76.dp
    val dayColumnWidth = 126.dp
    val headerHeight = 38.dp
    val rowGap = 3.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // The hour gutter is pinned: it stays readable while the day
            // columns scroll, which on a phone is barely two columns wide.
            Column {
                Box(
                    modifier = Modifier
                        .width(timeColumnWidth)
                        .height(headerHeight)
                        .clip(TermChinDesign.ChipShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ساعت",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                timeSlots.forEachIndexed { index, (slotStart, slotEnd) ->
                    Box(
                        modifier = Modifier
                            .width(timeColumnWidth)
                            .height(slotHeights[index] + rowGap * 2)
                            .padding(vertical = rowGap)
                            .clip(TermChinDesign.ChipShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = ir.courseplanner.app.util.JalaliDate.toFaDigits(slotStart),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            Text(
                                text = "تا",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            Text(
                                text = ir.courseplanner.app.util.JalaliDate.toFaDigits(slotEnd),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .testTag("weekly_timetable_grid")
            ) {
                // Header Row: Days of week
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    days.forEach { dayIdx ->
                        val isToday = highlightedDay == dayIdx
                        Box(
                            modifier = Modifier
                                .width(dayColumnWidth)
                                .height(headerHeight)
                                .padding(horizontal = 3.dp)
                                .clip(TermChinDesign.ChipShape)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ClassSession.getDayName(dayIdx),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold
                                ),
                                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }

                // Time Slots Rows
                timeSlots.forEachIndexed { index, (slotStart, slotEnd) ->
                    val slotStartMin = ClassSession.timeToMinutes(slotStart)
                    val slotEndMin = ClassSession.timeToMinutes(slotEnd)

                    Row(modifier = Modifier.padding(vertical = rowGap)) {
                        // Cells for each day
                        days.forEach { dayIdx ->
                            val matchingItems = items.filter { item ->
                                item.session.dayOfWeek == dayIdx &&
                                    item.session.startMinutes >= slotStartMin &&
                                    item.session.startMinutes < slotEndMin
                            }

                            Box(
                                modifier = Modifier
                                    .width(dayColumnWidth)
                                    .height(slotHeights[index])
                                    .padding(horizontal = 3.dp)
                                    .clip(TermChinDesign.ChipShape)
                                    .background(
                                        if (matchingItems.isNotEmpty()) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (matchingItems.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(3.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        matchingItems.forEach { item ->
                                            ClassBlock(
                                                item = item,
                                                onClick = { onItemClick(item) },
                                                isMultiple = matchingItems.size > 1,
                                                dimmed = !ScheduleEngine.occursInWeek(
                                                    item.session,
                                                    currentParity
                                                )
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

@Composable
private fun ClassBlock(
    item: TimetableItem,
    onClick: () -> Unit,
    isMultiple: Boolean = false,
    dimmed: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.45f else 1f)
            .semantics {
                contentDescription = "${item.section.courseName}، ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.session.startTime)} تا ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.session.endTime)}"
            }
            .clickable { onClick() },
        shape = TermChinDesign.ChipShape,
        color = item.color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, item.color.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = if (isMultiple) 3.dp else 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.section.courseName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isMultiple) 9.5.sp else 10.5.sp
                ),
                color = item.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                softWrap = false
            )
            if (!isMultiple && item.session.location.isNotBlank()) {
                Text(
                    text = item.session.location,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    softWrap = false
                )
            }
            val weekSuffix = when (item.session.weekType) {
                WeekType.EVEN_WEEKS -> " (زوج)"
                WeekType.ODD_WEEKS -> " (فرد)"
                else -> ""
            }
            Text(
                text = "${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.session.startTime)} تا ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.session.endTime)}$weekSuffix",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                softWrap = false
            )
        }
    }
}

@Composable
private fun WeeklyDateStrip(
    items: List<TimetableItem>,
    selectedDay: Int,
    highlightedDay: Int?,
    todayEpochDay: Long,
    days: List<Int>,
    onDayChange: (Int) -> Unit
) {
    // Class counts come from the SAME timetable items the day view renders —
    // no new data source, no duplication.
    val countByDay = remember(items) {
        items.groupingBy { it.session.dayOfWeek }.eachCount()
    }
    val accent = MaterialTheme.colorScheme.primary
    // All six days stay visible. This used to be a LazyRow, which on a 360dp
    // phone left the last cell cut in half mid-word (and the clip moved with
    // the selection). The cells now shrink to whatever width the screen gives
    // them instead of scrolling — the week is a fixed, glanceable row.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_timetable_date_strip")
    ) {
        val gap = 6.dp
        val cellWidth = ((maxWidth - gap * (days.size - 1)) / days.size)
            .coerceAtMost(TermChinDesign.DateCellWidth)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            days.forEach { dayIdx ->
                WeeklyDateCell(
                    dayIdx = dayIdx,
                    count = countByDay[dayIdx] ?: 0,
                    isSelected = selectedDay == dayIdx,
                    isToday = highlightedDay == dayIdx,
                    highlightedDay = highlightedDay,
                    todayEpochDay = todayEpochDay,
                    accent = accent,
                    width = cellWidth,
                    onDayChange = onDayChange
                )
            }
        }
    }
}

@Composable
private fun WeeklyDateCell(
    dayIdx: Int,
    count: Int,
    isSelected: Boolean,
    isToday: Boolean,
    highlightedDay: Int?,
    todayEpochDay: Long,
    accent: Color,
    width: Dp,
    onDayChange: (Int) -> Unit
) {
    // Narrow cells (360dp phones) drop the horizontal padding and use a
    // slightly smaller day name so «چهارشنبه» still fits without an ellipsis.
    val compact = width < 52.dp
    // A flat, soft-purple wash plus a heavier border makes the selected day the
    // obvious one whose class list is rendered below. Flat washes only — never
    // a gradient or glow — so every saved theme stays visually quiet.
    val cellBg = if (isSelected) {
        SolidColor(MaterialTheme.colorScheme.primaryContainer)
    } else {
        SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    }
    val cellBorder = if (isSelected) {
        accent
    } else if (isToday) {
        accent.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    // Jalali day-of-month badge for the academic week row, derived from
    // today ± offset through the real Jalali calendar (never invented).
    // Every cell resolves its own offset from the highlighted today index.
    val jalaliDay = remember(dayIdx, highlightedDay, todayEpochDay) {
        jalaliDayBadge(dayIdx = dayIdx, todayIdx = highlightedDay, todayEpochDay = todayEpochDay)
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(TermChinDesign.DateCellHeight)
            // Selection is carried by background + border, not elevation.
            .clip(TermChinDesign.PillShape)
            .background(cellBg)
            .border(if (isSelected) 2.dp else 1.dp, cellBorder, TermChinDesign.PillShape)
            .clickable(
                onClickLabel = ClassSession.getDayName(dayIdx),
                onClick = { onDayChange(dayIdx) }
            )
            .testTag("weekly_timetable_day_$dayIdx")
            .semantics {
                contentDescription = buildString {
                    append(ClassSession.getDayName(dayIdx))
                    if (isToday) append("، امروز")
                    if (count > 0) append("، $count کلاس")
                    if (isSelected) append("، انتخاب شده")
                }
            }
            .padding(vertical = 6.dp, horizontal = if (compact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        WeeklyDateCellContent(
            dayIdx = dayIdx,
            count = count,
            isSelected = isSelected,
            isToday = isToday,
            accent = accent,
            jalaliDay = jalaliDay,
            compact = compact
        )
    }
}

/**
 * Inner content of one date cell: weekday name, Jalali day number and a single
 * accent dot next to the class count. Kept separate so the cell container stays
 * readable and the content can evolve without touching selection logic.
 */
@Composable
private fun WeeklyDateCellContent(
    dayIdx: Int,
    count: Int,
    isSelected: Boolean,
    isToday: Boolean,
    accent: Color,
    jalaliDay: String,
    compact: Boolean
) {
    val cellContent = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val cellSubtle = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = ClassSession.getDayName(dayIdx),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected || isToday) FontWeight.Bold
                else FontWeight.Medium,
                fontSize = if (compact) 9.5.sp else 11.sp
            ),
            color = cellContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
        if (jalaliDay.isNotBlank()) {
            Text(
                text = jalaliDay,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = cellContent,
                maxLines = 1
            )
        }
        if (count > 0) {
            // One dot + the count, on a single line. The previous layout stacked
            // three dots above the count text, which overflowed the 72dp cell and
            // clipped the only part that said how many classes there are.
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(TermChinDesign.DateDotSize)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) accent
                            else accent.copy(alpha = 0.75f)
                        )
                )
                Text(
                    text = if (count == 1) "۱ کلاس"
                    else "${ir.courseplanner.app.util.JalaliDate.toFaDigits(count)} کلاس",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp
                    ),
                    color = cellSubtle,
                    maxLines = 1,
                    softWrap = false
                )
            }
        // Free day: nothing is printed under the day number. The missing count
        // already communicates "no classes today".
        }
    }
}

/**
 * Jalali day-of-month label for a strip cell: offset from today's academic
 * weekday ([todayIdx]) to the cell's [dayIdx], resolved through the real
 * Jalali calendar. Blank when today is unknown (builder preview) — never
 * crashes, never invents data.
 */
private fun jalaliDayBadge(dayIdx: Int, todayIdx: Int?, todayEpochDay: Long): String {
    if (todayIdx == null) return ""
    // The academic week runs Saturday(0)..Thursday(5) with strictly increasing
    // dates, so a cell's day-of-month is simply the Saturday anchor plus its
    // weekday index. The previous ±3 wrap made the strip non-monotonic
    // (…۲۵، ۲۶، ۲۷، ۲۱، ۲۲); anchoring the whole week removes that.
    val saturdayEpoch = if (todayIdx <= 5) todayEpochDay - todayIdx else todayEpochDay + 1
    val resolved = ir.courseplanner.app.util.JalaliDate.fromEpochDay(saturdayEpoch + dayIdx)
        ?: return ""
    return ir.courseplanner.app.util.JalaliDate.toFaDigits(resolved.day)
}

@Composable
private fun TimetableDayView(
    items: List<TimetableItem>,
    selectedDay: Int,
    highlightedDay: Int? = null,
    todayEpochDay: Long,
    showThursday: Boolean = true,
    currentParity: ScheduleEngine.WeekParity? = null,
    onDayChange: (Int) -> Unit,
    onItemClick: (TimetableItem) -> Unit
) {
    val hasThursdayClasses = items.any { it.session.dayOfWeek == 5 }
    val days = if (showThursday || hasThursdayClasses) (0..5).toList() else (0..4).toList()
    val effectiveSelectedDay = if (selectedDay in days) selectedDay else days.first()
    val dayItems = items
        .filter { it.session.dayOfWeek == effectiveSelectedDay }
        .sortedBy { it.session.startMinutes }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Premium horizontal weekly date strip (Linear/Raycast-inspired):
        // same selection model as before (selectedDay + highlightedDay),
        // same testTags, same class-count source — only the presentation is
        // elevated: date cells with day name + count, clear selected state,
        // class indicator dots, and smooth animated scrolling.
        WeeklyDateStrip(
            items = items,
            // Show the day whose list is actually rendered. On a Friday (or
            // when Thursday is hidden) `selectedDay` falls outside the visible
            // week, and the strip would otherwise show no selected cell at all
            // while the list below showed Saturday.
            selectedDay = effectiveSelectedDay,
            highlightedDay = highlightedDay,
            todayEpochDay = todayEpochDay,
            days = days,
            onDayChange = onDayChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (dayItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(TermChinDesign.CardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), TermChinDesign.CardShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "این روز تعطیل است",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "روز استراحت و مطالعه",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Flat list (dayItems is already sorted by start): every class
                // renders EXACTLY ONCE, with its real time range on the badge.
                // The old per-bucket overlap match repeated a long class —
                // «14:00 تا 17:00» was painted in BOTH the 14–16 and the 16–18
                // buckets — because buckets are a display grid, not session
                // boundaries.
                dayItems.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(
                                    if (ScheduleEngine.occursInWeek(item.session, currentParity)) 1f
                                    else 0.45f
                                )
                                .clickable { onItemClick(item) },
                            shape = TermChinDesign.CardShape,
                            color = MaterialTheme.colorScheme.surface,
                            // One border weight everywhere (1.dp). The old 1.2.dp
                            // read as a visual offset next to the other cards.
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                item.color.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left accent color bar
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(48.dp)
                                        .clip(TermChinDesign.MicroShape)
                                        .background(item.color)
                                )
                                Spacer(modifier = Modifier.width(12.dp))

                                // Course info
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.section.courseName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                            softWrap = false
                                        )
                                        if (item.session.weekType != WeekType.EVERY_WEEK) {
                                            Surface(
                                                shape = TermChinDesign.MicroShape,
                                                color = item.color.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = item.session.weekType.titleFa,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                    color = item.color,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = item.section.instructor.ifBlank { "استاد نامشخص" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = false
                                            )
                                        }
                                        if (item.session.location.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Icon(
                                                    Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = item.session.location,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Time badge on right
                                Surface(
                                    shape = TermChinDesign.ControlShape,
                                    color = item.color.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, item.color.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = item.color
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.session.startTime)} تا ${ir.courseplanner.app.util.JalaliDate.toFaDigits(item.session.endTime)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = item.color,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false
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

