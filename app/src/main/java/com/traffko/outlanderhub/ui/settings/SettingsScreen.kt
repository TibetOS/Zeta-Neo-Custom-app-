package com.traffko.outlanderhub.ui.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traffko.outlanderhub.MainViewModel
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
            SourceOption(
                title = "Zeta CAN decoder (FYT)",
                subtitle = "Live data from the head unit's com.syu.ms service. " +
                    "Experimental — verify signals in the CAN screen.",
                selected = settings.source == VehicleSource.FYT_CAN,
            ) { viewModel.setSource(VehicleSource.FYT_CAN) }
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
                .offset(x = knobOffset)
                .align(Alignment.CenterStart)
                .size(25.dp)
                .clip(CircleShape)
                .background(if (checked) Hue.TextPrimary else Hue.TextSecondary)
        )
    }
}
