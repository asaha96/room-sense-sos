package edu.gatech.cs8803.roomsense.ui.responder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.gatech.cs8803.roomsense.domain.beacon.MapWire
import edu.gatech.cs8803.roomsense.ui.ago
import edu.gatech.cs8803.roomsense.ui.confidencePercent
import edu.gatech.cs8803.roomsense.ui.theme.CodeFamily
import edu.gatech.cs8803.roomsense.ui.theme.Gold
import edu.gatech.cs8803.roomsense.ui.theme.Ink
import edu.gatech.cs8803.roomsense.ui.theme.Line
import edu.gatech.cs8803.roomsense.ui.theme.Moss
import edu.gatech.cs8803.roomsense.ui.theme.Paper
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import edu.gatech.cs8803.roomsense.ui.theme.Signal
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The caller's map, received over Bluetooth. */
@Composable
fun RemoteMapScreen(
    session: RemoteMapSession,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUseHotspot: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
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
                Text("Caller's map", style = MaterialTheme.typography.headlineMedium)
                Text("Over Bluetooth · no hotspot needed", color = PaperDim)
            }
        }

        Text(
            state.code.toCharArray().joinToString(" "),
            fontFamily = CodeFamily,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            color = Gold,
        )

        ConnectionBanner(state, now, onRetry, onUseHotspot)

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Caller is in", color = PaperDim, style = MaterialTheme.typography.labelLarge)
                Text(
                    state.roomName ?: if (state.lastHeardAt == 0L) "Waiting for the caller…" else "Still estimating",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (state.roomName != null) {
                    Text("${confidencePercent(state.confidence)} confidence", color = Moss)
                }
                if (state.updatedAt > 0L) {
                    Text("Estimate updated ${ago(state.updatedAt, now)}", color = PaperDim)
                }
                if (state.demoMode) Text("Demo mode is on. Scans are simulated.", color = Gold)
            }
        }

        if (state.layoutLoaded) {
            RemoteFloor(state.rooms, state.roomId, state)
            if (state.rooms.isEmpty()) {
                Text("The caller's phone has no rooms on its map.", color = PaperDim)
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .border(1.dp, Line, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.phase != RemotePhase.Failed) CircularProgressIndicator()
                    Text(
                        if (state.layoutProgress != null) "Loading rooms ${percent(state.layoutProgress)}" else "Map not loaded yet",
                        color = PaperDim,
                    )
                }
            }
        }

        state.floorProgress?.let { progress ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Loading floor plan ${percent(progress)}. Rooms are already live.", color = PaperDim)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }

        OutlinedButton(onClick = onUseHotspot, modifier = Modifier.fillMaxWidth()) {
            Text("Open the hotspot page instead")
        }
    }
}

@Composable
private fun ConnectionBanner(
    state: RemoteMapState,
    now: Long,
    onRetry: () -> Unit,
    onUseHotspot: () -> Unit,
) {
    val stale = state.phase == RemotePhase.Live && state.lastHeardAt > 0L && now - state.lastHeardAt > 15_000
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.phase) {
                RemotePhase.Live -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(if (stale) Gold else Moss, RoundedCornerShape(5.dp)))
                    Text(
                        if (stale) {
                            "No update for ${(now - state.lastHeardAt) / 1000}s. Checking the link…"
                        } else {
                            "Live · heard ${ago(state.lastHeardAt, now)}"
                        },
                        color = if (stale) Gold else Moss,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                RemotePhase.Failed -> {
                    Text(state.message, color = Signal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Retry") }
                        OutlinedButton(onClick = onUseHotspot, modifier = Modifier.weight(1f)) { Text("Use hotspot") }
                    }
                    if (state.layoutLoaded) {
                        Text("The map below is the last one received and may be out of date.", color = PaperDim)
                    }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        state.message.ifBlank { "Connecting…" },
                        color = if (state.phase == RemotePhase.Reconnecting) Gold else PaperDim,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            if (state.phase == RemotePhase.Live && state.message.isNotBlank()) {
                Text(state.message, color = PaperDim)
            }
        }
    }
}

@Composable
private fun RemoteFloor(rooms: List<MapWire.WireRoom>, hereId: String?, state: RemoteMapState) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val floor = state.floor
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .onSizeChanged { canvasSize = it },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (floor != null) {
                drawImage(floor, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            } else {
                drawRect(Ink)
                val stepX = size.width / 8f
                val stepY = size.height / 6f
                for (index in 1 until 8) {
                    drawLine(Line, Offset(stepX * index, 0f), Offset(stepX * index, size.height), strokeWidth = 1f)
                }
                for (index in 1 until 6) {
                    drawLine(Line, Offset(0f, stepY * index), Offset(size.width, stepY * index), strokeWidth = 1f)
                }
            }
            rooms.forEach { room ->
                val here = room.id == hereId
                val topLeft = Offset(room.x * size.width, room.y * size.height)
                val box = Size(room.width * size.width, room.height * size.height)
                val color = Color(room.color)
                drawRoundRect(
                    color = color.copy(alpha = if (here) 0.9f else 0.4f),
                    topLeft = topLeft,
                    size = box,
                    cornerRadius = CornerRadius(18f, 18f),
                )
                drawRoundRect(
                    color = if (here) Signal else color,
                    topLeft = topLeft,
                    size = box,
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Stroke(width = if (here) 9f else 2f),
                )
            }
        }
        rooms.forEach { room ->
            if (canvasSize.width == 0) return@forEach
            val here = room.id == hereId
            Text(
                if (here) "● ${room.name} · here" else room.name,
                color = Paper,
                fontWeight = if (here) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (room.x * canvasSize.width).roundToInt() + 10,
                            (room.y * canvasSize.height).roundToInt() + 10,
                        )
                    }
                    .background(if (here) Signal else Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private fun percent(progress: Float?): String = "${((progress ?: 0f) * 100).roundToInt()}%"
