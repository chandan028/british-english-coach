package com.britishcoach.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Restrained palette: deep teal, warm cream and a soft amber accent.
private val Teal = Color(0xFF1F5C5A)
private val TealLight = Color(0xFFCFE5E2)
private val Amber = Color(0xFFB5773A)
private val AmberLight = Color(0xFFF6E3CF)
private val Cream = Color(0xFFFAF7F2)
private val Ink = Color(0xFF1E2524)
private val Muted = Color(0xFF5B6664)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealLight,
    onPrimaryContainer = Color(0xFF0B2E2D),
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = AmberLight,
    onSecondaryContainer = Color(0xFF3D2410),
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEBE4),
    onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFFF4F0EA),
    outline = Color(0xFFD7D1C8),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CCBC6),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF184845),
    onPrimaryContainer = TealLight,
    secondary = Color(0xFFE6B583),
    onSecondary = Color(0xFF452A10),
    secondaryContainer = Color(0xFF5E4024),
    onSecondaryContainer = AmberLight,
    background = Color(0xFF141918),
    onBackground = Color(0xFFE4E9E7),
    surface = Color(0xFF1B2120),
    onSurface = Color(0xFFE4E9E7),
    surfaceVariant = Color(0xFF2A3230),
    onSurfaceVariant = Color(0xFFB9C4C1),
    surfaceContainer = Color(0xFF202826),
    outline = Color(0xFF3E4846),
)

private val Base = Typography()
private val AppTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 22.sp),
)

/** Large style for the sentence the learner is practising. */
val TargetTextStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)

@Composable
fun CoachTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
