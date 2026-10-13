package ir.courseplanner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared visual rules for the unified Home dashboard + bottom navigation.
 *
 * Single source of truth for screen margins, section spacing, corner radii,
 * icon sizes and status colors so Home, its sections and the bottom bar stay
 * consistent in light/dark themes, RTL and large font scales.
 *
 * Sections use a small number of meaningful surfaces: plain text sections by
 * default, a filled [Card] only for Today's Schedule / Attention / Overview.
 */
object TermChinDesign {
    /** Outer screen padding (also used by HomeScreen scroll column). */
    val ScreenMargin: Dp = 16.dp
    /** Vertical gap between major Home sections. */
    val SectionSpacing: Dp = 16.dp
    /** Gap inside a section between title and content. */
    val SectionInnerSpacing: Dp = 8.dp
    /** Gap between rows inside lists/timelines. */
    val RowSpacing: Dp = 8.dp

    /** Primary cards (Today schedule, attention, overview). */
    val CardRadius: Dp = 16.dp
    /** Nested rows / chips inside a card. */
    val RowRadius: Dp = 12.dp
    /** Small badges and time pills. */
    val BadgeRadius: Dp = 10.dp

    /** Standard content icon (section headers, attention rows). */
    val IconMd: Dp = 20.dp
    /** Small inline icon (instructor / location / exam lines). */
    val IconSm: Dp = 16.dp
    /** Bottom-navigation icons — uniform for all five destinations. */
    val NavIcon: Dp = 24.dp
    /** Empty-state / onboarding illustration icon. */
    val EmptyArt: Dp = 24.dp

    /** Focused timeline row accent bar width. */
    val FocusBarWidth: Dp = 4.dp

    /** Minimum comfortable touch height for text actions. */
    val MinTouchHeight: Dp = 48.dp

    val CardShape = RoundedCornerShape(CardRadius)
    val RowShape = RoundedCornerShape(RowRadius)
    val BadgeShape = RoundedCornerShape(BadgeRadius)
}

/** Status colors resolved from the active theme (never hardcoded). */
object TermChinStatus {
    @Composable
    fun ongoing(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun warning(): Color = MaterialTheme.colorScheme.error

    @Composable
    fun success(): Color =
        if (isDarkTheme()) SuccessGreenDark else SuccessGreenLight
}

/** Section title style shared by every Home section header. */
@Composable
fun termChinSectionTitle(): TextStyle =
    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

/** Small status / countdown line under a focused class. */
@Composable
fun termChinStatusLine(): TextStyle =
    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
