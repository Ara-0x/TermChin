package ir.courseplanner.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.engine.ScheduleEngine.UpcomingSession
import ir.courseplanner.app.ui.theme.TermChinDesign
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

/** Persian countdown line for an upcoming class today. Reuses [formatGapFa]
 * so a long wait reads as «۱ ساعت و ۳۰ دقیقه تا شروع کلاس» — one line, one
 * hours+minutes pair, never a bare minute count that duplicates a second row. */
fun countdownFa(minutesToStart: Int): String =
    if (minutesToStart < 1) "کمتر از یک دقیقه تا شروع کلاس"
    else "${formatGapFa(minutesToStart)} تا شروع کلاس"

/**
 * Persian countdown for a class that is running right now. The wording is
 * explicit about direction — the number counts down to the END of the class,
 * not its start — so «۱ ساعت و ۱۰ دقیقه تا پایان کلاس» cannot be misread.
 */
fun ongoingRemainingFa(minutesLeft: Int): String =
    if (minutesLeft < 1) "لحظه‌های پایانی کلاس"
    else "${formatGapFa(minutesLeft)} تا پایان کلاس"

/**
 * Warm, time-aware greeting from real device time (minutes since midnight).
 * Boundaries are explicit and unit-tested; blank names keep the brand title.
 */
fun greetingFa(nowMinutes: Int, name: String): String {
    val period = when (nowMinutes) {
        in 300..719 -> "صبح بخیر" // 05:00–11:59
        in 720..1019 -> "ظهر بخیر" // 12:00–16:59
        in 1020..1199 -> "عصر بخیر" // 17:00–19:59
        else -> "شب بخیر" // night and early dawn
    }
    return if (name.isNotBlank()) "$period، $name" else "ترم‌چین"
}

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

/**
 * Shared icon + title header for dashboard sections. The icon is decorative
 * (the title carries the meaning) so it stays silent for accessibility.
 */
@Composable
fun DashboardSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(TermChinDesign.IconMd)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/** Stable identity for matching the focused session inside today's list. */
internal fun focusedSessionKey(item: UpcomingSession): String =
    item.section.course.code + "|" + item.section.section.sectionCode + "|" +
        item.session.dayOfWeek.toString() + "|" + item.session.startTime + "|" +
        item.session.endTime



/**
 * One integrated Today card: every class today in a single timeline; the
 * focused class (ongoing/next) is emphasized inline and shown exactly once.
 */
@Composable
internal fun NowNextCard(
    todayIdx: Int,
    todayClasses: List<UpcomingSession>,
    state: NowNextState,
    nowMinutes: Int,
    onOpenWeeklyTimetable: () -> Unit,
    onManageCourses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_now_next_card"),
        shape = TermChinDesign.CardShape,
        // The only persistent primary surface on Home: it answers the
        // student's first question — what do I need to do today?
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardSectionHeader(title = "امروز چه خبر؟", icon = Icons.Default.Schedule)
            when (state) {
                is NowNextState.Ongoing ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TodayTimeline(
                            todayClasses = todayClasses,
                            focusedKey = focusedSessionKey(state.item),
                            nowMinutes = nowMinutes,
                            focusedIsOngoing = true,
                            statusLabelFor = { item ->
                                if (focusedSessionKey(item) == focusedSessionKey(state.item)) {
                                    "در حال برگزاری"
                                } else {
                                    null
                                }
                            },
                            statusFor = { item ->
                                if (focusedSessionKey(item) == focusedSessionKey(state.item)) {
                                    ongoingRemainingFa(state.minutesLeft)
                                } else {
                                    null
                                }
                            }
                        )
                        TimetableRouteButton(onOpenWeeklyTimetable)
                    }
                is NowNextState.UpcomingToday -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TodayTimeline(
                            todayClasses = todayClasses,
                            focusedKey = focusedSessionKey(state.item),
                            nowMinutes = nowMinutes,
                            statusLabelFor = { item ->
                                if (focusedSessionKey(item) == focusedSessionKey(state.item)) {
                                    "کلاس بعدی"
                                } else {
                                    null
                                }
                            },
                            statusFor = { item ->
                                if (focusedSessionKey(item) == focusedSessionKey(state.item)) {
                                    countdownFa(state.minutesToStart)
                                } else {
                                    null
                                }
                            }
                        )
                        TimetableRouteButton(onOpenWeeklyTimetable)
                    }
                }
                is NowNextState.LaterDay -> {
                    Text(
                        text = "کلاس‌های امروز تمام شد",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    FocusedClassRow(
                        item = state.item,
                        status = "کلاس بعدی: " + ClassSession.getDayName(state.item.dayOfWeek),
                        nowMinutes = null
                    )
                    OutlinedButton(
                        onClick = onOpenWeeklyTimetable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_view_weekly_timetable_button")
                    ) {
                        Text("مشاهده برنامه هفتگی")
                    }
                }
                NowNextState.NoneScheduled -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (todayClasses.isEmpty()) {
                            Text(
                                text = "امروز کلاسی نداری.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(TermChinDesign.IconMd)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "کلاسی در ۷ روز آینده ثبت نشده است",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onManageCourses,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تکمیل ساعت کلاس‌ها")
                        }
                    }
                }
            }
        }
    }
}
/**
 * Contextual route to the full weekly timetable below. Rendered once per hero
 * state (branches are exclusive), so the tag stays unique on screen.
 */
@Composable
private fun TimetableRouteButton(onOpenWeeklyTimetable: () -> Unit) {
    OutlinedButton(
        onClick = onOpenWeeklyTimetable,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_view_weekly_timetable_button")
    ) {
        Text("مشاهده برنامه هفتگی")
    }
}

