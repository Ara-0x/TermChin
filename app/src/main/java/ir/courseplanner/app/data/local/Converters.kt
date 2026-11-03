package ir.courseplanner.app.data.local

import androidx.room.TypeConverter
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.DocumentCategory
import ir.courseplanner.app.data.model.TermCourseStatus
import ir.courseplanner.app.data.model.WeekType

class Converters {
    @TypeConverter
    fun fromWeekType(value: WeekType?): String {
        return (value ?: WeekType.EVERY_WEEK).name
    }

    @TypeConverter
    fun toWeekType(value: String?): WeekType {
        return try {
            if (value.isNullOrBlank()) WeekType.EVERY_WEEK else WeekType.valueOf(value)
        } catch (e: Exception) {
            WeekType.EVERY_WEEK
        }
    }

    @TypeConverter
    fun fromDocumentCategory(value: DocumentCategory?): String {
        return (value ?: DocumentCategory.PAMPHLET).name
    }

    @TypeConverter
    fun toDocumentCategory(value: String?): DocumentCategory {
        return try {
            if (value.isNullOrBlank()) DocumentCategory.PAMPHLET else DocumentCategory.valueOf(value)
        } catch (e: Exception) {
            DocumentCategory.PAMPHLET
        }
    }

    @TypeConverter
    fun fromAttendanceStatus(value: AttendanceStatus?): String {
        return (value ?: AttendanceStatus.PRESENT).name
    }

    @TypeConverter
    fun toAttendanceStatus(value: String?): AttendanceStatus {
        return try {
            if (value.isNullOrBlank()) AttendanceStatus.PRESENT else AttendanceStatus.valueOf(value)
        } catch (e: Exception) {
            AttendanceStatus.PRESENT
        }
    }

    @TypeConverter
    fun fromTermCourseStatus(value: TermCourseStatus?): String {
        return (value ?: TermCourseStatus.INCOMPLETE).name
    }

    @TypeConverter
    fun toTermCourseStatus(value: String?): TermCourseStatus {
        return try {
            if (value.isNullOrBlank()) TermCourseStatus.INCOMPLETE
            else TermCourseStatus.valueOf(value)
        } catch (e: Exception) {
            TermCourseStatus.INCOMPLETE
        }
    }
}
