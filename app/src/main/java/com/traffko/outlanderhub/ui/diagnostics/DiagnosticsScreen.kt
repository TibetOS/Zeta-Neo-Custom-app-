package com.traffko.outlanderhub.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.ui.components.MicroLabel
import com.traffko.outlanderhub.ui.components.glassPanel
import com.traffko.outlanderhub.ui.components.pressable
import com.traffko.outlanderhub.ui.theme.Hue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live view of raw CAN-decoder traffic, styled as a quiet terminal panel.
 * Perform an action in the car and watch which code changes, then update
 * FytSignalMap with the observed code.
 */
@Composable
fun DiagnosticsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val log by viewModel.eventLog.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    // Instant (non-animated) scroll: overlapping animations freeze the UI
    // when the CAN bus is chatty.
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.scrollToItem(log.lastIndex)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CAN bus", fontSize = 30.sp, fontWeight = FontWeight.Light, color = Hue.TextPrimary)
            Spacer(Modifier.weight(1f))
            MicroLabel("${log.size} events")
            Spacer(Modifier.width(20.dp))
            GhostButton("Clear") { viewModel.clearEventLog() }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .glassPanel(corner = 18.dp, fill = Color(0xFF0A0C0F))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            items(log) { event ->
                val time = dateFormatter.format(Date(event.timestampMs))
                Text(
                    "$time  ${event.pretty()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = if (event.channel.endsWith("info")) Hue.BlueBright
                    else Color(0xFFB9C2CC),
                )
            }
        }

        // Manual command sender for probing decoder controls.
        var codeText by remember { mutableStateOf("") }
        var intsText by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminalField(codeText, { codeText = it }, "cmd code", Modifier.width(170.dp))
            Spacer(Modifier.width(12.dp))
            TerminalField(intsText, { intsText = it }, "ints (comma separated)", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            val enabled = codeText.trim().toIntOrNull() != null
            GhostButton("Send", accent = true, enabled = enabled) {
                val code = codeText.trim().toIntOrNull() ?: return@GhostButton
                val ints = intsText.split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toIntArray()
                viewModel.sendCommand(code, *ints)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TerminalField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .glassPanel(corner = 14.dp, fill = Color(0xFF0A0C0F))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Hue.TextPrimary,
            ),
            cursorBrush = SolidColor(Hue.BlueBright),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Hue.TextTertiary,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun GhostButton(
    label: String,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .pressable { if (enabled) onClick() }
            .glassPanel(
                corner = 14.dp,
                fill = if (accent && enabled) Hue.Blue.copy(alpha = 0.22f) else Hue.Panel,
                stroke = if (accent && enabled) Hue.Blue.copy(alpha = 0.5f) else Hue.Hairline,
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = when {
                !enabled -> Hue.TextTertiary
                accent -> Hue.BlueBright
                else -> Hue.TextPrimary
            },
        )
    }
}
