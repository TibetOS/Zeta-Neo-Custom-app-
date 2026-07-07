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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.traffko.outlanderhub.MainViewModel
import com.traffko.outlanderhub.vehicle.fyt.SignalKind
import com.traffko.outlanderhub.ui.components.MicroLabel
import com.traffko.outlanderhub.ui.components.glassPanel
import com.traffko.outlanderhub.ui.components.pressable
import com.traffko.outlanderhub.ui.theme.Hue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// DateTimeFormatter (unlike SimpleDateFormat) is immutable and thread-safe,
// so rows can format timestamps with no shared mutable scratch state.
private val TimestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US)

/**
 * Live view of raw CAN-decoder traffic, styled as a quiet terminal panel.
 * The in-car mapping workflow: perform an action in the car, watch which
 * code lights up amber (payload changed), tap that row and assign the code
 * to a signal — the mapping applies live, no rebuild.
 */
@Composable
fun DiagnosticsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val log by viewModel.eventLog.collectAsStateWithLifecycle()
    val signalMap by viewModel.signalMap.collectAsStateWithLifecycle()
    val exportPath by viewModel.lastExportPath.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val zone = remember { ZoneId.systemDefault() }
    var assignCode by remember { mutableStateOf<Int?>(null) }

    // Instant (non-animated) scroll: overlapping animations freeze the UI
    // when the CAN bus is chatty. Keyed on the generation counter, not the
    // list size — once the log reaches its cap the size stops changing, but
    // events keep rotating through and the view must keep following.
    LaunchedEffect(log.generation) {
        if (log.events.isNotEmpty()) listState.scrollToItem(log.events.lastIndex)
    }

    // Each open of this tab re-reads the decoder's static config via
    // pull-mode get() — those codes never push, so without this probe they
    // would never appear in the log.
    LaunchedEffect(Unit) { viewModel.probeFytConfig() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CAN bus", fontSize = 30.sp, fontWeight = FontWeight.Light, color = Hue.TextPrimary)
            Spacer(Modifier.width(16.dp))
            MicroLabel("tap a row to map it")
            Spacer(Modifier.weight(1f))
            MicroLabel("${log.events.size} events")
            Spacer(Modifier.width(20.dp))
            GhostButton("Export") { viewModel.exportLog() }
            Spacer(Modifier.width(12.dp))
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
            items(log.events) { event ->
                val time = TimestampFormatter.format(Instant.ofEpochMilli(event.timestampMs).atZone(zone))
                val tag = signalMap[event.code]?.let { "[${it.name}] " } ?: ""
                Text(
                    "$time  $tag${event.pretty()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = when {
                        event.channel.endsWith("info") -> Hue.BlueBright
                        event.changed -> Hue.Amber
                        else -> Color(0xFFB9C2CC)
                    },
                    modifier = if (event.code >= 0) {
                        Modifier.pressable { assignCode = event.code }
                    } else {
                        Modifier
                    },
                )
            }
        }

        exportPath?.let {
            MicroLabel("log saved: $it", color = Hue.TextTertiary)
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

    assignCode?.let { code ->
        AssignSignalDialog(
            code = code,
            signalMap = signalMap,
            onAssign = { kind ->
                viewModel.assignSignal(code, kind)
                assignCode = null
            },
            onReset = {
                viewModel.resetSignalMap()
                assignCode = null
            },
            onDismiss = { assignCode = null },
        )
    }
}

/**
 * Picker shown when a log row is tapped: assigns the tapped code to a
 * vehicle signal (moving the signal off its previous code), clears the
 * code's mapping, or resets the whole map to the built-in defaults.
 */
@Composable
private fun AssignSignalDialog(
    code: Int,
    signalMap: Map<Int, SignalKind>,
    onAssign: (SignalKind?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .glassPanel(corner = 20.dp)
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Map code $code", fontSize = 20.sp, fontWeight = FontWeight.Light, color = Hue.TextPrimary)
                Spacer(Modifier.weight(1f))
                signalMap[code]?.let { MicroLabel("now: ${it.name}") }
            }
            Spacer(Modifier.height(14.dp))
            SignalKind.entries.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { kind ->
                        val currentCode = signalMap.entries.firstOrNull { it.value == kind }?.key
                        val selected = currentCode == code
                        Row(
                            Modifier
                                .weight(1f)
                                .padding(vertical = 3.dp, horizontal = 4.dp)
                                .pressable { onAssign(kind) }
                                .glassPanel(
                                    corner = 12.dp,
                                    fill = if (selected) Hue.Blue.copy(alpha = 0.22f) else Hue.Panel,
                                    stroke = if (selected) Hue.Blue.copy(alpha = 0.5f) else Hue.Hairline,
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                kind.label,
                                fontSize = 14.sp,
                                color = if (selected) Hue.BlueBright else Hue.TextPrimary,
                            )
                            Spacer(Modifier.weight(1f))
                            MicroLabel(currentCode?.toString() ?: "--", color = Hue.TextTertiary)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                if (signalMap.containsKey(code)) {
                    GhostButton("Clear mapping") { onAssign(null) }
                    Spacer(Modifier.width(12.dp))
                }
                GhostButton("Reset all to defaults") { onReset() }
                Spacer(Modifier.weight(1f))
                GhostButton("Close", accent = true) { onDismiss() }
            }
        }
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