/**
 * Plain HH:mm range for one session, e.g. 08:00-10:00.
 */
private fun timeRangeFa(session: ClassSession): String =
    session.startTime + " – " + session.endTime

/**
 * Live progress of the teaching window (start .. display end). Driven by the
 * fixed clock sample of the current frame.
 */
@Composable
private fun SessionProgress(nowMinutes: Int, session: ClassSession) {
    val end = ScheduleEngine.displayEndMinutes(session)
    val span = (end - session.startMinutes).coerceAtLeast(1)
    val done = (nowMinutes - session.startMinutes).coerceIn(0, span)
    LinearProgressIndicator(
        progress = { done / span.toFloat() },
        modifier = Modifier.fillMaxWidth(),
        strokeCap = StrokeCap.Round
    )
}

/**
 * Single timeline: every class today in start-time order; the focused class
 * is emphasized inline (accent bar + status) and appears exactly once.
 */
@Composable
private fun TodayTimeline(
    todayClasses: List<UpcomingSession>,
    focusedKey: String,
    nowMinutes: Int,
    statusFor: (UpcomingSession) -> String?,
    statusLabelFor: (UpcomingSession) -> String? = { null },
    focusedIsOngoing: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        todayClasses.sortedBy { it.session.startMinutes }.forEach { item ->
            val status = statusFor(item)
            if (focusedSessionKey(item) == focusedKey && status != null) {
                FocusedClassRow(
                    item = item,
                    status = status,
                    statusLabel = statusLabelFor(item),
                    isOngoing = focusedIsOngoing,
                    nowMinutes = nowMinutes
                )
            } else {
                PlainTimelineRow(item = item, nowMinutes = nowMinutes)
            }
        }
    }
}

@Composable
private fun FocusedClassRow(
    item: UpcomingSession,
    status: String,
    statusLabel: String? = null,
    isOngoing: Boolean = false,
    nowMinutes: Int?
) {
    // An ongoing class gets a warmer accent than a merely-upcoming one, so
    // "happening now" is distinguishable from "next" before reading a word.
    val accent = if (isOngoing) MaterialTheme.colorScheme.tertiary
                 else MaterialTheme.colorScheme.primary
    val accentContainer = if (isOngoing) MaterialTheme.colorScheme.tertiaryContainer
                          else MaterialTheme.colorScheme.primaryContainer
    val onAccentContainer = if (isOngoing) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = TermChinDesign.RowShape,
        color = accentContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = item.section.courseName + ". " + status }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(TermChinDesign.FocusBarWidth)
                    .height(48.dp)
                    .clip(TermChinDesign.MicroShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (statusLabel != null) {
                    StatusPill(label = statusLabel, ongoing = isOngoing)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = item.section.courseName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("home_now_next_headline")
                )
                Text(
                    text = timeRangeFa(item.session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.session.location.isNotBlank()) {
                    Text(
                        text = item.session.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                CountdownBadge(
                    text = status,
                    containerColor = accentContainer,
                    contentColor = onAccentContainer
                )
                if (nowMinutes != null) {
                    SessionProgress(nowMinutes = nowMinutes, session = item.session)
                }
            }
        }
    }
}

/**
 * Small status chip («در حال برگزاری» / «کلاس بعدی») with a leading dot. The
 * dot is decorative emphasis; the label carries the meaning. Ongoing uses a
 * warmer accent so "now" reads differently from "next" at a glance.
 */
@Composable
private fun StatusPill(label: String, ongoing: Boolean) {
    val accent = if (ongoing) MaterialTheme.colorScheme.tertiary
                 else MaterialTheme.colorScheme.primary
    Surface(shape = TermChinDesign.PillShape, color = accent.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1
            )
        }
    }
}

/**
 * Countdown rendered as a clock-prefixed chip so it reads as a live timer
 * rather than static metadata. Owns the `home_now_next_countdown` tag.
 */
@Composable
private fun CountdownBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(shape = TermChinDesign.PillShape, color = containerColor.copy(alpha = 0.55f)) {
        Row(
            modifier = Modifier
                .padding(horizontal = 9.dp, vertical = 4.dp)
                .testTag("home_now_next_countdown"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(TermChinDesign.IconSm)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlainTimelineRow(
    item: UpcomingSession,
    nowMinutes: Int
) {
    val dimmed = item.session.endMinutes <= nowMinutes
    val alpha = if (dimmed) 0.55f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(56.dp)
        ) {
            Text(
                text = JalaliDate.toFaDigits(item.session.startTime),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1
            )
            Text(
                text = JalaliDate.toFaDigits(item.session.endTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(36.dp)
                .clip(TermChinDesign.MicroShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.section.courseName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = buildString {
                if (item.session.location.isNotBlank()) append(item.session.location)
                if (item.session.weekType != WeekType.EVERY_WEEK) {
                    if (isNotEmpty()) append(" \u2022 ")
                    append(if (item.session.weekType == WeekType.ODD_WEEKS) "هفته فرد" else "هفته زوج")
                }
            }
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
        shape = TermChinDesign.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "نمای کلی ترم",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
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
                        .clip(TermChinDesign.MicroShape),
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
                    shape = TermChinDesign.ControlShape
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(TermChinDesign.IconSm)
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
        shape = TermChinDesign.CardShape,
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(TermChinDesign.IconMd)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "نیاز به توجه",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.semantics { heading() }
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
        shape = TermChinDesign.RowShape,
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
                    modifier = Modifier.size(TermChinDesign.IconMd)
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
                modifier = Modifier
                    .heightIn(min = TermChinDesign.MinTouchHeight)
                    .testTag("home_attention_action_${item.kind.name}")
            ) {
                Text(actionLabel, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
