package ir.courseplanner.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.courseplanner.app.data.model.PassedCourse
import kotlinx.coroutines.flow.Flow

@Dao
interface PassedCourseDao {

    @Query("SELECT * FROM passed_courses ORDER BY name ASC, code ASC")
    fun getAllPassedCourses(): Flow<List<PassedCourse>>

    @Query("SELECT * FROM passed_courses ORDER BY name ASC, code ASC")
    suspend fun getAllPassedCoursesOnce(): List<PassedCourse>

    @Query("SELECT * FROM passed_courses WHERE id = :id LIMIT 1")
    suspend fun getPassedCourseById(id: Long): PassedCourse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassedCourse(course: PassedCourse): Long

    @Update
    suspend fun updatePassedCourse(course: PassedCourse)

    @Query("DELETE FROM passed_courses WHERE id = :id")
    suspend fun deletePassedCourseById(id: Long)

    @Query("DELETE FROM passed_courses")
    suspend fun deleteAllPassedCourses()

    @Query("SELECT COUNT(*) FROM passed_courses")
    suspend fun getPassedCourseCount(): Int
}
