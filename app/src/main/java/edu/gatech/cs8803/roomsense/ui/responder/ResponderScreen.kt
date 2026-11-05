package edu.gatech.cs8803.roomsense.ui.responder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.gatech.cs8803.roomsense.data.ble.NearbyIncident
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.di.viewModelFactory
import edu.gatech.cs8803.roomsense.ui.RuntimePermissions
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.components.ScreenHeader
import edu.gatech.cs8803.roomsense.ui.theme.Action
import edu.gatech.cs8803.roomsense.ui.theme.Moss
import edu.gatech.cs8803.roomsense.ui.theme.Paper
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import edu.gatech.cs8803.roomsense.ui.theme.RoomButtonShape
import edu.gatech.cs8803.roomsense.ui.theme.roomButtonHeight
import edu.gatech.cs8803.roomsense.ui.theme.roomOutlinedColors
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
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var permissionRequestPending by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRequestPending = false
        permissionDenied = RuntimePermissions.missingForListening(context).isNotEmpty()
        if (!permissionDenied) viewModel.startListening()
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionDenied = RuntimePermissions.missingForListening(context).isNotEmpty()
        if (!permissionDenied) viewModel.startListening()
    }
    val listen: () -> Unit = {
        val missing = RuntimePermissions.missingForListening(context)
        if (missing.isEmpty()) {
            permissionDenied = false
            viewModel.startListening()
        } else {
            permissionRequestPending = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
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
        ScreenHeader(title = "Responder", onBack = onBack)
        Text(
            "Listen for a phone that is already broadcasting an SOS. You still join its hotspot to see the floor map. The beacon carries the incident code, the room, and the network name.",
            color = PaperDim,
        )
        val routineStatus = viewModel.status == "Not listening." ||
            viewModel.status == "Stopped." ||
            viewModel.status.startsWith("Listening")
        if (!routineStatus) {
            Text(viewModel.status, color = PaperDim, style = MaterialTheme.typography.bodySmall)
        }
        if (viewModel.listening) {
            OutlinedButton(
                onClick = viewModel::toggle,
                modifier = Modifier.fillMaxWidth().roomButtonHeight(),
                shape = RoomButtonShape,
                border = BorderStroke(1.dp, Action),
                colors = roomOutlinedColors(),
            ) { Text("Stop listening") }
        } else {
            Button(
                onClick = listen,
                enabled = !permissionRequestPending,
                modifier = Modifier.fillMaxWidth().roomButtonHeight(),
                shape = RoomButtonShape,
            ) {
                Text(
                    if (permissionDenied) "Retry permission request" else "Listen for SOS",
                    fontWeight = FontWeight.Bold,
                )
            }
            if (permissionDenied) {
                Text(
                    if (Build.VERSION.SDK_INT >= 31) {
                        "Nearby devices permission is needed to find SOS beacons over Bluetooth. Retry to grant access. If no dialog appears, open app settings and allow Nearby devices."
                    } else {
                        "Location permission is needed for Bluetooth scanning on this Android version. Retry to grant access. If no dialog appears, open app settings and allow Location."
                    },
                    color = PaperDim,
                )
                OutlinedButton(
                    onClick = {
                        settingsLauncher.launch(Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().roomButtonHeight(),
                    shape = RoomButtonShape,
                    border = BorderStroke(1.dp, Action),
                    colors = roomOutlinedColors(),
                ) { Text("Open app settings") }
            }
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
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                letterSpacing = 1.sp,
                color = Paper,
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
