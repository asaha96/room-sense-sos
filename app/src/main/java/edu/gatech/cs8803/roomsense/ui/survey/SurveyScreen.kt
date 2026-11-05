package edu.gatech.cs8803.roomsense.ui.survey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.components.ScreenHeader
import edu.gatech.cs8803.roomsense.ui.theme.Action
import edu.gatech.cs8803.roomsense.ui.theme.Paper
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import edu.gatech.cs8803.roomsense.ui.theme.RoomButtonShape
import edu.gatech.cs8803.roomsense.ui.theme.RoomChipShape
import edu.gatech.cs8803.roomsense.ui.theme.RoomFieldShape
import edu.gatech.cs8803.roomsense.ui.theme.roomButtonHeight
import edu.gatech.cs8803.roomsense.ui.theme.roomFilterChipBorder
import edu.gatech.cs8803.roomsense.ui.theme.roomFilterChipColors
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
            .imeNestedScroll()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(title = "Survey", onBack = onBack)

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
                        shape = RoomChipShape,
                        colors = roomFilterChipColors(),
                        border = roomFilterChipBorder(room.id == viewModel.selectedId),
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
                shape = RoomFieldShape,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = TextUnit.Unspecified,
                    color = Paper,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Paper,
                    unfocusedTextColor = Paper,
                    cursorColor = Action,
                ),
                label = { Text("New room") },
            )
            Button(
                onClick = {
                    if (newName.isBlank()) {
                        return@Button
                    }
                    viewModel.addRoom(newName)
                    newName = ""
                },
                modifier = Modifier.roomButtonHeight(),
                shape = RoomButtonShape,
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        }

        Text(viewModel.status, color = Action)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::saveOne,
                enabled = !viewModel.recording && viewModel.selectedId != null,
                modifier = Modifier.roomButtonHeight(),
                shape = RoomButtonShape,
            ) {
                Text("Save one", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = viewModel::recordSet,
                enabled = !viewModel.recording && viewModel.selectedId != null,
                modifier = Modifier.roomButtonHeight(),
                shape = RoomButtonShape,
            ) {
                Text(if (viewModel.demoMode) "Save 4 now" else "Record a set", fontWeight = FontWeight.Bold)
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
                        fontWeight = FontWeight.SemiBold,
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
