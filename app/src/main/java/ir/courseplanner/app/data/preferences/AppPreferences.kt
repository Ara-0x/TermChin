package ir.courseplanner.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class AppColorTheme(
    val id: String,
    val titleFa: String
) {
    INDIGO(
        id = "indigo",
        titleFa = "نیلی دانشگاهی",
    ),
    EMERALD(
        id = "emerald",
        titleFa = "زمردی جنگلی",
    ),
    VIOLET(
        id = "violet",
        titleFa = "ارغوانی رویال",
    ),
    AMBER(
        id = "amber",
        titleFa = "غروب کهربایی",
    ),
    OCEAN(
        id = "ocean",
        titleFa = "اقیانوسی فیروزه‌ای",
    ),
    ROSE(
        id = "rose",
        titleFa = "یاقوت سرخ",
    ),
    SLATE(
        id = "slate",
        titleFa = "نوک‌مدادی مینیمال",
    );

    companion object {
        fun fromId(id: String?): AppColorTheme {
            return values().find { it.id == id } ?: INDIGO
        }
    }
}

enum class ThemeMode(val id: String, val titleFa: String) {
    SYSTEM("system", "پیروی از سیستم"),
    LIGHT("light", "همیشه روشن"),
    DARK("dark", "همیشه تاریک");

    companion object {
        fun fromId(id: String?): ThemeMode {
            return values().find { it.id == id } ?: SYSTEM
        }
    }
}

enum class TimetableDensity(val id: String, val titleFa: String, val slotHeightDp: Int) {
    COMPACT("compact", "فشرده", 46),
    STANDARD("standard", "استاندارد", 58),
    SPACIOUS("spacious", "گسترده", 70);

    companion object {
        fun fromId(id: String?): TimetableDensity {
            return values().find { it.id == id } ?: STANDARD
        }
    }
}

data class UserPreferences(
    val theme: AppColorTheme = AppColorTheme.INDIGO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val studentName: String = "",
    val major: String = "",
    val semesterName: String = "نیم‌سال اول ۱۴۰۳-۱۴۰۴",
    val creditTarget: Int = 20,
    val showThursday: Boolean = true,
    val timetableDensity: TimetableDensity = TimetableDensity.STANDARD,
    /**
     * First day of university classes as epoch day (days since 1970-01-01).
     * Academic even/odd weeks are counted from here. Null = not set yet, so
     * the app must not guess the current week parity.
     */
    val semesterStartEpochDay: Long? = null,
    /** University convention: is week 1 (first 7 days) an odd week? Default true. */
    val firstWeekIsOdd: Boolean = true
)

private const val DATASTORE_NAME = "planner_user_prefs"

