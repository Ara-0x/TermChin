package ir.courseplanner.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import ir.courseplanner.app.data.preferences.AppColorTheme

/**
 * The complete Material 3 colour roles of one app theme, in both light and dark
 * variants, plus the accent stops the settings picker draws on its swatch.
 *
 * Every [AppColorTheme] owns a full light/dark pair. The previous version only
 * stored a handful of colours on the enum and shared one set of secondary /
 * container roles between all seven themes, so switching palette re-tinted the
 * buttons while chips, badges, dialogs and the tertiary metric cards kept the
 * same indigo/teal values - and each accent's dark variant was never tuned for
 * the navy surface. All roles are raw 0xAARRGGBB [Long] literals so the table
 * stays diff-friendly and is assertable from plain JVM tests.
 *
 * Colour discipline: the page chrome (background, surface, surfaceVariant,
 * onSurface / onSurfaceVariant text, outline, outlineVariant) is always an
 * achromatic grey - at the same relative luminance as the tinted value it
 * replaced - so the accents, not a single-hue wash, carry the identity of the
 * theme. The Forest theme used to tint all seven chrome roles green and read
 * as "a green app"; neutral chrome keeps every contrast ratio and the
 * light/dark classification intact while the screen no longer drowns in one
 * hue. AppThemePaletteTest guards this with a chroma assertion.
 */
data class ThemePalette(
    val light: ColorScheme,
    val dark: ColorScheme,
    val swatch: List<Color>
) {
    /** The scheme [MyApplicationTheme] feeds to MaterialTheme. */
    fun scheme(isDark: Boolean): ColorScheme = if (isDark) dark else light
}

/** Every Material 3 role of a single mode, as a raw 0xAARRGGBB value. */
private class SchemeTokens(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val tertiary: Long,
    val onTertiary: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,
    val background: Long,
    val onSurface: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val outline: Long,
    val outlineVariant: Long
)

// Shared status roles: conflicts/errors keep one red across every theme so red
// always means "problem", never "this palette's hue".
private const val ERROR_LIGHT = 0xFFDC2626L
private const val ON_ERROR_LIGHT = 0xFFFFFFFFL
private const val ERROR_CONTAINER_LIGHT = 0xFFFEE2E2L
private const val ON_ERROR_CONTAINER_LIGHT = 0xFF7F1D1DL
private const val ERROR_DARK = 0xFFF87171L
private const val ON_ERROR_DARK = 0xFF450A0AL
private const val ERROR_CONTAINER_DARK = 0xFF7F1D1DL
private const val ON_ERROR_CONTAINER_DARK = 0xFFFECACAL

private fun SchemeTokens.toColorScheme(dark: Boolean, inversePrimary: Long): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            inversePrimary = Color(inversePrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(background),
            onBackground = Color(onSurface),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            inverseSurface = Color(onSurface),
            inverseOnSurface = Color(onPrimary),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            error = Color(ERROR_DARK),
            onError = Color(ON_ERROR_DARK),
            errorContainer = Color(ERROR_CONTAINER_DARK),
            onErrorContainer = Color(ON_ERROR_CONTAINER_DARK)
        )
    } else {
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            inversePrimary = Color(inversePrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(background),
            onBackground = Color(onSurface),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            inverseSurface = Color(onSurface),
            inverseOnSurface = Color(onPrimary),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            error = Color(ERROR_LIGHT),
            onError = Color(ON_ERROR_LIGHT),
            errorContainer = Color(ERROR_CONTAINER_LIGHT),
            onErrorContainer = Color(ON_ERROR_CONTAINER_LIGHT)
        )
    }

