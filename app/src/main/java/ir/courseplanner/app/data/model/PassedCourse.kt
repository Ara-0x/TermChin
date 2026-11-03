package ir.courseplanner.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One course the student has already passed (academic record / transcript).
 *
 * Deliberately a SEPARATE table from [Course]: a passed course is history and
 * must never appear in "my courses", the schedule generator, or the conflict
 * checks. Keeping it apart also means deleting the current catalog can never
 * erase a student's academic record by accident.
 *
 * [code] uniqueness is enforced by the repository through [normalizeCode], so
 * "math101", " MATH101 " and "Math101" are one passed course.
 */
@Entity(
    tableName = "passed_courses",
    indices = [Index(value = ["code"])]
)
data class PassedCourse(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val name: String,
    val credits: Int = 3,
    /** Optional free text: "۱۸.۵", "A", "قبول", … (never parsed into a GPA). */
    val grade: String? = null,
    /** Optional free text, e.g. "نیم‌سال اول ۱۴۰۳-۱۴۰۴". */
    val termLabel: String? = null,
    val note: String = "",
    val recordedAt: Long = System.currentTimeMillis()
) {
    /** Comparison-only form; the stored [code] keeps the user's casing. */
    val normalizedCode: String get() = normalizeCode(code)
}
