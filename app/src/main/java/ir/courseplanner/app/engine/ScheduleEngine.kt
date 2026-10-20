package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Conflict
import ir.courseplanner.app.data.model.ConflictType
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType

enum class OptimizationPreference(val titleFa: String, val descriptionFa: String) {
    BALANCED("بهترین توازن کلی", "ترکیب بهینه حداقل روز و کمترین فاصله خالی"),
    MIN_GAPS("حداقل گپ و زمان مرده", "کلاس‌های پشت سر هم با کمترین زمان انتظار در دانشگاه"),
    MIN_DAYS("فشرده‌ترین روزها", "کمترین تعداد روزهای حضور در دانشگاه"),
    NO_EARLY_MORNING("شروع دیرتر (بدون ۸ صبح)", "اولویت با برنامه‌هایی که کلاس ۸ صبح ندارند")
}

data class ScoredSchedule(
    val schedule: List<SectionWithDetails>,
    /** Final user-facing score: [rawScore] clamped into 0..100 (never faked). */
    val score: Int,
    val totalGapMinutes: Int,
    /**
     * DISPLAY metric: distinct university days in the week, counting the union
     * of odd and even weeks. A day that only has an odd-week class still counts,
     * because the student has to keep that day free. See [ScheduleEngine.weeklyActiveDays]
     * for the parity-aware figure the SCORE is computed from.
     */
    val activeDaysCount: Int,
    /**
     * DISPLAY metric: sessions starting at/before 08:00 (union of parities).
     * See [ScheduleEngine.weeklyEarlyMorningCount] for the scoring figure.
     */
    val earlyMorningClassCount: Int,
    val totalWeeklyHours: Float,
    val tags: List<String>,
    /**
     * Explainable parts: the +100 base, the weighted deductions and — only when
     * the score had to be limited — an explicit clamp row. The deltas always add
     * up to [score], so the breakdown can never contradict the headline number.
     */
    val breakdown: List<ScoreComponent> = emptyList(),
    /**
     * Unclamped score. Invariant: `breakdown.sumOf { it.delta } == rawScore`,
     * and [score] is exactly [rawScore] limited to 0..100.
     */
    val rawScore: Int = score,
    /**
     * SCORING metric: weekly-average attendance days. A day whose only classes
     * are biweekly (odd-only or even-only) is attended every other week and
     * therefore counts 0.5 instead of 1.0.
     */
    val weeklyActiveDays: Double = activeDaysCount.toDouble(),
    /** SCORING metric: weekly-average number of early sessions (biweekly = 0.5). */
    val weeklyEarlyMorningCount: Double = earlyMorningClassCount.toDouble()
)

/** One explainable line of the quality score. [delta] is negative for deductions. */
data class ScoreComponent(
    val labelFa: String,
    val delta: Int
)

/** One selected course as seen by the generator (name kept for user-facing reports). */
data class GeneratorCourse(
    val courseName: String,
    val sections: List<SectionWithDetails>
)

/** Full outcome of a generation run — never silently drops a course. */
data class ScheduleSearchResult(
    /** Top-K schedules, best first. */
    val ranked: List<ScoredSchedule>,
    /** Every valid combination found (before the top-K cut). */
    val totalValid: Int,
    /** True when the leaf cap stopped the search early (best-so-far is kept). */
    val truncated: Boolean,
    /** Selected courses that had zero usable sections and were reported, not dropped. */
    val skippedCourses: List<String>
) {
    /**
     * True only when EVERY selected course got a usable section.
     *
     * An incomplete result is informational: it may be shown (with the skipped
     * course names) but it must never replace the user's current program, since
     * that would silently drop the courses listed in [skippedCourses]. See
     * `CourseRepository.applySchedule`, which refuses to persist such a result.
     */
    val isComplete: Boolean get() = skippedCourses.isEmpty()
}

object ScheduleEngine {

    /**
     * Checks if two time intervals overlap (strictly overlap, touching borders is not an overlap).
     */
    fun timesOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean {
        return maxOf(startA, startB) < minOf(endA, endB)
    }

