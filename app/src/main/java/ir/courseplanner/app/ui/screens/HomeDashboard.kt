package ir.courseplanner.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.engine.ScheduleEngine.UpcomingSession
import ir.courseplanner.app.util.JalaliDate
import java.util.Calendar
import java.util.TimeZone

// ---------------------------------------------------------------------------
// Pure dashboard models: every time-sensitive decision lives here, on explicit
// inputs, so it is unit-testable with a deterministic clock. Composables below
// only render.
// ---------------------------------------------------------------------------

/** Wall-clock snapshot the dashboard derives everything from. */
data class DashboardNow(
    val epochDay: Long,
    val appDayIdx: Int,
    val nowMinutes: Int
)

/**
 * Pure derivation of [DashboardNow] from epoch millis. Midnight, week and
 * day rollover fall out of the inputs — no cached Calendar survives across
 * ticks. [tz] is injectable so tests use a fixed zone.
 */
fun dashboardNow(millis: Long, tz: TimeZone = TimeZone.getDefault()): DashboardNow {
    val cal = Calendar.getInstance(tz).apply { timeInMillis = millis }
    return DashboardNow(
        epochDay = JalaliDate.daysFromCivil(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        ),
        appDayIdx = ScheduleEngine.calendarDayOfWeekToAppDay(cal.get(Calendar.DAY_OF_WEEK)),
        nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    )
}

/** The single "now and next" verdict for the hero card. */
sealed interface NowNextState {
    /** A class is running right now ([minutesLeft] counts the live window). */
    data class Ongoing(val item: UpcomingSession, val minutesLeft: Int) : NowNextState

    /** A later class today ([minutesToStart] > 0). */
    data class UpcomingToday(val item: UpcomingSession, val minutesToStart: Int) : NowNextState

    /** Nothing left today; the next class is on a future day. */
    data class LaterDay(val item: UpcomingSession) : NowNextState

    /** Enrolled, but nothing is scheduled in the coming 7 days. */
    data object NoneScheduled : NowNextState
}

/**
 * Resolves [NowNextState] from the engine's next session. Past sessions are
 * never reported as upcoming: [ScheduleEngine.nextUpcomingSession] only
 * returns sessions ending after now (or future days), and parity filtering
 * already happened upstream — an odd-week session is never "next" in an even
 * week when parity is known.
 */
fun resolveNowNext(
    next: UpcomingSession?,
    todayIdx: Int,
    nowMinutes: Int
): NowNextState {
    if (next == null) return NowNextState.NoneScheduled
    if (next.ongoing) {
        val left = ScheduleEngine.displayEndMinutes(next.session) - nowMinutes
        return NowNextState.Ongoing(next, left.coerceAtLeast(0))
    }
    if (next.dayOfWeek == todayIdx) {
        val toStart = next.session.startMinutes - nowMinutes
        return NowNextState.UpcomingToday(next, toStart.coerceAtLeast(0))
    }
    return NowNextState.LaterDay(next)
}

/** Persian countdown line for an upcoming class today. */
fun countdownFa(minutesToStart: Int): String =
    if (minutesToStart < 1) "کمتر از یک دقیقه تا شروع کلاس"
    else "${JalaliDate.toFaDigits(minutesToStart)} دقیقه تا شروع کلاس"

/**
 * Credit progress 0..1, or null when the target is unset (<= 0): callers must
 * hide the bar instead of dividing by zero or rendering NaN.
 */
fun creditProgressRatio(credits: Int, target: Int): Float? =
    if (target > 0) (credits / target.toFloat()).coerceIn(0f, 1f) else null

/** Human Persian duration for gap minutes. */
fun formatGapFa(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "بدون گپ"
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hours <= 0 -> "${JalaliDate.toFaDigits(mins)} دقیقه"
        mins <= 0 -> "${JalaliDate.toFaDigits(hours)} ساعت"
        else -> "${JalaliDate.toFaDigits(hours)} ساعت و ${JalaliDate.toFaDigits(mins)} دقیقه"
    }
}

/** Gap size that justifies offering the schedule-builder route. */
const val GAP_OPTIMIZE_THRESHOLD_MINUTES = 120

/** One actionable dashboard issue, most severe first. */
enum class AttentionKind {
    CLASS_CONFLICT,
    EXAM_CONFLICT,
    MISSING_SEMESTER_START,
    EXAM_WARNING
}

data class AttentionItem(
    val kind: AttentionKind,
    val title: String,
    val detail: String
)

/**
 * Builds the "needs attention" list from existing engine outputs. Empty when
 * there is nothing meaningful — callers render no card at all then. A missing
 * semester start is reported only for enrolled students: for a fresh user it
 * is not an error, and the guided empty state owns that moment.
 */