/** Inverse roles cross over: a theme's snackbar accents use the *other* mode's primary. */
private fun buildPalette(light: SchemeTokens, dark: SchemeTokens): ThemePalette = ThemePalette(
    light = light.toColorScheme(dark = false, inversePrimary = dark.primary),
    dark = dark.toColorScheme(dark = true, inversePrimary = light.primary),
    swatch = listOf(Color(light.primary), Color(light.secondary), Color(light.tertiary))
)
private val IndigoLight = SchemeTokens(
    primary = 0xFF2563EBL, onPrimary = 0xFFFFFFFFL,
    primaryContainer = 0xFFDBEAFEL, onPrimaryContainer = 0xFF1E3A8AL,
    secondary = 0xFF0F766EL, onSecondary = 0xFFFFFFFFL,
    secondaryContainer = 0xFFC5F3E9L, onSecondaryContainer = 0xFF115E59L,
    tertiary = 0xFF7C3AEDL, onTertiary = 0xFFFFFFFFL,
    tertiaryContainer = 0xFFEAE7FBL, onTertiaryContainer = 0xFF4C1D95L,
    background = 0xFFF3F3F3L, onSurface = 0xFF171717L,
    surface = 0xFFFFFFFFL, surfaceVariant = 0xFFEEEEEEL,
    onSurfaceVariant = 0xFF545454L, outline = 0xFF888888L,
    outlineVariant = 0xFFE2E2E2L
)

private val IndigoDark = SchemeTokens(
    primary = 0xFF8AB4F8L, onPrimary = 0xFF0A1B3CL,
    primaryContainer = 0xFF1E3A8AL, onPrimaryContainer = 0xFFCFE1FFL,
    secondary = 0xFF5EEAD4L, onSecondary = 0xFF04332EL,
    secondaryContainer = 0xFF115E59L, onSecondaryContainer = 0xFFB8F2E6L,
    tertiary = 0xFFB39DFFL, onTertiary = 0xFF241366L,
    tertiaryContainer = 0xFF4C1D95L, onTertiaryContainer = 0xFFE2DAFFL,
    background = 0xFF0F0F0FL, onSurface = 0xFFEDEDEDL,
    surface = 0xFF1A1A1AL, surfaceVariant = 0xFF292929L,
    onSurfaceVariant = 0xFFAFAFAFL, outline = 0xFF737373L,
    outlineVariant = 0xFF333333L
)

private val EmeraldLight = SchemeTokens(
    primary = 0xFF047857L, onPrimary = 0xFFFFFFFFL,
    primaryContainer = 0xFFCBF3DEL, onPrimaryContainer = 0xFF064E3BL,
    secondary = 0xFF0369A1L, onSecondary = 0xFFFFFFFFL,
    secondaryContainer = 0xFFDAECF7L, onSecondaryContainer = 0xFF0C4A6EL,
    tertiary = 0xFFB45309L, onTertiary = 0xFFFFFFFFL,
    tertiaryContainer = 0xFFF4EABFL, onTertiaryContainer = 0xFF78350FL,
    background = 0xFFF3F3F3L, onSurface = 0xFF1B1B1BL,
    surface = 0xFFFFFFFFL, surfaceVariant = 0xFFEEEEEEL,
    onSurfaceVariant = 0xFF545454L, outline = 0xFF7F7F7FL,
    outlineVariant = 0xFFE2E2E2L
)

