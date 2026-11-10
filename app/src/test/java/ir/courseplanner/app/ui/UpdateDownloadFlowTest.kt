package ir.courseplanner.app.ui

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.courseplanner.app.BuildConfig
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.update.ApkDownloader
import ir.courseplanner.app.update.ApkInspector
import ir.courseplanner.app.update.DownloadException
import ir.courseplanner.app.update.DownloadProgress
import ir.courseplanner.app.update.InstalledApkInfo
import ir.courseplanner.app.update.ReleaseVersion
import ir.courseplanner.app.update.UpdateChecker
import ir.courseplanner.app.update.UpdateDownloadState
import ir.courseplanner.app.update.UpdateFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The in-app download state machine, end to end but offline: a fake
 * [ApkDownloader] supplies the bytes and a fake inspector supplies the archive
 * metadata, so the real ViewModel transitions are exercised without a network
 * or an installed APK.
 *
 * Pins the promises the dialog depends on: the prompt stays open while
 * downloading, a verified archive becomes [UpdateDownloadState.Ready], every
 * rejection reason maps to its own state, and cancelling/dismissing deletes the
 * cached file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UpdateDownloadFlowTest {

    private lateinit var repository: CourseRepository
    private lateinit var preferencesManager: PreferencesManager

    private fun viewModel(
        downloader: ApkDownloader,
        inspector: (File) -> InstalledApkInfo?,
        tag: String
    ): CoursePlannerViewModel = CoursePlannerViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        repository = repository,
        preferencesManager = preferencesManager,
        updateChecker = UpdateChecker(
            currentVersion = ReleaseVersion.parse("9.9.8"),
            dispatcher = UnconfinedTestDispatcher(),
            fetcher = { releaseJson("v$tag") }
        ),
        apkDownloader = downloader,
        apkInspector = LambdaApkInspector(inspector)
    )

    /** Adapts a plain lambda to the [ApkInspector] seam the ViewModel exposes. */
    private class LambdaApkInspector(
        private val inspect: (File) -> InstalledApkInfo?
    ) : ApkInspector() {
        override fun read(context: Context, file: File): InstalledApkInfo? = inspect(file)
    }

    private fun releaseJson(tag: String): String = """
        {
          "tag_name": "$tag",
          "html_url": "https://github.com/Ara-0x/TermChin/releases/tag/$tag",
          "assets": [
            {"name":"TermChin-$tag.apk","browser_download_url":"https://github.com/Ara-0x/TermChin/releases/download/$tag/TermChin-$tag.apk"}
          ]
        }
    """.trimIndent()

    /**
     * Builds a ViewModel for a release with its own [tag].
     *
     * Each test uses a distinct tag on purpose: the "already prompted" marker is
     * durable, so a version another test (or an earlier run on the same data
     * directory) dismissed must never suppress this test's prompt.
     */
    private fun newVm(
        tag: String = "9.9.9",
        downloader: ApkDownloader = FakeApkDownloader(),
        inspector: (File) -> InstalledApkInfo? = {
            InstalledApkInfo("ir.courseplanner.app", BuildConfig.VERSION_CODE + 1L)
        }
    ): CoursePlannerViewModel {
        val vm = viewModel(downloader, inspector, tag)
        vm.checkForUpdateOnce()
        awaitStage("the release must be offered") { vm.availableUpdate.value }
        return vm
    }

    private fun awaitStage(stage: String, read: () -> Any?): Any? {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MS * 1_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            read()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Timed out after ${AWAIT_TIMEOUT_MS}ms — stage: $stage")
    }

    private fun awaitDownload(vm: CoursePlannerViewModel): UpdateDownloadState {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MS * 1_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = vm.updateDownload.value
            if (state !is UpdateDownloadState.Idle && state !is UpdateDownloadState.Downloading) {
                return state
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError(
            "Timed out waiting for the download to finish; last state ${vm.updateDownload.value}"
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CourseRepository(
            db,
            db.courseDao(),
            db.sectionDao(),
            db.documentDao(),
            db.passedCourseDao(),
            db.attendanceDao()
        )
        preferencesManager = PreferencesManager(context)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a verified download becomes ready and keeps the prompt open`() = runBlocking {
        val vm = newVm()
        vm.startUpdateDownload()

        val ready = awaitDownload(vm) as UpdateDownloadState.Ready
        assertTrue(ready.apk.exists())
        assertEquals("TermChin-9.9.9.apk", ready.apk.name)
        assertEquals("updates", ready.apk.parentFile?.name)
        assertNotNull("the dialog must stay open while downloading", vm.availableUpdate.value)
        assertTrue(
            "the archive must live in the FileProvider cache folder",
            ready.apk.path.contains("cache")
        )
    }

    @Test
    fun `a failed transfer surfaces its reason and offers a retry`() = runBlocking {
        val vm = newVm(tag = "9.9.10", downloader = FakeApkDownloader(failure = UpdateFailure.CHECKSUM))
        vm.startUpdateDownload()

        val failed = awaitDownload(vm) as UpdateDownloadState.Failed
        assertEquals(UpdateFailure.CHECKSUM, failed.failure)
        assertNotNull(vm.availableUpdate.value)
    }

    @Test
    fun `an archive that is not an apk is rejected and deleted`() = runBlocking {
        var downloaded: File? = null
        val vm = newVm(
            tag = "9.9.11",
            downloader = FakeApkDownloader(onWritten = { downloaded = it }),
            inspector = { null }
        )
        vm.startUpdateDownload()

        val failed = awaitDownload(vm) as UpdateDownloadState.Failed
        assertEquals(UpdateFailure.NOT_AN_APK, failed.failure)
        assertFalse("an unusable archive must not stay cached", downloaded!!.exists())
    }

    @Test
    fun `an archive for another package is rejected`() = runBlocking {
        val vm = newVm(tag = "9.9.12", inspector = { InstalledApkInfo("com.example.other", 999L) })
        vm.startUpdateDownload()

        val failed = awaitDownload(vm) as UpdateDownloadState.Failed
        assertEquals(UpdateFailure.WRONG_PACKAGE, failed.failure)
    }

    @Test
    fun `an archive that is not newer is rejected`() = runBlocking {
        val vm = newVm(
            tag = "9.9.13",
            inspector = {
                InstalledApkInfo("ir.courseplanner.app", BuildConfig.VERSION_CODE.toLong())
            }
        )
        vm.startUpdateDownload()

        val failed = awaitDownload(vm) as UpdateDownloadState.Failed
        assertEquals(UpdateFailure.OLD_VERSION, failed.failure)
    }

    @Test
    fun `cancelling after a finished download deletes the file`() = runBlocking {
        val vm = newVm(tag = "9.9.14")
        vm.startUpdateDownload()
        val ready = awaitDownload(vm) as UpdateDownloadState.Ready

        vm.cancelUpdateDownload()
        assertEquals(UpdateDownloadState.Idle, vm.updateDownload.value)
        assertFalse(ready.apk.exists())
    }

    @Test
    fun `dismissing closes the prompt and discards the download`() = runBlocking {
        val vm = newVm(tag = "9.9.15")
        vm.startUpdateDownload()
        val ready = awaitDownload(vm) as UpdateDownloadState.Ready

        vm.dismissUpdatePrompt()
        assertNull(vm.availableUpdate.value)
        assertEquals(UpdateDownloadState.Idle, vm.updateDownload.value)
        assertFalse(ready.apk.exists())
    }

    /**
     * Fakes the network half: optionally reports one progress sample, then either
     * writes [payload] to the target or throws [failure].
     */
    private class FakeApkDownloader(
        private val failure: UpdateFailure? = null,
        private val onWritten: (File) -> Unit = {}
    ) : ApkDownloader() {
        override suspend fun download(
            url: String,
            expectedSha256: String?,
            target: File,
            onProgress: (DownloadProgress) -> Unit
        ): File {
            onProgress(DownloadProgress(512, 1024))
            if (failure != null) throw DownloadException(failure)
            target.parentFile?.mkdirs()
            target.writeBytes(byteArrayOf(1, 2, 3))
            onWritten(target)
            return target
        }
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 25L
    }
}
