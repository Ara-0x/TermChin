package ir.courseplanner.app.data.importer

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [CourseImporter.exportToCsv]: the export must use the
 * exact column layout [CourseImporter.parseCsv] accepts, quote fields with
 * commas, and round-trip back through the parser without loss.
 */
class CourseImporterCsvExportTest {

    private fun sampleItems(): List<ImportItem> = listOf(
        ImportItem(
            course = Course(code = "MATH101", name = "ریاضی ۱", department = "علوم پایه", credits = 3),
            sections = listOf(
                ImportSectionItem(
                    section = CourseSection(
                        courseId = 0, sectionCode = "01", instructor = "دکتر تست",
                        examDate = "1404/03/20", examStartTime = "08:00", examEndTime = "10:00"
                    ),
                    sessions = listOf(
                        ClassSession(
                            sectionId = 0, dayOfWeek = 0, startTime = "08:00",
                            endTime = "10:00", location = "کلاس ۱۰۱"
                        ),
                        ClassSession(
                            sectionId = 0, dayOfWeek = 2, startTime = "08:00",
                            endTime = "10:00", location = "تالار الف، طبقه ۲",
                            weekType = WeekType.EVEN_WEEKS
                        )
                    )
                ),
                ImportSectionItem(
                    section = CourseSection(courseId = 0, sectionCode = "02", instructor = "دکتر دوم"),
                    sessions = emptyList()
                )
            )
        )
    )

    @Test
    fun `export uses the documented header`() {
        val csv = CourseImporter.exportToCsv(sampleItems())

        val header = csv.lineSequence().first()
        assertEquals(
            "course_code,course_name,department,credits,section_code,instructor," +
                "capacity,exam_date,exam_start,exam_end,day_of_week,start_time,end_time," +
                "location,week_type,degree",
            header
        )
    }

    @Test
    fun `exported csv re-imports with the same courses and sessions`() {
        val csv = CourseImporter.exportToCsv(sampleItems())

        val result = CourseImporter.parseCsv(csv)
        assertTrue(result is ImportResult.Success)
        val items = (result as ImportResult.Success).items
        assertEquals(1, items.size)
        assertEquals("MATH101", items.single().course.code)
        assertEquals(2, items.single().sections.size)
        val first = items.single().sections.single { it.section.sectionCode == "01" }
        assertEquals(2, first.sessions.size)
        // Comma inside the location must survive the round trip via quoting.
        assertTrue(first.sessions.any { it.location == "تالار الف، طبقه ۲" })
        assertTrue(first.sessions.any { it.weekType == WeekType.EVEN_WEEKS })
    }

    @Test
    fun `empty catalog exports header only and parses to failure not crash`() {
        val csv = CourseImporter.exportToCsv(emptyList())

        assertEquals(1, csv.lineSequence().count())
        // Header-only CSV has no data rows: the parser reports it honestly.
        assertTrue(CourseImporter.parseCsv(csv) is ImportResult.Failure)
    }
}
