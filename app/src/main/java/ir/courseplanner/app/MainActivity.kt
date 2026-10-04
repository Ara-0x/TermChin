package ir.courseplanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.courseplanner.app.data.preferences.ThemeMode
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.components.UpdateAvailableDialog
import ir.courseplanner.app.ui.screens.CoursesScreen
import ir.courseplanner.app.ui.screens.DocumentsScreen
import ir.courseplanner.app.ui.screens.HomeScreen
import ir.courseplanner.app.ui.screens.ScheduleScreen
import ir.courseplanner.app.ui.screens.SettingsScreen
import ir.courseplanner.app.ui.theme.MyApplicationTheme
import ir.courseplanner.app.update.downloadInBrowser

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
            Surface(
                modifier = Modifier
                    .shadow(8.dp)
                    .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                color = MaterialTheme.colorScheme.surface
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    NavigationBarItem(
                        selected = currentDestination == AppDestination.HOME,
                        onClick = { viewModel.navigateTo(AppDestination.HOME) },
                        icon = {
                            Icon(
                                if (currentDestination == AppDestination.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "خانه"
                            )
                        },
                        label = {
                            Text(
                                "خانه",
                                fontSize = 11.5.sp,
                                fontWeight = if (currentDestination == AppDestination.HOME) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.COURSES,
                        onClick = { viewModel.navigateTo(AppDestination.COURSES) },
                        icon = {
                            Icon(
                                 if (currentDestination == AppDestination.COURSES) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = "دروس"
                            )
                        },
                        label = {
                            Text(
                                "دروس",
                                fontSize = 11.5.sp,
                                fontWeight = if (currentDestination == AppDestination.COURSES) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_courses")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.SCHEDULE,
                        onClick = { viewModel.navigateTo(AppDestination.SCHEDULE) },
                        icon = {
                            Icon(
                                if (currentDestination == AppDestination.SCHEDULE) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                contentDescription = "برنامه‌ساز"
                            )
                        },
                        label = {
                            Text(
                                "برنامه‌ساز",
                                fontSize = 11.sp,
                                fontWeight = if (currentDestination == AppDestination.SCHEDULE) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_schedule")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.DOCUMENTS,
                        onClick = { viewModel.navigateTo(AppDestination.DOCUMENTS) },
                        icon = {
                            Icon(
                                if (currentDestination == AppDestination.DOCUMENTS) Icons.Filled.Description else Icons.Outlined.Description,
                                contentDescription = "جزوات"
                            )
                        },
                        label = {
                            Text(
                                "جزوات",
                                fontSize = 11.sp,
                                fontWeight = if (currentDestination == AppDestination.DOCUMENTS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_documents")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.SETTINGS,
                        onClick = { viewModel.navigateTo(AppDestination.SETTINGS) },
                        icon = {
                            Icon(
                                if (currentDestination == AppDestination.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "تنظیمات"
                            )
                        },
                        label = {
                            Text(
                                "تنظیمات",
                                fontSize = 11.sp,
                                fontWeight = if (currentDestination == AppDestination.SETTINGS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                // RTL-aware: forward motion slides right-to-left, matching the
                // RTL layout direction of the app.
                val slideOffset = if (targetState.ordinal > initialState.ordinal) -160 else 160
                (slideInHorizontally(animationSpec = tween(260)) { slideOffset } + fadeIn(animationSpec = tween(220)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(260)) { -slideOffset / 2 } + fadeOut(animationSpec = tween(180)))
            },
            label = "ScreenTransition"
        ) { destination ->
            when (destination) {
                AppDestination.HOME -> HomeScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.COURSES -> CoursesScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.SCHEDULE -> ScheduleScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.DOCUMENTS -> DocumentsScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.SETTINGS -> SettingsScreen(viewModel = viewModel, modifier = screenModifier)
            }
        }
    }
}
