package edu.gatech.cs8803.roomsense.ui.responder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.gatech.cs8803.roomsense.data.ble.NearbyIncident
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.di.viewModelFactory
import edu.gatech.cs8803.roomsense.ui.RuntimePermissions
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.theme.CodeFamily
import edu.gatech.cs8803.roomsense.ui.theme.Gold
import edu.gatech.cs8803.roomsense.ui.theme.Moss
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import kotlinx.coroutines.delay

@Composable
fun ResponderRoute(
    graph: AppGraph,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    val viewModel: ResponderViewModel = viewModel(factory = viewModelFactory { ResponderViewModel(graph) })
    ResponderScreen(viewModel, onBack, onOpenMap, onOpenRemote)
}

@Composable
fun ResponderScreen(
    viewModel: ResponderViewModel,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    val context = LocalContext.current
    val mapLink = viewModel.mapLink
    LaunchedEffect(mapLink.phase) {
        if (mapLink.phase == MapPhase.Ready) onOpenMap()
    }
    var pendingMap by remember { mutableStateOf<NearbyIncident?>(null) }
    // Joining a Wi-Fi network works without these on most builds, but some
    // vendors want them, so ask once and continue either way.
    val joinPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        pendingMap?.let(viewModel::viewMap)
        pendingMap = null
    }
    val viewMap: (NearbyIncident) -> Unit = { incident ->
        val missing = RuntimePermissions.missingForJoining(context)
        if (missing.isEmpty()) {
            viewModel.viewMap(incident)
        } else {
            pendingMap = incident
            joinPermissionLauncher.launch(missing.toTypedArray())
        }
    }
    // Bluetooth first: no hotspot, no QR code, nothing from the caller.
    val openMap: (NearbyIncident) -> Unit = { incident ->
        if (viewModel.openRemote(incident)) onOpenRemote() else viewMap(incident)
    }
    val openWifiSettings: () -> Unit = {
        val intent = if (Build.VERSION.SDK_INT >= 29) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
    }
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
            Button(onClick = listen, enabled = !permissionRequestPending, modifier = Modifier.fillMaxWidth()) {
                Text(if (permissionDenied) "Retry permission request" else "Listen for SOS")
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
                OutlinedButton(onClick = {
                    settingsLauncher.launch(Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ))
                }, modifier = Modifier.fillMaxWidth()) { Text("Open app settings") }
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
            incidents.forEach { incident ->
                val link = mapLink.takeIf { it.code == incident.code && it.phase != MapPhase.Idle }
                IncidentCard(
                    incident = incident,
                    link = link,
                    anotherBusy = mapLink.busy && mapLink.code != incident.code,
                    onViewMap = { openMap(incident) },
                    onRetryHotspot = { viewMap(incident) },
                    onCancel = viewModel::closeMap,
                    onOpenWifi = openWifiSettings,
                )
            }
        }
    }
}

@Composable
private fun IncidentCard(
    incident: NearbyIncident,
    link: MapLinkState?,
    anotherBusy: Boolean,
    onViewMap: () -> Unit,
    onRetryHotspot: () -> Unit,
    onCancel: () -> Unit,
    onOpenWifi: () -> Unit,
) {
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
            when {
                link == null || link.phase == MapPhase.Ready -> {
                    Button(
                        onClick = onViewMap,
                        enabled = !anotherBusy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("View map") }
                }
                link.busy -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(link.message, color = PaperDim, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                else -> ManualJoin(link, onRetry = onRetryHotspot, onCancel = onCancel, onOpenWifi = onOpenWifi)
            }
        }
    }
}

@Composable
private fun ManualJoin(
    link: MapLinkState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpenWifi: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(link.message, color = Gold)
        Text(
            buildString {
                append("Join ")
                append(link.ssid.ifBlank { "the caller's hotspot" })
                append(" yourself: open Wi-Fi settings and pick it")
                if (link.password == null) {
                    append(", or tap the QR icon there and scan the Wi-Fi code on the caller's screen")
                }
                append(". Stay connected if Android says there is no internet. The map opens here as soon as this phone is on the hotspot.")
            },
            color = PaperDim,
        )
        link.password?.let { password ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Password ", color = PaperDim)
                Text(password, fontFamily = CodeFamily, modifier = Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(password)) }) { Text("Copy") }
            }
        }
        Button(onClick = onOpenWifi, modifier = Modifier.fillMaxWidth()) { Text("Open Wi-Fi settings") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Try again") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
        Text(
            "Still stuck? Scan the map QR code on the caller's screen with the camera after joining the hotspot.",
            color = PaperDim,
        )
    }
}
