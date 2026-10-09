package edu.gatech.cs8803.roomsense.ui.responder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import edu.gatech.cs8803.roomsense.data.ble.BleIncidentScanner
import edu.gatech.cs8803.roomsense.data.ble.NearbyIncident
import edu.gatech.cs8803.roomsense.di.AppGraph
import kotlinx.coroutines.flow.StateFlow

class ResponderViewModel(graph: AppGraph) : ViewModel() {
    private val scanner = BleIncidentScanner(graph.appContext)
    val incidents: StateFlow<List<NearbyIncident>> = scanner.incidents
    var status by mutableStateOf("Not listening.")
        private set
    var listening by mutableStateOf(false)
        private set

    fun toggle() {
        if (listening) {
            scanner.stop()
            listening = false
            status = "Stopped."
        } else {
            status = scanner.start()
            listening = status.startsWith("Listening")
        }
    }

    fun prune() = scanner.prune()

    override fun onCleared() {
        scanner.stop()
    }
}
