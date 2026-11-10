package ir.courseplanner.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.courseplanner.app.data.preferences.ThemeMode
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.components.StartNewTermDialog
import ir.courseplanner.app.ui.components.UpdateAvailableDialog
import ir.courseplanner.app.ui.screens.AcademicRecordsScreen
import ir.courseplanner.app.ui.screens.AddCourseScreen
import ir.courseplanner.app.ui.screens.AttendanceScreen
import ir.courseplanner.app.ui.screens.CoursesScreen
import ir.courseplanner.app.ui.screens.DocumentsScreen
import ir.courseplanner.app.ui.screens.EndTermScreen
import ir.courseplanner.app.ui.screens.HomeScreen
import ir.courseplanner.app.ui.screens.PassedCoursesScreen
import ir.courseplanner.app.ui.screens.ScheduleScreen
import ir.courseplanner.app.ui.screens.SettingsScreen
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.update.UpdateDownloadState
import ir.courseplanner.app.update.UpdateInstaller
import ir.courseplanner.app.update.downloadInBrowser
import kotlinx.coroutines.launch

/**
 * One motion curve for the whole shell: a strong ease-out that starts quickly
 * and settles calmly. Built-in curves are too weak for a screen transition.
 */
private val ScreenEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: CoursePlannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Status/navigation bars follow the app theme (fix for washed-out
            // light icons on light background and vice versa).
            val context = LocalContext.current
            SideEffect {
                val window = (context as android.app.Activity).window
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }

            MyApplicationTheme(
                darkTheme = isDark,
                colorTheme = preferences.theme
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CoursePlannerApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun CoursePlannerApp(viewModel: CoursePlannerViewModel) {
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val isErrorMessage by viewModel.isErrorMessage.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val showStartNewTermDialog by viewModel.showStartNewTermDialog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val backScope = rememberCoroutineScope()
    val activity = LocalContext.current as? android.app.Activity
    var lastHomeBackPressAt by remember { mutableStateOf(0L) }

    // Back always returns to the student's safe starting point. At Home, a
    // second press in a short window confirms exit without a blocking dialog.
    // The discovery sub-screen is a child of Courses, so system back there
    // returns to «دروس من» instead of skipping straight to Home.
    BackHandler {
        if (currentDestination == AppDestination.ADD_COURSE ||
            currentDestination == AppDestination.PASSED_COURSES
        ) {
            lastHomeBackPressAt = 0L
            viewModel.navigateTo(AppDestination.COURSES)
        } else if (currentDestination != AppDestination.HOME) {
            lastHomeBackPressAt = 0L
            viewModel.navigateTo(AppDestination.HOME)
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHomeBackPressAt <= EXIT_CONFIRM_WINDOW_MS) {
                activity?.finish()
            } else {
                lastHomeBackPressAt = now
                backScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "برای خروج از ترم‌چین، دوباره دکمهٔ بازگشت را بزنید.",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // Exactly one update check per cold start, kicked off after the first frame
    // rather than during composition. The ViewModel guards against a second run,
    // so a recomposition or a configuration change cannot re-query the network.
    LaunchedEffect(Unit) {
        viewModel.checkForUpdateOnce()
    }

    // In-app update flow: the ViewModel owns the transfer (so it survives
    // recomposition), while the activity owns the two system hand-offs — the
    // "install unknown apps" toggle and the package installer.
    val updateContext = LocalContext.current
    val updateDownload by viewModel.updateDownload.collectAsStateWithLifecycle()
    var canInstallUpdates by remember { mutableStateOf(UpdateInstaller.canInstall(updateContext)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // The permission is changed in Settings, outside this process: re-read it
        // whenever the app comes back, so the dialog switches from "اجازهٔ نصب"
        // to "نصب بهروزرسانی" without a restart.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstallUpdates = UpdateInstaller.canInstall(updateContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    availableUpdate?.let { update ->
        UpdateAvailableDialog(
            update = update,
            currentVersionName = BuildConfig.VERSION_NAME,
            downloadState = if (update.hasApkAsset) updateDownload else UpdateDownloadState.Idle,
            installPermissionGranted = canInstallUpdates,
            onDownload = {
                if (update.hasApkAsset) {
                    viewModel.startUpdateDownload()
                } else {
                    // No APK asset in this release: the honest fallback is the
                    // release page in the browser.
                    val opened = downloadInBrowser(updateContext, update.downloadUrl)
                    if (opened) {
                        viewModel.dismissUpdatePrompt()
                    } else {
                        viewModel.showUserFacingMessage(
                            "مرورگری برای باز کردن صفحهٔ انتشار پیدا نشد. آدرس Releases را در گیت‌هاب باز کنید.",
                            isError = true
                        )
                    }
                }
            },
            onCancelDownload = viewModel::cancelUpdateDownload,
            onInstall = {
                val apk = (updateDownload as? UpdateDownloadState.Ready)?.apk
                if (apk == null) {
                    return@UpdateAvailableDialog
                }
                if (!UpdateInstaller.canInstall(updateContext)) {
                    canInstallUpdates = false
                    UpdateInstaller.openInstallPermissionSettings(updateContext)
                } else if (UpdateInstaller.install(updateContext, apk)) {
                    viewModel.onUpdateInstallerLaunched()
                } else {
                    viewModel.showUserFacingMessage(
                        "نصب‌کنندهٔ اندروید باز نشد. دوباره تلاش کنید.",
                        isError = true
                    )
                }
            },
            onGrantInstallPermission = {
                UpdateInstaller.openInstallPermissionSettings(updateContext)
            },
            onLater = viewModel::dismissUpdatePrompt
        )
    }

    if (showStartNewTermDialog) {
        StartNewTermDialog(
            onDismiss = viewModel::dismissStartNewTerm,
            onConfirm = viewModel::startNewTerm
        )
    }

    // App-wide result channel: every repository write in the ViewModel reports
    // through userMessage/isErrorMessage, so no DB failure can die silently in
    // a coroutine — success and error results land here as a visible snackbar.
    LaunchedEffect(userMessage, isErrorMessage) {
        val message = userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissUserMessage()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isErrorMessage) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseSurface
                    },
                    contentColor = if (isErrorMessage) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseOnSurface
                    }
                )
            }
        },
        bottomBar = {
            // The discovery sub-screen is a full page of its own: standard
            // back affordance in its header replaces the bottom navigation.
            // Ending a term and the academic-record page are full pages too.
            if (currentDestination != AppDestination.ADD_COURSE &&
                currentDestination != AppDestination.PASSED_COURSES &&
                currentDestination != AppDestination.END_TERM &&
                currentDestination != AppDestination.ACADEMIC_RECORDS &&
                currentDestination != AppDestination.ATTENDANCE
            ) {
                TermChinBottomBar(
                    current = currentDestination,
                    onNavigate = { viewModel.navigateTo(it) }
                )
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                // Tabs are peers: switching between them cross-fades, never
                // slides (a slide implies a spatial hierarchy the tab bar does
                // not have). Push-style sub-pages keep a short directional
                // slide so "deeper" and "back" stay legible.
                val topLevel = setOf(
                    AppDestination.HOME,
                    AppDestination.COURSES,
                    AppDestination.SCHEDULE,
                    AppDestination.DOCUMENTS,
                    AppDestination.SETTINGS
                )
                if (initialState in topLevel && targetState in topLevel) {
                    fadeIn(tween(180, easing = ScreenEasing)) togetherWith
                        fadeOut(tween(140, easing = ScreenEasing))
                } else {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) -1 else 1
                    val enter = slideInHorizontally(
                        animationSpec = tween(durationMillis = 260, easing = ScreenEasing),
                        initialOffsetX = { fullWidth -> direction * fullWidth / 8 }
                    ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = ScreenEasing))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(durationMillis = 200, easing = ScreenEasing),
                        targetOffsetX = { fullWidth -> -direction * fullWidth / 12 }
                    ) + fadeOut(animationSpec = tween(durationMillis = 140, easing = ScreenEasing))
                    enter togetherWith exit
                }
            },
            label = "ScreenTransition"
        ) { destination ->
            when (destination) {
                AppDestination.HOME -> HomeScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.COURSES -> CoursesScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.SCHEDULE -> ScheduleScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.DOCUMENTS -> DocumentsScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.SETTINGS -> SettingsScreen(viewModel = viewModel, modifier = screenModifier)
                // Discovery sub-screen of Courses: full page with its own
                // back button; no extra bottom-nav tab is created for it.
                AppDestination.ADD_COURSE -> AddCourseScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.PASSED_COURSES -> PassedCoursesScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.END_TERM -> EndTermScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.ACADEMIC_RECORDS -> AcademicRecordsScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.ATTENDANCE -> AttendanceScreen(viewModel = viewModel, modifier = screenModifier)
            }
        }
    }
}

private const val EXIT_CONFIRM_WINDOW_MS = 2_000L
