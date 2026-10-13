package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Conflict
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.SectionWithDetails

/**
 * Smart per-course ranking for the "Add course" discovery screen.
 *
 * Unlike [ScheduleEngine], which scores COMPLETE schedules (one group per
 * course), this ranker scores individual COURSES by asking: "if I add this
 * course to the program with its best still-compatible group, how does the
 * program change?" Every figure is computed from the real catalog groups,
 * the currently enrolled groups and the engine's own conflict and metric
 * rules — no invented percentages or scores.
 */
object CourseSuggestionRanker {

    /** Verdict for one catalog course. */
    enum class SuggestionStatus {
        /** At least one group is compatible with the current program. */
        COMPATIBLE,
        /** All groups clash with the current program. */
        NO_COMPATIBLE_GROUP,
        /** Groups exist but none has verifiable class-time data. */
        NO_TIME_DATA,
        /** The course has no groups at all. */
        NO_GROUPS,
        /** Already in "my courses" (generator target or enrolled group). */
        ALREADY_ADDED
    }

    /** Verdict for a single group of the course. */
    enum class GroupVerdict {
        /** No conflict with the current program. */
        COMPATIBLE,
        /** Clashes with an enrolled group (see [GroupAssessment.conflict]). */
        CONFLICTING,
        /** Class-time data missing or invalid: cannot be verified. */
        INCOMPLETE_TIME_DATA
    }

    /**
     * One group of a catalog course, assessed against the current program.
     * Gap/day/early-morning deltas describe how the program would change if
     * THIS group were added; they stay neutral for unusable groups so an
     * unusable group never claims a measurable effect.
     */
    data class GroupAssessment(
        val section: SectionWithDetails,
        val verdict: GroupVerdict,
        /** First blocking conflict, only for [GroupVerdict.CONFLICTING]. */
        val conflict: Conflict? = null,
        /** Warnings (same exam day, incomplete times), never blocking. */
        val examWarnings: List<Conflict> = emptyList(),
        val gapDeltaMinutes: Int = 0,
        val addsActiveDay: Boolean = false,
        val earlyMorningDelta: Double = 0.0
    )

    /**
     * Ranking result for one catalog course. [bestGroup] is the compatible
     * group the course would be added with (null unless COMPATIBLE).
     * [reasons] holds ONLY facts that were actually computed.
     */
    data class CourseSuggestion(
        val course: Course,
        val groups: List<GroupAssessment>,
        val bestGroup: GroupAssessment?,
        val status: SuggestionStatus,
        val alreadyAdded: Boolean,
        val reasons: List<String>
    )

    /**
     * Ranks every course of [catalog] against the current program.
     *
     * Pure and deterministic: the same inputs always produce the same order.
     * Order rules (in priority order):
     * 1. courses with a compatible group first;
     * 2. smallest increase in idle gap minutes;
     * 3. smallest increase in university days;
     * 4. only when [preference] is NO_EARLY_MORNING: smallest increase in
     *    early-morning load;
     * 5. fixed tie-break: course code, then name, then id.
     */
    fun rank(
        catalog: List<CourseWithSections>,
        sectionsByCourse: Map<Long, List<SectionWithDetails>>,
        enrolled: List<SectionWithDetails>,
        preference: OptimizationPreference = OptimizationPreference.BALANCED
    ): List<CourseSuggestion> {
        val baselineGaps = ScheduleEngine.calculateTotalGaps(enrolled)
        val baselineDays = activeDays(enrolled)
        val baselineEarly = ScheduleEngine.weeklyEarlyMorningCount(enrolled)

        return catalog
            .map { cws ->
                assess(
                    courseWithSections = cws,
                    sections = sectionsByCourse[cws.course.id].orEmpty(),
                    enrolled = enrolled,
                    baselineGaps = baselineGaps,
                    baselineDays = baselineDays,
                    baselineEarly = baselineEarly,
                    preference = preference
                )
            }
            .sortedWith(suggestionComparator(preference))
    }

