package ir.courseplanner.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: CourseSection): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<CourseSection>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ClassSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ClassSession>): List<Long>

    @Update
    suspend fun updateSection(section: CourseSection)

    @Transaction
    @Query("SELECT * FROM course_sections WHERE isEnrolled = 1")
    fun getEnrolledSections(): Flow<List<SectionWithDetails>>

    @Transaction
    @Query("SELECT * FROM course_sections")
    fun getAllSectionsWithDetails(): Flow<List<SectionWithDetails>>

    @Transaction
    @Query("SELECT * FROM course_sections WHERE id = :sectionId LIMIT 1")
    suspend fun getSectionWithDetailsById(sectionId: Long): SectionWithDetails?

    @Query("UPDATE course_sections SET isEnrolled = :isEnrolled WHERE id = :sectionId")
    suspend fun setSectionEnrolled(sectionId: Long, isEnrolled: Boolean)

    @Query("UPDATE course_sections SET isEnrolled = 0 WHERE courseId = :courseId")
    suspend fun unenrollAllForCourse(courseId: Long)

    @Query("UPDATE course_sections SET isEnrolled = 0")
    suspend fun clearAllEnrollments()

    @Query("SELECT COUNT(*) FROM course_sections WHERE isEnrolled = 1")
    suspend fun getEnrolledSectionCount(): Int

    @Query("SELECT DISTINCT courseId FROM course_sections WHERE isEnrolled = 1")
    suspend fun getEnrolledCourseIdsOnce(): List<Long>

    @Query("DELETE FROM course_sections WHERE id = :sectionId")
    suspend fun deleteSectionById(sectionId: Long)

    @Query("SELECT * FROM course_sections WHERE id = :sectionId LIMIT 1")
    suspend fun getSectionById(sectionId: Long): CourseSection?

    // NOTE: there is intentionally no exact-match lookup by (courseId, code).
    // Uniqueness is checked through `getSectionsByCourseId` + `normalizeCode`
    // so " 01 " and "01" collide (see CourseRepository / CodeNormalization).

    /** Sections of one course, for re-import sync matched by sectionCode. */
    @Query("SELECT * FROM course_sections WHERE courseId = :courseId")
    suspend fun getSectionsByCourseId(courseId: Long): List<CourseSection>

    @Query("DELETE FROM class_sessions WHERE sectionId = :sectionId")
    suspend fun deleteSessionsBySectionId(sectionId: Long)

    @Query("DELETE FROM course_sections")
    suspend fun deleteAllSections()

    @Query("DELETE FROM class_sessions")
    suspend fun deleteAllSessions()
}
