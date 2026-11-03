package ir.courseplanner.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Final result of one course in an archived term.
 *
 * [INCOMPLETE] is a real, deliberate choice ("ناتمام / نامشخص"), not "not
 * answered yet": the end-term form starts every course unselected, so the user
 * must explicitly pick one of the four states before the archive can run.
 */
enum class TermCourseStatus(val titleFa: String) {
    PASSED("پاس‌شده"),
    FAILED("مردود"),
    WITHDRAWN("حذف‌شده"),
    INCOMPLETE("ناتمام / نامشخص")
}

/**
 * One finished term. Archive rows live in their OWN tables and never point at
 * the live catalog with a foreign key: deleting a course/group from the current
 * term (or re-taking it later) must not rewrite history.
 */
@Entity(tableName = "academic_terms")
data class AcademicTerm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** First class day of the term, if it was known. */
    val startEpochDay: Long? = null,
    /** Day the term was closed (epoch day). */
    val endEpochDay: Long,
    val endedAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

/**
 * Snapshot of one course inside a finished term.
 *
 * [courseId] is informational only (there is intentionally no FK): the live
 * course row may be deleted or reused by a later term, while this row stays
 * exactly as it was when the term ended.
 */
@Entity(
    tableName = "archived_courses",
    foreignKeys = [
        ForeignKey(
            entity = AcademicTerm::class,
            parentColumns = ["id"],
            childColumns = ["termId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["termId"])]
)
data class ArchivedCourse(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val termId: Long,
    val courseId: Long,
    val code: String,
    val name: String,
    val credits: Int = 3,
    val department: String = "",
    /** Enrolled group at the moment the term ended; blank when none was picked. */
    val sectionCode: String = "",
    val instructor: String = "",
    val status: TermCourseStatus = TermCourseStatus.INCOMPLETE,
    /** Optional free text, exactly like [PassedCourse.grade]. */
    val grade: String? = null,
    val attendancePresent: Int = 0,
    val attendanceAbsent: Int = 0,
    val attendanceLate: Int = 0,
    val attendanceExcused: Int = 0,
    val archivedAt: Long = System.currentTimeMillis()
) {
    val passedCredits: Int get() = if (status == TermCourseStatus.PASSED) credits else 0

    val attendanceTotal: Int
        get() = attendancePresent + attendanceAbsent + attendanceLate + attendanceExcused
}

/** Class-time snapshot of the enrolled group of [ArchivedCourse]. */
@Entity(
    tableName = "archived_course_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ArchivedCourse::class,
            parentColumns = ["id"],
            childColumns = ["archivedCourseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["archivedCourseId"])]
)
data class ArchivedClassSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val archivedCourseId: Long,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val location: String = "",
    val weekType: WeekType = WeekType.EVERY_WEEK
)

data class ArchivedCourseWithSessions(
    @Embedded
    val course: ArchivedCourse,

    @Relation(
        parentColumn = "id",
        entityColumn = "archivedCourseId"
    )
    val sessions: List<ArchivedClassSession> = emptyList()
)

data class AcademicTermWithCourses(
    @Embedded
    val term: AcademicTerm,

    @Relation(
        entity = ArchivedCourse::class,
        parentColumn = "id",
        entityColumn = "termId"
    )
    val courses: List<ArchivedCourseWithSessions> = emptyList()
) {
    val courseCount: Int get() = courses.size

    val passedCount: Int
        get() = courses.count { it.course.status == TermCourseStatus.PASSED }

    val passedCredits: Int get() = courses.sumOf { it.course.passedCredits }

    val totalCredits: Int get() = courses.sumOf { it.course.credits }
}
