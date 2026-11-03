package ir.courseplanner.app.data.model

/**
 * How much data the user asked to remove.
 *
 * Every flag is independent and defaults to `false`: nothing is deleted unless
 * the user explicitly selected it. Presets only pre-fill flags — they never
 * bypass the confirmation step in the UI.
 */
data class DataDeletionSelection(
    /** Enrolled groups only; courses, documents and attendance survive. */
    val schedule: Boolean = false,
    /** Courses the student added/selected. Their documents and attendance cascade. */
    val myCourses: Boolean = false,
    /** Catalog-only portal courses that are neither selected nor enrolled. */
    val catalog: Boolean = false,
    /** All pamphlets/documents, independent of which courses exist. */
    val documents: Boolean = false,
    /** Academic record (passed courses). */
    val passedCourses: Boolean = false,
    /** Attendance history. */
    val attendance: Boolean = false,
    /** Profile/preferences (DataStore): theme, name, semester, limits. */
    val preferences: Boolean = false
) {
    val isEmpty: Boolean
        get() = !schedule && !myCourses && !catalog && !documents &&
            !passedCourses && !attendance && !preferences

    /** Something in the Room database is being deleted. */
    val touchesDatabase: Boolean
        get() = schedule || myCourses || catalog || documents || passedCourses || attendance

    /** Human-readable list used by the confirmation summary. */
    fun summaryFa(): List<String> = buildList {
        if (schedule) add("برنامه هفتگی (گروه‌های انتخاب‌شده)")
        if (myCourses) add("دروس من (با جزوات و سوابق حضور وابسته)")
        if (catalog) add("کاتالوگ پورتال (دروس اضافه‌نشده)")
        if (documents) add("همهٔ جزوات و اسناد")
        if (passedCourses) add("دروس پاس‌شده")
        if (attendance) add("سوابق حضور و غیاب")
        if (preferences) add("تنظیمات و پروفایل (بازگشت به مقادیر پیش‌فرض)")
    }

    companion object {
        /** «فقط برنامه هفتگی» — the old "clear schedule" action. */
        val SCHEDULE_ONLY = DataDeletionSelection(schedule = true)

        /** «داده‌های من» — courses, documents, record and attendance; keeps catalog. */
        val MY_DATA = DataDeletionSelection(
            myCourses = true,
            documents = true,
            passedCourses = true,
            attendance = true
        )

        /** «همه چیز» — including preferences. */
        val ALL = DataDeletionSelection(
            schedule = true,
            myCourses = true,
            catalog = true,
            documents = true,
            passedCourses = true,
            attendance = true,
            preferences = true
        )
    }
}

/** Live row counts shown next to each deletion switch. */
data class DataCounts(
    val enrolledSections: Int = 0,
    val myCourses: Int = 0,
    val catalogCourses: Int = 0,
    val documents: Int = 0,
    val passedCourses: Int = 0,
    val attendanceRecords: Int = 0
)
