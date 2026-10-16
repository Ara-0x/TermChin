package ir.courseplanner.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * Modern floating bottom navigation: a rounded pill that hovers above the
 * content instead of a full-width slab. Five destinations share one consistent
 * selected state (filled icon + semibold label + soft indicator), uniform 24dp
 * icons and theme typography labels that scale with the system font. The bar
 * draws its own system-navigation inset so the pill never collides with the
 * Android gesture bar.
 */
@Composable
fun TermChinBottomBar(
    current: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
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
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "navIconScale"
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(entry.destination) },
                    icon = {
                        Icon(
                            if (selected) entry.selectedIcon else entry.unselectedIcon,
                            contentDescription = entry.label,
                            modifier = Modifier
                                .size(TermChinDesign.NavIcon)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    },
                    label = {
                        // A compact label keeps all five Persian destinations
                        // readable on common narrow Android handsets.
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
}
