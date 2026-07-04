package com.traffko.outlanderhub.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traffko.outlanderhub.ui.components.MicroLabel
import com.traffko.outlanderhub.ui.components.PulseDot
import com.traffko.outlanderhub.ui.components.glassPanel
import com.traffko.outlanderhub.ui.components.pressable
import com.traffko.outlanderhub.ui.theme.Hue
import com.traffko.outlanderhub.vehicle.VehicleState
import kotlinx.coroutines.flow.StateFlow

/**
 * The floating vehicle-status pill drawn over other apps (typically the
 * CarPlay/ZLink projection). Collapsed it shows speed + fuel + a status dot;
 * tapping expands a small card with the body/engine numbers. An active alert
 * (see [overlayAlert]) forces it open and turns it red.
 */
@Composable
fun OverlayContent(
    stateFlow: StateFlow<VehicleState>,
    onDrag: (Float, Float) -> Unit,
) {
    val vehicle by stateFlow.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val alert = overlayAlert(vehicle)

    LaunchedEffect(alert != null) {
        if (alert != null) expanded = true
    }

    Column(
        Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
            .pressable { expanded = !expanded }
            .glassPanel(
                corner = 18.dp,
                stroke = if (alert != null) Hue.Red.copy(alpha = 0.6f) else Hue.Hairline,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(
                color = when {
                    alert != null -> Hue.Red
                    vehicle.connected -> Hue.Green
                    else -> Hue.TextTertiary
                }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                vehicle.speedKmh?.let { "%.0f".format(it) } ?: "--",
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                color = Hue.TextPrimary,
            )
            Spacer(Modifier.width(5.dp))
            MicroLabel("km/h", color = Hue.TextTertiary)
            Spacer(Modifier.width(16.dp))
            Text(
                vehicle.fuelPercent?.let { "%.0f%%".format(it) } ?: "--",
                fontSize = 16.sp,
                color = Hue.TextSecondary,
            )
        }

        if (alert != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                alert,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = Hue.Red,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OverlayRow("Coolant", vehicle.coolantTempC?.let { "%.0f °C".format(it) } ?: "--")
                OverlayRow("Battery", vehicle.batteryVolts?.let { "%.1f V".format(it) } ?: "--")
                OverlayRow("Tires", tireSummary(vehicle))
                OverlayRow("Doors", if (vehicle.doors.anyOpen) "Open" else "Closed")
            }
            Spacer(Modifier.height(8.dp))
            MicroLabel("drag to move · tap to collapse", color = Hue.TextTertiary)
        }
    }
}

@Composable
private fun OverlayRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MicroLabel(label, modifier = Modifier.width(86.dp))
        Text(value, fontSize = 14.sp, color = Hue.TextPrimary)
    }
}

private fun tireSummary(vehicle: VehicleState): String {
    val known = vehicle.tirePressuresKpa.filterNotNull()
    if (known.isEmpty()) return "--"
    val min = known.min()
    return "min %.0f kPa".format(min)
}
