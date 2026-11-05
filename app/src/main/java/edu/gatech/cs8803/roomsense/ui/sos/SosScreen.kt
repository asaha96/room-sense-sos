package edu.gatech.cs8803.roomsense.ui.sos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.gatech.cs8803.roomsense.domain.incident.WifiQr
import edu.gatech.cs8803.roomsense.service.SosSession
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.components.QrImage
import edu.gatech.cs8803.roomsense.ui.components.ScreenHeader
import edu.gatech.cs8803.roomsense.ui.confidencePercent
import edu.gatech.cs8803.roomsense.ui.theme.Action
import edu.gatech.cs8803.roomsense.ui.theme.ActionDanger
import edu.gatech.cs8803.roomsense.ui.theme.InkHigh
import edu.gatech.cs8803.roomsense.ui.theme.Moss
import edu.gatech.cs8803.roomsense.ui.theme.Paper
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import edu.gatech.cs8803.roomsense.ui.theme.RoomButtonShape
import edu.gatech.cs8803.roomsense.ui.theme.Signal
import edu.gatech.cs8803.roomsense.ui.theme.roomButtonHeight

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
    val port = state.port
    val mapUrls = if (port == null || state.simulatedHotspot) emptyList() else
        state.hotspotHosts.map { "http://$it:$port/" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Incident",
            onBack = onBack,
            subtitle = if (state.active) {
                "Leave this screen up while you are on the call."
            } else {
                "No incident is running."
            },
        )

        if (!state.active || code == null) {
            Text("Hold the SOS button on the home screen to start one.")
            return@Column
        }

        Text("READ THIS TO 911", color = Action, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
        Text(
            code.toCharArray().joinToString(" "),
            color = Paper,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        if (state.demoMode) {
            Text("Demo mode is on.", color = Action)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Current room", color = PaperDim, style = MaterialTheme.typography.labelLarge)
                Text(
                    state.roomName ?: "Still estimating",
                    style = MaterialTheme.typography.titleLarge,
                    minLines = 1,
                    maxLines = 2,
                )
                Text(
                    if (state.roomName != null) {
                        "${confidencePercent(state.confidence)} confidence"
                    } else {
                        "Waiting for a match"
                    },
                    color = if (state.roomName != null) Moss else PaperDim,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Updated ${ago(state.updatedAt)} · ${state.accessPointCount} access points",
                    color = PaperDim,
                )
                Text(
                    state.scanNote.ifBlank { " " },
                    color = PaperDim,
                    minLines = 1,
                )
            }
        }

        val ssid = state.ssid
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.simulatedHotspot) "1. Join hotspot (simulated)" else "1. Join hotspot",
                    color = PaperDim,
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (ssid.isNullOrBlank()) {
                        QrPlaceholder("Starting the hotspot")
                    } else {
                        QrImage(
                            payload = WifiQr.payload(ssid, state.password),
                            contentDescription = "QR code to join $ssid",
                        )
                    }
                    Column(
                        Modifier
                            .padding(start = 16.dp)
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Network", color = PaperDim)
                        Text(
                            ssid ?: "Starting the hotspot…",
                            fontWeight = FontWeight.SemiBold,
                            minLines = 2,
                            maxLines = 2,
                        )
                        Text("Password", color = PaperDim)
                        Text(
                            if (ssid.isNullOrBlank()) "Android assigns this" else state.password ?: "None",
                            color = if (ssid.isNullOrBlank()) PaperDim else Paper,
                            fontWeight = if (ssid.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold,
                            minLines = 2,
                            maxLines = 2,
                        )
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString("${ssid.orEmpty()}\n${state.password.orEmpty()}"))
                                copied = "Copied the network and password."
                            },
                            enabled = !ssid.isNullOrBlank(),
                        ) { Text("Copy") }
                    }
                }
                if (state.hotspotNote.isNotBlank()) Text(state.hotspotNote, color = PaperDim)
                copied?.let { Text(it, color = Moss) }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Open map", color = PaperDim, style = MaterialTheme.typography.labelLarge)
                Text(
                    "On the responder phone, join the hotspot above first, then scan this QR code to open the map. Stay connected even if Android says there is no internet.",
                    color = PaperDim,
                )
                if (mapUrls.isEmpty()) {
                    QrPlaceholder(
                        when {
                            state.simulatedHotspot -> "Preview only"
                            port == null -> "Starting the page"
                            else -> "Waiting for the address"
                        },
                    )
                } else {
                    mapUrls.forEach { url ->
                        QrImage(payload = url, contentDescription = "Open map at $url")
                    }
                }
                Text(
                    when {
                        state.simulatedHotspot -> "A simulated hotspot cannot connect another phone. Use the preview below."
                        port == null -> "Starting the responder page…"
                        mapUrls.isEmpty() -> "Waiting for the hotspot address…"
                        else -> mapUrls.joinToString("\n")
                    },
                    fontWeight = if (mapUrls.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (mapUrls.isNotEmpty()) Paper else PaperDim,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Or type the URL above into the responder phone’s browser. Check that the incident code matches $code. The map keeps updating without internet.",
                    color = PaperDim,
                    minLines = 2,
                )
                Button(
                    onClick = { port?.let { onPreview("http://127.0.0.1:$it/") } },
                    enabled = port != null,
                    modifier = Modifier.fillMaxWidth().roomButtonHeight(),
                    shape = RoomButtonShape,
                ) {
                    Text(
                        if (port == null) "Starting the preview…" else "Preview the page on this phone",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (state.bleNote.isNotBlank()) Text(state.bleNote, color = PaperDim)
        state.error?.let { Text(it, color = Signal) }

        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().roomButtonHeight(),
            shape = RoomButtonShape,
            border = BorderStroke(1.dp, ActionDanger),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionDanger),
        ) {
            Text("Stop SOS")
        }
    }
}

@Composable
private fun QrPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .size(112.dp)
            .background(InkHigh, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = PaperDim,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
    }
}
