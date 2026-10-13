package ir.courseplanner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared visual rules for the unified Home dashboard + bottom navigation.
 *
 * Single source of truth for screen margins, corner radii and icon sizes so
 * Home, its sections and the bottom bar stay consistent in light/dark themes,
 * RTL and large font scales. Only tokens with at least one call site live
 * here — speculative constants are removed, not stockpiled.
 *
 * Sections use a small number of meaningful surfaces: plain text sections by
 * default, a filled [Card] only for Today's Schedule / Attention / Overview.
 */
object TermChinDesign {
    /** Outer screen padding (also used by HomeScreen scroll column). */
    val ScreenMargin: Dp = 16.dp

    /** Primary cards (Today schedule, attention, overview). */
    val CardRadius: Dp = 16.dp
    /** Nested rows inside a card. */
    val RowRadius: Dp = 12.dp

    /** Standard content icon (section headers, attention rows). */
    val IconMd: Dp = 20.dp
    /** Small inline icon (buttons, compact rows). */
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
}