fun buildAttentionItems(
    classConflicts: Int,
    examConflicts: Int,
    examWarnings: Int,
    parityKnown: Boolean,
    hasEnrolled: Boolean,
    maxItems: Int = 3
): List<AttentionItem> {
    if (!hasEnrolled) return emptyList()
    val items = mutableListOf<AttentionItem>()
    if (classConflicts > 0) {
        items.add(
            AttentionItem(
                kind = AttentionKind.CLASS_CONFLICT,
                title = "تداخل ساعت کلاسی",
                detail = "${JalaliDate.toFaDigits(classConflicts)} مورد در برنامه ثبت‌شده — روی جدول بررسی کنید."
            )
        )
    }
    if (examConflicts > 0) {
        items.add(
            AttentionItem(
                kind = AttentionKind.EXAM_CONFLICT,
                title = "تداخل امتحان",
                detail = "${JalaliDate.toFaDigits(examConflicts)} امتحان هم‌پوشان — زمان‌بندی را بازبینی کنید."
            )
        )
    }
    if (!parityKnown) {
        items.add(
            AttentionItem(
                kind = AttentionKind.MISSING_SEMESTER_START,
                title = "شروع ترم تنظیم نشده",
                detail = "بدون آن، تشخیص هفته زوج/فرد و کلاس‌های امروز قابل اعتماد نیست."
            )
        )
    }
    if (examWarnings > 0) {
        items.add(
            AttentionItem(
                kind = AttentionKind.EXAM_WARNING,
                title = "ساعت امتحان نامشخص",
                detail = "${JalaliDate.toFaDigits(examWarnings)} امتحان هم‌روز با ساعت ناقص — برنامه امتحانات را چک کنید."
            )
        )
    }
    return items.take(maxItems)
}

// ---------------------------------------------------------------------------
// Dashboard cards (rendering only).
// ---------------------------------------------------------------------------

