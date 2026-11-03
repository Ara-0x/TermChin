package ir.courseplanner.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.courseplanner.app.data.model.AttendanceRecord
import ir.courseplanner.app.data.model.AcademicTerm
import ir.courseplanner.app.data.model.ArchivedClassSession
import ir.courseplanner.app.data.model.ArchivedCourse
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.PassedCourse

@Database(
    entities = [
        Course::class,
        CourseSection::class,
        ClassSession::class,
        CourseDocument::class,
        PassedCourse::class,
        AttendanceRecord::class,
        AcademicTerm::class,
        ArchivedCourse::class,
        ArchivedClassSession::class
    ],
    version = 6
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun sectionDao(): SectionDao
    abstract fun documentDao(): CourseDocumentDao
    abstract fun passedCourseDao(): PassedCourseDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun academicTermDao(): AcademicTermDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Discipline: NEVER use fallbackToDestructiveMigration() here.
         * The database version is the contract with student data on device:
         * every schema change MUST bump [version] and add a Migration to
         * [ALL_MIGRATIONS]. Schemas are exported to app/schemas for review.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Portal degree level per course. Nullable + no default on purpose:
                // this is the only ADD COLUMN shape Room's schema validation
                // accepts byte-identically (pre-migration rows read back as null).
                db.execSQL("ALTER TABLE courses ADD COLUMN degree TEXT")
            }
        }

        /**
         * v5 adds two independent tables:
         * - `passed_courses`: the student's academic record (never mixed into
         *   the catalog / generator / conflict checks).
         * - `attendance_records`: one row per (group, day, start time), with a
         *   CASCADE FK to the section so deleting a group removes its history
         *   instead of leaving orphan rows.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `passed_courses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`code` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`credits` INTEGER NOT NULL, " +
                        "`grade` TEXT, " +
                        "`termLabel` TEXT, " +
                        "`note` TEXT NOT NULL, " +
                        "`recordedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_passed_courses_code` " +
                        "ON `passed_courses` (`code`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attendance_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sectionId` INTEGER NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, " +
                        "`startTime` TEXT NOT NULL, " +
                        "`endTime` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sectionId`) REFERENCES `course_sections`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attendance_records_sectionId` " +
                        "ON `attendance_records` (`sectionId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_attendance_records_sectionId_dateEpochDay_startTime` " +
                        "ON `attendance_records` (`sectionId`, `dateEpochDay`, `startTime`)"
                )
            }
        }

        /**
         * v6 adds the end-of-term archive. All three tables are append-only and
         * independent of the live catalog:
         * - `academic_terms`: one row per finished term;
         * - `archived_courses`: per-course status/grade/snapshot;
         * - `archived_course_sessions`: the enrolled group's class times.
         *
         * No existing table is modified, so installing this version cannot
         * change or delete current courses, groups, documents or attendance.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `academic_terms` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`startEpochDay` INTEGER, " +
                        "`endEpochDay` INTEGER NOT NULL, " +
                        "`endedAt` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `archived_courses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`termId` INTEGER NOT NULL, " +
                        "`courseId` INTEGER NOT NULL, " +
                        "`code` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`credits` INTEGER NOT NULL, " +
                        "`department` TEXT NOT NULL, " +
                        "`sectionCode` TEXT NOT NULL, " +
                        "`instructor` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`grade` TEXT, " +
                        "`attendancePresent` INTEGER NOT NULL, " +
                        "`attendanceAbsent` INTEGER NOT NULL, " +
                        "`attendanceLate` INTEGER NOT NULL, " +
                        "`attendanceExcused` INTEGER NOT NULL, " +
                        "`archivedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`termId`) REFERENCES `academic_terms`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_archived_courses_termId` " +
                        "ON `archived_courses` (`termId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `archived_course_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`archivedCourseId` INTEGER NOT NULL, " +
                        "`dayOfWeek` INTEGER NOT NULL, " +
                        "`startTime` TEXT NOT NULL, " +
                        "`endTime` TEXT NOT NULL, " +
                        "`location` TEXT NOT NULL, " +
                        "`weekType` TEXT NOT NULL, " +
                        "FOREIGN KEY(`archivedCourseId`) REFERENCES `archived_courses`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_archived_course_sessions_archivedCourseId` " +
                        "ON `archived_course_sessions` (`archivedCourseId`)"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "course_planner_database"
                )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
