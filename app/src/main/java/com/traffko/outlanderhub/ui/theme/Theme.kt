package com.traffko.outlanderhub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tesla-inspired design language for an in-dash screen:
 * true-black canvas, near-invisible panel surfaces separated by hairlines,
 * ultra-light oversized numerals, and one restrained blue accent.
 * Red is reserved exclusively for alerts.
 */
object Hue {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF101318)          // resting panel surface
    val PanelHigh = Color(0xFF171B21)      // hovered / emphasized surface
    val Hairline = Color(0x16FFFFFF)       // 8% white stroke
    val HairlineBright = Color(0x2EFFFFFF)

    val TextPrimary = Color(0xFFF4F6F8)
    val TextSecondary = Color(0xFF98A0AB)
    val TextTertiary = Color(0xFF5F6771)

    val Blue = Color(0xFF3E6AE1)           // Tesla action blue
    val BlueBright = Color(0xFF5B85F2)
    val Red = Color(0xFFE82127)            // alerts only
    val Green = Color(0xFF34C77B)
    val Amber = Color(0xFFF4B63F)
}

// Legacy names still used across screens.
val GaugeGood = Hue.Green
val GaugeWarn = Hue.Amber
val GaugeDanger = Hue.Red

private val OutlanderColors = darkColorScheme(
    primary = Hue.Blue,
    onPrimary = Color.White,
    secondary = Hue.BlueBright,
    onSecondary = Color.Black,
    background = Hue.Black,
    onBackground = Hue.TextPrimary,
    surface = Hue.Panel,
    onSurface = Hue.TextPrimary,
    surfaceVariant = Hue.PanelHigh,
    onSurfaceVariant = Hue.TextSecondary,
    outline = Hue.Hairline,
    error = Hue.Red,
)

/** Oversized readout numerals — the "speed" style. */
val DisplayXL = TextStyle(
    fontSize = 132.sp,
    fontWeight = FontWeight.ExtraLight,
    letterSpacing = (-4).sp,
    color = Hue.TextPrimary,
)

val DisplayL = TextStyle(
    fontSize = 64.sp,
    fontWeight = FontWeight.Light,
    letterSpacing = (-1).sp,
    color = Hue.TextPrimary,
)

val DisplayM = TextStyle(
    fontSize = 34.sp,
    fontWeight = FontWeight.Light,
    color = Hue.TextPrimary,
)

/** Uppercase micro-label, generous tracking — used for all captions. */
val Micro = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 2.2.sp,
    color = Hue.TextSecondary,
)

private val OutlanderTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Hue.TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Hue.TextSecondary),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Light, color = Hue.TextPrimary),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Hue.TextPrimary),
    labelSmall = Micro,
)

private val OutlanderShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun OutlanderHubTheme(content: @Composable () -> Unit) {
    // Always dark: this runs on an in-dash screen.
    MaterialTheme(
        colorScheme = OutlanderColors,
        typography = OutlanderTypography,
        shapes = OutlanderShapes,
        content = content,
    )
}
