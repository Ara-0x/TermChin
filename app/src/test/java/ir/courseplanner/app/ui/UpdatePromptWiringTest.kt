package ir.courseplanner.app.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.update.ReleaseVersion
import ir.courseplanner.app.update.UpdateChecker
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * ViewModel-level wiring of the update prompt: the checker, the
 * once-per-version gate and the persisted "already shown" marker composed
 * together, with the real [PreferencesManager] (Robolectric) and a fake
 * [UpdateChecker] (no network).
 *
 * Covers the composition no unit test reached before: prompt → «بعداً» →
 * relaunch stays quiet → a genuinely newer release prompts again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UpdatePromptWiringTest {

    private lateinit var repository: CourseRepository
    private lateinit var preferencesManager: PreferencesManager

    private fun releaseJson(tag: String): String = """
        {
          "tag_name": "$tag",
          "html_url": "https://github.com/Ara-0x/TermChin/releases/tag/$tag",
          "assets": [
            {"name":"TermChin-$tag.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/$tag/TermChin-$tag.apk"}
          ]
        }
    """.trimIndent()

    private fun viewModelFor(tag: String): CoursePlannerViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val checker = UpdateChecker(
            currentVersion = ReleaseVersion.parse("2.7.3"),
            dispatcher = UnconfinedTestDispatcher(),
            fetcher = { releaseJson(tag) }
        )
        return CoursePlannerViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            repository = repository,
            preferencesManager = preferencesManager,
            updateChecker = checker
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CourseRepository(db, db.courseDao(), db.sectionDao(), db.documentDao())
        preferencesManager = PreferencesManager(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `prompt then later then quiet then newer prompts again`() = runBlocking {
        // Start from a clean marker: other suites (and reruns) share the same
        // Robolectric DataStore file, so never assume "never prompted".
        preferencesManager.setLastPromptedUpdateVersionSync("")
        shadowOf(Looper.getMainLooper()).idle()

        // First cold start: a newer release prompts.
        val first = viewModelFor("v2.7.4")
        first.checkForUpdateOnce()
        shadowOf(Looper.getMainLooper()).idle()
        val offered = withTimeout(10_000) {
            first.availableUpdate.first { it != null }
        }
        assertEquals("2.7.4", offered?.version?.versionName)

        // «بعداً»: dismissed, and the version durably recorded.
        first.dismissUpdatePrompt()
        shadowOf(Looper.getMainLooper()).idle()
        withTimeout(10_000) {
            preferencesManager.lastPromptedUpdateVersion.first { it == "2.7.4" }
        }

        // Second cold start, same release: the gate suppresses the prompt, so
        // _availableUpdate stays null. Poll briefly instead of waiting forever.
        val second = viewModelFor("v2.7.4")
        second.checkForUpdateOnce()
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            delay(20)
        }
        assertNull(
            "already-prompted version must stay quiet on relaunch",
            second.availableUpdate.value
        )

        // Third cold start, genuinely newer release: prompts again.
        val third = viewModelFor("v2.7.5")
        third.checkForUpdateOnce()
        shadowOf(Looper.getMainLooper()).idle()
        val reoffered = withTimeout(10_000) {
            third.availableUpdate.first { it != null }
        }
        assertEquals("2.7.5", reoffered?.version?.versionName)
    }
}
