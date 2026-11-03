package ir.courseplanner.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ir.courseplanner.app.data.model.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Query("SELECT * FROM attendance_records ORDER BY dateEpochDay DESC, startTime ASC")
    fun getAllAttendance(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records ORDER BY dateEpochDay DESC, startTime ASC")
    suspend fun getAllAttendanceOnce(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE sectionId = :sectionId ORDER BY dateEpochDay DESC, startTime ASC")
    fun getAttendanceForSection(sectionId: Long): Flow<List<AttendanceRecord>>

    /**
     * The record for one class occurrence, if the student already recorded it.
     * The logical key is (section, day, start time): re-marking updates it.
     */
    @Query(
        """
        SELECT * FROM attendance_records
        WHERE sectionId = :sectionId
          AND dateEpochDay = :dateEpochDay
          AND startTime = :startTime
        LIMIT 1
        """
    )
    suspend fun getAttendanceForOccurrence(
        sectionId: Long,
        dateEpochDay: Long,
        startTime: String
    ): AttendanceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(record: AttendanceRecord): Long

    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun deleteAttendanceById(id: Long)

    @Query(
        """
        DELETE FROM attendance_records
        WHERE sectionId = :sectionId
          AND dateEpochDay = :dateEpochDay
          AND startTime = :startTime
        """
    )
    suspend fun deleteAttendanceForOccurrence(
        sectionId: Long,
        dateEpochDay: Long,
        startTime: String
    )

    @Query("DELETE FROM attendance_records")
    suspend fun deleteAllAttendance()

    @Query("SELECT COUNT(*) FROM attendance_records")
    suspend fun getAttendanceCount(): Int
}
