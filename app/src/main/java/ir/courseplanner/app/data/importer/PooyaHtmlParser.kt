package ir.courseplanner.app.data.importer

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.WeekType
import kotlin.math.roundToInt

/**
 * Parses the "Presented Courses" HTML page saved from university portals
 * (Pooya, Golestan, Sama, …) into [ImportItem]s.
 *
 * Expected table columns (Pooya layout):
 *   ردیف | شماره درس | گروه | نام درس | واحد | ثبت‌نام‌شده | ظرفیت |
 *   دانشکده | نام استاد | کد درس اصلی | نام درس اصلی | ثبت‌نام‌شده کل | رزرو | جزئیات
 *
 * Class-session details live inside the info-icon `title` tooltip, e.g.
 * `… جلسه اول روز: دوشنبه ساعت 8(هر هفته به مدت 120 دقیقه در کارگاه 2) شروع زوج …`
 *
 * Design notes:
 * - Pure Kotlin, zero new dependencies (regex + string ops only).
 * - Imported courses are catalog-only: `isSelectedForGeneration = false`, so a
 *   200+ row portal file never floods the "my courses" list or the generator.
 * - Users add courses by code from the Courses screen quick-add card.
 */
object PooyaHtmlParser {

    private val tableRegex =
        Regex("<table[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val rowRegex =
        Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val cellRegex =
        Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tagRegex = Regex("<[^>]*>")
    private val titleAttrRegex = Regex("title\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val titleAttrSingleRegex = Regex("title\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)

    // جلسه اول روز: دوشنبه ساعت 14(هر هفته به مدت 120 دقیقه در کارگاه 1) شروع زوج
    private val sessionRegex = Regex(
        "جلسه\\s*(?:اول|دوم|سوم|چهارم|پنجم)?\\s*روز:\\s*(.*?)\\s*ساعت\\s*(\\d+)(?::(\\d+))?\\s*\\(([^)]+)\\)\\s*(?:شروع\\s*(زوج|فرد))?"
    )
    private val durationRegex = Regex("به مدت\\s*(\\d+)\\s*دقیقه")
    private val minutesAfterRegex = Regex("دقیقه\\s*در\\s+(.+)")
    // Duration is captured from the exam segment itself ("به مدت 120 دقیقه");
    // group 4 = duration minutes (may be absent), group 5 = location, group 6 = date.
    // The date pattern is strict on purpose: a loose capture would swallow the
    // next Persian word (e.g. "قابل") as a fake exam date.
    private val examRegex = Regex(
        "امتحان روز:\\s*([^\\s]*)\\s*ساعت\\s*(\\d+)(?::(\\d+))?\\s*(?:به مدت\\s*(\\d+)\\s*دقیقه\\s*)?در کلاس\\s*([^\\s]*)\\s*(?:به تاریخ\\s*(\\d{2,4}[/-]\\d{1,2}[/-]\\d{1,2}))?"
    )
    private val degreeRegex =
        Regex("مقطع:\\s*(.+?)(?=\\s*(?:گروه آموزشی|جلسه|امتحان|قابل انتخاب|$))")
    private val departmentRegex =
        Regex("گروه آموزشی:\\s*(.+?)(?=\\s*(?:جلسه|امتحان|قابل انتخاب|$))")
    private val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
    private val arabicDigits = "٠١٢٣٤٥٦٧٨٩"

    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val p = persianDigits.indexOf(ch)
            if (p >= 0) {
                sb.append(('0' + p))
                continue
            }
            val a = arabicDigits.indexOf(ch)
            if (a >= 0) {
                sb.append(('0' + a))
                continue
            }
            sb.append(ch)
        }
        return sb.toString()
    }

    fun decodeEntities(raw: String): String {
        return raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
    }

    fun stripTags(html: String): String {
        return tagRegex.replace(html, " ")
            .replace("[\\u200B-\\u200D\\uFEFF]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun cleanText(htmlFragment: String): String = stripTags(decodeEntities(htmlFragment))

    /**
     * Scores one table body for the presented-courses list (0 = not it).
     *
     * The header row must carry شماره درس + نام درس (the list's identity).
     * Bonus points for the remaining list columns let the real table win over
     * the filter-form table (faculty select) and the submit-form table on
     * full-page saves. A data-table check on title=/جلسه tooltips mirrors the
     * row parser below: header-only tables never parse.
     */
    internal fun scoreCoursesTable(tableBody: String): Int {
        val headerRow = rowRegex.find(tableBody)?.groupValues?.getOrNull(1).orEmpty()
        val headerText = stripTags(headerRow)
        var score = 0
        if (headerText.contains("شماره درس")) score += 30
        if (headerText.contains("نام درس")) score += 30
        if (headerText.contains("ظرفیت")) score += 10
        if (headerText.contains("نام استاد") || headerText.contains("استاد")) score += 10
        if (tableBody.contains("title=") && tableBody.contains("جلسه")) score += 10
        return score
    }

    /** Shared MIME sniff: both the byte path and the paste safety net use it. */
    internal fun looksLikeMht(content: String): Boolean =
        MhtHtmlExtractor.looksLikeMhtml(content.take(4096), null)

    /**
     * Single entry point. Returns [ImportResult] so the ViewModel import path
     * is identical to JSON/CSV imports.
     */
    fun parsePortalHtml(html: String): ImportResult {
        if (html.isBlank()) {
            return ImportResult.Failure("فایل HTML خالی است.")
        }
        // MHTML single-file archives (.mht) share this entry point in tests and
        // in the paste flow: unwrap the courses frame before table parsing so
        // MIME headers/boundaries are never read as course rows. The picker
        // path (ViewModel.importPortalBytes) already unwraps, so this is a
        // no-op for plain HTML and a safety net for everything else.
        val effectiveHtml = when (val extraction = MhtHtmlExtractor.extract(html)) {
            is MhtHtmlExtractor.Extraction.Html -> extraction.html
            is MhtHtmlExtractor.Extraction.Missing ->
                if (looksLikeMht(html)) {
                    return ImportResult.Failure(
                        when (extraction.reason) {
                            MhtHtmlExtractor.Reason.NO_HTML_PART ->
                                "در فایل تک‌فایل (.mht) هیچ سند HTML پیدا نشد؛ فایل سالم را دوباره ذخیره کنید."
                            MhtHtmlExtractor.Reason.NO_COURSE_CONTENT ->
                                "فایل تک‌فایل (.mht) جدول «دروس ارائه‌شده» را ندارد؛ مطمئن شوید صفحهٔ دروس را (نه صفحهٔ ورود) ذخیره کرده‌اید."
                            MhtHtmlExtractor.Reason.NOT_MHTML ->
                                "فایل HTML خالی است."
                        }
                    )
                } else {
                    html
                }
        }
        return try {
            val parsed = parseRowsDetailed(effectiveHtml)
            if (parsed.items.isEmpty()) {
                ImportResult.Failure(
                    "هیچ درسی در فایل HTML یافت نشد. مطمئن شوید فایل ذخیره‌شده " +
                        "صفحه «لیست دروس ارائه‌شده» پرتال است."
                )
            } else {
                val groupCount = parsed.items.sumOf { it.sections.size }
                val base = "تعداد ${parsed.items.size} درس در $groupCount گروه از فایل پرتال وارد کاتالوگ شد."
                val warningNote = if (parsed.warnings.isNotEmpty()) {
                    " ⚠️ ${parsed.warnings.size} ردیف نیاز به بررسی دارد: ${parsed.warnings.take(2).joinToString("؛ ")}"
                } else ""
                ImportResult.Success(
                    items = parsed.items,
                    message = base + warningNote,
                    warnings = parsed.warnings
                )
            }
        } catch (e: Exception) {
            ImportResult.Failure("خطا در پردازش فایل پرتال: ${e.localizedMessage}")
        }
    }

    /** Header aliases (normalized before compare) for semantic column mapping. */
    private val headerAliases = mapOf(
        "code" to listOf("شماره درس", "کد درس", "شماره", "کد", "code", "lescode"),
        "group" to listOf("گروه", "group"),
        "name" to listOf("نام درس", "عنوان درس", "درس", "name"),
        "credits" to listOf("واحد", "تعداد واحد", "واحدها", "credits"),
        "capacity" to listOf("ظرفیت", "capacity"),
        "instructor" to listOf("نام استاد", "استاد", "مدرس", "instructor")
    )

    private fun normHeaderCell(rawHtml: String): String =
        normalizeDigits(cleanText(rawHtml))
            .replace("[\\s\\u200B-\\u200D\\uFEFF_\\-]+".toRegex(), "")

    /** Maps semantic field → cell index from the <th> header row, if present. */
    internal fun mapColumnsFromHeader(headerRowHtml: String): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val headers = cellRegex.findAll(headerRowHtml).map { normHeaderCell(it.groupValues[1]) }.toList()
        // Header cells may be <th> instead of <td>.
        val thCells = Regex("<th[^>]*>(.*?)</th>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(headerRowHtml).map { normHeaderCell(it.groupValues[1]) }.toList()
        val all = if (thCells.isNotEmpty()) thCells else headers
        all.forEachIndexed { idx, h ->
            for ((field, aliases) in headerAliases) {
                if (field !in map && aliases.any { normHeaderCell(it) == h }) {
                    map[field] = idx
                }
            }
        }
        return map
    }

    private fun cellAt(cells: List<String>, mapped: Map<String, Int>, field: String, legacyIndex: Int): String {
        val idx = mapped[field] ?: legacyIndex
        return if (idx in cells.indices) cells[idx] else ""
    }

    internal data class ParsedRows(
        val items: List<ImportItem>,
        /** Human-readable, capped list of skipped-row reasons. */
        val warnings: List<String>
    )

    internal fun parseRows(html: String): List<ImportItem> = parseRowsDetailed(html).items

    internal fun parseRowsDetailed(html: String): ParsedRows {
        val tableHtml = findCoursesTable(html) ?: return ParsedRows(emptyList(), emptyList())

        // Semantic column mapping from the header row; legacy Pooya positions as fallback.
        var colMap: Map<String, Int> = emptyMap()
        for (rowMatch in rowRegex.findAll(tableHtml)) {
            if (rowMatch.groupValues[1].contains("<th", ignoreCase = true)) {
                colMap = mapColumnsFromHeader(rowMatch.groupValues[1])
                break
            }
        }

        val warnings = mutableListOf<String>()
        var skippedRows = 0
        fun skipRow(reason: String) {
            skippedRows++
            if (warnings.size < 5) warnings.add(reason)
        }

        val coursesMap = linkedMapOf<String, MutableCourseAcc>()
        var rowNumber = 0
        for (rowMatch in rowRegex.findAll(tableHtml)) {
            val rowHtml = rowMatch.groupValues[1]
            if (rowHtml.contains("<th", ignoreCase = true)) continue
            rowNumber++

            val cells = cellRegex.findAll(rowHtml).map { it.groupValues[1] }.toList()
            if (cells.size < 9 && colMap.isEmpty()) continue

            val code = normalizeDigits(cleanText(cellAt(cells, colMap, "code", 1)))
            val groupCode = normalizeDigits(cleanText(cellAt(cells, colMap, "group", 2))).ifBlank { "1" }
            val name = cleanText(cellAt(cells, colMap, "name", 3))
            if (code.isBlank() || name.isBlank()) {
                skipRow("ردیف $rowNumber: کد یا نام درس خالی است؛ نادیده گرفته شد.")
                continue
            }

            // Invalid credits are skipped with a warning — never silently defaulted.
            val creditsRaw = normalizeDigits(cleanText(cellAt(cells, colMap, "credits", 4)))
            val credits = creditsRaw.toDoubleOrNull()?.roundToInt()
            if (credits == null || credits !in 1..20) {
                skipRow("ردیف $rowNumber (کد $code): تعداد واحد نامعتبر («$creditsRaw»)؛ نادیده گرفته شد.")
                continue
            }
            val capacity = normalizeDigits(cleanText(cellAt(cells, colMap, "capacity", 6))).toIntOrNull() ?: 0
            val instructor = cleanText(cellAt(cells, colMap, "instructor", 8)).replace("\\s+".toRegex(), " ").trim()

            val tooltip = normalizeDigits(cleanText(extractTooltip(rowHtml)))
            val sessions = parseSessions(tooltip)
            val exam = parseExam(tooltip)
            val degree = degreeRegex.find(tooltip)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val department = departmentRegex.find(tooltip)?.groupValues?.getOrNull(1)?.trim()
                .orEmpty().ifBlank { "كامپيوتر" }

            val examEnd = if (exam != null && exam.durationMin > 0) {
                val startMin = ClassSession.parseTimeMinutesOrNull(exam.time) ?: 0
                val endMin = startMin + exam.durationMin
                "%02d:%02d".format(endMin / 60, endMin % 60)
            } else ""

            val section = ImportSectionItem(
                section = CourseSection(
                    courseId = 0,
                    sectionCode = groupCode,
                    instructor = instructor,
                    capacity = capacity,
                    examDate = exam?.date.orEmpty(),
                    examStartTime = exam?.time.orEmpty(),
                    examEndTime = examEnd,
                    isEnrolled = false
                ),
                sessions = sessions
            )

            val acc = coursesMap.getOrPut(code) {
                MutableCourseAcc(
                    course = Course(
                        code = code,
                        name = name,
                        department = department,
                        credits = credits,
                        // Catalog-only: never floods "my courses" or the generator.
                        isSelectedForGeneration = false,
                        degree = degree
                    ),
                    sections = mutableListOf()
                )
            }
            if (acc.sections.none { it.section.sectionCode == groupCode }) {
                acc.sections.add(section)
            }
        }

        val items = coursesMap.values.map { acc ->
            ImportItem(course = acc.course, sections = acc.sections)
        }
        return ParsedRows(items, warnings.toList())
    }

    private data class MutableCourseAcc(
        val course: Course,
        val sections: MutableList<ImportSectionItem>
    )

    internal data class ParsedExam(val date: String, val time: String, val durationMin: Int)

    private fun findCoursesTable(html: String): String? {
        // Score every table by how much it looks like the presented-courses
        // list. A first-match rule would grab the filter-form table (faculty
        // <select>) on full-page saves; scoring still lands on the border=1
        // list with the course headers.
        val scored = tableRegex.findAll(html).map { it.groupValues[1] }.map { body ->
            body to scoreCoursesTable(body)
        }.toList()
        return scored.maxByOrNull { it.second }
            ?.takeIf { it.second > 0 }
            ?.first
    }

    private fun extractTooltip(rowHtml: String): String {
        val candidates = titleAttrRegex.findAll(rowHtml).map { it.groupValues[1] } +
            titleAttrSingleRegex.findAll(rowHtml).map { it.groupValues[1] }
        for (raw in candidates) {
            if (raw.contains("جلسه") || raw.contains("body=") ||
                raw.contains("header=") || raw.contains("امتحان")
            ) {
                return raw
            }
        }
        return ""
    }

    internal fun parseSessions(cleanedTooltip: String): List<ClassSession> {
        val sessions = mutableListOf<ClassSession>()
        if (cleanedTooltip.isBlank()) return sessions

        for (m in sessionRegex.findAll(cleanedTooltip)) {
            val day = normalizeDayName(m.groupValues[1].trim())
            if (day.isEmpty()) continue
            val dayOfWeek = dayNameToNumber(day)
            if (dayOfWeek == -1) continue

            val hour = m.groupValues[2].toIntOrNull() ?: continue
            val minute = m.groupValues[3].toIntOrNull() ?: 0
            // Out-of-range class times are skipped, never stored as-is.
            if (hour !in 0..23 || minute !in 0..59) continue
            val details = m.groupValues[4]
            val parity = m.groupValues[5].ifBlank { "هردو" }

            val duration = durationRegex.find(details)?.groupValues?.getOrNull(1)
                ?.toIntOrNull() ?: 90
            // Placeholder rows (project/internship) carry duration 0 and no real class time.
            if (duration == 0) continue

            var location = minutesAfterRegex.find(details)?.groupValues?.getOrNull(1)
                ?.trim().orEmpty()
            if (location == "0") location = ""

            val biweekly = details.contains("هفته در میان")
            // Mirror of the web parser: explicit parity counts only for biweekly
            // sessions; plain "شروع زوج/فرد" on a weekly row stays EVERY_WEEK so the
            // conflict engine treats it conservatively.
            val weekType = if (biweekly) {
                when (parity) {
                    "زوج" -> WeekType.EVEN_WEEKS
                    "فرد" -> WeekType.ODD_WEEKS
                    else -> WeekType.EVERY_WEEK
                }
            } else {
                WeekType.EVERY_WEEK
            }

            val startTotal = hour * 60 + minute
            val endTotal = startTotal + duration
            sessions.add(
                ClassSession(
                    sectionId = 0,
                    dayOfWeek = dayOfWeek,
                    startTime = "%02d:%02d".format(startTotal / 60, startTotal % 60),
                    endTime = "%02d:%02d".format(endTotal / 60, endTotal % 60),
                    location = location,
                    weekType = weekType
                )
            )
        }
        return sessions
    }

    internal fun parseExam(cleanedTooltip: String): ParsedExam? {
        if (cleanedTooltip.isBlank()) return null
        val m = examRegex.find(cleanedTooltip) ?: return null
        val dayDesc = m.groupValues[1].trim()
        val hour = m.groupValues[2].toIntOrNull() ?: return null
        val minute = m.groupValues[3].toIntOrNull() ?: 0
        // Reject out-of-range exam times instead of storing them silently.
        if (hour !in 0..23 || minute !in 0..59) return null
        // 0/absent duration means "unknown duration" (engine warns, never assumes).
        val durationMin = m.groupValues[4].toIntOrNull() ?: 0
        val rawLoc = m.groupValues[5].trim()
        val loc = if (rawLoc == "0") "" else rawLoc
        val date = m.groupValues[6].trim()
        // Dummy portal rows use hour 6, class 0 and no date/day.
        if (date.isEmpty() && loc.isEmpty() && (hour == 6 || hour == 0) && dayDesc.isEmpty()) {
            return null
        }
        return ParsedExam(
            date = date,
            time = "%02d:%02d".format(hour, minute),
            durationMin = durationMin
        )
    }

    internal fun normalizeDayName(day: String): String {
        val clean = day.replace("[\u200B-\u200D\uFEFF]".toRegex(), "")
            .replace("\\s+".toRegex(), " ").trim()
        return when {
            clean.contains("جمعه") -> "جمعه"
            clean.contains("پنج") -> "پنج‌شنبه"
            clean.contains("چهار") -> "چهارشنبه"
            clean.contains("سه") -> "سه‌شنبه"
            clean.contains("دو") -> "دوشنبه"
            clean.contains("یک") -> "یکشنبه"
            clean.contains("شنبه") -> "شنبه"
            else -> ""
        }
    }

    internal fun dayNameToNumber(day: String): Int = when (day) {
        "شنبه" -> 0
        "یکشنبه" -> 1
        "دوشنبه" -> 2
        "سه‌شنبه" -> 3
        "چهارشنبه" -> 4
        "پنج‌شنبه" -> 5
        "جمعه" -> 6
        else -> -1
    }
}
