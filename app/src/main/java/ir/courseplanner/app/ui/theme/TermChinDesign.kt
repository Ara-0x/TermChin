package ir.courseplanner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
 *
 * Premium layer (Linear/Raycast-inspired): all accents derive from the ACTIVE
 * Material3 palette at call sites — no hard-coded hue lives here. These
 * tokens only describe geometry and overlay strengths so the premium finish
 * follows every one of the 8 saved themes in both light and dark modes.
 */
object TermChinDesign {
    /** Outer screen padding (also used by HomeScreen scroll column). */
    val ScreenMargin: Dp = 16.dp

    /** Every card on every screen — one radius, no competing scales. */
    val CardRadius: Dp = 22.dp
    /** Nested rows inside a card. */
    val RowRadius: Dp = 16.dp
    /** Compact pill (date cells, count chips). */
    val PillRadius: Dp = 20.dp
    /** Compact control radius (chips, badges, segmented buttons). */
    val ChipRadius: Dp = 10.dp
    /** Input / small-button radius — one step under the nested row. */
    val ControlRadius: Dp = 13.dp
    /** Micro accent radius for tiny bars and mini badges. */
    val MicroRadius: Dp = 8.dp

    /**
     * The only card elevation in the app. Hierarchy comes from tonal surface
     * color and a hairline outline, never a second shadow step.
     */
    val CardElevation: Dp = 1.dp

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

    /** Premium weekly date-strip cell geometry. */
    val DateCellWidth: Dp = 56.dp
    val DateCellHeight: Dp = 72.dp
    /** Class indicator dot under a date cell that has sessions. */
    val DateDotSize: Dp = 5.dp

    val CardShape = RoundedCornerShape(CardRadius)
    val RowShape = RoundedCornerShape(RowRadius)
    val PillShape = RoundedCornerShape(PillRadius)
    val ChipShape = RoundedCornerShape(ChipRadius)
    val ControlShape = RoundedCornerShape(ControlRadius)
    val MicroShape = RoundedCornerShape(MicroRadius)

    /**
     * Selected date-cell wash: a flat accent tint. Deliberately a single flat
     * color, not a gradient — selection state is communicated by tint plus
     * border weight only, so nothing on screen looks "lit up".
     */
    fun selectedCell(accent: Color): Brush = SolidColor(accent)
}
