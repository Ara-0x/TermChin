package ir.courseplanner.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.courseplanner.app.data.preferences.ThemeMode
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.components.UpdateAvailableDialog
import ir.courseplanner.app.ui.screens.AddCourseScreen
import ir.courseplanner.app.ui.screens.CoursesScreen
import ir.courseplanner.app.ui.screens.DocumentsScreen
import ir.courseplanner.app.ui.screens.HomeScreen
import ir.courseplanner.app.ui.screens.ScheduleScreen
import ir.courseplanner.app.ui.screens.SettingsScreen
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.update.downloadInBrowser
import kotlinx.coroutines.launch

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
    val snackbarHostState = remember { SnackbarHostState() }
    val backScope = rememberCoroutineScope()
    val activity = LocalContext.current as? android.app.Activity
    var lastHomeBackPressAt by remember { mutableStateOf(0L) }

    // Back always returns to the student's safe starting point. At Home, a
    // second press in a short window confirms exit without a blocking dialog.
    // The discovery sub-screen is a child of Courses, so system back there
    // returns to «دروس من» instead of skipping straight to Home.
    BackHandler {
        if (currentDestination == AppDestination.ADD_COURSE) {
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

    // The prompt hands the download to the browser and does nothing else: the app
    // neither downloads nor installs the APK itself.
    val updateContext = LocalContext.current
    availableUpdate?.let { update ->
        UpdateAvailableDialog(
            update = update,
            currentVersionName = BuildConfig.VERSION_NAME,
            onDownload = {
                viewModel.onUpdateDownloadRequested()
                val opened = downloadInBrowser(updateContext, update.downloadUrl)
                if (!opened) {
                    viewModel.showUserFacingMessage(
                        "مرورگری برای باز کردن لینک دانلود پیدا نشد. لینک را از صفحهٔ Releases در گیت‌هاب باز کنید.",
                        isError = true
                    )
                }
            },
            onLater = { viewModel.dismissUpdatePrompt() }
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
            if (currentDestination != AppDestination.ADD_COURSE) {
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
                // Modern shared-axis feel: a short directional slide combined
                // with a subtle depth scale and a cross-fade. RTL-aware — forward
                // motion slides right-to-left so it matches the app's direction.
                val forward = targetState.ordinal > initialState.ordinal
                val direction = if (forward) -1 else 1
                val enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> direction * fullWidth / 6 }
                ) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 260))
                val exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> -direction * fullWidth / 10 }
                ) + scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 200))
                enter togetherWith exit
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
            }
        }
    }
}

private const val EXIT_CONFIRM_WINDOW_MS = 2_000L
