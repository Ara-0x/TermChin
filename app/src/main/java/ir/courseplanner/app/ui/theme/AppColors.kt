package ir.courseplanner.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Renk paleti doğrudan referans React tasarımından (index.css) alınmıştır.
 * Her renk light ve dark çifti olarak tanımlanmıştır; component'ler
 * [isDarkTheme] üzerinden ilgili değeri seçer.
 */
object AppColors {
    // ------------------------------------------------------------------ Primary
    val PrimaryLight = Color(0xFF6558D9)
    val PrimaryDarkLight = Color(0xFF4A3DB6)
    val PrimarySoftLight = Color(0xFFEEECFF)
    val PrimaryBorderLight = Color(0xFFDEDAFF)

    val PrimaryDark = Color(0xFF9C8FFF)
    val PrimaryDarkDark = Color(0xFFC5BDFF)
    val PrimarySoftDark = Color(0xFF302C50)
    val PrimaryBorderDark = Color(0xFF4B456F)

    // ------------------------------------------------------------------ Chrome
    val CanvasLight = Color(0xFFF6F6FA)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceMutedLight = Color(0xFFF1F1F6)
    val TextLight = Color(0xFF1F1F29)
    val TextSoftLight = Color(0xFF777583)
    val LineLight = Color(0xFFE7E6ED)

    val CanvasDark = Color(0xFF111117)
    val SurfaceDark = Color(0xFF1B1B24)
    val SurfaceMutedDark = Color(0xFF23232E)
    val TextDark = Color(0xFFF2F1F7)
    val TextSoftDark = Color(0xFFAAA8B6)
    val LineDark = Color(0xFF30303C)

    // ------------------------------------------------------------------ Status
    val SuccessLight = Color(0xFF17845F)
    val SuccessSoftLight = Color(0xFFE6F6EF)
    val SuccessDark = Color(0xFF5CD2A5)
    val SuccessSoftDark = Color(0xFF183E33)

    val BlueLight = Color(0xFF3673D5)
    val BlueSoftLight = Color(0xFFE8F0FF)
    val BlueDark = Color(0xFF79A8FF)
    val BlueSoftDark = Color(0xFF1C3153)

    val AmberLight = Color(0xFFC27B16)
    val AmberSoftLight = Color(0xFFFFF3DD)
    val AmberDark = Color(0xFFF3B75A)
    val AmberSoftDark = Color(0xFF45351C)

    // Convenience: resolve by theme mode.
    fun primary(isDark: Boolean) = if (isDark) PrimaryDark else PrimaryLight
    fun primarySoft(isDark: Boolean) = if (isDark) PrimarySoftDark else PrimarySoftLight
    fun primaryBorder(isDark: Boolean) = if (isDark) PrimaryBorderDark else PrimaryBorderLight
    fun canvas(isDark: Boolean) = if (isDark) CanvasDark else CanvasLight
    fun surface(isDark: Boolean) = if (isDark) SurfaceDark else SurfaceLight
    fun surfaceMuted(isDark: Boolean) = if (isDark) SurfaceMutedDark else SurfaceMutedLight
    fun text(isDark: Boolean) = if (isDark) TextDark else TextLight
    fun textSoft(isDark: Boolean) = if (isDark) TextSoftDark else TextSoftLight
    fun line(isDark: Boolean) = if (isDark) LineDark else LineLight
    fun success(isDark: Boolean) = if (isDark) SuccessDark else SuccessLight
    fun successSoft(isDark: Boolean) = if (isDark) SuccessSoftDark else SuccessSoftLight
    fun blue(isDark: Boolean) = if (isDark) BlueDark else BlueLight
    fun blueSoft(isDark: Boolean) = if (isDark) BlueSoftDark else BlueSoftLight
    fun amber(isDark: Boolean) = if (isDark) AmberDark else AmberLight
    fun amberSoft(isDark: Boolean) = if (isDark) AmberSoftDark else AmberSoftLight
}
