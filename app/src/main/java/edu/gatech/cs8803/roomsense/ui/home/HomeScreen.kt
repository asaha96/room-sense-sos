package edu.gatech.cs8803.roomsense.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.gatech.cs8803.roomsense.data.settings.AppSettings
import edu.gatech.cs8803.roomsense.domain.model.RoomModel
import edu.gatech.cs8803.roomsense.domain.model.RoomPrediction
import edu.gatech.cs8803.roomsense.service.SosSnapshot
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.components.HoldToActivateButton
import edu.gatech.cs8803.roomsense.ui.confidencePercent
import edu.gatech.cs8803.roomsense.ui.theme.Gold
import edu.gatech.cs8803.roomsense.ui.theme.Moss
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSurvey: () -> Unit,
    onMap: () -> Unit,
    onResponder: () -> Unit,
    onOpenSos: () -> Unit,
    onActivateSos: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "ROOM SENSE",
                    color = Gold,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 2.sp,
                )
                Text("Find the room. Then call.", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (maxWidth >= 720.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LocationCard(viewModel, session)
                        ActionRow(onSurvey, onMap, onResponder)
                    }
                    Spacer(Modifier.width(24.dp))
                    SosColumn(session, onOpenSos, onActivateSos)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    LocationCard(viewModel, session)
                    Spacer(Modifier.height(20.dp))
                    SosColumn(session, onOpenSos, onActivateSos)
                    Spacer(Modifier.height(16.dp))
                    ActionRow(onSurvey, onMap, onResponder)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SettingsSheet(
                settings = viewModel.settings,
                rooms = viewModel.rooms,
                onDemo = viewModel::setDemo,
                onFixed = viewModel::setFixedSsid,
                onStand = viewModel::standIn,
                onLoadDemo = { confirmDemo = true },
            )
        }
    }

    if (confirmDemo) {
        AlertDialog(
            onDismissRequest = { confirmDemo = false },
            title = { Text("Replace this building?") },
            text = {
                Text(
                    "The demo wing replaces every room and fingerprint on this phone and turns on simulated scans.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDemo = false
                    viewModel.loadDemoFloor()
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDemo = false }) { Text("Keep mine") }
            },
        )
    }
}

@Composable
private fun SosColumn(
    session: SosSnapshot,
    onOpenSos: () -> Unit,
    onActivateSos: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (session.active && session.code != null) {
            Surface(
                onClick = onOpenSos,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Text(
                    "SOS ${session.code} is live. Tap to open it.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Gold,
                )
            }
        }
        HoldToActivateButton(onActivated = onActivateSos)
        Spacer(Modifier.height(12.dp))
        Text(
            "Hold until the ring closes. You will get a code to read to 911.",
            style = MaterialTheme.typography.bodyMedium,
            color = PaperDim,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LocationCard(viewModel: HomeViewModel, session: SosSnapshot) {
    val prediction = viewModel.prediction
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estimated room", color = PaperDim, style = MaterialTheme.typography.labelLarge)
            when {
                viewModel.sampleCount == 0 -> {
                    Text("No fingerprints yet", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Survey three or four rooms so a scan can name the one you are standing in.",
                        color = PaperDim,
                    )
                }
                prediction == null -> {
                    Text("Not located yet", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${viewModel.sampleCount} fingerprints saved across ${viewModel.rooms.size} rooms.",
                        color = PaperDim,
                    )
                }
                prediction.known -> {
                    Text(prediction.roomName ?: "Unknown", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${confidencePercent(prediction.confidence)} confidence",
                        color = Moss,
                        fontWeight = FontWeight.Medium,
                    )
                }
                else -> {
                    Text("No match", style = MaterialTheme.typography.headlineSmall)
                    Text("The scan did not look like a surveyed room.", color = PaperDim)
                }
            }
            if (viewModel.scanNote.isNotBlank()) {
                Text(viewModel.scanNote, color = PaperDim, style = MaterialTheme.typography.bodyMedium)
            }
            if (session.active && session.updatedAt > 0L) {
                Text(
                    "SOS estimate ${session.roomName ?: "pending"} · ${ago(session.updatedAt)}",
                    color = Gold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = viewModel::locate, enabled = !viewModel.locating) {
                Text(if (viewModel.locating) "Listening…" else "Locate")
            }
            if (viewModel.settings.demoMode && viewModel.rooms.isNotEmpty()) {
                Text("Standing in", color = PaperDim, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.rooms.forEach { room ->
                        FilterChip(
                            selected = room.id == viewModel.settings.demoStandingRoomId,
                            onClick = { viewModel.standIn(room.id) },
                            label = { Text(room.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    onSurvey: () -> Unit,
    onMap: () -> Unit,
    onResponder: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ActionCard("Survey", "Fingerprint rooms", Icons.Outlined.Wifi, onSurvey, Modifier.weight(1f))
        ActionCard("Map", "Draw the floor", Icons.Outlined.Map, onMap, Modifier.weight(1f))
        ActionCard("Listen", "Nearby SOS", Icons.Outlined.Wifi, onResponder, Modifier.weight(1f))
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = Gold)
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = PaperDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    settings: AppSettings,
    rooms: List<RoomModel>,
    onDemo: (Boolean) -> Unit,
    onFixed: (Boolean) -> Unit,
    onStand: (String) -> Unit,
    onLoadDemo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            "CS 8803 · Mobile Computing and IoT. Room Sense SOS is an Android-only class project.",
            color = PaperDim,
        )
        SettingRow(
            title = "Demo mode",
            body = "Fake Wi-Fi scans so the whole path runs on an emulator. Survey still stores fingerprints, and the responder page is real.",
            checked = settings.demoMode,
            onChecked = onDemo,
        )
        if (settings.demoMode && rooms.isNotEmpty()) {
            Text("Standing in", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.forEach { room ->
                    FilterChip(
                        selected = room.id == settings.demoStandingRoomId,
                        onClick = { onStand(room.id) },
                        label = { Text(room.name) },
                    )
                }
            }
        }
        TextButton(onClick = onLoadDemo) { Text("Load demo wing") }
        SettingRow(
            title = "Try fixed SSID ROOMSENSE-SOS",
            body = "A normal app cannot name a local-only hotspot. This asks anyway, then shows the system SSID and password if Android refuses.",
            checked = settings.attemptFixedSsid,
            onChecked = onFixed,
        )
        Text(
            "Android allows about four Wi-Fi scans every two minutes while the app is in front. " +
                "Room Sense reuses the cached scan and says so, and the SOS loop waits 32 seconds between fresh attempts.",
            color = PaperDim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    body: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(body, color = PaperDim, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
