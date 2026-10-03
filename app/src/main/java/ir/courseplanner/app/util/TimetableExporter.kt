package ir.courseplanner.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType

object TimetableExporter {

    /**
     * Generates a beautifully formatted Persian plain-text summary of the student's weekly schedule.
     */
    fun formatScheduleAsText(
        sections: List<SectionWithDetails>,
        studentName: String = "",
        major: String = "",
        semester: String = ""
    ): String {
        if (sections.isEmpty()) {
            return "هنوز کلاسی در برنامه هفتگی انتخاب نشده است."
        }

        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("🗓 برنامه هفتگی انتخاب واحد دانشگاه")
        sb.appendLine("═══════════════════════════")

        if (studentName.isNotBlank() || major.isNotBlank() || semester.isNotBlank()) {
            val studentPart = if (studentName.isNotBlank()) "دانشجو: $studentName" else ""
            val majorPart = if (major.isNotBlank()) "رشته: $major" else ""
            val semPart = if (semester.isNotBlank()) semester else ""
            val infoLine = listOf(studentPart, majorPart, semPart).filter { it.isNotBlank() }.joinToString(" • ")
            sb.appendLine(infoLine)
            sb.appendLine("───────────────────────────")
        }

        val totalCredits = sections.sumOf { it.course.credits }
        sb.appendLine("تعداد دروس: ${sections.size} درس | مجموع واحدها: $totalCredits واحد")
        sb.appendLine()

        // Group sessions by day of week (0: Saturday to 5: Thursday)
        val dayNames = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه")

        for (dayIndex in 0..5) {
            val daySessions = mutableListOf<Pair<SectionWithDetails, ClassSession>>()
            for (sec in sections) {
                for (session in sec.sessions) {
                    if (session.dayOfWeek == dayIndex) {
                        daySessions.add(sec to session)
                    }
                }
            }

            if (daySessions.isNotEmpty()) {
                val dayName = dayNames.getOrElse(dayIndex) { "روز $dayIndex" }
                sb.appendLine("📌 $dayName:")
                daySessions.sortBy { it.second.startMinutes }

                for ((sec, session) in daySessions) {
                    val weekNote = when (session.weekType) {
                        WeekType.EVEN_WEEKS -> " (هفته‌های زوج)"
                        WeekType.ODD_WEEKS -> " (هفته‌های فرد)"
                        else -> ""
                    }
                    val loc = if (session.location.isNotBlank()) " | کلاس ${session.location}" else ""
                    val inst = if (sec.section.instructor.isNotBlank()) " | استاد: ${sec.section.instructor}" else ""

                    sb.appendLine("  • ${sec.course.name} (گروه ${sec.section.sectionCode})")
                    sb.appendLine("    ⏰ ${session.startTime} تا ${session.endTime}$weekNote$loc$inst")
                }
                sb.appendLine()
            }
        }

        // Exam Schedule Section
        val examList = sections.filter { it.section.examDate.isNotBlank() }
            .sortedBy { it.section.examDate }

        if (examList.isNotEmpty()) {
            sb.appendLine("───────────────────────────")
            sb.appendLine("📝 برنامه امتحانات پایان‌ترم:")
            for (sec in examList) {
                val time = if (sec.section.examStartTime.isNotBlank()) "ساعت ${sec.section.examStartTime}" else ""
                sb.appendLine("  • ${sec.course.name}: ${sec.section.examDate} $time")
            }
            sb.appendLine("───────────────────────────")
        }

        sb.appendLine("ایجاد شده توسط TermChin | ترم‌چین")
        return sb.toString().trim()
    }

    /**
     * Copies arbitrary plain text to the clipboard and shows a toast.
     * Used by the JSON-export dialog (and anything else that only shows
     * text today but should let the user take it elsewhere).
     */
    fun copyToClipboard(
        context: Context,
        sections: List<SectionWithDetails>,
        studentName: String = "",
        major: String = "",
        semester: String = ""
    ) {
        val text = formatScheduleAsText(sections, studentName, major, semester)
        copyTextToClipboard(context, text, "Weekly Schedule", "متن مرتب برنامه در کلیپ‌بورد کپی شد")
    }

    /**
     * Copies [text] to the clipboard under [label] and shows [toastMessage].
     * Single clipboard choke-point: every copy in the app funnels through
     * here, so permission/toast behaviour stays in one place.
     */
    fun copyTextToClipboard(
        context: Context,
        text: String,
        label: String,
        toastMessage: String
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    /**
     * Opens Android's native share sheet (100% offline).
     */

    /**
     * Opens Android's native share sheet (100% offline).
     */
    fun shareSchedule(
        context: Context,
        sections: List<SectionWithDetails>,
        studentName: String = "",
        major: String = "",
        semester: String = ""
    ) {
        val text = formatScheduleAsText(sections, studentName, major, semester)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "اشتراک‌گذاری برنامه هفتگی")
        context.startActivity(shareIntent)
    }
}