/** Small section heading with an optional trailing action. */
@Composable
internal fun DashboardSectionHeading(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Primary hero: today's classes plus the single now/next verdict with a live
 * countdown. The next-class block is informational only (never clickable).
 */
@Composable
internal fun NowNextCard(
    todayIdx: Int,
    todayClasses: List<UpcomingSession>,
    state: NowNextState,
    weekLabel: String?,
    nowMinutes: Int,
    onOpenWeeklyTimetable: () -> Unit,
    onManageCourses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_now_next_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "امروز (${ClassSession.getDayName(todayIdx)})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // No chip when parity is unknown: unavailable information
                    // is omitted here and surfaced once in the attention card.
                    if (weekLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = weekLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (todayClasses.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${JalaliDate.toFaDigits(todayClasses.size)} کلاس",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (todayClasses.isEmpty()) {
                Text(
                    text = "امروز کلاسی نداری.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // A fixed clock sample drives every row, so past/ongoing/future
                // can never disagree within one frame.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    todayClasses.forEach { item ->
                        TodayClassRow(item = item, nowMinutes = nowMinutes)
                    }
                }
            }

            NowNextStatusBlock(
                state = state,
                onManageCourses = onManageCourses
            )

            OutlinedButton(
                onClick = onOpenWeeklyTimetable,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_view_weekly_timetable_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "مشاهده برنامه هفتگی",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun TodayClassRow(
    item: UpcomingSession,
    nowMinutes: Int
) {
    val isPast = item.session.endMinutes <= nowMinutes
    val titleColor = if (isPast) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.session.startTime,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isPast) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.width(52.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.section.courseName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.session.location.isNotBlank()) {
                Text(
                    text = item.session.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.session.weekType != WeekType.EVERY_WEEK) {
            Text(
                text = item.session.weekType.titleFa,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun NowNextStatusBlock(
    state: NowNextState,
    onManageCourses: () -> Unit
) {
    when (state) {
        is NowNextState.Ongoing -> {
            StatusPanel(
                status = "در حال برگزاری",
                headline = state.item.section.courseName,
                subline = buildString {
                    val displayEnd = ScheduleEngine.displayEndMinutes(state.item.session)
                    append(state.item.session.startTime)
                    append(" تا ")
                    append("%02d:%02d".format(displayEnd / 60, displayEnd % 60))
                    if (state.item.session.location.isNotBlank()) {
                        append(" • ")
                        append(state.item.session.location)
                    }
                },
                accent = "${JalaliDate.toFaDigits(state.minutesLeft)} دقیقه مانده"
            )
        }
        is NowNextState.UpcomingToday -> {
            StatusPanel(
                status = "کلاس بعدی",
                headline = state.item.section.courseName,
                subline = buildString {
                    append(state.item.session.startTime)
                    append(" تا ")
                    append(state.item.session.endTime)
                    if (state.item.session.location.isNotBlank()) {
                        append(" • ")
                        append(state.item.session.location)
                    }
                },
                accent = countdownFa(state.minutesToStart)
            )
        }
        is NowNextState.LaterDay -> {
            StatusPanel(
                status = "کلاس بعدی",
                headline = state.item.section.courseName,
                subline = buildString {
                    append(ClassSession.getDayName(state.item.dayOfWeek))
                    append(" ")
                    append(state.item.session.startTime)
                    append(" تا ")
                    append(state.item.session.endTime)
                    if (state.item.session.location.isNotBlank()) {
                        append(" • ")
                        append(state.item.session.location)
                    }
                },
                accent = null
            )
        }
        NowNextState.NoneScheduled -> {
            StatusPanel(
                status = "برنامه‌ای ثبت نشده",
                headline = "در ۷ روز آینده کلاسی نداری.",
                subline = "ساعت کلاس‌ها را در مدیریت درس‌ها تکمیل کن.",
                accent = null,
                actionLabel = "تکمیل ساعت کلاس‌ها",
                onAction = onManageCourses
            )
        }
    }
}

@Composable
private fun StatusPanel(
    status: String,
    headline: String,
    subline: String,
    accent: String?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("home_now_next_headline")
                )
                Text(
                    text = subline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (accent != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = accent,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("home_now_next_countdown")
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = onAction) {
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Compact academic overview replacing the four colorful metric cards: one
 * quiet card, credits with a safe progress bar, three plain stats, and an
 * optimization route only when the gap is substantial.
 */
@Composable
internal fun OverviewCard(
    credits: Int,
    target: Int,
    activeDays: Int,
    gapMinutes: Int,
    courseCount: Int,
    onOptimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_overview_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "نمای کلی ترم",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            val ratio = creditProgressRatio(credits, target)
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
                    text = if (target > 0) {
                        "${JalaliDate.toFaDigits(credits)} از ${JalaliDate.toFaDigits(target)} واحد"
                    } else {
                        "${JalaliDate.toFaDigits(credits)} واحد ثبت شده"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("home_overview_credits_value")
                )
            }
            // No bar when the target is unset: a zero target must never render
            // a bogus (NaN) progress value.
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
            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewStat(
                    value = JalaliDate.toFaDigits(activeDays),
                    label = "روز کلاسی",
                    modifier = Modifier.weight(1f)
                )
                OverviewStat(
                    value = formatGapFa(gapMinutes),
                    label = "زمان مرده",
                    modifier = Modifier.weight(1f)
                )
                OverviewStat(
                    value = JalaliDate.toFaDigits(courseCount),
                    label = "درس",
                    modifier = Modifier.weight(1f)
                )
            }
            if (gapMinutes >= GAP_OPTIMIZE_THRESHOLD_MINUTES) {
                OutlinedButton(
                    onClick = onOptimize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_optimize_schedule_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "بازبینی در برنامه‌ساز",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Contextual issues only: renders nothing when the list is empty, so a clean
 * program shows no warning card at all. Each row carries its own icon (never
 * color alone) and a route to the existing workflow that fixes it.
 */
@Composable
internal fun AttentionCard(
    items: List<AttentionItem>,
    onAction: (AttentionKind) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_attention_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "نیاز به توجه",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            items.forEach { item ->
                AttentionRow(
                    item = item,
                    onAction = { onAction(item.kind) }
                )
            }
        }
    }
}

@Composable
private fun AttentionRow(
    item: AttentionItem,
    onAction: () -> Unit
) {
    val icon: ImageVector = when (item.kind) {
        AttentionKind.CLASS_CONFLICT -> Icons.Default.Schedule
        AttentionKind.EXAM_CONFLICT -> Icons.Default.Event
        AttentionKind.MISSING_SEMESTER_START -> Icons.Default.Settings
        AttentionKind.EXAM_WARNING -> Icons.Default.WarningAmber
    }
    val actionLabel: String = when (item.kind) {
        AttentionKind.CLASS_CONFLICT -> "مشاهده جدول"
        AttentionKind.EXAM_CONFLICT -> "مشاهده امتحانات"
        AttentionKind.MISSING_SEMESTER_START -> "تنظیم در تنظیمات"
        AttentionKind.EXAM_WARNING -> "مشاهده امتحانات"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onAction,
                modifier = Modifier.testTag("home_attention_action_${item.kind.name}")
            ) {
                Text(actionLabel, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
