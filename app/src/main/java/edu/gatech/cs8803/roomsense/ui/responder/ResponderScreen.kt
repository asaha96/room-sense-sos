package edu.gatech.cs8803.roomsense.ui.responder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.gatech.cs8803.roomsense.data.ble.NearbyIncident
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.di.viewModelFactory
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.theme.CodeFamily
import edu.gatech.cs8803.roomsense.ui.theme.Gold
import edu.gatech.cs8803.roomsense.ui.theme.Moss
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import kotlinx.coroutines.delay

@Composable
fun ResponderRoute(graph: AppGraph, onBack: () -> Unit) {
    val viewModel: ResponderViewModel = viewModel(factory = viewModelFactory { ResponderViewModel(graph) })
    ResponderScreen(viewModel, onBack)
}

@Composable
fun ResponderScreen(
    viewModel: ResponderViewModel,
    onBack: () -> Unit,
) {
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.listening) {
        while (viewModel.listening) {
            delay(5_000)
            viewModel.prune()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Responder", style = MaterialTheme.typography.headlineMedium)
                Text(viewModel.status, color = PaperDim)
            }
        }
        Text(
            "Listen for a phone that is already broadcasting an SOS. You still join its hotspot to see the floor map. The beacon carries the incident code, the room, and the network name.",
            color = PaperDim,
        )
        if (viewModel.listening) {
            OutlinedButton(onClick = viewModel::toggle, modifier = Modifier.fillMaxWidth()) { Text("Stop listening") }
        } else {
            Button(onClick = viewModel::toggle, modifier = Modifier.fillMaxWidth()) { Text("Listen for SOS") }
        }
        if (incidents.isEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Text(
                    if (viewModel.listening) {
                        "No SOS beacons yet. Stay near the caller. Bluetooth has to be on for both phones."
                    } else {
                        "Listening is off."
                    },
                    modifier = Modifier.padding(16.dp),
                    color = PaperDim,
                )
            }
        } else {
            incidents.forEach { incident -> IncidentCard(incident) }
        }
    }
}

@Composable
private fun IncidentCard(incident: NearbyIncident) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                incident.code.toCharArray().joinToString(" "),
                fontFamily = CodeFamily,
                fontWeight = FontWeight.Medium,
                color = Gold,
            )
            Text(incident.room, style = MaterialTheme.typography.titleLarge)
            Text(
                if (incident.ssid.isBlank()) "Hotspot name not in this packet yet." else "Join ${incident.ssid}",
                color = Moss,
            )
            Text(
                "Signal ${incident.rssi} dBm · heard ${ago(incident.lastSeenMillis)}",
                color = PaperDim,
            )
            Text(
                "Join that hotspot, then open the address on the caller's screen. The password is on their phone, next to the code.",
                color = PaperDim,
            )
        }
    }
}
