package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity-aware scheduling logic. Plain JUnit (no Robolectric): the engine is
 * pure Kotlin and the models are plain data classes.
 */
class ScheduleParityTest {

    private fun section(
        courseId: Long,
        day: Int,
        start: String,
        end: String,
        weekType: WeekType = WeekType.EVERY_WEEK
    ): SectionWithDetails {
        return SectionWithDetails(
            section = CourseSection(id = courseId, courseId = courseId, sectionCode = "01"),
            course = Course(id = courseId, code = "C$courseId", name = "درس $courseId"),
            sessions = listOf(
                ClassSession(
                    sectionId = courseId,
                    dayOfWeek = day,
                    startTime = start,
                    endTime = end,
                    weekType = weekType
                )
            )
        )
    }

    @Test
    fun `academic week counts from semester start`() {
        val start = 19802L // a Wednesday; weekday itself does not matter, only elapsed weeks
        assertEquals(
            ScheduleEngine.AcademicWeek(1, ScheduleEngine.WeekParity.ODD),
            ScheduleEngine.academicWeek(start, start, true)
        )
        assertEquals(
            ScheduleEngine.AcademicWeek(2, ScheduleEngine.WeekParity.EVEN),
            ScheduleEngine.academicWeek(start + 7, start, true)
        )
        assertEquals(
            ScheduleEngine.AcademicWeek(2, ScheduleEngine.WeekParity.EVEN),
            ScheduleEngine.academicWeek(start + 13, start, true)
        )
        // Before the semester starts there is no academic week.
        assertNull(ScheduleEngine.academicWeek(start - 1, start, true))
        assertNull(ScheduleEngine.academicWeek(start, null, true))
        // Universities counting week 1 as even flip the parity.
        assertEquals(
            ScheduleEngine.AcademicWeek(1, ScheduleEngine.WeekParity.EVEN),
            ScheduleEngine.academicWeek(start, start, false)
        )
    }

    @Test
    fun `gap averages even and odd weeks`() {
        // Weekly 8-10, EVEN 10-12, weekly 12-14 on Saturday:
        // even weeks gap 0, odd weeks gap 120 -> honest figure is 60.
        val sections = listOf(
            section(1, 0, "08:00", "10:00"),
            section(2, 0, "10:00", "12:00", WeekType.EVEN_WEEKS),
            section(3, 0, "12:00", "14:00")
        )
        assertEquals(60, ScheduleEngine.calculateTotalGaps(sections))
    }

    @Test
    fun `gap unchanged without parity sessions`() {
        val sections = listOf(
            section(1, 0, "08:00", "10:00"),
            section(2, 0, "13:00", "15:00")
        )
        assertEquals(180, ScheduleEngine.calculateTotalGaps(sections))
    }

    @Test
    fun `alternating sessions at same time have no gap`() {
        val sections = listOf(
            section(1, 2, "14:00", "16:00", WeekType.EVEN_WEEKS),
            section(2, 2, "14:00", "16:00", WeekType.ODD_WEEKS)
        )
        assertEquals(0, ScheduleEngine.calculateTotalGaps(sections))
    }

    @Test
    fun `weekly hours average biweekly sessions`() {
        // 2h weekly + 2h even-only -> (4 + 2) / 2 = 3h = 180 min.
        val sections = listOf(
            section(1, 0, "08:00", "10:00"),
            section(2, 2, "14:00", "16:00", WeekType.EVEN_WEEKS)
        )
        assertEquals(180, ScheduleEngine.averageWeeklyMinutes(sections))
        val weeklyOnly = listOf(section(1, 0, "08:00", "10:00"))
        assertEquals(120, ScheduleEngine.averageWeeklyMinutes(weeklyOnly))
    }

    @Test
    fun `occursInWeek matrix`() {
        val every = ClassSession(sectionId = 0, dayOfWeek = 0, startTime = "08:00", endTime = "10:00")
        val even = every.copy(weekType = WeekType.EVEN_WEEKS)
        val odd = every.copy(weekType = WeekType.ODD_WEEKS)
        assertTrue(ScheduleEngine.occursInWeek(every, ScheduleEngine.WeekParity.EVEN))
        assertTrue(ScheduleEngine.occursInWeek(every, ScheduleEngine.WeekParity.ODD))
        assertTrue(ScheduleEngine.occursInWeek(even, ScheduleEngine.WeekParity.EVEN))
        assertFalse(ScheduleEngine.occursInWeek(even, ScheduleEngine.WeekParity.ODD))
        assertFalse(ScheduleEngine.occursInWeek(odd, ScheduleEngine.WeekParity.EVEN))
        assertTrue(ScheduleEngine.occursInWeek(odd, ScheduleEngine.WeekParity.ODD))
        assertTrue(ScheduleEngine.occursInWeek(odd, null))
    }

    @Test
    fun `today list and next session respect week parity`() {
        val oddOnly = section(1, 0, "08:00", "10:00", WeekType.ODD_WEEKS)
        val evenOnly = section(2, 0, "08:00", "10:00", WeekType.EVEN_WEEKS)
        val both = listOf(oddOnly, evenOnly)

        // Even academic week: only the even session is today / ongoing.
        val evenToday = ScheduleEngine.sessionsOnDay(both, 0, ScheduleEngine.WeekParity.EVEN)
        assertEquals(1, evenToday.size)
        assertEquals(WeekType.EVEN_WEEKS, evenToday[0].session.weekType)
        val evenNext = ScheduleEngine.nextUpcomingSession(both, 0, 510, ScheduleEngine.WeekParity.EVEN)
        assertNotNull(evenNext)
        assertEquals(WeekType.EVEN_WEEKS, evenNext?.session?.weekType)
        assertEquals(true, evenNext?.ongoing)

        // Odd academic week: only the odd session is today / ongoing.
        val oddToday = ScheduleEngine.sessionsOnDay(both, 0, ScheduleEngine.WeekParity.ODD)
        assertEquals(1, oddToday.size)
        assertEquals(WeekType.ODD_WEEKS, oddToday[0].session.weekType)
        val oddNext = ScheduleEngine.nextUpcomingSession(both, 0, 510, ScheduleEngine.WeekParity.ODD)
        assertNotNull(oddNext)
        assertEquals(WeekType.ODD_WEEKS, oddNext?.session?.weekType)
        assertEquals(true, oddNext?.ongoing)

        // Unknown parity: legacy unfiltered behavior is kept.
        assertEquals(2, ScheduleEngine.sessionsOnDay(both, 0, null).size)
        assertNotNull(ScheduleEngine.nextUpcomingSession(both, 0, 510, null))
    }
}
