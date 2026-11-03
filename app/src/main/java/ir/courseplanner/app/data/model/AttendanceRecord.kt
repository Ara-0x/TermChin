package ir.courseplanner.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AttendanceStatus(val titleFa: String, val shortFa: String) {
    PRESENT("حاضر", "ح"),
    ABSENT("غایب", "غ"),
    LATE("تأخیر", "ت"),
    EXCUSED("موجه", "م")
}

/**
 * One attendance decision for one group on one calendar day.
 *
 * Why the record points at [CourseSection] (not [ClassSession]): editing a
 * group replaces all of its `class_sessions` rows (new ids). A FK to the
 * session would therefore silently delete the student's attendance history on
 * every edit. The section id is stable, and the session is identified by the
 * start-time snapshot in [startTime]; [endTime] is stored for history display.
 *
 * Uniqueness: one record per (section, day, start time). Marking the same class
 * again UPDATES that record instead of creating duplicates.
 */
@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = CourseSection::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sectionId"]),
        Index(value = ["sectionId", "dateEpochDay", "startTime"], unique = true)
    ]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sectionId: Long,
    /** Epoch day (days since 1970-01-01), so date math never depends on locale. */
    val dateEpochDay: Long,
    val startTime: String,
    val endTime: String,
    val status: AttendanceStatus,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/** Aggregated attendance for one group (or one course). */
data class AttendanceStats(
    val present: Int = 0,
    val absent: Int = 0,
    val late: Int = 0,
    val excused: Int = 0
) {
    val total: Int get() = present + absent + late + excused

    /** «غیبت» means an actual absence; a late arrival is counted separately. */
    val absenceCount: Int get() = absent

    operator fun plus(other: AttendanceStats): AttendanceStats = AttendanceStats(
        present = present + other.present,
        absent = absent + other.absent,
        late = late + other.late,
        excused = excused + other.excused
    )

    companion object {
        fun from(records: Iterable<AttendanceRecord>): AttendanceStats {
            var present = 0
            var absent = 0
            var late = 0
            var excused = 0
            for (record in records) {
                when (record.status) {
                    AttendanceStatus.PRESENT -> present++
                    AttendanceStatus.ABSENT -> absent++
                    AttendanceStatus.LATE -> late++
                    AttendanceStatus.EXCUSED -> excused++
                }
            }
            return AttendanceStats(present, absent, late, excused)
        }
    }
}
