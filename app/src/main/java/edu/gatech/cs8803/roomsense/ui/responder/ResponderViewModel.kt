package edu.gatech.cs8803.roomsense.ui.responder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cs8803.roomsense.data.ble.BleIncidentScanner
import edu.gatech.cs8803.roomsense.data.ble.JoinInfoGattClient
import edu.gatech.cs8803.roomsense.data.ble.NearbyIncident
import edu.gatech.cs8803.roomsense.data.link.FoundServer
import edu.gatech.cs8803.roomsense.data.link.HotspotLink
import edu.gatech.cs8803.roomsense.data.link.JoinResult
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.domain.beacon.JoinInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MapPhase { Idle, Checking, Reading, Joining, Loading, Manual, Ready, Failed }

/**
 * State of the "View map" flow for one incident.
 *
 * [MapPhase.Manual] means automatic joining did not work. The screen shows
 * Wi-Fi settings and QR options while the view model keeps checking, and the
 * map opens as soon as this phone reaches the caller's server.
 */
data class MapLinkState(
    val code: String? = null,
    val phase: MapPhase = MapPhase.Idle,
    val message: String = "",
    val ssid: String = "",
    val password: String? = null,
    val url: String? = null,
) {
    val busy: Boolean
        get() = phase == MapPhase.Checking || phase == MapPhase.Reading ||
            phase == MapPhase.Joining || phase == MapPhase.Loading
}

class ResponderViewModel(graph: AppGraph) : ViewModel() {
    private val scanner = BleIncidentScanner(graph.appContext)
    private val gatt = JoinInfoGattClient(graph.appContext)
    private val link = HotspotLink(graph.appContext)
    private val joinInfo = mutableMapOf<String, JoinInfo>()
    private var mapJob: Job? = null
    private var lastIncident: NearbyIncident? = null

    val incidents: StateFlow<List<NearbyIncident>> = scanner.incidents
    var status by mutableStateOf("Not listening.")
        private set
    var listening by mutableStateOf(false)
        private set
    var mapLink by mutableStateOf(MapLinkState())
        private set

    /** Bluetooth map for the incident the responder opened, if any. */
    var remote by mutableStateOf<RemoteMapSession?>(null)
        private set
    private var remoteIncident: NearbyIncident? = null
    private val appContext = graph.appContext

    fun toggle() {
        if (listening) {
            scanner.stop()
            listening = false
            status = "Stopped."
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (listening) return
        status = scanner.start()
        listening = status.startsWith("Listening")
    }

    fun prune() = scanner.prune()

    fun viewMap(incident: NearbyIncident) {
        mapJob?.cancel()
        link.release()
        lastIncident = incident
        val code = incident.code
        mapJob = viewModelScope.launch {
            var info = joinInfo[code]
            set(code, MapPhase.Checking, "Checking whether this phone is already on the caller's hotspot…", incident.ssid, info)
            link.findOnWifi(code, info)?.let { return@launch ready(code, it) }

            if (info == null) {
                val device = incident.device
                    ?: return@launch manual(code, incident.ssid, null, "This beacon has no Bluetooth address to read hotspot details from.")
                set(code, MapPhase.Reading, "Reading hotspot details from the caller's phone over Bluetooth…", incident.ssid, null)
                when (val read = gatt.read(device)) {
                    is JoinInfoGattClient.Result.Failed -> return@launch manual(code, incident.ssid, null, read.reason)
                    is JoinInfoGattClient.Result.Ok -> {
                        if (!read.info.code.equals(code, ignoreCase = true)) {
                            return@launch manual(
                                code,
                                incident.ssid,
                                null,
                                "That phone is now sending incident ${read.info.code}, not $code. Listen again.",
                            )
                        }
                        info = read.info
                        joinInfo[code] = read.info
                    }
                }
            }
            val details = info ?: return@launch
            // The beacon may have been heard before this phone joined by itself.
            link.findOnWifi(code, details)?.let { return@launch ready(code, it) }

            set(code, MapPhase.Joining, "Joining ${details.ssid}. If Android asks, tap Connect.", details.ssid, details)
            when (val joined = link.join(details)) {
                is JoinResult.Failed -> manual(code, details.ssid, details.password, joined.reason)
                is JoinResult.Joined -> {
                    set(code, MapPhase.Loading, "Connected to ${details.ssid}. Loading the map…", details.ssid, details)
                    val found = link.probeUntil(joined.network, code, details, timeoutMs = 15_000)
                    if (found != null) {
                        ready(code, found)
                    } else {
                        manual(
                            code,
                            details.ssid,
                            details.password,
                            "Joined ${details.ssid}, but no map server answered with incident $code.",
                        )
                    }
                }
            }
        }
    }

    /**
     * Opens the caller's map over Bluetooth. Returns false when the beacon has
     * no device to connect to, so the caller can use the hotspot flow instead.
     */
    fun openRemote(incident: NearbyIncident): Boolean {
        val device = incident.device ?: return false
        closeMap()
        remote?.stop()
        remoteIncident = incident
        val code = incident.code
        remote = RemoteMapSession(
            context = appContext,
            code = code,
            initialDevice = device,
            latestDevice = { scanner.incidents.value.firstOrNull { it.code == code }?.device },
            scope = viewModelScope,
        ).also { it.start() }
        // Keep scanning so a rotated Bluetooth address can still be found.
        startListening()
        return true
    }

    fun retryRemote() {
        remote?.start()
    }

    fun closeRemote() {
        remote?.stop()
        remote = null
    }

    /** Fallback from the Bluetooth map to the hotspot web page. */
    fun useHotspotInstead() {
        val incident = remoteIncident ?: return
        closeRemote()
        viewMap(incident)
    }

    /** Called by the map screen when the page or the hotspot fails. */
    fun mapFailed(reason: String) {
        val current = mapLink
        if (current.phase != MapPhase.Ready) return
        mapLink = current.copy(phase = MapPhase.Failed, message = reason)
    }

    fun retryMap() {
        lastIncident?.let(::viewMap)
    }

    /** Leaving the map or cancelling: unbind from the hotspot and reset. */
    fun closeMap() {
        mapJob?.cancel()
        mapJob = null
        link.release()
        mapLink = MapLinkState()
    }

    private fun set(code: String, phase: MapPhase, message: String, ssid: String, info: JoinInfo?) {
        mapLink = MapLinkState(
            code = code,
            phase = phase,
            message = message,
            ssid = info?.ssid ?: ssid,
            password = info?.password,
        )
    }

    private fun ready(code: String, found: FoundServer) {
        link.bind(found.network) {
            viewModelScope.launch { mapFailed("Lost the connection to the caller's hotspot.") }
        }
        mapLink = mapLink.copy(code = code, phase = MapPhase.Ready, message = "", url = found.baseUrl)
    }

    /**
     * Shows manual options and keeps looking, so joining from Wi-Fi settings
     * or by scanning the caller's Wi-Fi QR code still ends on the map.
     */
    private suspend fun manual(code: String, ssid: String, password: String?, reason: String) {
        mapLink = MapLinkState(code = code, phase = MapPhase.Manual, message = reason, ssid = ssid, password = password)
        val deadline = System.currentTimeMillis() + MANUAL_WATCH_MS
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
            delay(3_000)
            if (mapLink.code != code || mapLink.phase != MapPhase.Manual) return
            link.findOnWifi(code, joinInfo[code])?.let { return ready(code, it) }
        }
    }

    override fun onCleared() {
        remote?.stop()
        scanner.stop()
        link.release()
    }

    private companion object {
        const val MANUAL_WATCH_MS = 10 * 60_000L
    }
}
