package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.SectionWithSessions
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.engine.CourseSuggestionRanker.GroupVerdict
import ir.courseplanner.app.engine.CourseSuggestionRanker.SuggestionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests (no Robolectric) for the per-course discovery ranking.
 *
 * Each test builds catalog groups from plain data classes and checks the
 * explainable verdicts, the deterministic order and the honesty rules:
 * incomplete time data is never treated as valid, a course with one
 * compatible group is never reported as unusable, and equal ranks fall back
 * to a fixed code/name/id order.
 */
class CourseSuggestionRankerTest {

    private var nextId = 1L

    private fun session(
        day: Int,
        start: String,
        end: String,
        weekType: WeekType = WeekType.EVERY_WEEK
    ) = ClassSession(
        sectionId = 0,
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        weekType = weekType
    )

    private fun section(
        courseId: Long,
        code: String,
        sessions: List<ClassSession>,
        examDate: String = "",
        examStart: String = "",
        examEnd: String = "",
        enrolled: Boolean = false,
        instructor: String = "استاد"
    ): SectionWithDetails {
        val id = nextId++
        val entity = CourseSection(
            id = id,
            courseId = courseId,
            sectionCode = code,
            instructor = instructor,
            examDate = examDate,
            examStartTime = examStart,
            examEndTime = examEnd,
            isEnrolled = enrolled
        )
        return SectionWithDetails(
            section = entity,
            course = Course(id = courseId, code = "C$courseId", name = "درس $courseId"),
            sessions = sessions.map { it.copy(sectionId = id) }
        )
    }

    private fun catalogEntry(
        course: Course,
        sections: List<SectionWithDetails>
    ) = CourseWithSections(
        course = course,
        sections = sections.map {
            SectionWithSessions(section = it.section, sessions = it.sessions)
        }
    )

    private fun course(id: Long, code: String, name: String, selected: Boolean = false) =
        Course(id = id, code = code, name = name, isSelectedForGeneration = selected)

    private fun rank(
        catalog: List<CourseWithSections>,
        byCourse: Map<Long, List<SectionWithDetails>>,
        enrolled: List<SectionWithDetails> = emptyList(),
        preference: OptimizationPreference = OptimizationPreference.BALANCED
    ) = CourseSuggestionRanker.rank(catalog, byCourse, enrolled, preference)

