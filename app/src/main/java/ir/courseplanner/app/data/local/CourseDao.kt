package ir.courseplanner.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseWithSections
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses ORDER BY code ASC")
    fun getAllCourses(): Flow<List<Course>>

    @Query("""
        SELECT * FROM courses 
        WHERE code LIKE '%' || :query || '%' 
           OR name LIKE '%' || :query || '%' 
           OR department LIKE '%' || :query || '%'
        ORDER BY code ASC
    """)
    fun searchCourses(query: String): Flow<List<Course>>

    @Transaction
    @Query("SELECT * FROM courses ORDER BY code ASC")
    fun getCoursesWithSections(): Flow<List<CourseWithSections>>

    /** One-shot full snapshot used by the atomic end-of-term archive. */
    @Transaction
    @Query("SELECT * FROM courses ORDER BY code ASC")
    suspend fun getCoursesWithSectionsOnce(): List<CourseWithSections>

    @Transaction
    @Query("SELECT * FROM courses WHERE isSelectedForGeneration = 1 ORDER BY code ASC")
    fun getCoursesSelectedForGeneration(): Flow<List<CourseWithSections>>

    /**
     * One-shot snapshot of every course, used for case/space-insensitive code
     * uniqueness checks (normalization lives in Kotlin — `normalizeCode` — so
     * there is exactly one definition of "same code" in the app).
     */
    @Query("SELECT * FROM courses")
    suspend fun getAllCoursesOnce(): List<Course>

    @Query("SELECT * FROM courses WHERE isSelectedForGeneration = 1")
    suspend fun getCoursesSelectedForGenerationOnce(): List<Course>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<Course>): List<Long>

    @Update
    suspend fun updateCourse(course: Course)

    @Delete
    suspend fun deleteCourse(course: Course)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: Long)

    /** Lookup for re-import sync (UPSERT by natural key, not row id). */
    @Query("SELECT * FROM courses WHERE code = :code LIMIT 1")
    suspend fun getCourseByCode(code: String): Course?

    @Query("SELECT * FROM courses WHERE id = :courseId LIMIT 1")
    suspend fun getCourseById(courseId: Long): Course?

    @Query("UPDATE courses SET isSelectedForGeneration = :isSelected WHERE id = :courseId")
    suspend fun setCourseSelectedForGeneration(courseId: Long, isSelected: Boolean)

    @Query("UPDATE courses SET isSelectedForGeneration = 0")
    suspend fun clearAllSelectedForGeneration()

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Query("DELETE FROM courses WHERE id IN (:courseIds)")
    suspend fun deleteCoursesByIds(courseIds: List<Long>)

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun getCourseCount(): Int

    @Query("SELECT COUNT(*) FROM courses WHERE isSelectedForGeneration = 1")
    suspend fun getSelectedCourseCount(): Int
}
