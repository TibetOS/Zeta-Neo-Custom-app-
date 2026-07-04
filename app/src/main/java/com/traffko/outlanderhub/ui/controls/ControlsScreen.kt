package com.traffko.outlanderhub.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.ui.components.CarTopView
import com.traffko.outlanderhub.ui.components.MicroLabel
import com.traffko.outlanderhub.ui.components.glassPanel
import com.traffko.outlanderhub.ui.theme.GaugeDanger
import com.traffko.outlanderhub.ui.theme.GaugeGood
import com.traffko.outlanderhub.ui.theme.GaugeWarn
import com.traffko.outlanderhub.ui.theme.Hue

/**
 * Tesla-style vehicle view: the car itself in the middle with live door
 * animation, tire pressures anchored to the four corners, and quiet status
 * panels on either side.
 */
@Composable
fun ControlsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()

    Row(modifier) {
        // Left: body status
        Column(
            Modifier
                .width(270.dp)
                .fillMaxHeight()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Vehicle", fontSize = 30.sp, fontWeight = FontWeight.Light, color = Hue.TextPrimary)
            Spacer(Modifier.height(2.dp))

            StatusRowPanel(
                items = listOf(
                    StatusItem(
                        "Handbrake",
                        when (vehicle.handbrake) { true -> "Engaged"; false -> "Off"; null -> "--" },
                        alert = false,
                    ),
                    StatusItem(
                        "Driver seatbelt",
                        when (vehicle.seatbeltDriver) { true -> "Fastened"; false -> "Unfastened"; null -> "--" },
                        alert = vehicle.seatbeltDriver == false,
                    ),
                    StatusItem(
                        "Climate",
                        vehicle.climate.let { c ->
                            if (c.acOn == null) "--"
                            else buildString {
                                append(if (c.acOn) "A/C on" else "A/C off")
                                c.tempLeftC?.let { append("  ·  %.1f°".format(it)) }
                                c.fanSpeed?.let { append("  ·  fan $it") }
                            }
                        },
                        alert = false,
                    ),
                )
            )

            Spacer(Modifier.weight(1f))
            Text(
                "Signals come from the head unit's CAN decoder. \"--\" means the signal " +
                    "has not been broadcast or mapped yet — see the CAN screen.",
                color = Hue.TextTertiary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }

        // Center: the car with TPMS at the corners
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            CarTopView(
                doors = vehicle.doors,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 70.dp, vertical = 10.dp),
            )
            TirePressure(vehicle.tirePressuresKpa.getOrNull(0), Modifier.align(Alignment.TopStart))
            TirePressure(vehicle.tirePressuresKpa.getOrNull(1), Modifier.align(Alignment.TopEnd), alignEnd = true)
            TirePressure(vehicle.tirePressuresKpa.getOrNull(2), Modifier.align(Alignment.BottomStart))
            TirePressure(vehicle.tirePressuresKpa.getOrNull(3), Modifier.align(Alignment.BottomEnd), alignEnd = true)
        }

        // Right: doors & closures
        Column(
            Modifier
                .width(270.dp)
                .fillMaxHeight()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(44.dp))
            val d = vehicle.doors
            StatusRowPanel(
                title = "Closures",
                items = listOf(
                    StatusItem("Front left", if (d.frontLeft) "Open" else "Closed", d.frontLeft),
                    StatusItem("Front right", if (d.frontRight) "Open" else "Closed", d.frontRight),
                    StatusItem("Rear left", if (d.rearLeft) "Open" else "Closed", d.rearLeft),
                    StatusItem("Rear right", if (d.rearRight) "Open" else "Closed", d.rearRight),
                    StatusItem("Trunk", if (d.trunk) "Open" else "Closed", d.trunk),
                    StatusItem("Hood", if (d.hood) "Open" else "Closed", d.hood),
                )
            )
        }
    }
}

private data class StatusItem(val label: String, val value: String, val alert: Boolean)

@Composable
private fun StatusRowPanel(items: List<StatusItem>, title: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassPanel()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (title != null) {
            Spacer(Modifier.height(10.dp))
            MicroLabel(title)
            Spacer(Modifier.height(4.dp))
        }
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.label, fontSize = 15.sp, color = Hue.TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    item.value,
                    fontSize = 15.sp,
                    fontWeight = if (item.alert) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (item.alert) Hue.Red else Hue.TextPrimary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TirePressure(kpa: Float?, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val psi = kpa?.let { it * 0.145038f }
    val tone: Color = when {
        kpa == null -> Hue.TextTertiary
        kpa < 190f -> GaugeDanger
        kpa < 210f -> GaugeWarn
        else -> GaugeGood
    }
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                psi?.let { "%.0f".format(it) } ?: "--",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = if (kpa == null) Hue.TextTertiary else Hue.TextPrimary,
            )
            Spacer(Modifier.width(5.dp))
            Text("psi", fontSize = 13.sp, color = tone, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(
            kpa?.let { "%.0f kPa".format(it) } ?: "",
            fontSize = 12.sp,
            color = Hue.TextTertiary,
        )
    }
}
