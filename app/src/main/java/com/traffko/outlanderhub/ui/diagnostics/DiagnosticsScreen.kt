package com.traffko.outlanderhub.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traffko.outlanderhub.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live view of raw CAN-decoder traffic. This is the tool used to map the
 * Zeta/Outlander decoder: perform an action in the car (open a door, press
 * the brake, change fan speed) and watch which code changes here, then update
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

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CAN bus monitor", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "${log.size} events",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
            OutlinedButton(onClick = { viewModel.clearEventLog() }) { Text("Clear") }
        }

        Card(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(state = listState, modifier = Modifier.padding(12.dp)) {
                items(log) { event ->
                    val time = dateFormatter.format(Date(event.timestampMs))
                    Text(
                        "$time  ${event.pretty()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (event.channel.endsWith("info"))
                            MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Manual command sender for probing decoder controls.
        var codeText by remember { mutableStateOf("") }
        var intsText by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = codeText,
                onValueChange = { codeText = it },
                label = { Text("cmd code") },
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = intsText,
                onValueChange = { intsText = it },
                label = { Text("ints (comma separated)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    val code = codeText.trim().toIntOrNull() ?: return@Button
                    val ints = intsText.split(',')
                        .mapNotNull { it.trim().toIntOrNull() }
                        .toIntArray()
                    viewModel.sendCommand(code, *ints)
                },
                enabled = codeText.trim().toIntOrNull() != null,
            ) { Text("Send") }
        }
        Spacer(Modifier.height(4.dp))
    }
}
