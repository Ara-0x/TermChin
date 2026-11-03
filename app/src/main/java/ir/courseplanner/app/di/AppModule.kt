package ir.courseplanner.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.repository.CourseRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideCourseRepository(database: AppDatabase): CourseRepository =
        CourseRepository(
            db = database,
            courseDao = database.courseDao(),
            sectionDao = database.sectionDao(),
            documentDao = database.documentDao(),
            passedCourseDao = database.passedCourseDao(),
            attendanceDao = database.attendanceDao(),
            academicTermDao = database.academicTermDao()
        )
}
