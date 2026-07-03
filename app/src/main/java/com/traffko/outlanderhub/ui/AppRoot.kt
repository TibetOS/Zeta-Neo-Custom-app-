package com.traffko.outlanderhub.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.ui.controls.ControlsScreen
import com.traffko.outlanderhub.ui.dashboard.DashboardScreen
import com.traffko.outlanderhub.ui.diagnostics.DiagnosticsScreen
import com.traffko.outlanderhub.ui.launcher.LauncherScreen
import com.traffko.outlanderhub.ui.settings.SettingsScreen

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
    val settings by viewModel.settings.collectAsState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Screen.entries
                    .filter { it != Screen.Diagnostics || settings.showDiagnostics }
                    .forEach { screen ->
                        NavigationRailItem(
                            selected = current == screen,
                            onClick = { current = screen },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                        )
                    }
            }
            val content = Modifier
                .fillMaxSize()
                .padding(16.dp)
            when (current) {
                Screen.Home -> LauncherScreen(viewModel, content)
                Screen.Dashboard -> DashboardScreen(viewModel, content)
                Screen.Controls -> ControlsScreen(viewModel, content)
                Screen.Diagnostics -> DiagnosticsScreen(viewModel, content)
                Screen.Settings -> SettingsScreen(viewModel, content)
            }
        }
    }
}
