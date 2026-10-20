package edu.gatech.cs8803.roomsense.ui.map

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import edu.gatech.cs8803.roomsense.domain.model.RoomModel
import edu.gatech.cs8803.roomsense.ui.theme.Gold
import edu.gatech.cs8803.roomsense.ui.theme.Ink
import edu.gatech.cs8803.roomsense.ui.theme.Line
import edu.gatech.cs8803.roomsense.ui.theme.Paper
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { MOVE, RESIZE }

@Composable
fun MapEditorScreen(
    viewModel: MapViewModel,
    onBack: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importFloorPlan(uri)
    }
    val floor by produceState<ImageBitmap?>(null, viewModel.floorPath) {
        value = withContext(Dispatchers.IO) { decodeFloor(viewModel.floorPath) }
    }
    LaunchedEffect(selectedId, viewModel.rooms) {
        nameDraft = viewModel.rooms.find { it.id == selectedId }?.name.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Floor", style = MaterialTheme.typography.headlineMedium)
                Text("Drag a room to move it. Drag the corner to resize.", color = PaperDim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::addRoom) { Text("Add room") }
            OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Import plan") }
            if (viewModel.floorPath != null) {
                TextButton(onClick = viewModel::clearFloorPlan) { Text("Clear plan") }
            }
        }
        FloorCanvas(
            rooms = viewModel.rooms,
            selectedId = selectedId,
            floor = floor,
            onSelect = { selectedId = it },
            onCommit = viewModel::commit,
        )
        val selected = viewModel.rooms.find { it.id == selectedId }
        if (selected == null) {
            Text(
                if (viewModel.rooms.isEmpty()) {
                    "No rooms on the map yet. Add one, or import a floor-plan image and draw over it."
                } else {
                    "Tap a room to rename or delete it."
                },
                color = PaperDim,
            )
        } else {
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text("Room name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.rename(selected.id, nameDraft) }) { Text("Rename") }
                TextButton(onClick = {
                    viewModel.delete(selected.id)
                    selectedId = null
                }) { Text("Delete") }
            }
        }
        viewModel.rooms.forEach { room ->
            Surface(
                onClick = { selectedId = room.id },
                shape = MaterialTheme.shapes.medium,
                color = if (room.id == selectedId) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    room.name,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FloorCanvas(
    rooms: List<RoomModel>,
    selectedId: String?,
    floor: ImageBitmap?,
    onSelect: (String?) -> Unit,
    onCommit: (RoomModel) -> Unit,
) {
    val draftState = remember { mutableStateOf(rooms) }
    val dragging = remember { mutableStateOf(false) }
    LaunchedEffect(rooms) {
        if (!dragging.value) draftState.value = rooms
    }
    val shown by draftState
    val selectRef = rememberUpdatedState(onSelect)
    val commitRef = rememberUpdatedState(onCommit)
    val selectedRef = rememberUpdatedState(selectedId)
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .onSizeChanged { canvasSize = it },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val handle = with(density) { 28.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        if (width <= 0f || height <= 0f) return@awaitEachGesture
                        val current = draftState.value
                        val selected = current.find { it.id == selectedRef.value }
                        val nearHandle = selected != null && nearCorner(selected, down.position, width, height, handle)
                        val hit = if (nearHandle) {
                            selected
                        } else {
                            pick(current, down.position, width, height)
                        }
                        val mode = if (nearHandle) DragMode.RESIZE else DragMode.MOVE
                        selectRef.value(hit?.id)
                        var moved = false
                        var target = hit
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - change.previousPosition
                            if (target != null && (abs(delta.x) > 0.5f || abs(delta.y) > 0.5f)) {
                                moved = true
                                dragging.value = true
                                val dx = delta.x / width
                                val dy = delta.y / height
                                val id = target.id
                                draftState.value = draftState.value.map { room ->
                                    if (room.id != id) {
                                        room
                                    } else if (mode == DragMode.RESIZE) {
                                        room.copy(
                                            width = (room.width + dx).coerceIn(0.1f, 1f - room.x),
                                            height = (room.height + dy).coerceIn(0.1f, 1f - room.y),
                                        )
                                    } else {
                                        room.copy(
                                            x = (room.x + dx).coerceIn(0f, 1f - room.width),
                                            y = (room.y + dy).coerceIn(0f, 1f - room.height),
                                        )
                                    }
                                }
                                target = draftState.value.find { it.id == id }
                                change.consume()
                            }
                        }
                        dragging.value = false
                        if (moved && target != null) commitRef.value(target)
                    }
                },
        ) {
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
            shown.forEach { room ->
                val left = room.x * size.width
                val top = room.y * size.height
                val w = room.width * size.width
                val h = room.height * size.height
                val color = Color(room.color)
                drawRoundRect(
                    color = color.copy(alpha = if (room.id == selectedId) 0.78f else 0.5f),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                )
                drawRoundRect(
                    color = if (room.id == selectedId) Gold else color,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                    style = Stroke(
                        width = if (room.id == selectedId) 4f else 2f,
                        pathEffect = if (room.id == selectedId) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
                if (room.id == selectedId) {
                    drawRect(
                        color = Paper,
                        topLeft = Offset(left + w - 14f, top + h - 14f),
                        size = androidx.compose.ui.geometry.Size(18f, 18f),
                    )
                }
            }
        }
        shown.forEach { room ->
            if (canvasSize.width == 0) return@forEach
            Text(
                room.name,
                color = Paper,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (room.x * canvasSize.width).roundToInt() + 10,
                            (room.y * canvasSize.height).roundToInt() + 10,
                        )
                    }
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private fun pick(rooms: List<RoomModel>, position: Offset, width: Float, height: Float): RoomModel? {
    val nx = position.x / width
    val ny = position.y / height
    return rooms.lastOrNull { room ->
        nx in room.x..(room.x + room.width) && ny in room.y..(room.y + room.height)
    }
}

private fun nearCorner(room: RoomModel, position: Offset, width: Float, height: Float, handle: Float): Boolean {
    val cx = (room.x + room.width) * width
    val cy = (room.y + room.height) * height
    return abs(position.x - cx) <= handle && abs(position.y - cy) <= handle
}

private fun decodeFloor(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}