private val EmeraldDark = SchemeTokens(
    primary = 0xFF34D399L, onPrimary = 0xFF03291DL,
    primaryContainer = 0xFF065F46L, onPrimaryContainer = 0xFFA7F3D0L,
    secondary = 0xFF38BDF8L, onSecondary = 0xFF05283DL,
    secondaryContainer = 0xFF075985L, onSecondaryContainer = 0xFFBAE6FDL,
    tertiary = 0xFFFBBF24L, onTertiary = 0xFF3A2404L,
    tertiaryContainer = 0xFF92400EL, onTertiaryContainer = 0xFFFDE68AL,
    background = 0xFF101010L, onSurface = 0xFFEEEEEEL,
    surface = 0xFF1A1A1AL, surfaceVariant = 0xFF252525L,
    onSurfaceVariant = 0xFFA8A8A8L, outline = 0xFF848484L,
    outlineVariant = 0xFF2F2F2FL
)
private val VioletLight = SchemeTokens(
    primary = 0xFF7C3AEDL, onPrimary = 0xFFFFFFFFL,
    primaryContainer = 0xFFEAE7FBL, onPrimaryContainer = 0xFF4C1D95L,
    secondary = 0xFFBE185DL, onSecondary = 0xFFFFFFFFL,
    secondaryContainer = 0xFFF8E4EFL, onSecondaryContainer = 0xFF831843L,
    tertiary = 0xFF0F766EL, onTertiary = 0xFFFFFFFFL,
    tertiaryContainer = 0xFFC5F3E9L, onTertiaryContainer = 0xFF134E4AL,
    background = 0xFFF3F3F3L, onSurface = 0xFF161616L,
    surface = 0xFFFFFFFFL, surfaceVariant = 0xFFECECECL,
    onSurfaceVariant = 0xFF515151L, outline = 0xFF757575L,
    outlineVariant = 0xFFE0E0E0L
)

private val VioletDark = SchemeTokens(
    primary = 0xFFB79CFFL, onPrimary = 0xFF22105CL,
    primaryContainer = 0xFF5B21B6L, onPrimaryContainer = 0xFFE6DBFFL,
    secondary = 0xFFF586B0L, onSecondary = 0xFF4A0A2BL,
    secondaryContainer = 0xFF9D174DL, onSecondaryContainer = 0xFFFBCFE8L,
    tertiary = 0xFF4FD8C4L, onTertiary = 0xFF04332EL,
    tertiaryContainer = 0xFF115E59L, onTertiaryContainer = 0xFFB6F2E8L,
    background = 0xFF0C0C0CL, onSurface = 0xFFEAEAEAL,
    surface = 0xFF171717L, surfaceVariant = 0xFF212121L,
    onSurfaceVariant = 0xFFA4A4A4L, outline = 0xFF7F7F7FL,
    outlineVariant = 0xFF2D2D2DL
)

// Amber keeps gold as its primary but pairs it with a plum secondary. The old
// secondary (burnt orange) sat only ~9 deg from the primary hue, so the metric
// cards and the palette swatch drew "one colour twice"; the deeper plum makes
// the gold/plum/teal triad read as three accents. The primaryContainer is
// amber-200 rather than amber-100 because the amber background is itself a pale
// cream - at amber-100 the selected chips and EVEN_WEEKS badge vanished into it.
private val AmberLight = SchemeTokens(
    primary = 0xFFB45309L, onPrimary = 0xFFFFFFFFL,
    primaryContainer = 0xFFFDE68AL, onPrimaryContainer = 0xFF78350FL,
    secondary = 0xFF7E22CEL, onSecondary = 0xFFFFFFFFL,
    secondaryContainer = 0xFFF0E5FBL, onSecondaryContainer = 0xFF581C87L,
    tertiary = 0xFF0F766EL, onTertiary = 0xFFFFFFFFL,
    tertiaryContainer = 0xFFC5F3E9L, onTertiaryContainer = 0xFF134E4AL,
    background = 0xFFF3F3F3L, onSurface = 0xFF1C1C1CL,
    surface = 0xFFFFFFFFL, surfaceVariant = 0xFFECECECL,
    onSurfaceVariant = 0xFF5D5D5DL, outline = 0xFF7F7F7FL,
    outlineVariant = 0xFFDDDDDDL
)