    /**
     * Checks if two sections conflict in either class schedule or exam schedule.
     * Takes week recurrence (every week, even, odd) into account.
     */
    fun checkConflict(sectionA: SectionWithDetails, sectionB: SectionWithDetails): Conflict? {
        // 1. Check class session overlap
        for (sessA in sectionA.sessions) {
            for (sessB in sectionB.sessions) {
                if (sessA.dayOfWeek == sessB.dayOfWeek) {
                    // Check week parity overlap (e.g., EVEN vs ODD weeks do NOT conflict)
                    if (sessA.weekType.overlapsWith(sessB.weekType)) {
                        if (timesOverlap(sessA.startMinutes, sessA.endMinutes, sessB.startMinutes, sessB.endMinutes)) {
                            val dayNameFa = ClassSession.getDayName(sessA.dayOfWeek, isFarsi = true)
                            val dayNameEn = ClassSession.getDayName(sessA.dayOfWeek, isFarsi = false)
                            val clashTime = "${maxOf(sessA.startTime, sessB.startTime)} - ${minOf(sessA.endTime, sessB.endTime)}"
                            val weekDetailFa = if (sessA.weekType != WeekType.EVERY_WEEK || sessB.weekType != WeekType.EVERY_WEEK) {
                                " (${sessA.weekType.titleFa} / ${sessB.weekType.titleFa})"
                            } else ""

                            return Conflict(
                                type = ConflictType.CLASS_TIME_OVERLAP,
                                sectionA = sectionA,
                                sectionB = sectionB,
                                description = "Time clash between ${sectionA.courseName} (Sec ${sectionA.sectionCode}) and ${sectionB.courseName} (Sec ${sectionB.sectionCode}) on $dayNameEn ($clashTime)",
                                descriptionFa = "تداخل کلاسی در روز $dayNameFa$weekDetailFa ساعت $clashTime بین «${sectionA.courseName}» و «${sectionB.courseName}»",
                                dayOfWeek = sessA.dayOfWeek,
                                timeRange = clashTime
                            )
                        }
                    }
                }
            }
        }

        // 2. Check exam date & time overlap — DEFINITE conflicts only.
        // Same day with unknown/missing times is NOT a conflict here; see
        // checkExamWarning() which reports it as insufficient-data instead.
        val examDateA = sectionA.examDate.trim()
        val examDateB = sectionB.examDate.trim()
        if (examDateA.isNotBlank() && examDateB.isNotBlank() && examDateA == examDateB) {
            val startA = ClassSession.parseTimeMinutesOrNull(sectionA.section.examStartTime)
            val endA = ClassSession.parseTimeMinutesOrNull(sectionA.section.examEndTime)
            val startB = ClassSession.parseTimeMinutesOrNull(sectionB.section.examStartTime)
            val endB = ClassSession.parseTimeMinutesOrNull(sectionB.section.examEndTime)

            if (startA != null && endA != null && startB != null && endB != null &&
                startA < endA && startB < endB
            ) {
                if (timesOverlap(startA, endA, startB, endB)) {
                    val clashTime = "${maxOf(sectionA.section.examStartTime, sectionB.section.examStartTime)} - ${minOf(sectionA.section.examEndTime, sectionB.section.examEndTime)}"
                    return Conflict(
                        type = ConflictType.EXAM_OVERLAP,
                        sectionA = sectionA,
                        sectionB = sectionB,
                        description = "Final exam overlap on $examDateA ($clashTime) between ${sectionA.courseName} and ${sectionB.courseName}",
                        descriptionFa = "تداخل تاریخ امتحان در $examDateA ساعت $clashTime بین «${sectionA.courseName}» و «${sectionB.courseName}»",
                        timeRange = "$examDateA $clashTime"
                    )
                }
            }
        }

        return null
    }

