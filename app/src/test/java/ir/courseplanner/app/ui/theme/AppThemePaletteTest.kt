package ir.courseplanner.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ir.courseplanner.app.data.preferences.AppColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Guards the seven hand-tuned palettes in AppThemePalettes.kt.
 *
 * The previous implementation reused one shared secondary/container set for all
 * themes and never tuned the dark accents, so this test asserts that (a) every
 * theme ships a complete light + dark scheme, (b) the accent roles of a theme
 * differ from each other and from the other themes, (c) text stays readable
 * on each scheme's own surfaces in both modes, and (d) the page chrome stays
 * achromatic so the accents - not a single-hue wash - carry the theme.
 */
class AppThemePaletteTest {

    /** WCAG 2.1 relative-luminance contrast ratio of two colours. */
    private fun contrast(foreground: Color, background: Color): Double {
        val first = foreground.luminance()
        val second = background.luminance()
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertContrast(
        theme: AppColorTheme,
        mode: String,
        role: String,
        foreground: Color,
        background: Color,
        minimum: Double
    ) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$mode/${theme.id}: $role contrast is ${"%.2f".format(ratio)} (needs >= $minimum)",
            ratio >= minimum
        )
    }

    @Test
    fun `every theme ships a full light and dark scheme`() {
        for (theme in AppColorTheme.values()) {
            val palette = paletteOf(theme)
            assertEquals("${theme.id}: swatch stops", 3, palette.swatch.size)
            assertNotEquals("${theme.id}: light/dark primary", palette.light.primary, palette.dark.primary)
            assertNotEquals("${theme.id}: light/dark surface", palette.light.surface, palette.dark.surface)
            assertNotEquals(Boolean::class.java, palette.scheme(isDark = true)::class.java)
            assertEquals("${theme.id}: scheme(dark)", palette.dark.background, palette.scheme(isDark = true).background)
            assertEquals("${theme.id}: scheme(light)", palette.light.background, palette.scheme(isDark = false).background)
            assertEquals("${theme.id}: extension must match", palette, theme.palette)
        }
    }

    @Test
    fun `text roles keep AA contrast in both modes`() {
        for (theme in AppColorTheme.values()) {
            val palette = paletteOf(theme)
            for ((mode, scheme) in modesOf(palette)) {
                // Text on primary/secondary/tertiary fill.
                assertContrast(theme, mode, "onPrimary/primary", scheme.onPrimary, scheme.primary, 4.5)
                assertContrast(theme, mode, "onSecondary/secondary", scheme.onSecondary, scheme.secondary, 4.5)
                assertContrast(theme, mode, "onTertiary/tertiary", scheme.onTertiary, scheme.tertiary, 4.5)
                // Text inside the soft containers used by chips, badges and dialogs.
                assertContrast(
                    theme, mode, "onPrimaryContainer/primaryContainer",
                    scheme.onPrimaryContainer, scheme.primaryContainer, 4.5
                )
                assertContrast(
                    theme, mode, "onSecondaryContainer/secondaryContainer",
                    scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5
                )
                assertContrast(
                    theme, mode, "onTertiaryContainer/tertiaryContainer",
                    scheme.onTertiaryContainer, scheme.tertiaryContainer, 4.5
                )
                assertContrast(
                    theme, mode, "onSurfaceVariant/surfaceVariant",
                    scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5
                )
                // Body text on cards and on the canvas.
                assertContrast(theme, mode, "onSurface/surface", scheme.onSurface, scheme.surface, 7.0)
                assertContrast(theme, mode, "onSurface/background", scheme.onSurface, scheme.background, 7.0)
                // Accent text/icons drawn straight on a card (metric cards, links).
                assertContrast(theme, mode, "primary/surface", scheme.primary, scheme.surface, 3.0)
                assertContrast(theme, mode, "secondary/surface", scheme.secondary, scheme.surface, 3.0)
                assertContrast(theme, mode, "tertiary/surface", scheme.tertiary, scheme.surface, 3.0)
            }
        }
    }

    @Test
    fun `surface luminance still classifies the mode`() {
        // WeeklyTimetable and a few cards decide light/dark from `background`.
        for (theme in AppColorTheme.values()) {
            val palette = paletteOf(theme)
            assertTrue("${theme.id}: light background too dark", palette.light.background.luminance() > 0.7f)
            assertTrue("${theme.id}: dark background too light", palette.dark.background.luminance() < 0.2f)
            assertTrue("${theme.id}: light surface must be light", palette.light.surface.luminance() > 0.7f)
            assertTrue("${theme.id}: dark surface must be dark", palette.dark.surface.luminance() < 0.2f)
        }
    }

    @Test
    fun `accents are distinct within and across themes`() {
        val lightPrimaries = AppColorTheme.values().map { paletteOf(it).light.primary }
        val darkPrimaries = AppColorTheme.values().map { paletteOf(it).dark.primary }
        assertEquals("light primaries must be unique", lightPrimaries.size, lightPrimaries.toSet().size)
        assertEquals("dark primaries must be unique", darkPrimaries.size, darkPrimaries.toSet().size)

        for (theme in AppColorTheme.values()) {
            val palette = paletteOf(theme)
            for ((mode, scheme) in modesOf(palette)) {
                assertNotEquals("$mode/${theme.id}: secondary == primary", scheme.primary, scheme.secondary)
                assertNotEquals("$mode/${theme.id}: tertiary == primary", scheme.primary, scheme.tertiary)
                assertNotEquals("$mode/${theme.id}: tertiary == secondary", scheme.secondary, scheme.tertiary)
                assertNotEquals(
                    "$mode/${theme.id}: primary == primaryContainer",
                    scheme.primaryContainer, scheme.primary
                )
            }
        }
    }

    @Test
    fun `errors stay one shared red across themes`() {
        val reference = paletteOf(AppColorTheme.INDIGO)
        for (theme in AppColorTheme.values()) {
            val palette = paletteOf(theme)
            assertEquals("${theme.id}: light error", reference.light.error, palette.light.error)
            assertEquals("${theme.id}: dark error", reference.dark.error, palette.dark.error)
            assertEquals(
                "${theme.id}: light errorContainer",
                reference.light.errorContainer, palette.light.errorContainer
            )
        }
    }

    @Test
    fun `theme ids are unique and persistable`() {
        val ids = AppColorTheme.values().map { it.id }
        assertEquals("theme ids must be unique", ids.size, ids.toSet().size)
        for (theme in AppColorTheme.values()) {
            assertEquals(theme, AppColorTheme.fromId(theme.id))
            assertTrue("${theme.id}: titleFa", theme.titleFa.isNotBlank())
            assertEquals("${theme.id}: id is the persisted key", theme.id, theme.id.trim().lowercase())
        }
        assertEquals(AppColorTheme.INDIGO, AppColorTheme.fromId("unknown-theme"))
        assertEquals(AppColorTheme.INDIGO, AppColorTheme.fromId(null))
    }

    @Test
    fun `createCustomColorScheme returns the palette of the requested mode`() {
        for (theme in AppColorTheme.values()) {
            val palette = paletteOf(theme)
            assertEquals(palette.light, createCustomColorScheme(theme, isDark = false))
            assertEquals(palette.dark, createCustomColorScheme(theme, isDark = true))
            assertNotEquals(
                "${theme.id}: light and dark must not be the same scheme",
                createCustomColorScheme(theme, isDark = false).primary,
                createCustomColorScheme(theme, isDark = true).primary
            )
        }
    }

    /**
     * Hue in degrees (0..360) of a colour. Achromatic colours report 0.
     * Compose [Color] exposes plain sRGB channels, so this is enough to compare
     * how the three accents of a theme read to the eye.
     */
    private fun hue(color: Color): Double {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta < 1e-4f) return 0.0
        val h = when (max) {
            r -> 60.0 * (((g - b) / delta) % 6.0)
            g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }
        return (h + 360.0) % 360.0
    }

    /** Shortest angular distance between two hues, in degrees (0..180). */
    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return minOf(d, 360.0 - d)
    }

    /**
     * The three accent hues must be far enough apart to be told apart at a
     * glance. The Amber theme once shipped a burnt-orange secondary only ~9 deg
     * from its gold primary, so the Home metric cards and the settings swatch
     * showed what looked like a single colour; this guards the perceptual gap,
     * not just `secondary != primary`.
     */
    @Test
    fun `the three accent hues of a theme are well separated`() {
        for (theme in AppColorTheme.values()) {
            val palette = paletteOf(theme)
            for ((mode, scheme) in modesOf(palette)) {
                val hp = hue(scheme.primary)
                val dSecondary = hueDistance(hp, hue(scheme.secondary))
                val dTertiary = hueDistance(hp, hue(scheme.tertiary))
                assertTrue(
                    "$mode/${theme.id}: primary/secondary hues only " +
                        "${"%.1f".format(dSecondary)} deg apart",
                    dSecondary >= 20.0
                )
                assertTrue(
                    "$mode/${theme.id}: primary/tertiary hues only " +
                        "${"%.1f".format(dTertiary)} deg apart",
                    dTertiary >= 20.0
                )
            }
        }
    }

    /** Chroma of a colour: max-min RGB channel in 0..255 (0 = pure grey). */
    private fun chromaOf(color: Color): Int {
        val r = (color.red * 255).roundToInt()
        val g = (color.green * 255).roundToInt()
        val b = (color.blue * 255).roundToInt()
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    /**
     * A theme must read as "neutral canvas + coloured accents", not as a wash
     * of one hue. The Forest theme used to tint every chrome role - canvas,
     * cards, dividers, secondary text - green, so selecting it turned the whole
     * screen green. The chrome must stay achromatic while the three accents
     * stay clearly chromatic (each theme's identity lives in the accents).
     */
    @Test
    fun `page chrome stays neutral while accents stay chromatic`() {
        for (theme in AppColorTheme.values()) {
            val palette = paletteOf(theme)
            for ((mode, scheme) in modesOf(palette)) {
                val chrome = listOf(
                    "background" to scheme.background,
                    "surface" to scheme.surface,
                    "surfaceVariant" to scheme.surfaceVariant,
                    "onSurface" to scheme.onSurface,
                    "onSurfaceVariant" to scheme.onSurfaceVariant,
                    "outline" to scheme.outline,
                    "outlineVariant" to scheme.outlineVariant
                )
                for ((role, color) in chrome) {
                    val chroma = chromaOf(color)
                    assertTrue(
                        "$mode/${theme.id}: chrome role $role is tinted (chroma $chroma > 6)",
                        chroma <= 6
                    )
                }
                val accents = listOf(
                    "primary" to scheme.primary,
                    "secondary" to scheme.secondary,
                    "tertiary" to scheme.tertiary
                )
                for ((role, color) in accents) {
                    val chroma = chromaOf(color)
                    assertTrue(
                        "$mode/${theme.id}: accent $role lost its colour (chroma $chroma < 30)",
                        chroma >= 30
                    )
                }
            }
        }
    }

    private fun modesOf(palette: ThemePalette): List<Pair<String, ColorScheme>> =
        listOf("light" to palette.light, "dark" to palette.dark)
}