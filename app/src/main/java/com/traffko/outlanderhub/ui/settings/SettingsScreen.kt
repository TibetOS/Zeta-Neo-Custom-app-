package com.traffko.outlanderhub.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traffko.outlanderhub.BuildConfig
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.UpdateStatus
import com.traffko.outlanderhub.ui.components.MicroLabel
import com.traffko.outlanderhub.ui.components.glassPanel
import com.traffko.outlanderhub.ui.components.pressable
import com.traffko.outlanderhub.ui.theme.Hue
import com.traffko.outlanderhub.vehicle.VehicleSource

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Setup", fontSize = 30.sp, fontWeight = FontWeight.Light, color = Hue.TextPrimary)
        Text(
            "v${BuildConfig.VERSION_NAME}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Hue.TextSecondary,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .glassPanel()
                .padding(22.dp),
        ) {
            MicroLabel("Vehicle data source")
            Spacer(Modifier.height(14.dp))
            SourceOption(
                title = "Demo (simulated drive)",
                subtitle = "For testing the UI without the car.",
                selected = settings.source == VehicleSource.DEMO,
            ) { viewModel.setSource(VehicleSource.DEMO) }
            Spacer(Modifier.height(10.dp))

            val context = LocalContext.current
            // Re-checked on resume so the warning tracks what the user actually
            // did on the system permission dialog / app-settings screen.
            val lifecycleOwner = LocalLifecycleOwner.current
            var hasLocationPermission by remember { mutableStateOf(hasFineLocation(context)) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasLocationPermission = hasFineLocation(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val locationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasLocationPermission = granted
                if (granted) viewModel.retryGps()
            }
            SourceOption(
                title = "GPS (no CAN needed)",
                subtitle = "Speed, heading and altitude from the unit's GPS antenna. " +
                    "Drives the dash, trip computer and overlay while the CAN decoder is unmapped.",
                selected = settings.source == VehicleSource.GPS,
            ) {
                viewModel.setSource(VehicleSource.GPS)
                if (!hasLocationPermission) {
                    locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
            if (settings.source == VehicleSource.GPS && !hasLocationPermission) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Location permission not granted — GPS can't deliver speed. Tap here to grant it.",
                    color = Hue.Amber,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(start = 38.dp)
                        .pressable { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                )
            }
            Spacer(Modifier.height(10.dp))
            SourceOption(
                title = "Zeta CAN decoder (FYT)",
                subtitle = "Live data from the head unit's com.syu.ms service. " +
                    "Experimental — verify signals in the CAN screen.",
                selected = settings.source == VehicleSource.FYT_CAN,
            ) { viewModel.setSource(VehicleSource.FYT_CAN) }
            Spacer(Modifier.height(10.dp))
            SourceOption(
                title = "Topway MCU (TWUtil)",
                subtitle = "Raw capture from this unit's MCU serial link. " +
                    "Discovery mode — map the codes that change in the CAN screen.",
                selected = settings.source == VehicleSource.TOPWAY_TW,
            ) { viewModel.setSource(VehicleSource.TOPWAY_TW) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .glassPanel()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show CAN diagnostics tab", fontSize = 17.sp, color = Hue.TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Raw bus monitor used to map decoder signals.",
                    color = Hue.TextTertiary,
                    fontSize = 13.sp,
                )
            }
            TeslaSwitch(
                checked = settings.showDiagnostics,
                onToggle = { viewModel.setShowDiagnostics(it) },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .glassPanel()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val context = LocalContext.current
            // Re-checked on every resume so the warning below tracks what the
            // user actually did on the system permission screen.
            val lifecycleOwner = LocalLifecycleOwner.current
            var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasOverlayPermission = Settings.canDrawOverlays(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            Column(Modifier.weight(1f)) {
                Text("Floating overlay", fontSize = 17.sp, color = Hue.TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Vehicle pill shown over other apps (e.g. CarPlay projection). " +
                        "Needs the \"display over other apps\" permission.",
                    color = Hue.TextTertiary,
                    fontSize = 13.sp,
                )
                if (settings.overlayEnabled && !hasOverlayPermission) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Permission not granted — the pill can't be shown. Tap here to open the system setting.",
                        color = Hue.Amber,
                        fontSize = 13.sp,
                        modifier = Modifier.pressable {
                            runCatching { context.startActivity(overlayPermissionIntent(context)) }
                        },
                    )
                }
            }
            TeslaSwitch(
                checked = settings.overlayEnabled,
                onToggle = { enabled ->
                    if (enabled && !Settings.canDrawOverlays(context)) {
                        // Send the user to the system permission screen; the
                        // service starts once permission exists and the app
                        // returns to the foreground. Some firmwares strip this
                        // settings screen — don't crash if it's missing.
                        runCatching { context.startActivity(overlayPermissionIntent(context)) }
                    }
                    viewModel.setOverlayEnabled(enabled)
                },
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .glassPanel()
                .padding(22.dp),
        ) {
            MicroLabel("About")
            Spacer(Modifier.height(8.dp))
            Text(
                "Outlander Hub — custom launcher and dashboard for the Mitsubishi Outlander 2019 " +
                    "on the Zeta Neo 14 head unit.",
                color = Hue.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            val context = LocalContext.current
            val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("Version ${viewModel.currentVersion}")
                Spacer(Modifier.weight(1f))
                when (val status = updateStatus) {
                    UpdateStatus.Idle -> Text(
                        "Check for updates",
                        fontSize = 14.sp,
                        color = Hue.BlueBright,
                        modifier = Modifier.pressable { viewModel.checkForUpdates() },
                    )
                    UpdateStatus.Checking -> Text("Checking…", fontSize = 14.sp, color = Hue.TextTertiary)
                    UpdateStatus.UpToDate -> Text("Up to date", fontSize = 14.sp, color = Hue.TextSecondary)
                    UpdateStatus.Failed -> Text(
                        "Check failed — tap to retry",
                        fontSize = 14.sp,
                        color = Hue.Amber,
                        modifier = Modifier.pressable { viewModel.checkForUpdates() },
                    )
                    is UpdateStatus.Available -> Text(
                        "Download ${status.release.tag}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Hue.BlueBright,
                        modifier = Modifier.pressable {
                            val url = status.release.apkUrl ?: status.release.htmlUrl
                            // Stripped-down head-unit firmwares may lack a browser.
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SourceOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val ring by animateColorAsState(
        targetValue = if (selected) Hue.Blue else Hue.TextTertiary,
        label = "ring",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Hue.Blue.copy(alpha = 0.18f) else Hue.PanelHigh),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(if (selected) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(ring)
            )
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, fontSize = 16.sp, color = Hue.TextPrimary)
            Text(subtitle, color = Hue.TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private fun hasFineLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun overlayPermissionIntent(context: Context) = Intent(
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    Uri.parse("package:${context.packageName}"),
).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Minimal pill switch in the Tesla idiom — blue when on, no Material thumb icon. */
@Composable
private fun TeslaSwitch(checked: Boolean, onToggle: (Boolean) -> Unit) {
    val track by animateColorAsState(
        targetValue = if (checked) Hue.Blue else Hue.PanelHigh,
        label = "track",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 26.dp else 3.dp,
        animationSpec = spring(stiffness = 500f),
        label = "knob",
    )
    Box(
        Modifier
            .pressable { onToggle(!checked) }
            .width(54.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                // Lambda offset defers the animated read to the layout phase,
                // so the knob glides without recomposing the switch each frame.
                .offset { IntOffset(knobOffset.roundToPx(), 0) }
                .align(Alignment.CenterStart)
                .size(25.dp)
                .clip(CircleShape)
                .background(if (checked) Hue.TextPrimary else Hue.TextSecondary)
        )
    }
}
