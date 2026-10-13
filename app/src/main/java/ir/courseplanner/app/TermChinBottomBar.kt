package ir.courseplanner.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.theme.TermChinDesign

private data class NavEntry(
    val destination: AppDestination,
    val label: String,
    val testTag: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_ENTRIES = listOf(
    NavEntry(AppDestination.HOME, "خانه", "nav_item_home", Icons.Filled.Home, Icons.Outlined.Home),
    NavEntry(AppDestination.COURSES, "دروس", "nav_item_courses", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    NavEntry(AppDestination.SCHEDULE, "برنامه‌ساز", "nav_item_schedule", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    NavEntry(AppDestination.DOCUMENTS, "جزوات", "nav_item_documents", Icons.Filled.Description, Icons.Outlined.Description),
    NavEntry(AppDestination.SETTINGS, "تنظیمات", "nav_item_settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

/**
 * Unified bottom navigation: five destinations, one consistent selected state
 * (filled icon + semibold label + soft indicator), uniform 24dp icons and
 * theme typography labels that scale with the system font. The bar draws its
 * own system-navigation inset so edge-to-edge content never collides with the
 * Android gesture bar.
 */
@Composable
fun TermChinBottomBar(
    current: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars),
        shadowElevation = 2.dp
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.testTag("main_bottom_nav")
        ) {
            val navColors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NAV_ENTRIES.forEach { entry ->
                val selected = current == entry.destination
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(entry.destination) },
                    icon = {
                        Icon(
                            if (selected) entry.selectedIcon else entry.unselectedIcon,
                            contentDescription = entry.label,
                            modifier = Modifier.size(TermChinDesign.NavIcon)
                        )
                    },
                    label = {
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = true,
                    colors = navColors,
                    modifier = Modifier.testTag(entry.testTag)
                )
            }
        }
    }
}
