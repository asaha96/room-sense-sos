package edu.gatech.cs8803.roomsense.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.domain.demo.DemoRadio
import edu.gatech.cs8803.roomsense.domain.model.RoomModel
import edu.gatech.cs8803.roomsense.domain.model.RoomPrediction
import edu.gatech.cs8803.roomsense.service.SosSnapshot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val graph: AppGraph) : ViewModel() {
    var rooms by mutableStateOf<List<RoomModel>>(emptyList())
        private set
    var sampleCount by mutableStateOf(0)
        private set
    var settings by mutableStateOf(graph.settings.state.value)
        private set
    var prediction by mutableStateOf<RoomPrediction?>(null)
        private set
    var scanNote by mutableStateOf("")
        private set
    var locating by mutableStateOf(false)
        private set

    val session: StateFlow<SosSnapshot> = graph.session.state

    init {
        viewModelScope.launch {
            graph.repository.observeRooms().collect { rooms = it }
        }
        viewModelScope.launch {
            graph.repository.observeSamples().collect { sampleCount = it.size }
        }
        viewModelScope.launch {
            graph.settings.state.collect { settings = it }
        }
    }

    fun locate() {
        if (locating) return
        viewModelScope.launch {
            locating = true
            val capture = graph.wifiSource().capture(settings.demoStandingRoomId)
            val samples = graph.repository.samplesOnce()
            prediction = graph.classifier.predict(capture.readings, samples)
            scanNote = capture.message
            locating = false
        }
    }

    fun setDemo(enabled: Boolean) {
        graph.settings.demoMode = enabled
        if (enabled && graph.settings.demoStandingRoomId == null) {
            graph.settings.demoStandingRoomId = rooms.firstOrNull()?.id ?: DemoRadio.rooms.first().id
        }
    }

    fun setFixedSsid(enabled: Boolean) {
        graph.settings.attemptFixedSsid = enabled
    }

    fun standIn(roomId: String) {
        graph.settings.demoStandingRoomId = roomId
    }

    fun loadDemoFloor() {
        viewModelScope.launch {
            graph.settings.demoMode = true
            graph.repository.replaceWithDemoFloor()
            graph.settings.demoStandingRoomId = DemoRadio.rooms.first().id
        }
    }
}