private val AmberDark = SchemeTokens(
    primary = 0xFFFBBF24L, onPrimary = 0xFF3A2404L,
    primaryContainer = 0xFF92400EL, onPrimaryContainer = 0xFFFDE68AL,
    secondary = 0xFFC084FCL, onSecondary = 0xFF2E1065L,
    secondaryContainer = 0xFF6B21A8L, onSecondaryContainer = 0xFFF3E8FFL,
    tertiary = 0xFF2DD4BFL, onTertiary = 0xFF04332EL,
    tertiaryContainer = 0xFF115E59L, onTertiaryContainer = 0xFFCCFBF1L,
    background = 0xFF101010L, onSurface = 0xFFECECECL,
    surface = 0xFF1B1B1BL, surfaceVariant = 0xFF242424L,
    onSurfaceVariant = 0xFFACACACL, outline = 0xFF7F7F7FL,
    outlineVariant = 0xFF303030L
)
// Ocean keeps turquoise as its primary, but the old secondary (royal blue) sat
// only ~28 deg from it, so the theme read as a bluer "indigo" and the metric
// cards showed two near-identical blues. Secondary is now indigo and tertiary
// a fuchsia dusk: turquoise -> indigo -> fuchsia with 46-52 deg between pairs.
private val OceanLight = SchemeTokens(
    primary = 0xFF0E7490L, onPrimary = 0xFFFFFFFFL,
    primaryContainer = 0xFFC7F1F4L, onPrimaryContainer = 0xFF164E63L,
    secondary = 0xFF4338CAL, onSecondary = 0xFFFFFFFFL,
    secondaryContainer = 0xFFE0E7FFL, onSecondaryContainer = 0xFF312E81L,
    tertiary = 0xFFA21CAFL, onTertiary = 0xFFFFFFFFL,
    tertiaryContainer = 0xFFF5E4FAL, onTertiaryContainer = 0xFF701A75L,
    background = 0xFFF3F3F3L, onSurface = 0xFF1B1B1BL,
    surface = 0xFFFFFFFFL, surfaceVariant = 0xFFEDEDEDL,
    onSurfaceVariant = 0xFF565656L, outline = 0xFF777777L,
    outlineVariant = 0xFFE3E3E3L
)

private val OceanDark = SchemeTokens(
    primary = 0xFF22D3EEL, onPrimary = 0xFF042A33L,
    primaryContainer = 0xFF155E75L, onPrimaryContainer = 0xFFA5F3FCL,
    secondary = 0xFF818CF8L, onSecondary = 0xFF1E1B4BL,
    secondaryContainer = 0xFF3730A3L, onSecondaryContainer = 0xFFE0E7FFL,
    tertiary = 0xFFE879F9L, onTertiary = 0xFF4A044EL,
    tertiaryContainer = 0xFF86198FL, onTertiaryContainer = 0xFFFAE8FFL,
    background = 0xFF111111L, onSurface = 0xFFEFEFEFL,
    surface = 0xFF1B1B1BL, surfaceVariant = 0xFF292929L,
    onSurfaceVariant = 0xFFAAAAAAL, outline = 0xFF858585L,
    outlineVariant = 0xFF363636L
)

private val RoseLight = SchemeTokens(
    primary = 0xFFBE123CL, onPrimary = 0xFFFFFFFFL,
    primaryContainer = 0xFFFEE3E5L, onPrimaryContainer = 0xFF881337L,
    secondary = 0xFF7C3AEDL, onSecondary = 0xFFFFFFFFL,
    secondaryContainer = 0xFFEAE7FBL, onSecondaryContainer = 0xFF4C1D95L,
    tertiary = 0xFF0F766EL, onTertiary = 0xFFFFFFFFL,
    tertiaryContainer = 0xFFC5F3E9L, onTertiaryContainer = 0xFF134E4AL,
    background = 0xFFF3F3F3L, onSurface = 0xFF161616L,
    surface = 0xFFFFFFFFL, surfaceVariant = 0xFFEBEBEBL,
    onSurfaceVariant = 0xFF535353L, outline = 0xFF707070L,
    outlineVariant = 0xFFDEDEDEL
)

