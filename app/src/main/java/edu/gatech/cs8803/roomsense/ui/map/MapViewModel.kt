package edu.gatech.cs8803.roomsense.ui.map

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.domain.model.RoomModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MapViewModel(private val graph: AppGraph) : ViewModel() {
    var rooms by mutableStateOf<List<RoomModel>>(emptyList())
        private set
    var floorPath by mutableStateOf(graph.settings.floorPlanPath)
        private set

    init {
        viewModelScope.launch {
            graph.repository.observeRooms().collect { rooms = it }
        }
        viewModelScope.launch {
            graph.settings.state.collect { floorPath = it.floorPlanPath }
        }
    }

    fun addRoom() {
        viewModelScope.launch {
            val count = rooms.size + 1
            graph.repository.createRoom("Room $count")
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { graph.repository.rename(id, name) }
    }

    fun commit(room: RoomModel) {
        viewModelScope.launch { graph.repository.updateGeometry(room) }
    }

    fun delete(id: String) {
        viewModelScope.launch { graph.repository.deleteRoom(id) }
    }

    fun importFloorPlan(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dest = File(graph.appContext.filesDir, "floorplan")
                graph.appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext
                graph.settings.floorPlanPath = dest.absolutePath
            }
        }
    }

    fun clearFloorPlan() {
        viewModelScope.launch(Dispatchers.IO) {
            graph.settings.floorPlanPath?.let { path -> runCatching { File(path).delete() } }
            graph.settings.floorPlanPath = null
        }
    }
}