    /**
     * Local (offline) search over the catalog: matches course name, code,
     * department and group instructors. The query is trimmed; blank returns
     * everything.
     */
    fun matchesQuery(courseWithSections: CourseWithSections, rawQuery: String): Boolean {
        val q = rawQuery.trim()
        if (q.isBlank()) return true
        val course = courseWithSections.course
        if (course.name.contains(q, ignoreCase = true)) return true
        if (course.code.contains(q, ignoreCase = true)) return true
        if (course.department.contains(q, ignoreCase = true)) return true
        return courseWithSections.sections.any { section ->
            section.section.instructor.contains(q, ignoreCase = true)
        }
    }

    private fun assess(
        courseWithSections: CourseWithSections,
        sections: List<SectionWithDetails>,
        enrolled: List<SectionWithDetails>,
        baselineGaps: Int,
        baselineDays: Int,
        baselineEarly: Double,
        preference: OptimizationPreference
    ): CourseSuggestion {
        val course = courseWithSections.course
        val alreadyAdded = course.isSelectedForGeneration ||
            sections.any { it.section.isEnrolled }
        if (alreadyAdded) {
            return CourseSuggestion(
                course = course,
                groups = emptyList(),
                bestGroup = null,
                status = SuggestionStatus.ALREADY_ADDED,
                alreadyAdded = true,
                reasons = listOf("این درس قبلاً در «دروس من» است.")
            )
        }
        if (sections.isEmpty()) {
            return CourseSuggestion(
                course = course,
                groups = emptyList(),
                bestGroup = null,
                status = SuggestionStatus.NO_GROUPS,
                alreadyAdded = false,
                reasons = listOf("برای این درس هنوز گروهی ثبت نشده است.")
            )
        }

        val assessed = sections.map { section ->
            assessGroup(
                section = section,
                enrolled = enrolled,
                baselineGaps = baselineGaps,
                baselineDays = baselineDays,
                baselineEarly = baselineEarly
            )
        }
        val compatible = assessed.filter { it.verdict == GroupVerdict.COMPATIBLE }
        if (compatible.isNotEmpty()) {
            val best = compatible.minWith(groupComparator(preference))
            return CourseSuggestion(
                course = course,
                groups = assessed,
                bestGroup = best,
                status = SuggestionStatus.COMPATIBLE,
                alreadyAdded = false,
                reasons = explain(best, preference)
            )
        }
        if (assessed.all { it.verdict == GroupVerdict.INCOMPLETE_TIME_DATA }) {
            return CourseSuggestion(
                course = course,
                groups = assessed,
                bestGroup = null,
                status = SuggestionStatus.NO_TIME_DATA,
                alreadyAdded = false,
                reasons = listOf(
                    "ساعت کلاسی گروه‌های این درس ثبت یا کامل نیست؛ سازگاری آن بدون داده معتبر بررسی نمی‌شود."
                )
            )
        }
        val clashes = assessed.count { it.verdict == GroupVerdict.CONFLICTING }
        return CourseSuggestion(
            course = course,
            groups = assessed,
            bestGroup = null,
            status = SuggestionStatus.NO_COMPATIBLE_GROUP,
            alreadyAdded = false,
            reasons = listOf(
                "این درس فعلاً گروه سازگار ندارد ($clashes گروه با برنامه فعلی تداخل دارند)."
            )
        )
    }

    private fun assessGroup(
        section: SectionWithDetails,
        enrolled: List<SectionWithDetails>,
        baselineGaps: Int,
        baselineDays: Int,
        baselineEarly: Double
    ): GroupAssessment {
        // Incomplete time data is never assumed valid: a group without any
        // session, with an out-of-range day, or with an unparseable
        // start >= end range cannot be conflict-checked honestly.
        if (!hasVerifiableTime(section)) {
            return GroupAssessment(
                section = section,
                verdict = GroupVerdict.INCOMPLETE_TIME_DATA
            )
        }
        val conflict = ScheduleEngine.findConflictWithCurrent(section, enrolled)
        if (conflict != null) {
            return GroupAssessment(
                section = section,
                verdict = GroupVerdict.CONFLICTING,
                conflict = conflict
            )
        }
        val withCandidate = enrolled + section
        val warnings = ScheduleEngine.findExamWarnings(withCandidate)
            .filter {
                it.sectionA.section.id == section.section.id ||
                    it.sectionB.section.id == section.section.id
            }
        return GroupAssessment(
            section = section,
            verdict = GroupVerdict.COMPATIBLE,
            examWarnings = warnings,
            gapDeltaMinutes = ScheduleEngine.calculateTotalGaps(withCandidate) - baselineGaps,
            addsActiveDay = activeDays(withCandidate) > baselineDays,
            earlyMorningDelta = ScheduleEngine.weeklyEarlyMorningCount(withCandidate) - baselineEarly
        )
    }

