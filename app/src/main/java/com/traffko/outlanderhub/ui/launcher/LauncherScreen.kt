package com.traffko.outlanderhub.ui.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.OutlanderApp
import com.traffko.outlanderhub.apps.LaunchableApp
import com.traffko.outlanderhub.ui.components.MicroLabel
import com.traffko.outlanderhub.ui.components.PulseDot
import com.traffko.outlanderhub.ui.components.glassPanel
import com.traffko.outlanderhub.ui.components.pressable
import com.traffko.outlanderhub.ui.theme.DisplayM
import com.traffko.outlanderhub.ui.theme.Hue
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home: a calm left panel with an oversized clock and vehicle glance, and the
 * app grid on the right. Everything sits directly on black — the panel look
 * is reserved for the glance card and alerts.
 */
@Composable
fun LauncherScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appsRepo = (context.applicationContext as OutlanderApp).installedAppsRepository
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var appsVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(appsVersion) { apps = appsRepo.loadApps() }

    // Refresh the grid when packages are installed/removed/updated while the
    // launcher is showing (otherwise a new app only appears after a tab switch).
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                appsVersion++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        // NOT_EXPORTED still receives system broadcasts; it only blocks other apps.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val vehicle by viewModel.vehicleState.collectAsStateWithLifecycle()

    Row(modifier) {
        // Left: clock + vehicle glance
        Column(
            Modifier
                .width(340.dp)
                .fillMaxHeight()
                .padding(top = 18.dp, bottom = 20.dp),
        ) {
            Clock()

            Spacer(Modifier.height(34.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .glassPanel()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                GlanceRow("Outside", vehicle.outsideTempC?.let { "%.0f°".format(it) } ?: "--")
                GlanceRow("Fuel", vehicle.fuelPercent?.let { "%.0f%%".format(it) } ?: "--")
                GlanceRow("Battery", vehicle.batteryVolts?.let { "%.1f V".format(it) } ?: "--")
            }

            Spacer(Modifier.weight(1f))

            if (vehicle.doors.anyOpen) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Hue.Red.copy(alpha = 0.14f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulseDot(Hue.Red, size = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Door open",
                        color = Hue.Red,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(if (vehicle.connected) Hue.Green else Hue.TextTertiary)
                    Spacer(Modifier.width(10.dp))
                    MicroLabel(if (vehicle.connected) "Vehicle online" else "Vehicle offline")
                }
            }
        }

        Spacer(Modifier.width(30.dp))

        // Right: app grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(app) { appsRepo.launch(app) }
            }
        }
    }
}

/**
 * Self-contained ticking clock: the display only shows minutes, so ticks are
 * aligned to the next minute boundary — one recomposition per minute, local
 * to these two Texts, and always in step with the system clock.
 */
@Composable
private fun Clock() {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }
    val zone = remember { ZoneId.systemDefault() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val millis = System.currentTimeMillis()
            now = millis
            delay(60_000 - millis % 60_000)
        }
    }
    val (timeText, dateText) = remember(now) {
        val moment = Instant.ofEpochMilli(now).atZone(zone)
        timeFormatter.format(moment) to dateFormatter.format(moment)
    }
    Text(
        timeText,
        fontSize = 92.sp,
        fontWeight = FontWeight.ExtraLight,
        letterSpacing = (-2).sp,
        color = Hue.TextPrimary,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        dateText,
        fontSize = 16.sp,
        color = Hue.TextSecondary,
    )
}

@Composable
private fun GlanceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MicroLabel(label)
        Spacer(Modifier.weight(1f))
        Text(value, style = DisplayM, fontSize = 26.sp)
    }
}

@Composable
private fun AppTile(app: LaunchableApp, onClick: () -> Unit) {
    Column(
        Modifier
            .pressable(onClick)
            .glassPanel(corner = 20.dp)
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberDrawablePainter(app.icon),
                contentDescription = app.label,
                modifier = Modifier.size(54.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = Hue.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}
