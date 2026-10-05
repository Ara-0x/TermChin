package ir.courseplanner.app.ui

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.update.ReleaseVersion
import ir.courseplanner.app.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ViewModel-level wiring of the update prompt: the checker, the
 * once-per-version gate and the persisted "already shown" marker composed
 * together, with the real [PreferencesManager] (Robolectric) and a fake
 * [UpdateChecker] (no network).
 *
 * Covers the composition no unit test reached before: prompt → «بعداً» →
 * relaunch stays quiet → a genuinely newer release prompts again.
 *
 * Waits are polling loops over `.value` plus an explicit main-looper pump, not
 * `withTimeout { flow.first {...} }`: the ViewModel coroutine completes on a
 * background thread (DataStore I/O), so a bare suspend-wait can hang without
 * ever telling the caller WHICH stage stalled. Each stage has its own deadline
 * and message, so a CI failure names the stage instead of printing a bare
 * `TimeoutCancellationException`.
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

    /**
     * Polls [read] until non-null, pumping the Robolectric main looper each pass.
     *
     * Returns the value, or throws with [stage] in the message so a CI failure
     * identifies exactly which step never completed.
     */
    private fun <T : Any> awaitStage(stage: String, read: () -> T?): T {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MS * 1_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            read()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
        read()?.let { return it }
        throw AssertionError("Timed out after ${AWAIT_TIMEOUT_MS}ms — stage: $stage")
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

        // Stage 1 — first cold start: a newer release prompts.
        val firstVm = viewModelFor("v2.7.4")
        firstVm.checkForUpdateOnce()
        val offered = awaitStage(
            "first launch must offer 2.7.4, but availableUpdate stayed null"
        ) { firstVm.availableUpdate.value }
        assertEquals("2.7.4", offered.version.versionName)

        // Stage 2 — «بعداً»: dismissed, and the version durably recorded.
        firstVm.dismissUpdatePrompt()
        val marker = awaitStage(
            "«بعداً» must persist 2.7.4 as the prompted marker"
        ) {
            // Read the CURRENT value (never suspends waiting for a future one)
            // and only surface it once it matches, so the stage deadline still
            // applies instead of a predicate that could hang forever.
            runBlocking { preferencesManager.lastPromptedUpdateVersion.first() }
                ?.takeIf { it == "2.7.4" }
        }
        assertEquals("«بعداً» recorded the wrong marker", "2.7.4", marker)

        // Stage 3 — second cold start, same release: the gate suppresses the
        // prompt, so _availableUpdate must stay null for the whole window.
        val second = viewModelFor("v2.7.4")
        second.checkForUpdateOnce()
        val quietDeadline = System.nanoTime() + QUIET_WINDOW_MS * 1_000_000L
        while (System.nanoTime() < quietDeadline) {
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(
                "already-prompted version must stay quiet on relaunch, " +
                    "but 2.7.4 was re-offered",
                second.availableUpdate.value
            )
            Thread.sleep(POLL_INTERVAL_MS)
        }

        // Stage 4 — third cold start, genuinely newer release: prompts again.
        val third = viewModelFor("v2.7.5")
        third.checkForUpdateOnce()
        val reoffered = awaitStage(
            "a genuinely newer release (2.7.5) must prompt, but availableUpdate stayed null"
        ) { third.availableUpdate.value }
        assertEquals("2.7.5", reoffered.version.versionName)
    }

    private companion object {
        /** Generous: CI runners are slower than a dev laptop. */
        const val AWAIT_TIMEOUT_MS = 30_000L
        /** How long the gate must keep the prompt suppressed. */
        const val QUIET_WINDOW_MS = 1_500L
        const val POLL_INTERVAL_MS = 25L
    }
}
