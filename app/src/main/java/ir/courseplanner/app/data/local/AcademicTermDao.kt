package ir.courseplanner.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ir.courseplanner.app.data.model.AcademicTerm
import ir.courseplanner.app.data.model.AcademicTermWithCourses
import ir.courseplanner.app.data.model.ArchivedClassSession
import ir.courseplanner.app.data.model.ArchivedCourse
import ir.courseplanner.app.data.model.ArchivedCourseWithSessions
import kotlinx.coroutines.flow.Flow

/**
 * Finished terms and their snapshots.
 *
 * Everything here is append-only from the app UI's point of view: ending a term
 * inserts new rows, and nothing in the end-term/start-term flow updates or
 * deletes an existing archive.
 */
@Dao
interface AcademicTermDao {

    @Transaction
    @Query("SELECT * FROM academic_terms ORDER BY endEpochDay DESC, id DESC")
    fun getAllTerms(): Flow<List<AcademicTermWithCourses>>

    @Transaction
    @Query("SELECT * FROM academic_terms ORDER BY endEpochDay DESC, id DESC")
    suspend fun getAllTermsOnce(): List<AcademicTermWithCourses>

    @Transaction
    @Query("SELECT * FROM academic_terms WHERE id = :termId LIMIT 1")
    suspend fun getTermById(termId: Long): AcademicTermWithCourses?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTerm(term: AcademicTerm): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArchivedCourses(courses: List<ArchivedCourse>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArchivedSessions(sessions: List<ArchivedClassSession>)

    @Query("SELECT * FROM archived_courses WHERE termId = :termId ORDER BY name ASC, code ASC")
    suspend fun getArchivedCoursesOnce(termId: Long): List<ArchivedCourse>

    @Transaction
    @Query("SELECT * FROM archived_courses WHERE termId = :termId ORDER BY name ASC, code ASC")
    suspend fun getArchivedCoursesWithSessionsOnce(termId: Long): List<ArchivedCourseWithSessions>

    @Query("SELECT COUNT(*) FROM academic_terms")
    suspend fun getTermCount(): Int

    @Query("SELECT COUNT(*) FROM archived_courses")
    suspend fun getArchivedCourseCount(): Int
}
