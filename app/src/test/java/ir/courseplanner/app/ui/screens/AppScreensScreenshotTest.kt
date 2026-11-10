package ir.courseplanner.app.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.model.AcademicTerm
import ir.courseplanner.app.data.model.ArchivedCourse
import ir.courseplanner.app.data.model.AttendanceRecord
import ir.courseplanner.app.data.model.AttendanceStatus
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.DocumentCategory
import ir.courseplanner.app.data.model.PassedCourse
import ir.courseplanner.app.data.model.TermCourseStatus
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual baselines for the app's screens. One seeded in-memory program is used
 * for every capture, in light and dark, so the whole app can be compared
 * against a single visual system.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppScreensScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var viewModel: CoursePlannerViewModel
    private lateinit var preferences: PreferencesManager
    private lateinit var collectorScope: CoroutineScope
    private val seededSectionIds = mutableListOf<Long>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = CourseRepository(
            db,
            db.courseDao(),
            db.sectionDao(),
            db.documentDao(),
            db.passedCourseDao(),
            db.attendanceDao()
        )
        preferences = PreferencesManager(context)
        viewModel = CoursePlannerViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            repository = repository,
            preferencesManager = preferences
        )
        collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        collectorScope.launch {
            launch { viewModel.enrolledSections.collect { } }
            launch { viewModel.coursesWithSections.collect { } }
            launch { viewModel.attendanceRecords.collect { } }
            launch { viewModel.documentsWithCourse.collect { } }
            launch { viewModel.passedCourses.collect { } }
            launch { viewModel.currentTermCourseCount.collect { } }
            launch { viewModel.allCourses.collect { } }
            launch { viewModel.departmentNames.collect { } }
        }
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
        Dispatchers.resetMain()
        db.close()
    }

    // Top-level tabs, light + dark.

    @Test
    fun courses_light() = runBlocking { seedThenCapture("design_courses_light") { CoursesScreen(it) } }

    @Test
    fun courses_dark() = runBlocking { seedThenCapture("design_courses_dark", darkTheme = true) { CoursesScreen(it) } }

    @Test
    fun schedule_light() = runBlocking { seedThenCapture("design_schedule_light") { ScheduleScreen(it) } }

    @Test
    fun schedule_dark() = runBlocking { seedThenCapture("design_schedule_dark", darkTheme = true) { ScheduleScreen(it) } }

    @Test
    fun documents_light() = runBlocking { seedThenCapture("design_documents_light") { DocumentsScreen(it) } }

    @Test
    fun documents_dark() = runBlocking { seedThenCapture("design_documents_dark", darkTheme = true) { DocumentsScreen(it) } }

    @Test
    fun settings_light() = runBlocking { seedThenCapture("design_settings_light") { SettingsScreen(it) } }

    @Test
    fun settings_dark() = runBlocking { seedThenCapture("design_settings_dark", darkTheme = true) { SettingsScreen(it) } }

    // Sub-pages, light.

    @Test
    fun add_course_light() = runBlocking {
        seedThenCapture(
            name = "design_add_course_light",
            extraSeed = {
                seedCourse(
                    "10501", "آزمایشگاه برنامه‌سازی", "دکتر موسوی",
                    day = 2, start = "08:00", end = "09:30", location = "آزمایشگاه ۱",
                    enrolled = false, selected = false
                )
                seedCourse(
                    "10602", "نظریه زبان‌ها و ماشین‌ها", "دکتر شریفی",
                    day = 2, start = "10:00", end = "11:30", location = "کلاس ۲۰۵",
                    enrolled = false, selected = false
                )
            }
        ) { AddCourseScreen(it) }
    }

    @Test
    fun attendance_light() = runBlocking { seedThenCapture("design_attendance_light") { AttendanceScreen(it) } }

    @Test
    fun passed_courses_light() = runBlocking { seedThenCapture("design_passed_courses_light") { PassedCoursesScreen(it) } }

    @Test
    fun academic_records_light() = runBlocking { seedThenCapture("design_academic_records_light") { AcademicRecordsScreen(it) } }

    @Test
    fun end_term_light() = runBlocking { seedThenCapture("design_end_term_light") { EndTermScreen(it) } }

    private suspend fun seedThenCapture(
        name: String,
        darkTheme: Boolean = false,
        extraSeed: (suspend () -> Unit)? = null,
        screen: @Composable (CoursePlannerViewModel) -> Unit
    ) {
        preferences.setStudentProfile(
            name = "امیررضا",
            major = "مهندسی کامپیوتر",
            semester = "نیم‌سال اول ۱۴۰۴"
        )
        preferences.setSemesterStartEpochDay(dashboardNow(System.currentTimeMillis()).epochDay - 42)
        preferences.setCreditTarget(20)
        withTimeout(10_000) {
            preferences.preferences.first { it.studentName == "امیررضا" }
        }

        val math = seedCourse("10103", "ریاضی عمومی ۱", "دکتر رضایی", day = 1, "10:00", "11:30", "کلاس ۲۰۴")
        seedCourse("10201", "برنامه‌سازی پیشرفته", "دکتر احمدی", day = 3, "13:30", "15:00", "آزمایشگاه ۲")
        seedCourse("10302", "ساختمان داده", "دکتر کریمی", day = 4, "08:00", "09:30", "کلاس ۱۰۱")
        seedCourse("10404", "مدارهای الکتریکی", "دکتر نوری", day = 5, "15:30", "17:00", "کلاس ۳۰۲")

        db.documentDao().insertDocument(
            CourseDocument(
                courseId = math,
                title = "جزوه فصل اول",
                category = DocumentCategory.PAMPHLET,
                isBookmarked = true
            )
        )
        db.documentDao().insertDocument(
            CourseDocument(
                courseId = math,
                title = "تمرین‌های سری ۲",
                category = DocumentCategory.ASSIGNMENT
            )
        )

        // One recorded absence, so the attendance card renders its alert state
        // instead of the neutral "nothing recorded" state.
        if (seededSectionIds.isNotEmpty()) {
            val today = dashboardNow(System.currentTimeMillis()).epochDay
            db.attendanceDao().upsertAttendance(
                AttendanceRecord(
                    sectionId = seededSectionIds.first(),
                    dateEpochDay = today - 7,
                    startTime = "10:00",
                    endTime = "11:30",
                    status = AttendanceStatus.ABSENT
                )
            )
        }

        // Past courses for the passed-courses screen.
        db.passedCourseDao().insertPassedCourse(
            PassedCourse(code = "90001", name = "ریاضی ۲", credits = 3, grade = "۱۸.۵", termLabel = "نیم‌سال دوم ۱۴۰۳")
        )
        db.passedCourseDao().insertPassedCourse(
            PassedCourse(code = "90002", name = "فیزیک ۱", credits = 3, grade = "۱۷", termLabel = "نیم‌سال دوم ۱۴۰۳")
        )
        db.passedCourseDao().insertPassedCourse(
            PassedCourse(code = "90003", name = "آمار و احتمال", credits = 2, grade = "۱۹", termLabel = "نیم‌سال اول ۱۴۰۳")
        )

        // One archived term for the academic-records screen.
        val termId = db.academicTermDao().insertTerm(
            AcademicTerm(
                name = "نیم‌سال دوم ۱۴۰۳",
                endEpochDay = dashboardNow(System.currentTimeMillis()).epochDay - 180
            )
        )
        db.academicTermDao().insertArchivedCourses(
            listOf(
                ArchivedCourse(
                    termId = termId,
                    courseId = 90001,
                    code = "90001",
                    name = "ریاضی ۲",
                    credits = 3,
                    status = TermCourseStatus.PASSED,
                    grade = "۱۸.۵"
                ),
                ArchivedCourse(
                    termId = termId,
                    courseId = 90002,
                    code = "90002",
                    name = "فیزیک ۱",
                    credits = 3,
                    status = TermCourseStatus.FAILED,
                    grade = "۹"
                )
            )
        )

        extraSeed?.invoke()

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                MyApplicationTheme(darkTheme = darkTheme, colorTheme = AppColorTheme.INDIGO) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        screen(viewModel)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private suspend fun seedCourse(
        code: String,
        name: String,
        instructor: String,
        day: Int,
        start: String,
        end: String,
        location: String,
        enrolled: Boolean = true,
        selected: Boolean = true
    ): Long {
        val courseId = db.courseDao().insertCourse(
            Course(code = code, name = name, credits = 3, isSelectedForGeneration = selected)
        )
        val sectionId = db.sectionDao().insertSection(
            CourseSection(
                courseId = courseId,
                sectionCode = "1",
                instructor = instructor,
                isEnrolled = enrolled
            )
        )
        seededSectionIds += sectionId
        db.sectionDao().insertSession(
            ClassSession(
                sectionId = sectionId,
                dayOfWeek = day,
                startTime = start,
                endTime = end,
                location = location
            )
        )
        return courseId
    }
}
