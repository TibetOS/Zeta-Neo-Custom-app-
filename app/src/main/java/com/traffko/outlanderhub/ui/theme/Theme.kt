package com.traffko.outlanderhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Automotive night-friendly palette: near-black surfaces, red accent echoing
// the Outlander instrument cluster.
private val OutlanderColors = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color.White,
    secondary = Color(0xFF90CAF9),
    onSecondary = Color.Black,
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF14181D),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF1D232A),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF37474F),
    error = Color(0xFFFF7043),
)

val GaugeGood = Color(0xFF66BB6A)
val GaugeWarn = Color(0xFFFFCA28)
val GaugeDanger = Color(0xFFE53935)

@Composable
fun OutlanderHubTheme(content: @Composable () -> Unit) {
    // Always dark: this runs on an in-dash screen.
    isSystemInDarkTheme() // (ignored on purpose)
    MaterialTheme(
        colorScheme = OutlanderColors,
        content = content,
    )
}