private val RoseDark = SchemeTokens(
    primary = 0xFFFB7185L, onPrimary = 0xFF400618L,
    primaryContainer = 0xFF9F1239L, onPrimaryContainer = 0xFFFFD6DDL,
    secondary = 0xFFC4A6FFL, onSecondary = 0xFF241366L,
    secondaryContainer = 0xFF5B21B6L, onSecondaryContainer = 0xFFE6DBFFL,
    tertiary = 0xFF4FD8C4L, onTertiary = 0xFF04332EL,
    tertiaryContainer = 0xFF115E59L, onTertiaryContainer = 0xFFB6F2E8L,
    background = 0xFF0D0D0DL, onSurface = 0xFFEBEBEBL,
    surface = 0xFF191919L, surfaceVariant = 0xFF1F1F1FL,
    onSurfaceVariant = 0xFFA5A5A5L, outline = 0xFF7C7C7CL,
    outlineVariant = 0xFF2C2C2CL
)

private val SlateLight = SchemeTokens(
    primary = 0xFF334155L, onPrimary = 0xFFFFFFFFL,
    primaryContainer = 0xFFE2E8F0L, onPrimaryContainer = 0xFF1E293BL,
    secondary = 0xFF0F766EL, onSecondary = 0xFFFFFFFFL,
    secondaryContainer = 0xFFC5F3E9L, onSecondaryContainer = 0xFF134E4AL,
    tertiary = 0xFFB45309L, onTertiary = 0xFFFFFFFFL,
    tertiaryContainer = 0xFFF4EABFL, onTertiaryContainer = 0xFF78350FL,
    background = 0xFFF3F3F3L, onSurface = 0xFF191919L,
    surface = 0xFFFFFFFFL, surfaceVariant = 0xFFEFEFEFL,
    onSurfaceVariant = 0xFF545454L, outline = 0xFF787878L,
    outlineVariant = 0xFFE2E2E2L
)

private val SlateDark = SchemeTokens(
    primary = 0xFF94A3B8L, onPrimary = 0xFF0B1220L,
    primaryContainer = 0xFF334155L, onPrimaryContainer = 0xFFE2E8F0L,
    secondary = 0xFF5EEAD4L, onSecondary = 0xFF04332EL,
    secondaryContainer = 0xFF115E59L, onSecondaryContainer = 0xFFB8F2E6L,
    tertiary = 0xFFFBBF24L, onTertiary = 0xFF3A2404L,
    tertiaryContainer = 0xFF92400EL, onTertiaryContainer = 0xFFFDE68AL,
    background = 0xFF0F0F0FL, onSurface = 0xFFF4F4F4L,
    surface = 0xFF1B1B1BL, surfaceVariant = 0xFF292929L,
    onSurfaceVariant = 0xFFA9A9A9L, outline = 0xFF858585L,
    outlineVariant = 0xFF323232L
)

private val indigoPalette = buildPalette(IndigoLight, IndigoDark)
private val emeraldPalette = buildPalette(EmeraldLight, EmeraldDark)
private val violetPalette = buildPalette(VioletLight, VioletDark)
private val amberPalette = buildPalette(AmberLight, AmberDark)
private val oceanPalette = buildPalette(OceanLight, OceanDark)
private val rosePalette = buildPalette(RoseLight, RoseDark)
private val slatePalette = buildPalette(SlateLight, SlateDark)

/**
 * Resolves the selected theme to its full palette. The `when` is exhaustive on
 * purpose: adding a new [AppColorTheme] without colours fails to compile.
 */
fun paletteOf(theme: AppColorTheme): ThemePalette = when (theme) {
    AppColorTheme.INDIGO -> indigoPalette
    AppColorTheme.EMERALD -> emeraldPalette
    AppColorTheme.VIOLET -> violetPalette
    AppColorTheme.AMBER -> amberPalette
    AppColorTheme.OCEAN -> oceanPalette
    AppColorTheme.ROSE -> rosePalette
    AppColorTheme.SLATE -> slatePalette
}

/** Convenience for call sites: `theme.palette.light.primary`. */
val AppColorTheme.palette: ThemePalette get() = paletteOf(this)