private val Context.plannerDataStore by preferencesDataStore(
    name = DATASTORE_NAME,
    produceMigrations = { context ->
        // One-time upgrade path: existing installs keep their SharedPreferences values.
        listOf<DataMigration<Preferences>>(SharedPreferencesMigration(context, DATASTORE_NAME))
    }
)

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dataStore = context.plannerDataStore

    val preferences: StateFlow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { it.toUserPreferences() }
        .stateIn(scope, SharingStarted.Eagerly, UserPreferences())

    private fun update(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { dataStore.edit(block) }
    }

    fun setColorTheme(theme: AppColorTheme) {
        update { it[KEY_THEME] = theme.id }
    }

    fun setThemeMode(mode: ThemeMode) {
        update { it[KEY_THEME_MODE] = mode.id }
    }

    fun setStudentProfile(name: String, major: String, semester: String) {
        update {
            it[KEY_STUDENT_NAME] = name.trim()
            it[KEY_MAJOR] = major.trim()
            it[KEY_SEMESTER_NAME] = semester.trim().ifBlank { DEFAULT_SEMESTER }
        }
    }

    fun setCreditTarget(target: Int) {
        update { it[KEY_CREDIT_TARGET] = target.coerceIn(10, 30) }
    }

    fun setShowThursday(show: Boolean) {
        update { it[KEY_SHOW_THURSDAY] = show }
    }

    fun setTimetableDensity(density: TimetableDensity) {
        update { it[KEY_DENSITY] = density.id }
    }

    /** Null clears the stored start date (parity becomes unknown again). */
    fun setSemesterStartEpochDay(epochDay: Long?) {
        update {
            if (epochDay == null) it.remove(KEY_SEMESTER_START_EPOCH_DAY)
            else it[KEY_SEMESTER_START_EPOCH_DAY] = epochDay
        }
    }

    fun setFirstWeekIsOdd(firstWeekIsOdd: Boolean) {
        update { it[KEY_FIRST_WEEK_IS_ODD] = firstWeekIsOdd }
    }

    /**
     * Version whose update prompt was already shown to this user.
     *
     * Persisting it is what makes the prompt appear exactly once per released
     * version: a null value means "never shown", so a fresh install sees the
     * prompt for a newer release, and choosing "بعداً" (Later) writes the version
     * here so it is never shown again for that same release. Only a genuinely
     * newer version (a different string) re-arms the prompt.
     */
    val lastPromptedUpdateVersion: Flow<String?> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[KEY_LAST_PROMPTED_UPDATE_VERSION] }

    fun setLastPromptedUpdateVersion(versionName: String) {
        // Fire-and-forget on purpose: prompt dismissal is not data the user
        // edits, and DataStore applies edits in order. Callers that need the
        // write to be durable before reading it back use the suspend overload.
        update { it[KEY_LAST_PROMPTED_UPDATE_VERSION] = versionName }
    }

    /**
     * Suspends until the version is durably recorded. Prefer this from
     * coroutine callers (e.g. the update-prompt handlers) so a kill-and-relaunch
     * immediately after tapping «بعداً» cannot re-show the same prompt.
     */
    suspend fun setLastPromptedUpdateVersionSync(versionName: String) {
        dataStore.edit { it[KEY_LAST_PROMPTED_UPDATE_VERSION] = versionName }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        return UserPreferences(
            theme = AppColorTheme.fromId(this[KEY_THEME]),
            themeMode = ThemeMode.fromId(this[KEY_THEME_MODE]),
            studentName = this[KEY_STUDENT_NAME] ?: "",
            major = this[KEY_MAJOR] ?: "",
            semesterName = this[KEY_SEMESTER_NAME] ?: DEFAULT_SEMESTER,
            creditTarget = this[KEY_CREDIT_TARGET] ?: 20,
            showThursday = this[KEY_SHOW_THURSDAY] ?: true,
            timetableDensity = TimetableDensity.fromId(this[KEY_DENSITY]),
            semesterStartEpochDay = this[KEY_SEMESTER_START_EPOCH_DAY],
            firstWeekIsOdd = this[KEY_FIRST_WEEK_IS_ODD] ?: true
        )
    }

    companion object {
        private const val DEFAULT_SEMESTER = "نیم‌سال اول ۱۴۰۳-۱۴۰۴"
        private val KEY_THEME = stringPreferencesKey("app_theme")
        private val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
        private val KEY_STUDENT_NAME = stringPreferencesKey("student_name")
        private val KEY_MAJOR = stringPreferencesKey("student_major")
        private val KEY_SEMESTER_NAME = stringPreferencesKey("semester_name")
        private val KEY_CREDIT_TARGET = intPreferencesKey("credit_target")
        private val KEY_SHOW_THURSDAY = booleanPreferencesKey("show_thursday")
        private val KEY_DENSITY = stringPreferencesKey("timetable_density")
        private val KEY_SEMESTER_START_EPOCH_DAY = longPreferencesKey("semester_start_epoch_day")
        private val KEY_FIRST_WEEK_IS_ODD = booleanPreferencesKey("first_week_is_odd")
        private val KEY_LAST_PROMPTED_UPDATE_VERSION =
            stringPreferencesKey("last_prompted_update_version")
        // NOTE: the old "release_clean_courses_v1" marker was removed in v2.5.0.
        // It gated an automatic `clearAllData()` on startup ("marker missing →
        // wipe the database"), which could erase a real student's data whenever
        // the flag was absent. There is no replacement flag: nothing may delete
        // user data automatically. A stale value in an old DataStore file is
        // simply ignored.
    }
}
