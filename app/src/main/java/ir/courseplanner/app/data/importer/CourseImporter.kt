package ir.courseplanner.app.data.importer

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.WeekType
import org.json.JSONArray
import org.json.JSONObject

data class ImportItem(
    val course: Course,
    val sections: List<ImportSectionItem>
)

data class ImportSectionItem(
    val section: CourseSection,
    val sessions: List<ClassSession>
)

sealed class ImportResult {
    data class Success(
        val items: List<ImportItem>,
        val message: String,
        /** Non-fatal row-level issues (skipped rows); UI shows them as "needs review". */
        val warnings: List<String> = emptyList()
    ) : ImportResult()
    data class Failure(val errorMessage: String) : ImportResult()
}

object CourseImporter {

    fun parseWeekType(str: String?): WeekType {
        val s = (str ?: "").trim().lowercase()
        return when {
            s.contains("even") || s.contains("زوج") -> WeekType.EVEN_WEEKS
            s.contains("odd") || s.contains("فرد") -> WeekType.ODD_WEEKS
            else -> WeekType.EVERY_WEEK
        }
    }

    /**
     * Parses and validates JSON course string.
     */
    fun parseJson(jsonString: String): ImportResult {
        if (jsonString.isBlank()) {
            return ImportResult.Failure("متن JSON خالی است / JSON input is empty.")
        }

        try {
            val jsonArray = try {
                JSONArray(jsonString.trim())
            } catch (e: Exception) {
                // Try wrapping in array if root was a single object
                try {
                    val singleObj = JSONObject(jsonString.trim())
                    JSONArray().put(singleObj)
                } catch (ex: Exception) {
                    return ImportResult.Failure("ساختار JSON نامعتبر است: ${e.localizedMessage}")
                }
            }

            if (jsonArray.length() == 0) {
                return ImportResult.Failure("هیچ درسی در لیست JSON یافت نشد / No courses found in JSON array.")
            }

            val parsedItems = mutableListOf<ImportItem>()

            for (i in 0 until jsonArray.length()) {
                val courseObj = jsonArray.optJSONObject(i)
                    ?: return ImportResult.Failure("ردیف ${i + 1}: آیتم معتبر JSON نیست.")

                val code = courseObj.optString("code", "").trim()
                val name = courseObj.optString("name", "").trim()
                val department = courseObj.optString("department", "").trim()
                val credits = courseObj.optInt("credits", 3)
                // Null = unknown degree (old files); blank is normalized to null.
                val degree = courseObj.optString("degree", "").trim().ifBlank { null }

                if (code.isBlank()) {
                    return ImportResult.Failure("ردیف ${i + 1}: کد درس (code) نمی‌تواند خالی باشد.")
                }
                if (name.isBlank()) {
                    return ImportResult.Failure("ردیف ${i + 1} (کد: $code): نام درس (name) نمی‌تواند خالی باشد.")
                }
                if (credits <= 0 || credits > 20) {
                    return ImportResult.Failure("درس $code: تعداد واحد ($credits) باید بین ۱ تا ۲۰ باشد.")
                }

                val course = Course(
                    code = code,
                    name = name,
                    department = department,
                    credits = credits,
                    isSelectedForGeneration = true,
                    degree = degree
                )

                val sectionItems = mutableListOf<ImportSectionItem>()
                val sectionsArray = courseObj.optJSONArray("sections")

                if (sectionsArray != null && sectionsArray.length() > 0) {
                    for (j in 0 until sectionsArray.length()) {
                        val secObj = sectionsArray.getJSONObject(j)
                        val secCode = secObj.optString("sectionCode", String.format("%02d", j + 1)).trim()
                        val instructor = secObj.optString("instructor", "").trim()
                        val capacity = secObj.optInt("capacity", 30)
                        val examDate = secObj.optString("examDate", "").trim()
                        val examStartTime = secObj.optString("examStartTime", "").trim()
                        val examEndTime = secObj.optString("examEndTime", "").trim()

                        val sessions = mutableListOf<ClassSession>()
                        val sessionsArray = secObj.optJSONArray("sessions")

                        if (sessionsArray != null) {
                            for (k in 0 until sessionsArray.length()) {
                                val sessObj = sessionsArray.getJSONObject(k)

                                val dayOfWeek = sessObj.optInt("dayOfWeek", -1)
                                if (dayOfWeek !in 0..6) {
                                    return ImportResult.Failure("درس $code گروه $secCode جلسه ${k + 1}: روز نامعتبر ($dayOfWeek). باید عددی بین ۰ (شنبه) تا ۶ (جمعه) باشد.")
                                }

                                val startTime = sessObj.optString("startTime", "").trim()
                                val endTime = sessObj.optString("endTime", "").trim()
                                val location = sessObj.optString("location", "").trim()
                                val weekType = parseWeekType(sessObj.optString("weekType", ""))

                                if (!isValidTime(startTime) || !isValidTime(endTime)) {
                                    return ImportResult.Failure("درس $code گروه $secCode جلسه ${k + 1}: زمان شروع یا پایان نامعتبر است ($startTime - $endTime).")
                                }

                                if (ClassSession.timeToMinutes(startTime) >= ClassSession.timeToMinutes(endTime)) {
                                    return ImportResult.Failure("درس $code گروه $secCode: زمان شروع ($startTime) باید قبل از زمان پایان ($endTime) باشد.")
                                }

                                sessions.add(
                                    ClassSession(
                                        sectionId = 0,
                                        dayOfWeek = dayOfWeek,
                                        startTime = startTime,
                                        endTime = endTime,
                                        location = location,
                                        weekType = weekType
                                    )
                                )
                            }
                        }

                        sectionItems.add(
                            ImportSectionItem(
                                section = CourseSection(
                                    courseId = 0,
                                    sectionCode = secCode,
                                    instructor = instructor,
                                    capacity = capacity,
                                    examDate = examDate,
                                    examStartTime = examStartTime,
                                    examEndTime = examEndTime
                                ),
                                sessions = sessions
                            )
                        )
                    }
                } else {
                    // Provide a default section if none specified
                    sectionItems.add(
                        ImportSectionItem(
                            section = CourseSection(courseId = 0, sectionCode = "01"),
                            sessions = emptyList()
                        )
                    )
                }

                parsedItems.add(ImportItem(course = course, sections = sectionItems))
            }

            return ImportResult.Success(
                items = parsedItems,
                message = "تعداد ${parsedItems.size} درس با موفقیت بارگذاری شد."
            )
        } catch (e: Exception) {
            return ImportResult.Failure("خطا در پردازش JSON: ${e.localizedMessage}")
        }
    }

