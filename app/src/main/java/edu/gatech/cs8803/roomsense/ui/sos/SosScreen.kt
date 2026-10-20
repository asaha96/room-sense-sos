package edu.gatech.cs8803.roomsense.ui.sos

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.gatech.cs8803.roomsense.domain.incident.WifiQr
import edu.gatech.cs8803.roomsense.service.SosSession
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.components.QrImage
import edu.gatech.cs8803.roomsense.ui.confidencePercent
import edu.gatech.cs8803.roomsense.ui.theme.CodeFamily
import edu.gatech.cs8803.roomsense.ui.theme.Gold
import edu.gatech.cs8803.roomsense.ui.theme.Moss
import edu.gatech.cs8803.roomsense.ui.theme.Paper
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import edu.gatech.cs8803.roomsense.ui.theme.Signal

private val COMMON_GATEWAYS = listOf("192.168.43.1", "192.168.49.1")

@Composable
fun SosScreen(
    session: SosSession,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onPreview: (String) -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf<String?>(null) }
    val code = state.code
    val urls = run {
        val port = state.port
        if (port == null) {
            emptyList<String>() to emptyList()
        } else {
            val detected = state.detectedHosts.map { "http://$it:$port/" }
            val guesses = COMMON_GATEWAYS
                .filter { host -> state.detectedHosts.none { it == host } }
                .map { "http://$it:$port/" }
            detected to guesses
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Incident", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (state.active) "Leave this screen up while you are on the call." else "No incident is running.",
                    color = PaperDim,
                )
            }
        }

        if (!state.active || code == null) {
            Text("Hold the SOS button on the home screen to start one.")
            return@Column
        }

        Text("Read this to 911", color = Gold, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
        Text(
            code.toCharArray().joinToString(" "),
            color = Paper,
            fontFamily = CodeFamily,
            fontSize = 48.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
        if (state.demoMode) {
            Text("Demo mode is on.", color = Gold)
        }

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Current room", color = PaperDim, style = MaterialTheme.typography.labelLarge)
                Text(state.roomName ?: "Still estimating", style = MaterialTheme.typography.headlineSmall)
                if (state.roomName != null) {
                    Text("${confidencePercent(state.confidence)} confidence", color = Moss)
                }
                Text(
                    "Updated ${ago(state.updatedAt)} · ${state.accessPointCount} access points",
                    color = PaperDim,
                )
                if (state.scanNote.isNotBlank()) Text(state.scanNote, color = PaperDim)
            }
        }

        val ssid = state.ssid
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.simulatedHotspot) "Simulated hotspot" else "Hotspot",
                    color = PaperDim,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (ssid.isNullOrBlank()) {
                    Text("Starting the hotspot…", style = MaterialTheme.typography.titleLarge)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QrImage(
                            payload = WifiQr.payload(ssid, state.password),
                            contentDescription = "QR code to join $ssid",
                        )
                        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Network", color = PaperDim)
                            Text(ssid, fontFamily = CodeFamily, fontWeight = FontWeight.Medium)
                            Text("Password", color = PaperDim)
                            Text(state.password ?: "None", fontFamily = CodeFamily, fontWeight = FontWeight.Medium)
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString("${ssid}\n${state.password.orEmpty()}"))
                                copied = "Copied the network and password."
                            }) { Text("Copy") }
                        }
                    }
                }
                if (state.hotspotNote.isNotBlank()) Text(state.hotspotNote, color = PaperDim)
                copied?.let { Text(it, color = Moss) }
            }
        }

        if (urls.first.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Responder page", color = PaperDim, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "After someone joins the hotspot, they open one of these. The page is served by this phone and does not use the internet.",
                        color = PaperDim,
                    )
                    urls.first.forEach { url ->
                        Text(url, fontFamily = CodeFamily, modifier = Modifier.fillMaxWidth())
                    }
                    if (urls.second.isNotEmpty()) {
                        Text("If none of those open, try a common hotspot gateway:", color = PaperDim)
                        urls.second.forEach { url -> Text(url, fontFamily = CodeFamily) }
                    }
                    val local = urls.first.firstOrNull { it.contains("127.0.0.1") }
                    if (local != null) {
                        Button(onClick = { onPreview(local) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Preview the page on this phone")
                        }
                    }
                }
            }
        }

        if (state.bleNote.isNotBlank()) Text(state.bleNote, color = PaperDim)
        state.error?.let { Text(it, color = Signal) }

        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text("Stop SOS")
        }
    }
}
