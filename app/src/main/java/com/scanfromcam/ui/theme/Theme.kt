package com.scanfromcam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Design tokens. Single source of truth for color, radius and spacing.
 */
object Tokens {
    val Surface0 = Color(0xFF0A1120)
    val Surface1 = Color(0xFF0F1A2E)
    val Surface2 = Color(0xFF16233C)
    val Stroke = Color(0x14FFFFFF)
    val AccentStart = Color(0xFF5EEAD4)
    val AccentMid = Color(0xFF22D3EE)
    val AccentEnd = Color(0xFF3B82F6)
    val OnAccent = Color(0xFF04161F)
    val TextPrimary = Color(0xFFE9F1FC)
    val TextMuted = Color(0xFF7C93B0)
    val Danger = Color(0xFFFF6B6B)
    val Success = Color(0xFF34D399)

    const val RadiusCard = 20
    const val RadiusSheet = 28
    const val Gap = 4

    val Mono = FontFamily.Monospace
}

private val ScanColorScheme = darkColorScheme(
    primary = Tokens.AccentMid,
    onPrimary = Tokens.OnAccent,
    primaryContainer = Tokens.Surface2,
    onPrimaryContainer = Tokens.TextPrimary,
    secondary = Tokens.AccentEnd,
    onSecondary = Tokens.OnAccent,
    secondaryContainer = Tokens.Surface1,
    onSecondaryContainer = Tokens.TextPrimary,
    tertiary = Tokens.AccentStart,
    background = Tokens.Surface0,
    onBackground = Tokens.TextPrimary,
    surface = Tokens.Surface0,
    onSurface = Tokens.TextPrimary,
    surfaceVariant = Tokens.Surface1,
    onSurfaceVariant = Tokens.TextMuted,
    surfaceContainer = Tokens.Surface1,
    surfaceContainerHigh = Tokens.Surface2,
    outline = Color(0xFF22304A),
    outlineVariant = Tokens.Stroke,
    error = Tokens.Danger,
    onError = Color(0xFF2A0A0A),
)

private val ScanTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun ScanFromCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScanColorScheme,
        typography = ScanTypography,
        content = content,
    )
}