    /**
     * True when every session of the group has a real day (0..6) and a
     * parseable start < end. Empty groups are data-less, not valid.
     */
    fun hasVerifiableTime(section: SectionWithDetails): Boolean {
        if (section.sessions.isEmpty()) return false
        return section.sessions.all { session ->
            if (session.dayOfWeek !in 0..6) return@all false
            val start = ClassSession.parseTimeMinutesOrNull(session.startTime) ?: return@all false
            val end = ClassSession.parseTimeMinutesOrNull(session.endTime) ?: return@all false
            start < end
        }
    }

    /** Union of class days: the days the student must keep free / attend. */
    private fun activeDays(sections: List<SectionWithDetails>): Int =
        sections.flatMap { it.sessions }.map { it.dayOfWeek }.toSet().size

    /** Only computed facts become user-facing reasons. */
    private fun explain(best: GroupAssessment, preference: OptimizationPreference): List<String> {
        val reasons = mutableListOf<String>()
        reasons.add("گروه ${best.section.section.sectionCode} بدون تداخل با برنامه فعلی است.")
        if (best.gapDeltaMinutes <= 0) {
            reasons.add("زمان انتظار (فاصله خالی) برنامه را افزایش نمی‌دهد.")
        } else {
            reasons.add("حدود ${best.gapDeltaMinutes} دقیقه به زمان انتظار هفتگی اضافه می‌کند.")
        }
        if (!best.addsActiveDay) {
            reasons.add("روز جدیدی به روزهای حضور در دانشگاه اضافه نمی‌کند.")
        } else {
            reasons.add("یک روز جدید به روزهای حضور در دانشگاه اضافه می‌کند.")
        }
        if (preference == OptimizationPreference.NO_EARLY_MORNING && best.earlyMorningDelta <= 0.0) {
            reasons.add("کلاس صبح زود جدیدی اضافه نمی‌کند.")
        }
        if (best.examWarnings.isNotEmpty()) {
            reasons.add(
                "تاریخ امتحان آن با یک درس دیگر در یک روز است ولی ساعت دقیق حداقل یکی مشخص نیست؛ برنامه امتحانات را بررسی کنید."
            )
        }
        return reasons
    }

    private fun groupComparator(preference: OptimizationPreference): Comparator<GroupAssessment> {
        var comparator = compareBy<GroupAssessment> { it.gapDeltaMinutes }
            .thenBy { if (it.addsActiveDay) 1 else 0 }
        if (preference == OptimizationPreference.NO_EARLY_MORNING) {
            comparator = comparator.thenBy { it.earlyMorningDelta }
        }
        return comparator
            .thenBy { it.section.section.sectionCode }
            .thenBy { it.section.section.id }
    }

    private fun suggestionComparator(preference: OptimizationPreference): Comparator<CourseSuggestion> {
        val statusRank: (CourseSuggestion) -> Int = { suggestion ->
            when (suggestion.status) {
                SuggestionStatus.COMPATIBLE -> 0
                SuggestionStatus.NO_COMPATIBLE_GROUP -> 1
                SuggestionStatus.NO_TIME_DATA -> 2
                SuggestionStatus.NO_GROUPS -> 3
                SuggestionStatus.ALREADY_ADDED -> 4
            }
        }
        var comparator = compareBy(statusRank)
            .thenBy { it.bestGroup?.gapDeltaMinutes ?: Int.MAX_VALUE }
            .thenBy { if (it.bestGroup?.addsActiveDay == true) 1 else 0 }
        if (preference == OptimizationPreference.NO_EARLY_MORNING) {
            comparator = comparator.thenBy { it.bestGroup?.earlyMorningDelta ?: Double.MAX_VALUE }
        }
        return comparator
            .thenBy { it.course.code }
            .thenBy { it.course.name }
            .thenBy { it.course.id }
    }
}