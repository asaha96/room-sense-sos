package edu.gatech.cs8803.roomsense.ui.survey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.theme.Gold
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SurveyScreen(
    viewModel: SurveyViewModel,
    onBack: () -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    val selectedSamples = viewModel.samples.filter { it.roomId == viewModel.selectedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Survey", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (viewModel.demoMode) "Demo scans are simulated." else "Uses live Wi-Fi.",
                    color = PaperDim,
                )
            }
        }

        Text(
            "Stand in the room you are labeling. Each sample is a BSSID-to-RSSI fingerprint. " +
                "Four samples is a good start. Android allows about four scans every two minutes in the foreground, " +
                "so a real survey pauses when the radio is throttled and will not save the same cached reading twice.",
            color = PaperDim,
        )

        if (viewModel.rooms.isEmpty()) {
            Text("No rooms yet. Name one, then capture it.", fontWeight = FontWeight.Medium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.rooms.forEach { room ->
                    val count = viewModel.samples.count { it.roomId == room.id }
                    FilterChip(
                        selected = room.id == viewModel.selectedId,
                        onClick = { viewModel.select(room.id) },
                        label = { Text("${room.name} · $count") },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("New room") },
            )
            Button(onClick = {
                viewModel.addRoom(newName)
                newName = ""
            }) { Text("Add") }
        }

        Text(viewModel.status, color = Gold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::saveOne, enabled = !viewModel.recording && viewModel.selectedId != null) {
                Text("Save one")
            }
            Button(onClick = viewModel::recordSet, enabled = !viewModel.recording && viewModel.selectedId != null) {
                Text(if (viewModel.demoMode) "Save 4 now" else "Record a set")
            }
        }
        if (viewModel.recording) {
            TextButton(onClick = viewModel::cancelRecording) { Text("Stop recording") }
        }
        if (selectedSamples.isNotEmpty()) {
            TextButton(onClick = viewModel::clearSelected) { Text("Clear this room's fingerprints") }
        }

        selectedSamples.take(8).forEach { sample ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(sample.capturedAtMillis)),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${sample.readings.size} access points · ${ago(sample.capturedAtMillis)}" +
                            if (sample.fromCache) " · cached" else "",
                        color = PaperDim,
                    )
                }
            }
        }
    }
}
