package com.traffko.outlanderhub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.ui.components.pressable
import com.traffko.outlanderhub.ui.controls.ControlsScreen
import com.traffko.outlanderhub.ui.dashboard.DashboardScreen
import com.traffko.outlanderhub.ui.diagnostics.DiagnosticsScreen
import com.traffko.outlanderhub.ui.launcher.LauncherScreen
import com.traffko.outlanderhub.ui.settings.SettingsScreen
import com.traffko.outlanderhub.ui.theme.Hue

enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Apps),
    Dashboard("Dash", Icons.Filled.Speed),
    Controls("Car", Icons.Filled.DirectionsCar),
    Diagnostics("CAN", Icons.Filled.Terminal),
    Settings("Setup", Icons.Filled.Settings),
}

@Composable
fun AppRoot(viewModel: MainViewModel) {
    var current by remember { mutableStateOf(Screen.Home) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Hue.Black)
    ) {
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    fadeIn(tween(260)) togetherWith fadeOut(tween(180))
                },
                label = "screen",
                modifier = Modifier.weight(1f),
            ) { screen ->
                val content = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 28.dp, top = 22.dp)
                when (screen) {
                    Screen.Home -> LauncherScreen(viewModel, content)
                    Screen.Dashboard -> DashboardScreen(viewModel, content)
                    Screen.Controls -> ControlsScreen(viewModel, content)
                    Screen.Diagnostics -> DiagnosticsScreen(viewModel, content)
                    Screen.Settings -> SettingsScreen(viewModel, content)
                }
            }

            Dock(
                current = current,
                showDiagnostics = settings.showDiagnostics,
                onSelect = { current = it },
            )
        }
    }
}

/**
 * Tesla-style bottom dock: icon row on a black strip separated by a hairline,
 * with the active item lifted, brightened and underlined by a glowing pill.
 */
@Composable
private fun Dock(
    current: Screen,
    showDiagnostics: Boolean,
    onSelect: (Screen) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hue.Hairline)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(Color(0xFF07080A)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Screen.entries
                .filter { it != Screen.Diagnostics || showDiagnostics }
                .forEach { screen ->
                    DockItem(
                        screen = screen,
                        selected = screen == current,
                        onClick = { onSelect(screen) },
                    )
                }
        }
    }
}

@Composable
private fun DockItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(stiffness = 400f),
        label = "dockItem",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(108.dp)
            .pressable(onClick),
    ) {
        Icon(
            screen.icon,
            contentDescription = screen.label,
            tint = lerp(Hue.TextTertiary, Hue.TextPrimary, emphasis),
            modifier = Modifier
                .size(30.dp)
                .scale(1f + 0.12f * emphasis),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            screen.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp,
            color = lerp(Hue.TextTertiary, Hue.TextSecondary, emphasis),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(34.dp)
                .height(3.dp)
                .alpha(emphasis)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Hue.Blue.copy(alpha = 0.2f), Hue.BlueBright, Hue.Blue.copy(alpha = 0.2f))
                    )
                )
        )
    }
}