    /**
     * Parses and validates CSV string.
     * Expected CSV Header:
     * course_code,course_name,department,credits,section_code,instructor,capacity,exam_date,exam_start,exam_end,day_of_week,start_time,end_time,location,week_type,degree
     * (degree is an optional trailing column; older files without it still parse.)
     */
    fun parseCsv(csvString: String): ImportResult {
        if (csvString.isBlank()) {
            return ImportResult.Failure("متن CSV خالی است / CSV input is empty.")
        }

        try {
            val lines = csvString.trim().lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return ImportResult.Failure("متن CSV فاقد داده است.")
            }

            val startIndex = if (lines[0].contains("course_code", ignoreCase = true) ||
                lines[0].contains("کد", ignoreCase = true)) 1 else 0

            if (startIndex >= lines.size) {
                return ImportResult.Failure("فایل CSV تنها حاوی سرستون‌هاست و داده‌ای ندارد.")
            }

            val courseMap = mutableMapOf<String, MutableMap<String, Pair<CourseSection, MutableList<ClassSession>>>>()
            val courseMetadata = mutableMapOf<String, Course>()

            for (idx in startIndex until lines.size) {
                val line = lines[idx].trim()
                if (line.isBlank() || line.startsWith("#")) continue

                val cols = parseCsvLine(line)
                val lineNum = idx + 1

                if (cols.size < 4) {
                    return ImportResult.Failure("خطای خط $lineNum: حداقل ۴ ستون (کد، نام، دانشکده، واحد) الزامی است.")
                }

                val code = cols[0].trim()
                val name = cols[1].trim()
                val department = cols.getOrNull(2)?.trim() ?: ""
                val credits = cols.getOrNull(3)?.trim()?.toIntOrNull() ?: 3

                if (code.isBlank() || name.isBlank()) {
                    return ImportResult.Failure("خطای خط $lineNum: کد و نام درس نمی‌تواند خالی باشد.")
                }

                val sectionCode = cols.getOrNull(4)?.trim()?.ifBlank { "01" } ?: "01"
                val instructor = cols.getOrNull(5)?.trim() ?: ""
                val capacity = cols.getOrNull(6)?.trim()?.toIntOrNull() ?: 30
                val examDate = cols.getOrNull(7)?.trim() ?: ""
                val examStart = cols.getOrNull(8)?.trim() ?: ""
                val examEnd = cols.getOrNull(9)?.trim() ?: ""

                val dayOfWeekStr = cols.getOrNull(10) ?: ""
                val startTime = cols.getOrNull(11) ?: ""
                val endTime = cols.getOrNull(12) ?: ""
                val location = cols.getOrNull(13) ?: ""
                val weekTypeStr = cols.getOrNull(14) ?: ""
                // Optional trailing column; older files without it still parse.
                val degree = cols.getOrNull(15)?.trim()?.ifBlank { null }

                if (!courseMetadata.containsKey(code)) {
                    courseMetadata[code] = Course(
                        code = code,
                        name = name,
                        department = department,
                        credits = credits,
                        degree = degree
                    )
                }

                val sectionsForCourse = courseMap.getOrPut(code) { mutableMapOf() }
                val sectionPair = sectionsForCourse.getOrPut(sectionCode) {
                    Pair(
                        CourseSection(
                            courseId = 0,
                            sectionCode = sectionCode,
                            instructor = instructor,
                            capacity = capacity,
                            examDate = examDate,
                            examStartTime = examStart,
                            examEndTime = examEnd
                        ),
                        mutableListOf()
                    )
                }

                if (dayOfWeekStr.isNotBlank() && startTime.isNotBlank() && endTime.isNotBlank()) {
                    val dayOfWeek = parseDay(dayOfWeekStr)
                    if (dayOfWeek == -1) {
                        return ImportResult.Failure("خطای خط $lineNum: روز هفته «$dayOfWeekStr» نامعتبر است (باید عدد ۰ تا ۶ یا نام روز مانند شنبه باشد).")
                    }
                    if (!isValidTime(startTime) || !isValidTime(endTime)) {
                        return ImportResult.Failure("خطای خط $lineNum: ساعت کلاس نامعتبر است ($startTime - $endTime). فرمت باید HH:mm باشد.")
                    }
                    if (ClassSession.timeToMinutes(startTime) >= ClassSession.timeToMinutes(endTime)) {
                        return ImportResult.Failure("خطای خط $lineNum: ساعت شروع ($startTime) باید قبل از ساعت پایان ($endTime) باشد.")
                    }

                    sectionPair.second.add(
                        ClassSession(
                            sectionId = 0,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            location = location,
                            weekType = parseWeekType(weekTypeStr)
                        )
                    )
                }
            }

            val resultItems = courseMetadata.map { (code, course) ->
                val sections = courseMap[code]?.map { (_, secPair) ->
                    ImportSectionItem(section = secPair.first, sessions = secPair.second)
                } ?: emptyList()
                ImportItem(course = course, sections = sections)
            }

            return ImportResult.Success(
                items = resultItems,
                message = "تعداد ${resultItems.size} درس از فایل CSV با موفقیت وارد شد."
            )
        } catch (e: Exception) {
            return ImportResult.Failure("خطا در پردازش CSV: ${e.localizedMessage}")
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        result.add(current.toString().trim())
                        current = StringBuilder()
                    }
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private fun parseDay(dayStr: String): Int {
        val clean = dayStr.trim().lowercase()
        clean.toIntOrNull()?.let {
            if (it in 0..6) return it
        }
        return when {
            clean.contains("شنبه") && !clean.contains("یک") && !clean.contains("دو") && !clean.contains("سه") && !clean.contains("چهار") && !clean.contains("پنج") -> 0
            clean.contains("یک") || clean.contains("sun") -> 1
            clean.contains("دو") || clean.contains("mon") -> 2
            clean.contains("سه") || clean.contains("tue") -> 3
            clean.contains("چهار") || clean.contains("wed") -> 4
            clean.contains("پنج") || clean.contains("thu") -> 5
            clean.contains("جمعه") || clean.contains("fri") -> 6
            clean.contains("sat") -> 0
            else -> -1
        }
    }

