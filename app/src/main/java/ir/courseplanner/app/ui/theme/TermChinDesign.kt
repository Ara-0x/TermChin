package ir.courseplanner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared visual rules for the unified shell + all screens.
 *
 * Tokens are ported from the reference React design (index.css) so Android
 * and web render the same radii, gaps, icon sizes and elevations. Only tokens
 * with at least one call site live here.
 */
object TermChinDesign {
    /** Outer screen padding (also used by Home screen scroll column). */
    val ScreenMargin: Dp = 16.dp

    /** Max width of the centered app shell on wide screens (CSS: 520px). */
    val ShellMaxWidth: Dp = 520.dp

    /** App shell shadow: 0 0 80px rgba(35, 29, 77, 0.1). */
    val ShellElevation: Dp = 80.dp

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

    /** Large radius for hero card (CSS: 28px). */
    val HeroRadius: Dp = 28.dp

    /** Large radius for planner card (CSS: 26px). */
    val PlannerRadius: Dp = 26.dp

    /** Bottom navigation pill radius (CSS: 25px). */
    val BottomNavRadius: Dp = 25.dp

    /** The only card elevation in the app. Hierarchy comes from tonal surface
     * color and a hairline outline, never a second shadow step. */
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

    /** Brand mark size (CSS: 44px). */
    val BrandMarkSize: Dp = 44.dp
    val BrandMarkRadius: Dp = 15.dp

    /** Icon button size (CSS: 40px, radius 14px). */
    val IconButtonSize: Dp = 40.dp
    val IconButtonRadius: Dp = 14.dp

    /** Round arrow size (CSS: 34px, radius 12px). */
    val RoundArrowSize: Dp = 34.dp

    /** More button size (CSS: 34px, radius 12px). */
    val MoreButtonSize: Dp = 34.dp

    /** Credit ring size (CSS: 54px). */
    val CreditRingSize: Dp = 54.dp

    /** Day cell height (CSS: 68px, radius 18px). */
    val DayCellHeight: Dp = 68.dp
    val DayCellRadius: Dp = 18.dp

    /** Day course min height (CSS: 80px, radius 20px). */
    val DayCourseMinHeight: Dp = 80.dp
    val DayCourseRadius: Dp = 20.dp

    /** Stat card padding and radius (CSS: 14px 8px, radius 19px). */
    val StatCardPadding: Dp = 14.dp
    val StatCardRadius: Dp = 19.dp
    val StatIconSize: Dp = 34.dp
    val StatIconRadius: Dp = 12.dp

    /** Bottom nav height (CSS: 74px). */
    val BottomNavHeight: Dp = 74.dp

    /** Search field height (CSS: 52px, radius 17px). */
    val SearchFieldHeight: Dp = 52.dp
    val SearchFieldRadius: Dp = 17.dp

    /** Filter chip padding (CSS: 8px 13px, radius 12px). */
    val FilterChipPaddingHorizontal: Dp = 13.dp
    val FilterChipPaddingVertical: Dp = 8.dp
    val FilterChipRadius: Dp = 12.dp

    /** Course card padding (CSS: 14px, radius 20px). */
    val CourseCardPadding: Dp = 14.dp
    val CourseCardRadius: Dp = 20.dp
    val CourseAvatarSize: Dp = 46.dp
    val CourseAvatarRadius: Dp = 15.dp

    /** Planner card padding (CSS: 24px 18px 18px). */
    val PlannerCardPaddingTop: Dp = 24.dp
    val PlannerCardPaddingHorizontal: Dp = 18.dp
    val PlannerCardPaddingBottom: Dp = 18.dp

    /** Primary button height (CSS: 50px, radius 16px). */
    val PrimaryButtonHeight: Dp = 50.dp
    val PrimaryButtonRadius: Dp = 16.dp

    /** Settings row padding (CSS: 13px, radius 18px). */
    val SettingsRowPadding: Dp = 13.dp
    val SettingsRowRadius: Dp = 18.dp

    /** Toggle size (CSS: 42x24). */
    val ToggleWidth: Dp = 42.dp
    val ToggleHeight: Dp = 24.dp

    val CardShape = RoundedCornerShape(CardRadius)
    val RowShape = RoundedCornerShape(RowRadius)
    val PillShape = RoundedCornerShape(PillRadius)
    val ChipShape = RoundedCornerShape(ChipRadius)
    val ControlShape = RoundedCornerShape(ControlRadius)
    val MicroShape = RoundedCornerShape(MicroRadius)
    val HeroShape = RoundedCornerShape(HeroRadius)
    val PlannerShape = RoundedCornerShape(PlannerRadius)
    val BottomNavShape = RoundedCornerShape(BottomNavRadius)
    val DayShape = RoundedCornerShape(DayCellRadius)
    val DayCourseShape = RoundedCornerShape(DayCourseRadius)
    val SearchShape = RoundedCornerShape(SearchFieldRadius)
    val BrandShape = RoundedCornerShape(BrandMarkRadius)
    val RoundArrowShape = RoundedCornerShape(12.dp)
    val MoreButtonShape = RoundedCornerShape(12.dp)

    /**
     * Selected date-cell wash: a flat accent tint. Deliberately a single flat
     * color, not a gradient — selection state is communicated by tint plus
     * border weight only, so nothing on screen looks "lit up".
     */
    fun selectedCell(accent: Color): Brush = SolidColor(accent)
}
