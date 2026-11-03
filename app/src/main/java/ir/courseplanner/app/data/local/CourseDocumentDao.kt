package ir.courseplanner.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.courseplanner.app.data.model.CourseDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDocumentDao {
    @Query("SELECT * FROM course_documents ORDER BY isBookmarked DESC, createdAt DESC")
    fun getAllDocuments(): Flow<List<CourseDocument>>

    @Query("SELECT * FROM course_documents WHERE course_id = :courseId ORDER BY isBookmarked DESC, createdAt DESC")
    fun getDocumentsByCourse(courseId: Long): Flow<List<CourseDocument>>

    @Query("SELECT * FROM course_documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): CourseDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: CourseDocument): Long

    @Update
    suspend fun updateDocument(document: CourseDocument)

    @Delete
    suspend fun deleteDocument(document: CourseDocument)

    @Query("DELETE FROM course_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("UPDATE course_documents SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM course_documents")
    suspend fun deleteAllDocuments()

    @Query("SELECT COUNT(*) FROM course_documents")
    suspend fun countDocuments(): Int
}
