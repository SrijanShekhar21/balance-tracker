package com.dbt.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF475569),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF94A3B8),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111C33),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1B2942),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

/** Money should read as data, so figures are tabular-weighted and headings stay quiet. */
private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium)
    )
}

/** Semantic colours that are not part of the Material scheme. */
object Money2 {
    val positiveLight = Color(0xFF15803D)
    val positiveDark = Color(0xFF4ADE80)
    val negativeLight = Color(0xFFB91C1C)
    val negativeDark = Color(0xFFFCA5A5)
    val warnLight = Color(0xFFB45309)
    val warnDark = Color(0xFFFBBF24)
}

@Composable
fun positiveColor(): Color = if (isSystemInDarkTheme()) Money2.positiveDark else Money2.positiveLight

@Composable
fun negativeColor(): Color = if (isSystemInDarkTheme()) Money2.negativeDark else Money2.negativeLight

@Composable
fun warnColor(): Color = if (isSystemInDarkTheme()) Money2.warnDark else Money2.warnLight

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