    /**
     * Same exam day, but at least one side lacks usable start/end times.
     * Returns an [ConflictType.EXAM_SAME_DAY_WARNING] (insufficient data),
     * or null when there is nothing to warn about:
     * - different/blank exam days → null
     * - both sides fully timed (overlap or disjoint is owned by [checkConflict]) → null
     *
     * The generator and conflict banners must treat warnings as non-blocking.
     */
    fun checkExamWarning(sectionA: SectionWithDetails, sectionB: SectionWithDetails): Conflict? {
        val examDateA = sectionA.examDate.trim()
        val examDateB = sectionB.examDate.trim()
        if (examDateA.isBlank() || examDateA != examDateB) return null

        val aFull = ClassSession.parseTimeMinutesOrNull(sectionA.section.examStartTime) != null &&
            ClassSession.parseTimeMinutesOrNull(sectionA.section.examEndTime) != null
        val bFull = ClassSession.parseTimeMinutesOrNull(sectionB.section.examStartTime) != null &&
            ClassSession.parseTimeMinutesOrNull(sectionB.section.examEndTime) != null
        if (aFull && bFull) return null

        return Conflict(
            type = ConflictType.EXAM_SAME_DAY_WARNING,
            sectionA = sectionA,
            sectionB = sectionB,
            description = "Same exam day ($examDateA) for ${sectionA.courseName} and ${sectionB.courseName}, but exam times are incomplete",
            descriptionFa = "هر دو امتحان در تاریخ $examDateA است ولی ساعت دقیق حداقل یکی مشخص نیست؛ لطفاً برنامه امتحانات را بررسی کنید («${sectionA.courseName}» و «${sectionB.courseName}»)",
            timeRange = examDateA
        )
    }

    /** All same-day-with-unknown-time exam pairs (warnings, non-blocking). */
    fun findExamWarnings(sections: List<SectionWithDetails>): List<Conflict> {
        val warnings = mutableListOf<Conflict>()
        for (i in 0 until sections.size) {
            for (j in i + 1 until sections.size) {
                val warning = checkExamWarning(sections[i], sections[j])
                if (warning != null) warnings.add(warning)
            }
        }
        return warnings
    }

