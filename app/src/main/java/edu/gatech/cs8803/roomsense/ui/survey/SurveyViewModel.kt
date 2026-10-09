package edu.gatech.cs8803.roomsense.ui.survey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.domain.model.FingerprintSample
import edu.gatech.cs8803.roomsense.domain.model.RoomModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SurveyViewModel(private val graph: AppGraph) : ViewModel() {
    var rooms by mutableStateOf<List<RoomModel>>(emptyList())
        private set
    var samples by mutableStateOf<List<FingerprintSample>>(emptyList())
        private set
    var selectedId by mutableStateOf<String?>(null)
    var status by mutableStateOf("Pick a room, then save a fingerprint.")
        private set
    var recording by mutableStateOf(false)
        private set
    val demoMode: Boolean get() = graph.settings.demoMode

    private var recordJob: Job? = null
    private var lastReadings: Map<String, Int>? = null

    init {
        viewModelScope.launch {
            graph.repository.observeRooms().collect { latest ->
                rooms = latest
                if (selectedId == null || latest.none { it.id == selectedId }) {
                    selectedId = latest.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            graph.repository.observeSamples().collect { samples = it }
        }
    }

    fun select(id: String) {
        if (recording) return
        selectedId = id
    }

    fun addRoom(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val room = graph.repository.createRoom(trimmed)
            selectedId = room.id
            status = "Added ${room.name}. Save a few fingerprints inside it."
        }
    }

    fun clearSelected() {
        val id = selectedId ?: return
        viewModelScope.launch {
            graph.repository.clearFingerprints(id)
            status = "Cleared fingerprints for this room."
        }
    }

    fun saveOne() {
        val id = selectedId ?: run {
            status = "Add a room before scanning."
            return
        }
        viewModelScope.launch { save(id) }
    }

    fun recordSet() {
        val id = selectedId ?: run {
            status = "Add a room before scanning."
            return
        }
        if (recording) return
        recording = true
        recordJob = viewModelScope.launch {
            var saved = 0
            var attempts = 0
            while (saved < 4 && attempts < 8 && isActive) {
                attempts += 1
                status = "Saving sample ${saved + 1} of 4"
                if (save(id)) saved += 1
                if (saved >= 4) break
                if (!graph.settings.demoMode) {
                    for (second in 32 downTo 1) {
                        if (!isActive) break
                        status = "Saved $saved of 4. Next scan in ${second}s. Android limits foreground scans."
                        delay(1_000)
                    }
                }
            }
            status = "Saved $saved fingerprint${if (saved == 1) "" else "s"} for this room."
            recording = false
        }
    }

    fun cancelRecording() {
        recordJob?.cancel()
        recording = false
        status = "Recording stopped."
    }

    private suspend fun save(roomId: String): Boolean {
        val capture = graph.wifiSource().capture(roomId)
        if (capture.readings.isEmpty()) {
            status = capture.message
            return false
        }
        val duplicate = capture.readings == lastReadings ||
            samples.any { it.roomId == roomId && it.readings == capture.readings }
        if (capture.throttled && duplicate) {
            status = "${capture.message} Wait for a fresh scan before saving this cached reading again."
            return false
        }
        graph.repository.saveSample(roomId, capture.readings, capture.fromCache)
        lastReadings = capture.readings
        val cached = if (capture.fromCache) " Cached." else ""
        status = "${capture.message}$cached"
        return true
    }
}