    private fun isValidTime(time: String): Boolean {
        // Strict + digit-aware: "۸:۳۰" passes, "abc"/"25:90" fail (never silent 00:00).
        return ClassSession.parseTimeMinutesOrNull(time) != null
    }

    fun getSampleCatalog(): List<ImportItem> {
        return listOf(
            ImportItem(
                course = Course(code = "MATH101", name = "ریاضی عمومی ۱", department = "علوم ریاضی", credits = 4),
                sections = listOf(
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "01", instructor = "دکتر معتمدی",
                            capacity = 45, examDate = "1403/10/20", examStartTime = "08:30", examEndTime = "11:30"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 0, startTime = "08:00", endTime = "10:00", location = "تالار ریاضی ۱"),
                            ClassSession(sectionId = 0, dayOfWeek = 2, startTime = "08:00", endTime = "10:00", location = "تالار ریاضی ۱")
                        )
                    ),
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "02", instructor = "دکتر شمس",
                            capacity = 40, examDate = "1403/10/20", examStartTime = "08:30", examEndTime = "11:30"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 1, startTime = "10:00", endTime = "12:00", location = "تالار ریاضی ۲"),
                            ClassSession(sectionId = 0, dayOfWeek = 3, startTime = "10:00", endTime = "12:00", location = "تالار ریاضی ۲")
                        )
                    )
                )
            ),
            ImportItem(
                course = Course(code = "PHYS101", name = "فیزیک ۱", department = "دانشکده فیزیک", credits = 3),
                sections = listOf(
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "01", instructor = "دکتر فیروزبخت",
                            capacity = 50, examDate = "1403/10/24", examStartTime = "13:30", examEndTime = "16:00"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 0, startTime = "10:00", endTime = "12:00", location = "آمفی‌تئاتر فیزیک"),
                            ClassSession(sectionId = 0, dayOfWeek = 2, startTime = "10:00", endTime = "12:00", location = "آمفی‌تئاتر فیزیک")
                        )
                    ),
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "02", instructor = "دکتر نوری",
                            capacity = 40, examDate = "1403/10/24", examStartTime = "13:30", examEndTime = "16:00"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 0, startTime = "08:00", endTime = "10:00", location = "کلاس ۲۰۳ فیزیک"),
                            ClassSession(sectionId = 0, dayOfWeek = 4, startTime = "08:00", endTime = "10:00", location = "کلاس ۲۰۳ فیزیک")
                        )
                    )
                )
            ),
            ImportItem(
                course = Course(code = "PHYS101L", name = "آزمایشگاه فیزیک ۱", department = "دانشکده فیزیک", credits = 1),
                sections = listOf(
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "01", instructor = "مهندس عباسی",
                            capacity = 20, examDate = "1403/10/18", examStartTime = "14:00", examEndTime = "16:00"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 2, startTime = "14:00", endTime = "16:00", location = "آزمایشگاه فیزیک", weekType = WeekType.EVEN_WEEKS)
                        )
                    ),
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "02", instructor = "مهندس عباسی",
                            capacity = 20, examDate = "1403/10/18", examStartTime = "14:00", examEndTime = "16:00"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 2, startTime = "14:00", endTime = "16:00", location = "آزمایشگاه فیزیک", weekType = WeekType.ODD_WEEKS)
                        )
                    )
                )
            ),
            ImportItem(
                course = Course(code = "CS101", name = "مبانی کامپیوتر و برنامه‌نویسی", department = "مهندسی کامپیوتر", credits = 3),
                sections = listOf(
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "01", instructor = "دکتر پاک‌سرشت",
                            capacity = 35, examDate = "1403/10/27", examStartTime = "09:00", examEndTime = "12:00"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 1, startTime = "13:30", endTime = "15:30", location = "سایت کامپیوتر ۱", weekType = WeekType.EVERY_WEEK),
                            ClassSession(sectionId = 0, dayOfWeek = 3, startTime = "13:30", endTime = "15:30", location = "سایت کامپیوتر ۱", weekType = WeekType.ODD_WEEKS) // Alternate week lab/tutorial
                        )
                    ),
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "02", instructor = "مهندس سعیدی",
                            capacity = 35, examDate = "1403/10/27", examStartTime = "09:00", examEndTime = "12:00"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 0, startTime = "13:30", endTime = "15:30", location = "سایت کامپیوتر ۲"),
                            ClassSession(sectionId = 0, dayOfWeek = 2, startTime = "13:30", endTime = "15:30", location = "سایت کامپیوتر ۲")
                        )
                    )
                )
            ),
            ImportItem(
                course = Course(code = "CS202", name = "مدارهای منطقی", department = "مهندسی کامپیوتر", credits = 3),
                sections = listOf(
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "01", instructor = "دکتر زارع",
                            capacity = 30, examDate = "1403/10/29", examStartTime = "14:00", examEndTime = "16:30"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 1, startTime = "08:00", endTime = "10:00", location = "کلاس ۱۰۴"),
                            ClassSession(sectionId = 0, dayOfWeek = 3, startTime = "08:00", endTime = "10:00", location = "کلاس ۱۰۴")
                        )
                    )
                )
            ),
            ImportItem(
                course = Course(code = "ENG101", name = "زبان عمومی دانشگاهی", department = "زبان‌های خارجی", credits = 2),
                sections = listOf(
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "01", instructor = "استاد حسنی",
                            capacity = 40, examDate = "1403/11/02", examStartTime = "10:00", examEndTime = "12:00"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 4, startTime = "10:00", endTime = "12:00", location = "کلاس زبان ۱")
                        )
                    ),
                    ImportSectionItem(
                        section = CourseSection(
                            courseId = 0, sectionCode = "02", instructor = "استاد رضازاده",
                            capacity = 40, examDate = "1403/11/02", examStartTime = "10:00", examEndTime = "12:00"
                        ),
                        sessions = listOf(
                            ClassSession(sectionId = 0, dayOfWeek = 2, startTime = "10:00", endTime = "12:00", location = "کلاس زبان ۲")
                        )
                    )
                )
            )
        )
    }

    /**
     * Exports current list of courses & sections to CSV using the exact column
     * layout [parseCsv] accepts (one row per class session; sections without
     * sessions still get one row with empty session columns), so an export
     * always re-imports cleanly. Fields containing commas/quotes/newlines are
     * quoted per RFC 4180.
     */
    fun exportToCsv(items: List<ImportItem>): String {
        val header = "course_code,course_name,department,credits,section_code,instructor," +
            "capacity,exam_date,exam_start,exam_end,day_of_week,start_time,end_time,location,week_type,degree"
        val lines = mutableListOf(header)
        for (item in items) {
            if (item.sections.isEmpty()) {
                lines.add(
                    listOf(
                        item.course.code, item.course.name, item.course.department,
                        item.course.credits.toString(), "", "", "", "", "", "",
                        "", "", "", "", "", item.course.degree.orEmpty()
                    ).joinToString(",") { escapeCsvField(it) }
                )
                continue
            }
            for (sec in item.sections) {
                if (sec.sessions.isEmpty()) {
                    lines.add(
                        csvRow(
                            item, sectionCode = sec.section.sectionCode,
                            instructor = sec.section.instructor, capacity = sec.section.capacity,
                            examDate = sec.section.examDate, examStart = sec.section.examStartTime,
                            examEnd = sec.section.examEndTime, day = "", start = "", end = "",
                            location = "", weekType = ""
                        )
                    )
                    continue
                }
                for (sess in sec.sessions) {
                    lines.add(
                        csvRow(
                            item, sectionCode = sec.section.sectionCode,
                            instructor = sec.section.instructor, capacity = sec.section.capacity,
                            examDate = sec.section.examDate, examStart = sec.section.examStartTime,
                            examEnd = sec.section.examEndTime, day = sess.dayOfWeek.toString(),
                            start = sess.startTime, end = sess.endTime,
                            location = sess.location,
                            weekType = if (sess.weekType == WeekType.EVERY_WEEK) "" else sess.weekType.name
                        )
                    )
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun csvRow(
        item: ImportItem,
        sectionCode: String,
        instructor: String,
        capacity: Int,
        examDate: String,
        examStart: String,
        examEnd: String,
        day: String,
        start: String,
        end: String,
        location: String,
        weekType: String
    ): String = listOf(
        item.course.code, item.course.name, item.course.department,
        item.course.credits.toString(), sectionCode, instructor, capacity.toString(),
        examDate, examStart, examEnd, day, start, end, location, weekType,
        item.course.degree.orEmpty()
    ).joinToString(",") { escapeCsvField(it) }

    private fun escapeCsvField(value: String): String {
        return if (value.contains(',') || value.contains('"') ||
            value.contains('\n') || value.contains('\r')
        ) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
    /**
     * Exports current list of courses & sections to clean formatted JSON.
     */
    fun exportToJson(items: List<ImportItem>): String {
        val root = JSONArray()
        for (item in items) {
            val cObj = JSONObject()
            cObj.put("code", item.course.code)
            cObj.put("name", item.course.name)
            cObj.put("department", item.course.department)
            cObj.put("credits", item.course.credits)
            cObj.put("degree", item.course.degree.orEmpty())

            val secArr = JSONArray()
            for (sec in item.sections) {
                val sObj = JSONObject()
                sObj.put("sectionCode", sec.section.sectionCode)
                sObj.put("instructor", sec.section.instructor)
                sObj.put("capacity", sec.section.capacity)
                sObj.put("examDate", sec.section.examDate)
                sObj.put("examStartTime", sec.section.examStartTime)
                sObj.put("examEndTime", sec.section.examEndTime)

                val sessArr = JSONArray()
                for (sess in sec.sessions) {
                    val ssObj = JSONObject()
                    ssObj.put("dayOfWeek", sess.dayOfWeek)
                    ssObj.put("startTime", sess.startTime)
                    ssObj.put("endTime", sess.endTime)
                    ssObj.put("location", sess.location)
                    if (sess.weekType != WeekType.EVERY_WEEK) {
                        ssObj.put("weekType", sess.weekType.name)
                    }
                    sessArr.put(ssObj)
                }
                sObj.put("sessions", sessArr)
                secArr.put(sObj)
            }
            cObj.put("sections", secArr)
            root.put(cObj)
        }
        return root.toString(2)
    }
}
