package com.dbt.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dbt.tracker.R

/**
 * One variable font covers every weight, so the four static files it replaces are not carried
 * around. Inter is drawn for screens at small sizes and, more usefully here, ships true
 * tabular figures.
 */
private fun inter(weight: Int) = Font(
    R.font.inter,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private val Inter = FontFamily(
    inter(400), inter(500), inter(600), inter(700)
)

/**
 * Money is set with tabular figures so digits occupy identical widths.
 *
 * Without this, a column of amounts ripples as the digits change and the eye cannot compare
 * magnitudes down the column, which is most of what this app asks of it.
 */
private const val TABULAR = "tnum"

/**
 * Deep ink surfaces with a single accent, rather than the tinted greys a default Material theme
 * produces. The accent, #0D9488, is the same value the charts use and the only one that cleared
 * the lightness band, chroma floor and 3:1 contrast against both surfaces.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0D9488),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3EE),
    onPrimaryContainer = Color(0xFF06302C),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF10151F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10151F),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF5B6472),
    outline = Color(0xFFD6DAE2),
    outlineVariant = Color(0xFFE6E9EF),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF14B8A6),
    onPrimary = Color(0xFF04211E),
    primaryContainer = Color(0xFF0B3B36),
    onPrimaryContainer = Color(0xFFB7EFE8),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0A0E16),
    background = Color(0xFF0A0E16),
    onBackground = Color(0xFFE6E9EE),
    surface = Color(0xFF141A24),
    onSurface = Color(0xFFE6E9EE),
    surfaceVariant = Color(0xFF1E2632),
    onSurfaceVariant = Color(0xFF98A2B3),
    outline = Color(0xFF2C3644),
    outlineVariant = Color(0xFF222B38),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/**
 * Money in, money out, and attention.
 *
 * These are status colours, so they never carry meaning alone: amounts are always written with
 * a sign, and flags always carry a title. That matters because red and green are the classic
 * confusable pair, and no choice of shades fixes it for a red-green colour-blind reader.
 *
 * Light-mode amber was re-stepped to #8A6D0B after the validator showed the obvious #B45309
 * scoring only 8.5 against the error red for *normal* vision, let alone impaired.
 */
object MoneyColors {
    val positiveLight = Color(0xFF15803D)
    val positiveDark = Color(0xFF4ADE80)
    val negativeLight = Color(0xFFB3261E)
    val negativeDark = Color(0xFFF87171)
    val warnLight = Color(0xFF8A6D0B)
    val warnDark = Color(0xFFFBBF24)
}

@Composable
fun positiveColor(): Color =
    if (isSystemInDarkTheme()) MoneyColors.positiveDark else MoneyColors.positiveLight

@Composable
fun negativeColor(): Color =
    if (isSystemInDarkTheme()) MoneyColors.negativeDark else MoneyColors.negativeLight

@Composable
fun warnColor(): Color =
    if (isSystemInDarkTheme()) MoneyColors.warnDark else MoneyColors.warnLight

private fun money(size: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = spacing.sp,
    fontFeatureSettings = TABULAR
)

private val AppTypography = Typography(
    // Figures. Tight tracking, because large numerals set at default spacing look loose.
    displayLarge = money(52, FontWeight.SemiBold, -1.4),
    displayMedium = money(42, FontWeight.SemiBold, -1.1),
    displaySmall = money(34, FontWeight.SemiBold, -0.8),
    headlineMedium = money(26, FontWeight.SemiBold, -0.4),
    headlineSmall = money(22, FontWeight.SemiBold, -0.3),
    titleLarge = money(20, FontWeight.SemiBold, -0.2),
    titleMedium = money(16, FontWeight.SemiBold),

    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 17.sp
    ),

    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    // Panel headings: small, spaced and quiet, so they organise without competing.
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.6.sp
    )
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