    /**
     * Finds all pairwise conflicts in a list of sections.
     */
    fun findAllConflicts(sections: List<SectionWithDetails>): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()
        for (i in 0 until sections.size) {
            for (j in i + 1 until sections.size) {
                val conflict = checkConflict(sections[i], sections[j])
                if (conflict != null) {
                    conflicts.add(conflict)
                }
            }
        }
        return conflicts
    }

    /**
     * Checks if a candidate section conflicts with any already enrolled sections.
     */
    fun findConflictWithCurrent(
        candidate: SectionWithDetails,
        currentSections: List<SectionWithDetails>
    ): Conflict? {
        for (enrolled in currentSections) {
            if (enrolled.section.id == candidate.section.id) continue
            if (enrolled.course.id == candidate.course.id) continue
            val conflict = checkConflict(candidate, enrolled)
            if (conflict != null) return conflict
        }
        return null
    }

    /**
     * Deterministic ranking order: real score first, then the tie-breakers a
     * student would pick by hand (fewer idle minutes, fewer university days,
     * fewer early-morning sessions) and finally a stable section-id tie-break,
     * so the same search space always yields the same Top-K — independent of
     * the order the DFS happened to visit combinations in.
     */
    val rankingComparator: Comparator<ScoredSchedule> =
        compareByDescending<ScoredSchedule> { it.score }
            .thenBy { it.totalGapMinutes }
            .thenBy { it.activeDaysCount }
            .thenBy { it.earlyMorningClassCount }
            .thenBy { scored -> scored.schedule.sumOf { sec -> sec.section.id } }

    /**
     * Searches conflict-free schedules with branch & bound, keeping the best
     * top-K instead of the first-K in DFS order.
     *
     * Guarantees:
     * - Courses with zero usable sections are REPORTED in [ScheduleSearchResult.skippedCourses],
     *   never silently dropped.
     * - The full space is explored (conflict-pruned) up to [maxLeaves] leaves, so the
     *   best schedule is not missed just because it sorts after an arbitrary cutoff.
     * - Constrained courses first (MRV) for earlier pruning; exam same-day warnings
     *   never block a combination (only definite conflicts do).
     */
    fun generateTopSchedules(
        courses: List<GeneratorCourse>,
        preference: OptimizationPreference = OptimizationPreference.BALANCED,
        topK: Int = 12,
        maxLeaves: Int = 200_000
    ): ScheduleSearchResult {
        val skipped = courses.filter { it.sections.isEmpty() }.map { it.courseName }
        val ordered = courses.filter { it.sections.isNotEmpty() }.sortedBy { it.sections.size }
        if (ordered.isEmpty()) {
            return ScheduleSearchResult(emptyList(), 0, false, skipped)
        }

        val top = mutableListOf<ScoredSchedule>()
        var totalValid = 0
        var leaves = 0
        var truncated = false
        val current = mutableListOf<SectionWithDetails>()

        fun insertCandidate(scored: ScoredSchedule) {
            if (topK <= 0) return
            if (top.size < topK) {
                top.add(scored)
                top.sortWith(rankingComparator)
            } else if (rankingComparator.compare(scored, top.last()) < 0) {
                top[top.lastIndex] = scored
                top.sortWith(rankingComparator)
            }
        }

        fun backtrack(courseIndex: Int) {
            if (truncated) return
            if (courseIndex == ordered.size) {
                leaves++
                if (leaves > maxLeaves) {
                    truncated = true
                    return
                }
                totalValid++
                insertCandidate(evaluateSchedule(current.toList(), preference))
                return
            }
            for (candidate in ordered[courseIndex].sections) {
                if (truncated) return
                var hasConflict = false
                for (placed in current) {
                    if (checkConflict(candidate, placed) != null) {
                        hasConflict = true
                        break
                    }
                }
                if (!hasConflict) {
                    current.add(candidate)
                    backtrack(courseIndex + 1)
                    current.removeAt(current.size - 1)
                }
            }
        }

        backtrack(0)
        return ScheduleSearchResult(
            ranked = markBest(top.sortedWith(rankingComparator)),
            totalValid = totalValid,
            truncated = truncated,
            skippedCourses = skipped
        )
    }

    /**
     * Legacy entry point kept for compatibility: returns the best-first raw
     * schedules (up to [maxCombinations]), same shape as before.
     */
    fun generateConflictFreeSchedules(
        courseGroups: List<List<SectionWithDetails>>,
        maxCombinations: Int = 100
    ): List<List<SectionWithDetails>> {
        return generateTopSchedules(
            courses = courseGroups.map { GeneratorCourse(courseName = "", sections = it) },
            topK = maxCombinations
        ).ranked.map { it.schedule }
    }

    /** Prepends the "top recommendation" tag without touching the real score. */
    private fun markBest(ranked: List<ScoredSchedule>): List<ScoredSchedule> {
        if (ranked.isEmpty()) return ranked
        val top = ranked[0]
        return listOf(top.copy(tags = listOf("پیشنهاد برتر هوشمند") + top.tags)) + ranked.drop(1)
    }

    /** Academic week parity, counted from the semester start (week 1 = first 7 days). */
    enum class WeekParity(val titleFa: String) {
        EVEN("زوج"),
        ODD("فرد")
    }

    /** Which academic week [todayEpochDay] falls in, or null when unknowable. */
    data class AcademicWeek(
        /** 1-based week number since the semester start. */
        val number: Int,
        val parity: WeekParity
    )

    fun academicWeek(
        todayEpochDay: Long,
        semesterStartEpochDay: Long?,
        firstWeekIsOdd: Boolean
    ): AcademicWeek? {
        if (semesterStartEpochDay == null || todayEpochDay < semesterStartEpochDay) return null
        val weekNumber = ((todayEpochDay - semesterStartEpochDay) / 7 + 1).toInt()
        val isEven = if (firstWeekIsOdd) weekNumber % 2 == 0 else weekNumber % 2 == 1
        return AcademicWeek(weekNumber, if (isEven) WeekParity.EVEN else WeekParity.ODD)
    }

    /** True when this session actually meets in a week of the given parity. */
    fun occursInWeek(session: ClassSession, parity: WeekParity?): Boolean {
        if (parity == null) return true
        return when (session.weekType) {
            WeekType.EVERY_WEEK -> true
            WeekType.EVEN_WEEKS -> parity == WeekParity.EVEN
            WeekType.ODD_WEEKS -> parity == WeekParity.ODD
        }
    }

    /**
     * Analyzes idle gap time between classes on each day.
     * Returns total gap minutes across the week.
     *
     * Parity-aware: a day mixing weekly and biweekly sessions is scored as the
     * average of its even-week and odd-week gaps. Example — weekly 8–10, even
     * 10–12, weekly 12–14: even weeks have no gap, odd weeks have a 120-minute
     * dead window, so the honest weekly figure is 60, not 0.
     */
    fun calculateTotalGaps(sections: List<SectionWithDetails>): Int {
        val sessionsByDay = sections.flatMap { it.sessions }.groupBy { it.dayOfWeek }
        var totalGapMinutes = 0

        for ((_, daySessions) in sessionsByDay) {
            if (daySessions.size <= 1) continue
            if (daySessions.none { it.weekType != WeekType.EVERY_WEEK }) {
                totalGapMinutes += gapOf(daySessions)
            } else {
                val evenSet = daySessions.filter {
                    it.weekType == WeekType.EVERY_WEEK || it.weekType == WeekType.EVEN_WEEKS
                }
                val oddSet = daySessions.filter {
                    it.weekType == WeekType.EVERY_WEEK || it.weekType == WeekType.ODD_WEEKS
                }
                totalGapMinutes += (gapOf(evenSet) + gapOf(oddSet)) / 2
            }
        }
        return totalGapMinutes
    }

    private fun gapOf(daySessions: List<ClassSession>): Int {
        if (daySessions.size <= 1) return 0
        val sorted = daySessions.sortedBy { it.startMinutes }
        var gap = 0
        for (i in 0 until sorted.size - 1) {
            val currentEnd = sorted[i].endMinutes
            val nextStart = sorted[i + 1].startMinutes
            if (nextStart > currentEnd) {
                gap += (nextStart - currentEnd)
            }
        }
        return gap
    }

    /**
     * True average class minutes per week: biweekly sessions count half, since
     * they meet every other week. Days without parity sessions are unchanged.
     */
    fun averageWeeklyMinutes(sections: List<SectionWithDetails>): Int {
        val all = sections.flatMap { it.sessions }
        if (all.none { it.weekType != WeekType.EVERY_WEEK }) {
            return all.sumOf { maxOf(0, it.endMinutes - it.startMinutes) }
        }
        fun minutesOf(parity: WeekParity): Int {
            return all.filter {
                it.weekType == WeekType.EVERY_WEEK ||
                    (parity == WeekParity.EVEN && it.weekType == WeekType.EVEN_WEEKS) ||
                    (parity == WeekParity.ODD && it.weekType == WeekType.ODD_WEEKS)
            }.sumOf { maxOf(0, it.endMinutes - it.startMinutes) }
        }
        return (minutesOf(WeekParity.EVEN) + minutesOf(WeekParity.ODD)) / 2
    }

    /**
     * Weekly-average attendance days (the figure the SCORE uses).
     *
     * Rule (single definition, so scoring can never "assume biweekly == weekly"):
     * - a day that has a weekly (EVERY_WEEK) class, or classes in BOTH parities,
     *   is attended every week → 1.0;
     * - a day whose only classes are biweekly (odd-only or even-only) is attended
     *   every other week → 0.5.
     *
     * Example: Saturday with an odd-week-only 8–10 class scores 0.5, whereas the
     * same class every week scores 1.0. [computeMetrics]/[ScoredSchedule.activeDaysCount]
     * intentionally keep the union count (a day you must keep free is a day you
     * must keep free) for display.
     */
    fun weeklyActiveDays(sections: List<SectionWithDetails>): Double {
        val sessionsByDay = sections.flatMap { it.sessions }.groupBy { it.dayOfWeek }
        if (sessionsByDay.isEmpty()) return 0.0
        return sessionsByDay.values.sumOf { daySessions ->
            val everyWeek = daySessions.any { it.weekType == WeekType.EVERY_WEEK } ||
                (
                    daySessions.any { it.weekType == WeekType.ODD_WEEKS } &&
                        daySessions.any { it.weekType == WeekType.EVEN_WEEKS }
                    )
            if (everyWeek) 1.0 else 0.5
        }
    }

    /** Weekly-average number of 08:00 sessions (biweekly early session = 0.5). */
    fun weeklyEarlyMorningCount(sections: List<SectionWithDetails>): Double =
        sections.flatMap { it.sessions }
            .filter { it.startMinutes <= EARLY_MORNING_CUTOFF_MINUTES }
            .sumOf { if (it.weekType == WeekType.EVERY_WEEK) 1.0 else 0.5 }

    /** 08:00 is the first slot of the university timetable (2-hour slots). */
    private const val EARLY_MORNING_CUTOFF_MINUTES = 8 * 60

    // ---- Scoring weights: the constants below ARE the documentation. ----
    /** Idle-time penalty block: every 30 minutes of gap costs 3 points. */
    private const val GAP_DEDUCTION_BLOCK_MINUTES = 30.0
    private const val GAP_DEDUCTION_PER_BLOCK = 3.0
    /** Up to this many effective weekly days is free (3 days is a good week). */
    private const val FREE_ACTIVE_DAYS = 3.0
    /** Every effective day above [FREE_ACTIVE_DAYS] costs 6 points. */
    private const val DAY_DEDUCTION_PER_EXTRA_DAY = 6.0
    /** Every effective 08:00 session costs 4 points. */
    private const val EARLY_MORNING_DEDUCTION_PER_SESSION = 4.0

    /**
     * Evaluates and scores a schedule based on user preferences.
     *
     * Score model (must stay consistent with [ScoreComponent] rows):
     * `rawScore = 100 - weightedGaps - weightedDays - weightedEarly`, and the
     * published [ScoredSchedule.score] is `rawScore` limited to 0..100. When the
     * limit actually bites, an explicit clamp row is added to the breakdown so
     * the rows always add up to the displayed score.
     *
     * Parity semantics: gaps ([calculateTotalGaps]) and the day/early-morning
     * figures used for scoring ([weeklyActiveDays], [weeklyEarlyMorningCount])
     * are weekly AVERAGES — a class that meets every other week is charged half,
     * never as if it met every week.
     *
     * Optimization goals:
     * - Minimize idle gap time between classes in the same day.
     * - Minimize effective active days count.
     * - Avoid 8 AM classes if preferred.
     */
    fun evaluateSchedule(
        schedule: List<SectionWithDetails>,
        preference: OptimizationPreference = OptimizationPreference.BALANCED
    ): ScoredSchedule {
        val allSessions = schedule.flatMap { it.sessions }
        val activeDays = allSessions.map { it.dayOfWeek }.toSet().size
        val totalGaps = calculateTotalGaps(schedule)
        val earlyMorningCount = allSessions.count { it.startMinutes <= EARLY_MORNING_CUTOFF_MINUTES }
        val weeklyDays = weeklyActiveDays(schedule)
        val weeklyEarly = weeklyEarlyMorningCount(schedule)

        val totalMinutes = averageWeeklyMinutes(schedule)

        // Base score starts at 100; every deduction below is recorded in the
        // breakdown so the UI can explain exactly why a schedule won.
        val breakdown = mutableListOf<ScoreComponent>()
        breakdown.add(ScoreComponent("امتیاز پایه", 100))

        // Idle-time penalty (3 points per 30 minutes of gap).
        val gapDeduction = (totalGaps / GAP_DEDUCTION_BLOCK_MINUTES) * GAP_DEDUCTION_PER_BLOCK
        // Day penalty (6 points for every weekly-average day above 3 — the
        // comment used to say "5" while the code used 6; the constant is now the
        // only source of truth and matches the behaviour v2.x shipped).
        val dayDeduction = maxOf(0.0, weeklyDays - FREE_ACTIVE_DAYS) * DAY_DEDUCTION_PER_EXTRA_DAY
        // Early-morning penalty (4 points per weekly-average 08:00 session).
        val earlyDeduction = weeklyEarly * EARLY_MORNING_DEDUCTION_PER_SESSION


        val weightedGap: Double
        val weightedDay: Double
        val weightedEarly: Double
        when (preference) {
            OptimizationPreference.BALANCED -> {
                weightedGap = gapDeduction * 1.0
                weightedDay = dayDeduction * 1.0
                weightedEarly = earlyDeduction * 0.5
            }
            OptimizationPreference.MIN_GAPS -> {
                weightedGap = gapDeduction * 2.2
                weightedDay = dayDeduction * 0.5
                weightedEarly = earlyDeduction * 0.3
            }
            OptimizationPreference.MIN_DAYS -> {
                weightedDay = dayDeduction * 2.5
                weightedGap = gapDeduction * 0.7
                weightedEarly = earlyDeduction * 0.3
            }
            OptimizationPreference.NO_EARLY_MORNING -> {
                weightedEarly = earlyDeduction * 3.0
                weightedGap = gapDeduction * 0.8
                weightedDay = dayDeduction * 0.6
            }
        }

        val gapDelta = -weightedGap.toInt()
        val dayDelta = -weightedDay.toInt()
        val earlyDelta = -weightedEarly.toInt()
        if (gapDelta != 0) {
            breakdown.add(ScoreComponent("گپ بین کلاس‌ها ($totalGaps دقیقه)", gapDelta))
        }
        if (dayDelta != 0) {
            breakdown.add(
                ScoreComponent("روزهای حضور در هفته (${formatWeekly(weeklyDays)} روز)", dayDelta)
            )
        }
        if (earlyDelta != 0) {
            breakdown.add(
                ScoreComponent(
                    "کلاس صبح زود در هفته (${formatWeekly(weeklyEarly)} جلسه)",
                    earlyDelta
                )
            )
        }

        val rawScore = 100.0 + gapDelta + dayDelta + earlyDelta
        val rawScoreInt = rawScore.toInt()
        // Honest clamp: a terrible schedule scores 0, a perfect one 100. The
        // difference is recorded as its own row so the breakdown always adds up
        // to the displayed score instead of silently contradicting it.
        val finalScore = rawScoreInt.coerceIn(0, 100)
        val clampDelta = finalScore - rawScoreInt
        if (clampDelta != 0) {
            breakdown.add(ScoreComponent("محدودسازی امتیاز به بازهٔ ۰ تا ۱۰۰", clampDelta))
        }

        // Generate descriptive tags
        val tags = mutableListOf<String>()
        if (totalGaps == 0) {
            tags.add("بدون گپ و زمان مرده (فشرده)")
        } else if (totalGaps <= 90) {
            tags.add("حداقل زمان انتظار (${totalGaps} دقیقه)")
        }

        if (activeDays <= 3) {
            tags.add("فقط $activeDays روز در هفته")
        }

        if (earlyMorningCount == 0) {
            tags.add("بدون کلاس صبح زود (۸ صبح)")
        }

        return ScoredSchedule(
            schedule = schedule,
            score = finalScore,
            totalGapMinutes = totalGaps,
            activeDaysCount = activeDays,
            earlyMorningClassCount = earlyMorningCount,
            totalWeeklyHours = totalMinutes / 60f,
            tags = tags,
            breakdown = breakdown.toList(),
            rawScore = rawScoreInt,
            weeklyActiveDays = weeklyDays,
            weeklyEarlyMorningCount = weeklyEarly
        )
    }

    /** "3" / "3.5" — compact weekly-average formatting for the score breakdown. */
    private fun formatWeekly(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(java.util.Locale.ROOT, "%.1f", value)

    /**
     * Ranks generated conflict-free schedules according to selected optimization goal.
     * The best schedule keeps its real score; only the "top recommendation" tag is added.
     */
    fun rankSchedules(
        schedules: List<List<SectionWithDetails>>,
        preference: OptimizationPreference = OptimizationPreference.BALANCED
    ): List<ScoredSchedule> {
        val scoredList = schedules.map { evaluateSchedule(it, preference) }
        return markBest(scoredList.sortedWith(rankingComparator))
    }

    /**
     * Maps [java.util.Calendar.DAY_OF_WEEK] to the app's day index
     * (Saturday = 0 … Friday = 6, the Iranian academic week).
     */
    fun calendarDayOfWeekToAppDay(calendarDay: Int): Int = when (calendarDay) {
        java.util.Calendar.SATURDAY -> 0
        java.util.Calendar.SUNDAY -> 1
        java.util.Calendar.MONDAY -> 2
        java.util.Calendar.TUESDAY -> 3
        java.util.Calendar.WEDNESDAY -> 4
        java.util.Calendar.THURSDAY -> 5
        else -> 6 // Friday (and anything unexpected)
    }

    /** One class occurrence, possibly on a later day when scanning ahead. */
    data class UpcomingSession(
        val section: SectionWithDetails,
        val session: ClassSession,
        val dayOfWeek: Int,
        /** True when [nowMinutes] falls inside this session right now. */
        val ongoing: Boolean
    )

    /** Today's sessions sorted by start time (pure; UI supplies the weekday).
     * When [currentParity] is known, only sessions that actually meet in this
     * academic week are returned (an odd-week class is NOT today's class in an
     * even week). Null keeps the legacy unfiltered behavior. */
    fun sessionsOnDay(
        sections: List<SectionWithDetails>,
        dayOfWeek: Int,
        currentParity: WeekParity? = null
    ): List<UpcomingSession> {
        return sections.flatMap { sec ->
            sec.sessions.filter { it.dayOfWeek == dayOfWeek && occursInWeek(it, currentParity) }
                .map { UpcomingSession(sec, it, dayOfWeek, ongoing = false) }
        }.sortedBy { it.session.startMinutes }
    }

    /**
     * Next upcoming class at/after ([dayOfWeek], [nowMinutes]), scanning the
     * coming 7 days. A currently-running class counts as "next" (ongoing).
     * Null when nothing is scheduled at all.
     *
     * [todayParity] filters today's sessions by academic week parity (odd-week
     * classes never count as ongoing in an even week). [parityForOffset] gives
     * the parity of a future day N days ahead; when null, future days keep the
     * legacy unfiltered behavior. Both default to null (no filtering).
     */
    fun nextUpcomingSession(
        sections: List<SectionWithDetails>,
        dayOfWeek: Int,
        nowMinutes: Int,
        todayParity: WeekParity? = null,
        parityForOffset: ((offsetDays: Int) -> WeekParity?)? = null
    ): UpcomingSession? {
        for (offset in 0..6) {
            val parity = if (offset == 0) todayParity else parityForOffset?.invoke(offset)
            val day = (dayOfWeek + offset) % 7
            val todays = sections.flatMap { sec ->
                sec.sessions.filter { it.dayOfWeek == day && occursInWeek(it, parity) }
                    .map { sec to it }
            }.sortedBy { it.second.startMinutes }
            for ((sec, sess) in todays) {
                if (offset > 0 || sess.endMinutes > nowMinutes) {
                    val ongoing = offset == 0 && sess.startMinutes <= nowMinutes
                    return UpcomingSession(sec, sess, day, ongoing)
                }
            }
        }
        return null
    }

    /**
     * Computes basic metrics for a schedule.
     *
     * Day/early-morning semantics: a day (or an 08:00 session) counts when it
     * exists in the union of odd and even weeks — this is the "how many days do
     * I have to go to university" number shown to the user. The parity-aware
     * weekly averages used for scoring are [weeklyActiveDays] /
     * [weeklyEarlyMorningCount] / [averageWeeklyMinutes].
     */
    fun computeMetrics(sections: List<SectionWithDetails>): ScheduleMetrics {
        val totalCredits = sections.sumOf { it.course.credits }
        val activeDays = sections.flatMap { it.sessions }.map { it.dayOfWeek }.toSet().size
        val totalGaps = calculateTotalGaps(sections)
        val totalMinutes = averageWeeklyMinutes(sections)
        return ScheduleMetrics(
            totalCourses = sections.size,
            totalCredits = totalCredits,
            activeDaysCount = activeDays,
            totalWeeklyHours = totalMinutes / 60f,
            totalGapMinutes = totalGaps
        )
    }
}

data class ScheduleMetrics(
    val totalCourses: Int,
    val totalCredits: Int,
    val activeDaysCount: Int,
    val totalWeeklyHours: Float,
    val totalGapMinutes: Int = 0
)
