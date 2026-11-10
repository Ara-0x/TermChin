package ir.courseplanner.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * An opaque colour that reads like [container] at [alpha] over the page
 * surface — without actually being translucent.
 *
 * Translucent brand containers are tempting, but they composite against
 * whatever happens to sit behind them (a dark window background, a sheet, a
 * dialog scrim), so the same header can flip from calm lavender to murky grey
 * and lose its contrast. Blending once against the known page surface keeps
 * the intended tint on every host.
 */
fun ColorScheme.tinted(container: Color, alpha: Float): Color =
    lerp(surface, container, alpha.coerceIn(0f, 1f))