    @Test
    fun `course with one conflict-free group is compatible`() {
        val enrolledSec = section(courseId = 1, code = "01", sessions = listOf(session(0, "08:00", "10:00")))
        val candidate = section(courseId = 2, code = "01", sessions = listOf(session(1, "08:00", "10:00")))
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(candidate)))

        val result = rank(catalog, mapOf(2L to listOf(candidate)), listOf(enrolledSec))

        assertEquals(1, result.size)
        val suggestion = result.single()
        assertEquals(SuggestionStatus.COMPATIBLE, suggestion.status)
        assertEquals("01", suggestion.bestGroup?.section?.section?.sectionCode)
        assertTrue(suggestion.reasons.any { it.contains("بدون تداخل") })
    }

    @Test
    fun `course with several groups is usable when only one is compatible`() {
        val enrolledSec = section(courseId = 1, code = "01", sessions = listOf(session(0, "08:00", "10:00")))
        val clashing = section(courseId = 2, code = "01", sessions = listOf(session(0, "09:00", "11:00")))
        val fine = section(courseId = 2, code = "02", sessions = listOf(session(2, "10:00", "12:00")))
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(clashing, fine)))

        val result = rank(catalog, mapOf(2L to listOf(clashing, fine)), listOf(enrolledSec))

        val suggestion = result.single()
        assertEquals(SuggestionStatus.COMPATIBLE, suggestion.status)
        assertEquals("02", suggestion.bestGroup?.section?.section?.sectionCode)
        assertEquals(GroupVerdict.CONFLICTING, suggestion.groups.single { it.section.section.sectionCode == "01" }.verdict)
        assertEquals(GroupVerdict.COMPATIBLE, suggestion.groups.single { it.section.section.sectionCode == "02" }.verdict)
    }

    @Test
    fun `course whose every group clashes is reported honestly`() {
        val enrolledSec = section(courseId = 1, code = "01", sessions = listOf(session(0, "08:00", "10:00")))
        val first = section(courseId = 2, code = "01", sessions = listOf(session(0, "08:30", "09:30")))
        val second = section(courseId = 2, code = "02", sessions = listOf(session(0, "09:00", "11:00")))
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(first, second)))

        val result = rank(catalog, mapOf(2L to listOf(first, second)), listOf(enrolledSec))

        val suggestion = result.single()
        assertEquals(SuggestionStatus.NO_COMPATIBLE_GROUP, suggestion.status)
        assertEquals(null, suggestion.bestGroup)
        assertTrue(suggestion.reasons.any { it.contains("گروه سازگار ندارد") })
    }

    @Test
    fun `equal ranks fall back to deterministic code order`() {
        val first = section(courseId = 21, code = "01", sessions = listOf(session(3, "10:00", "12:00")))
        val second = section(courseId = 22, code = "01", sessions = listOf(session(3, "10:00", "12:00")))
        val catalog = listOf(
            catalogEntry(course(22, "B22", "درس ب"), listOf(second)),
            catalogEntry(course(21, "A21", "درس الف"), listOf(first))
        )

        val result = rank(catalog, mapOf(21L to listOf(first), 22L to listOf(second)))

        assertEquals(listOf(21L, 22L), result.map { it.course.id })
    }

    @Test
    fun `even and odd week groups on the same slot do not clash`() {
        val enrolledSec = section(
            courseId = 1, code = "01",
            sessions = listOf(session(0, "08:00", "10:00", WeekType.EVEN_WEEKS))
        )
        val oddOnly = section(
            courseId = 2, code = "01",
            sessions = listOf(session(0, "08:00", "10:00", WeekType.ODD_WEEKS))
        )
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(oddOnly)))

        val result = rank(catalog, mapOf(2L to listOf(oddOnly)), listOf(enrolledSec))

        assertEquals(SuggestionStatus.COMPATIBLE, result.single().status)
    }

    @Test
    fun `same parity groups on the same slot do clash`() {
        val enrolledSec = section(
            courseId = 1, code = "01",
            sessions = listOf(session(0, "08:00", "10:00", WeekType.EVEN_WEEKS))
        )
        val evenOnly = section(
            courseId = 2, code = "01",
            sessions = listOf(session(0, "09:00", "11:00", WeekType.EVEN_WEEKS))
        )
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(evenOnly)))

        val result = rank(catalog, mapOf(2L to listOf(evenOnly)), listOf(enrolledSec))

        assertEquals(SuggestionStatus.NO_COMPATIBLE_GROUP, result.single().status)
    }

    @Test
    fun `group with incomplete time data is never treated as valid`() {
        val enrolledSec = section(courseId = 1, code = "01", sessions = listOf(session(0, "08:00", "10:00")))
        val noSessions = section(courseId = 2, code = "01", sessions = emptyList())
        val badTime = section(courseId = 2, code = "02", sessions = listOf(session(1, "abc", "10:00")))
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(noSessions, badTime)))

        val result = rank(catalog, mapOf(2L to listOf(noSessions, badTime)), listOf(enrolledSec))

        val suggestion = result.single()
        assertEquals(SuggestionStatus.NO_TIME_DATA, suggestion.status)
        assertEquals(null, suggestion.bestGroup)
        assertTrue(suggestion.groups.all { it.verdict == GroupVerdict.INCOMPLETE_TIME_DATA })
    }

    @Test
    fun `smaller gap increase outranks larger one`() {
        // Enrolled: Saturday 08:00-10:00 and 14:00-16:00 (a 240-minute gap).
        val enrolledSec = section(
            courseId = 1, code = "01",
            sessions = listOf(session(0, "08:00", "10:00"), session(0, "14:00", "16:00"))
        )
        // Fills the gap partially: Saturday 10:00-12:00.
        val gapFiller = section(courseId = 2, code = "01", sessions = listOf(session(0, "10:00", "12:00")))
        // Different day: Sunday 08:00-10:00 (gap unchanged, new day).
        val otherDay = section(courseId = 3, code = "01", sessions = listOf(session(1, "08:00", "10:00")))
        val catalog = listOf(
            catalogEntry(course(3, "C3", "درس ۳"), listOf(otherDay)),
            catalogEntry(course(2, "C2", "درس ۲"), listOf(gapFiller))
        )

        val result = rank(
            catalog,
            mapOf(2L to listOf(gapFiller), 3L to listOf(otherDay)),
            listOf(enrolledSec)
        )

        assertEquals(SuggestionStatus.COMPATIBLE, result[0].status)
        assertEquals(SuggestionStatus.COMPATIBLE, result[1].status)
        assertEquals(2L, result[0].course.id)
        assertEquals(3L, result[1].course.id)
        assertTrue(result[0].bestGroup!!.gapDeltaMinutes < result[1].bestGroup!!.gapDeltaMinutes)
    }

    @Test
    fun `added course keeps its oldest compatible exam slot preference`() {
        // Sanity on exam honesty: same exam day with fully known, disjoint
        // times is NOT a conflict, so the course stays compatible.
        val enrolledSec = section(
            courseId = 1, code = "01",
            sessions = listOf(session(0, "08:00", "10:00")),
            examDate = "1404/03/20", examStart = "08:00", examEnd = "10:00"
        )
        val candidate = section(
            courseId = 2, code = "01",
            sessions = listOf(session(1, "08:00", "10:00")),
            examDate = "1404/03/20", examStart = "14:00", examEnd = "16:00"
        )
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(candidate)))

        val result = rank(catalog, mapOf(2L to listOf(candidate)), listOf(enrolledSec))

        assertEquals(SuggestionStatus.COMPATIBLE, result.single().status)
    }

    @Test
    fun `same exam day with unknown times stays compatible but warns`() {
        val enrolledSec = section(
            courseId = 1, code = "01",
            sessions = listOf(session(0, "08:00", "10:00")),
            examDate = "1404/03/20"
        )
        val candidate = section(
            courseId = 2, code = "01",
            sessions = listOf(session(1, "08:00", "10:00")),
            examDate = "1404/03/20"
        )
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(candidate)))

        val result = rank(catalog, mapOf(2L to listOf(candidate)), listOf(enrolledSec))

        val suggestion = result.single()
        assertEquals(SuggestionStatus.COMPATIBLE, suggestion.status)
        assertTrue(suggestion.reasons.any { it.contains("امتحانات") })
    }

    @Test
    fun `overlapping exam times block the group`() {
        val enrolledSec = section(
            courseId = 1, code = "01",
            sessions = listOf(session(0, "08:00", "10:00")),
            examDate = "1404/03/20", examStart = "08:00", examEnd = "10:00"
        )
        val candidate = section(
            courseId = 2, code = "01",
            sessions = listOf(session(1, "08:00", "10:00")),
            examDate = "1404/03/20", examStart = "09:00", examEnd = "11:00"
        )
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(candidate)))

        val result = rank(catalog, mapOf(2L to listOf(candidate)), listOf(enrolledSec))

        assertEquals(SuggestionStatus.NO_COMPATIBLE_GROUP, result.single().status)
    }

    @Test
    fun `preference change reorders on early mornings only for no-early mode`() {
        // Two candidates, both compatible on an empty program:
        // - morning: 08:00 class (early), otherDay 10:00.
        val morning = section(courseId = 2, code = "01", sessions = listOf(session(2, "08:00", "10:00")))
        val late = section(courseId = 3, code = "01", sessions = listOf(session(2, "14:00", "16:00")))
        val catalog = listOf(
            catalogEntry(course(2, "C2", "درس ۲"), listOf(morning)),
            catalogEntry(course(3, "C3", "درس ۳"), listOf(late))
        )
        val byCourse = mapOf(2L to listOf(morning), 3L to listOf(late))

        val balanced = rank(catalog, byCourse, emptyList(), OptimizationPreference.BALANCED)
        val noEarly = rank(catalog, byCourse, emptyList(), OptimizationPreference.NO_EARLY_MORNING)

        // Same metrics under BALANCED: tie falls back to course code order.
        assertEquals(listOf(2L, 3L), balanced.map { it.course.id })
        // NO_EARLY_MORNING prefers the later class.
        assertEquals(listOf(3L, 2L), noEarly.map { it.course.id })
    }

    @Test
    fun `empty catalog ranks to empty list`() {
        assertTrue(rank(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `course already in my courses is marked not addable`() {
        val selected = section(courseId = 2, code = "01", sessions = listOf(session(1, "08:00", "10:00")))
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲", selected = true), listOf(selected)))

        val result = rank(catalog, mapOf(2L to listOf(selected)))

        val suggestion = result.single()
        assertEquals(SuggestionStatus.ALREADY_ADDED, suggestion.status)
        assertTrue(suggestion.alreadyAdded)
        assertEquals(null, suggestion.bestGroup)
    }

    @Test
    fun `course with an enrolled group is marked not addable`() {
        val enrolled = section(
            courseId = 2, code = "01",
            sessions = listOf(session(1, "08:00", "10:00")),
            enrolled = true
        )
        val other = section(courseId = 2, code = "02", sessions = listOf(session(2, "08:00", "10:00")))
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), listOf(enrolled, other)))

        val result = rank(catalog, mapOf(2L to listOf(enrolled, other)))

        assertEquals(SuggestionStatus.ALREADY_ADDED, result.single().status)
    }

    @Test
    fun `course without any group is reported honestly`() {
        val catalog = listOf(catalogEntry(course(2, "C2", "درس ۲"), emptyList()))

        val result = rank(catalog, mapOf(2L to emptyList()))

        assertEquals(SuggestionStatus.NO_GROUPS, result.single().status)
    }

    @Test
    fun `search matches name code department and instructor`() {
        val id = nextId++
        val entity = CourseSection(
            id = id, courseId = 7, sectionCode = "01", instructor = "دکتر تست‌چی"
        )
        val details = SectionWithDetails(
            section = entity,
            course = Course(id = 7, code = "PHYS204", name = "فیزیک عمومی", department = "علوم پایه"),
            sessions = emptyList()
        )
        val entry = CourseWithSections(
            course = details.course,
            sections = listOf(SectionWithSessions(section = entity, sessions = emptyList()))
        )

        assertTrue(CourseSuggestionRanker.matchesQuery(entry, "فیزیک"))
        assertTrue(CourseSuggestionRanker.matchesQuery(entry, "PHYS204"))
        assertTrue(CourseSuggestionRanker.matchesQuery(entry, "علوم پایه"))
        assertTrue(CourseSuggestionRanker.matchesQuery(entry, "تست‌چی"))
        assertTrue(CourseSuggestionRanker.matchesQuery(entry, "   "))
        assertFalse(CourseSuggestionRanker.matchesQuery(entry, "شیمی"))
    }

    @Test
    fun `hasVerifiableTime rejects empty and malformed sessions`() {
        val emptySec = section(courseId = 9, code = "01", sessions = emptyList())
        val badDay = section(
            courseId = 9, code = "02",
            sessions = listOf(session(9, "08:00", "10:00"))
        )
        val badRange = section(
            courseId = 9, code = "03",
            sessions = listOf(session(1, "12:00", "10:00"))
        )
        val good = section(
            courseId = 9, code = "04",
            sessions = listOf(session(1, "08:00", "10:00"))
        )

        assertFalse(CourseSuggestionRanker.hasVerifiableTime(emptySec))
        assertFalse(CourseSuggestionRanker.hasVerifiableTime(badDay))
        assertFalse(CourseSuggestionRanker.hasVerifiableTime(badRange))
        assertTrue(CourseSuggestionRanker.hasVerifiableTime(good))
    }
}