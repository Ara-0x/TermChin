package ir.courseplanner.app.util

import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableExporterTest {

    @Test
    fun formatScheduleAsText_emptyList_returnsEmptyMessage() {
        val result = TimetableExporter.formatScheduleAsText(emptyList())
        assertTrue(result.contains("هنوز کلاسی"))
    }

    @Test
    fun formatScheduleAsText_withCourses_formatsCorrectly() {
        val course = Course(id = 1, code = "101", name = "ریاضی عمومی ۱", credits = 3, department = "علوم پایه")
        val section = CourseSection(
            id = 1,
            courseId = 1,
            sectionCode = "01",
            instructor = "دکتر رضایی",
            examDate = "1403/10/25",
            examStartTime = "09:00",
            examEndTime = "11:00",
            isEnrolled = true
        )
        val session = ClassSession(
            id = 1,
            sectionId = 1,
            dayOfWeek = 0, // Saturday (شنبه)
            startTime = "08:00",
            endTime = "10:00",
            location = "۱۰۱",
            weekType = WeekType.EVERY_WEEK
        )
        val sectionWithDetails = SectionWithDetails(
            section = section,
            course = course,
            sessions = listOf(session)
        )

        val text = TimetableExporter.formatScheduleAsText(listOf(sectionWithDetails))

        assertTrue(text.contains("ریاضی عمومی ۱"))
        assertTrue(text.contains("دکتر رضایی"))
        assertTrue(text.contains("شنبه"))
        assertTrue(text.contains("08:00–10:00"))
        assertTrue(text.contains("1403/10/25"))
        assertTrue(text.contains("3 واحد"))
    }
}
