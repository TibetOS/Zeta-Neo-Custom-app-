package com.traffko.outlanderhub.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.ui.theme.GaugeDanger
import com.traffko.outlanderhub.ui.theme.GaugeGood
import com.traffko.outlanderhub.ui.theme.GaugeWarn

@Composable
fun ControlsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Vehicle status", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

        // Doors + body
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val d = vehicle.doors
            StatusCard("Front left door", if (d.frontLeft) "OPEN" else "Closed", d.frontLeft, Modifier.weight(1f))
            StatusCard("Front right door", if (d.frontRight) "OPEN" else "Closed", d.frontRight, Modifier.weight(1f))
            StatusCard("Rear left door", if (d.rearLeft) "OPEN" else "Closed", d.rearLeft, Modifier.weight(1f))
            StatusCard("Rear right door", if (d.rearRight) "OPEN" else "Closed", d.rearRight, Modifier.weight(1f))
            StatusCard("Trunk", if (d.trunk) "OPEN" else "Closed", d.trunk, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusCard(
                "Handbrake",
                when (vehicle.handbrake) { true -> "ON"; false -> "Off"; null -> "--" },
                alert = false,
                modifier = Modifier.weight(1f),
            )
            StatusCard(
                "Driver seatbelt",
                when (vehicle.seatbeltDriver) { true -> "Fastened"; false -> "UNFASTENED"; null -> "--" },
                alert = vehicle.seatbeltDriver == false,
                modifier = Modifier.weight(1f),
            )
            val c = vehicle.climate
            StatusCard(
                "Climate",
                if (c.acOn == null) "--"
                else buildString {
                    append(if (c.acOn) "A/C on" else "A/C off")
                    c.tempLeftC?.let { append("  %.1f°".format(it)) }
                    c.fanSpeed?.let { append("  fan $it") }
                },
                alert = false,
                modifier = Modifier.weight(2f),
            )
        }

        Text("Tire pressure", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val labels = listOf("Front left", "Front right", "Rear left", "Rear right")
            labels.forEachIndexed { i, label ->
                val kpa = vehicle.tirePressuresKpa.getOrNull(i)
                TireCard(label, kpa, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Signals come from the head unit's CAN decoder. Values showing \"--\" have not " +
                "been broadcast by the decoder yet (or are not mapped — see the CAN screen).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun StatusCard(title: String, value: String, alert: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (alert) GaugeDanger.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (alert) GaugeDanger else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TireCard(label: String, kpa: Float?, modifier: Modifier = Modifier) {
    val psi = kpa?.let { it * 0.145038f }
    val tone: Color? = when {
        kpa == null -> null
        kpa < 190f -> GaugeDanger
        kpa < 210f -> GaugeWarn
        else -> GaugeGood
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                psi?.let { "%.1f psi".format(it) } ?: "--",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = tone ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                kpa?.let { "%.0f kPa".format(it) } ?: "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}
