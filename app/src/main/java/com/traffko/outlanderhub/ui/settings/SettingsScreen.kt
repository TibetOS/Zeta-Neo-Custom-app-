package com.traffko.outlanderhub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.vehicle.VehicleSource

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("Vehicle data source", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.padding(4.dp))
                SourceOption(
                    title = "Demo (simulated drive)",
                    subtitle = "For testing the UI without the car.",
                    selected = settings.source == VehicleSource.DEMO,
                ) { viewModel.setSource(VehicleSource.DEMO) }
                SourceOption(
                    title = "Zeta CAN decoder (FYT)",
                    subtitle = "Live data from the head unit's com.syu.ms service. " +
                        "Experimental — verify signals in the CAN screen.",
                    selected = settings.source == VehicleSource.FYT_CAN,
                ) { viewModel.setSource(VehicleSource.FYT_CAN) }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show CAN diagnostics tab", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        "Raw bus monitor used to map decoder signals.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = settings.showDiagnostics,
                    onCheckedChange = { viewModel.setShowDiagnostics(it) },
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Text("About", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    "Outlander Hub — custom launcher/dashboard for Mitsubishi Outlander 2019 " +
                        "on the Zeta Neo 14 head unit.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun SourceOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}
