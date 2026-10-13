package ir.courseplanner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    /** Primary cards (Today schedule, attention, overview). */
    val CardRadius: Dp = 16.dp
    /** Nested rows inside a card. */
    val RowRadius: Dp = 12.dp
    /** Premium hero surfaces (header, weekly date strip, now/next). */
    val HeroRadius: Dp = 20.dp
    /** Compact pill (date cells, count chips). */
    val PillRadius: Dp = 14.dp

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
    val HeroShape = RoundedCornerShape(HeroRadius)
    val PillShape = RoundedCornerShape(PillRadius)

    /**
     * Soft top-glow overlay for a hero card, built from the caller's own
     * accent (typically primary) fading to transparent. Keeps the Linear
     * ambient-glow feel while following the active theme in light/dark.
     */
    fun heroGlow(accent: Color, strength: Float = 0.16f): Brush =
        Brush.verticalGradient(
            0.0f to accent.copy(alpha = strength),
            0.45f to accent.copy(alpha = strength * 0.35f),
            1.0f to Color.Transparent
        )

    /**
     * Selected date-cell wash: accent-tinted vertical gradient that stays
     * legible on both light and dark surfaces of every saved theme.
     */
    fun selectedCell(accent: Color): Brush =
        Brush.verticalGradient(
            0.0f to accent,
            1.0f to accent.copy(alpha = 0.82f)
        )
}
