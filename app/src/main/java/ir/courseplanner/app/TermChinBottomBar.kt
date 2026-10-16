package ir.courseplanner.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/** Uniform height of the pill interior, shared by items and the indicator. */
private val BAR_HEIGHT = 58.dp

/** Corner radius of the sliding indicator and its matching item hit-area. */
private val INDICATOR_RADIUS = 18.dp

/**
 * Modern floating bottom navigation: a rounded pill that hovers above the
 * content. The selected destination is marked by a single pill indicator that
 * *slides* between slots (a spring, not a snap), while icons and labels fade
 * between their selected and resting colors. The indicator is positioned from
 * the real layout direction, so the slide travels with the row in RTL as well
 * as LTR. The bar draws its own system-navigation inset so the pill never
 * collides with the Android gesture bar.
 */
@Composable
fun TermChinBottomBar(
    current: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = NAV_ENTRIES
        .indexOfFirst { it.destination == current }
        .coerceAtLeast(0)

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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .testTag("main_bottom_nav")
                    .padding(6.dp)
            ) {
                val count = NAV_ENTRIES.size
                val itemWidth = maxWidth / count
                // TopStart + a direction-aware offset mirror together under RTL,
                // so one slot of travel lines the pill up with the Arabic row.
                val indicatorX by animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "navIndicatorX"
                )
                // Drawn before the items so the sliding pill stays behind them.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = indicatorX)
                        .width(itemWidth)
                        .height(BAR_HEIGHT)
                        .clip(RoundedCornerShape(INDICATOR_RADIUS))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BAR_HEIGHT)
                ) {
                    NAV_ENTRIES.forEachIndexed { index, entry ->
                        NavItem(
                            entry = entry,
                            selected = index == selectedIndex,
                            onClick = { onNavigate(entry.destination) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    entry: NavEntry,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navIconScale"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navContentColor"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(INDICATOR_RADIUS))
            .clickable(onClick = onClick)
            .testTag(entry.testTag)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (selected) entry.selectedIcon else entry.unselectedIcon,
            contentDescription = entry.label,
            tint = contentColor,
            modifier = Modifier
                .size(TermChinDesign.NavIcon)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            entry.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = contentColor,
            maxLines = 1
        )
    }
}
