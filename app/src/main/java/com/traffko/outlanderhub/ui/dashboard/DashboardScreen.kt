package com.traffko.outlanderhub.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.ui.theme.GaugeDanger
import com.traffko.outlanderhub.ui.theme.GaugeGood
import com.traffko.outlanderhub.ui.theme.GaugeWarn
import com.traffko.outlanderhub.vehicle.VehicleState

@Composable
fun DashboardScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ArcGauge(
                value = vehicle.speedKmh ?: 0f,
                max = 220f,
                label = "km/h",
                title = "Speed",
                modifier = Modifier.weight(1.4f),
                bigText = vehicle.speedKmh?.let { "%.0f".format(it) } ?: "--",
            )
            ArcGauge(
                value = (vehicle.rpm ?: 0).toFloat(),
                max = 7000f,
                label = "rpm",
                title = "Engine",
                modifier = Modifier.weight(1.4f),
                bigText = vehicle.rpm?.toString() ?: "--",
                dangerFrom = 5500f,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    title = "Coolant",
                    value = vehicle.coolantTempC?.let { "%.0f°C".format(it) } ?: "--",
                    tone = toneForRange(vehicle.coolantTempC, warn = 105f, danger = 115f),
                    modifier = Modifier.weight(1f),
                )
                val volts = vehicle.batteryVolts
                StatCard(
                    title = "Battery",
                    value = volts?.let { "%.1f V".format(it) } ?: "--",
                    tone = when {
                        volts == null -> null
                        volts < 11.8f -> GaugeDanger
                        volts < 12.4f -> GaugeWarn
                        else -> GaugeGood
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatCard(
                title = "Fuel",
                value = vehicle.fuelPercent?.let { "%.0f%%".format(it) } ?: "--",
                tone = toneForLow(vehicle.fuelPercent, warn = 20f, danger = 10f),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Outside",
                value = vehicle.outsideTempC?.let { "%.1f°C".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Odometer",
                value = vehicle.odometerKm?.let { "%,d km".format(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Gear",
                value = vehicle.gear ?: "--",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Source",
                value = if (vehicle.connected) vehicle.source.name else "OFFLINE",
                tone = if (vehicle.connected) GaugeGood else GaugeDanger,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun toneForRange(value: Float?, warn: Float, danger: Float): Color? = when {
    value == null -> null
    value >= danger -> GaugeDanger
    value >= warn -> GaugeWarn
    else -> GaugeGood
}

private fun toneForLow(value: Float?, warn: Float, danger: Float): Color? = when {
    value == null -> null
    value <= danger -> GaugeDanger
    value <= warn -> GaugeWarn
    else -> GaugeGood
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = tone ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 270° sweep arc gauge drawn with Canvas — designed to stay smooth on the
 * head unit's mid-range GPU.
 */
@Composable
private fun ArcGauge(
    value: Float,
    max: Float,
    label: String,
    title: String,
    bigText: String,
    modifier: Modifier = Modifier,
    dangerFrom: Float? = null,
) {
    val animated by animateFloatAsState(
        targetValue = (value / max).coerceIn(0f, 1f),
        animationSpec = tween(220),
        label = "gauge",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val dangerFraction = dangerFrom?.let { (it / max).coerceIn(0f, 1f) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
            ) {
                val stroke = Stroke(width = size.minDimension * 0.07f, cap = StrokeCap.Round)
                val inset = stroke.width / 2
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                val topLeft = Offset(inset, inset)
                // Track
                drawArc(
                    color = track,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                // Danger zone
                if (dangerFraction != null) {
                    drawArc(
                        color = GaugeDanger.copy(alpha = 0.35f),
                        startAngle = 135f + 270f * dangerFraction,
                        sweepAngle = 270f * (1f - dangerFraction),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                }
                // Value
                drawArc(
                    color = accent,
                    startAngle = 135f,
                    sweepAngle = 270f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(bigText, fontSize = 54.sp, fontWeight = FontWeight.Bold)
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
            )
        }
    }
}
