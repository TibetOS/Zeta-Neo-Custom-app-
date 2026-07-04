package com.traffko.outlanderhub.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.ui.components.MicroLabel
import com.traffko.outlanderhub.ui.components.PulseDot
import com.traffko.outlanderhub.ui.theme.DisplayM
import com.traffko.outlanderhub.ui.theme.DisplayXL
import com.traffko.outlanderhub.ui.theme.GaugeDanger
import com.traffko.outlanderhub.ui.theme.GaugeGood
import com.traffko.outlanderhub.ui.theme.GaugeWarn
import com.traffko.outlanderhub.ui.theme.Hue

/**
 * Tesla-style driving view: one enormous speed numeral in the middle of a
 * black canvas, a PRND strip, a gradient RPM power bar with redline, and
 * quiet flanking stats. No gauges shouting for attention.
 */
@Composable
fun DashboardScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()

    Column(modifier) {
        // Status line
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PulseDot(if (vehicle.connected) GaugeGood else GaugeDanger)
            Spacer(Modifier.width(10.dp))
            MicroLabel(if (vehicle.connected) vehicle.source.name.replace('_', ' ') else "Offline")
            Spacer(Modifier.weight(1f))
            MicroLabel(vehicle.odometerKm?.let { "%,d km".format(it) } ?: "")
        }

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left stats
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                SideStat(
                    label = "Coolant",
                    value = vehicle.coolantTempC?.let { "%.0f".format(it) } ?: "--",
                    unit = "°C",
                    tone = toneForRange(vehicle.coolantTempC, warn = 105f, danger = 115f),
                )
                SideStat(
                    label = "Battery",
                    value = vehicle.batteryVolts?.let { "%.1f".format(it) } ?: "--",
                    unit = "V",
                    tone = vehicle.batteryVolts?.let {
                        when {
                            it < 11.8f -> GaugeDanger
                            it < 12.4f -> GaugeWarn
                            else -> null
                        }
                    },
                )
            }

            // Center: gear strip + speed + RPM power bar
            Column(
                Modifier.weight(2.3f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GearStrip(vehicle.gear)
                Text(
                    vehicle.speedKmh?.let { "%.0f".format(it) } ?: "--",
                    style = DisplayXL,
                )
                MicroLabel("km/h")
                Spacer(Modifier.height(26.dp))
                PowerBar(
                    rpm = vehicle.rpm,
                    max = 7000f,
                    redlineFrom = 5500f,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(30.dp),
                )
            }

            // Right stats
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(30.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SideStat(
                    label = "Fuel",
                    value = vehicle.fuelPercent?.let { "%.0f".format(it) } ?: "--",
                    unit = "%",
                    tone = toneForLow(vehicle.fuelPercent, warn = 20f, danger = 10f),
                    alignEnd = true,
                )
                SideStat(
                    label = "Outside",
                    value = vehicle.outsideTempC?.let { "%.1f".format(it) } ?: "--",
                    unit = "°C",
                    alignEnd = true,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun GearStrip(gear: String?) {
    val active = gear?.trim()?.take(1)?.uppercase()
    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        listOf("P", "R", "N", "D").forEach { g ->
            val selected = g == active
            Text(
                g,
                fontSize = if (selected) 26.sp else 20.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Hue.TextPrimary else Hue.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(26.dp),
            )
        }
    }
}

@Composable
private fun SideStat(
    label: String,
    value: String,
    unit: String,
    tone: Color? = null,
    alignEnd: Boolean = false,
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        MicroLabel(label)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = DisplayM, color = tone ?: Hue.TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(
                unit,
                fontSize = 16.sp,
                color = Hue.TextSecondary,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        // Warning underline only when something needs attention
        if (tone != null && tone != GaugeGood) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(44.dp)) {
                Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                    drawRoundRect(tone, cornerRadius = CornerRadius(2f))
                }
            }
        }
    }
}

/**
 * Horizontal RPM bar: blue gradient fill with a glowing tip, hairline track,
 * redline segment marked in red. Reads like Tesla's power meter.
 */
@Composable
private fun PowerBar(
    rpm: Int?,
    max: Float,
    redlineFrom: Float,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = ((rpm ?: 0) / max).coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "rpm",
    )
    val redlineFraction = (redlineFrom / max).coerceIn(0f, 1f)
    val overRedline = fraction >= redlineFraction

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val r = CornerRadius(size.height / 2f)
            // Track
            drawRoundRect(color = Color(0xFF191D23), cornerRadius = r)
            // Redline zone on the track
            drawRoundRect(
                color = Hue.Red.copy(alpha = 0.30f),
                topLeft = Offset(size.width * redlineFraction, 0f),
                size = Size(size.width * (1f - redlineFraction), size.height),
                cornerRadius = r,
            )
            if (fraction > 0.005f) {
                val w = size.width * fraction
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = if (overRedline)
                            listOf(Hue.Blue, Hue.BlueBright, Hue.Red)
                        else
                            listOf(Hue.Blue.copy(alpha = 0.55f), Hue.BlueBright),
                        endX = w,
                    ),
                    size = Size(w, size.height),
                    cornerRadius = r,
                )
                // Glowing tip
                drawCircle(
                    color = (if (overRedline) Hue.Red else Hue.BlueBright).copy(alpha = 0.35f),
                    radius = size.height * 1.6f,
                    center = Offset(w, size.height / 2f),
                )
                drawCircle(
                    color = if (overRedline) Hue.Red else Color.White,
                    radius = size.height * 0.55f,
                    center = Offset(w, size.height / 2f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        MicroLabel(rpm?.let { "%,d rpm".format(it) } ?: "-- rpm")
    }
}

private fun toneForRange(value: Float?, warn: Float, danger: Float): Color? = when {
    value == null -> null
    value >= danger -> GaugeDanger
    value >= warn -> GaugeWarn
    else -> null
}

private fun toneForLow(value: Float?, warn: Float, danger: Float): Color? = when {
    value == null -> null
    value <= danger -> GaugeDanger
    value <= warn -> GaugeWarn
    else -> null
}
