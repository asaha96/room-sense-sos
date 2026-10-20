package edu.gatech.cs8803.roomsense.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import edu.gatech.cs8803.roomsense.MainActivity
import edu.gatech.cs8803.roomsense.R
import edu.gatech.cs8803.roomsense.RoomSenseApplication
import edu.gatech.cs8803.roomsense.data.ble.BleBroadcaster
import edu.gatech.cs8803.roomsense.data.hotspot.HotspotController
import edu.gatech.cs8803.roomsense.data.hotspot.HotspotStartResult
import edu.gatech.cs8803.roomsense.data.server.LocalAddresses
import edu.gatech.cs8803.roomsense.data.server.ResponderHttpServer
import edu.gatech.cs8803.roomsense.data.server.ResponderSnapshot
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.domain.beacon.SosBeacon
import edu.gatech.cs8803.roomsense.domain.incident.IncidentCodeGenerator
import edu.gatech.cs8803.roomsense.domain.model.RoomModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.roundToInt

class SosForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val estimateMutex = Mutex()
    private lateinit var graph: AppGraph
    private lateinit var hotspot: HotspotController
    private lateinit var ble: BleBroadcaster
    private var server: ResponderHttpServer? = null
    private var loopJob: Job? = null
    private var running = false
    private var lastAdvert: String? = null

    @Volatile
    private var published = ResponderSnapshot()

    @Volatile
    private var latestRooms: List<RoomModel> = emptyList()

    override fun onCreate() {
        super.onCreate()
        graph = (application as RoomSenseApplication).graph
        hotspot = HotspotController(this)
        ble = BleBroadcaster(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            else -> {
                begin()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        loopJob?.cancel()
        ble.stop()
        hotspot.stop()
        server?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun begin() {
        if (running) return
        running = true
        val code = graph.session.state.value.code
            ?: graph.settings.activeIncidentCode
            ?: IncidentCodeGenerator.generate()
        graph.settings.activeIncidentCode = code
        graph.session.update {
            it.copy(
                active = true,
                code = code,
                demoMode = graph.settings.demoMode,
                detectedHosts = LocalAddresses.ipv4(),
            )
        }
        promote(buildNotification())
        loopJob = scope.launch {
            latestRooms = graph.repository.roomsOnce()
            startServer()
            republish()
            launch {
                graph.repository.observeRooms().collect { rooms ->
                    latestRooms = rooms
                    republish()
                }
            }
            launch {
                var standing = graph.settings.demoStandingRoomId
                graph.settings.state.collect { settings ->
                    val moved = settings.demoStandingRoomId != standing
                    standing = settings.demoStandingRoomId
                    if (moved && settings.demoMode) {
                        estimate()
                        advertise()
                    } else {
                        republish()
                    }
                }
            }
            estimate()
            advertise()
            beginHotspot()
            while (isActive && running) {
                delay(if (graph.settings.demoMode) DEMO_INTERVAL_MS else LIVE_INTERVAL_MS)
                estimate()
                advertise()
            }
        }
    }

    private fun startServer() {
        val http = ResponderHttpServer(this) { published }
        val port = try {
            http.start()
        } catch (error: Exception) {
            Log.e(TAG, "Responder server failed", error)
            graph.session.update { it.copy(error = error.message ?: "Responder page failed to start") }
            return
        }
        server = http
        graph.session.update {
            it.copy(port = port, detectedHosts = LocalAddresses.ipv4())
        }
        Log.i(TAG, "Responder page on port $port")
    }

    private suspend fun estimate() = estimateMutex.withLock {
        val settings = graph.settings.state.value
        val hint = if (settings.demoMode) settings.demoStandingRoomId else null
        val capture = graph.wifiSource().capture(hint)
        val samples = graph.repository.samplesOnce()
        val prediction = graph.classifier.predict(capture.readings, samples)
        graph.session.update {
            it.copy(
                roomId = prediction.roomId,
                roomName = prediction.roomName,
                confidence = prediction.confidence,
                accessPointCount = capture.readings.size,
                scanNote = capture.message,
                updatedAt = System.currentTimeMillis(),
                demoMode = settings.demoMode,
                detectedHosts = LocalAddresses.ipv4(),
            )
        }
        republish()
        val notification = buildNotification()
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun beginHotspot() {
        val demo = graph.settings.demoMode
        hotspot.start(graph.settings.attemptFixedSsid) { result ->
            if (!running) return@start
            when (result) {
                is HotspotStartResult.Running -> {
                    Log.i(TAG, "Hotspot ${result.ssid} fixed=${result.fixedSsidApplied}")
                    graph.session.update {
                        it.copy(
                            ssid = result.ssid,
                            password = result.password,
                            hotspotNote = result.note,
                            simulatedHotspot = false,
                        )
                    }
                }
                is HotspotStartResult.Failed -> {
                    Log.w(TAG, "Hotspot failed: ${result.note}")
                    if (demo) {
                        graph.session.update {
                            it.copy(
                                ssid = HotspotController.FIXED_SSID,
                                password = HotspotController.FIXED_PASSPHRASE,
                                simulatedHotspot = true,
                                hotspotNote = "Demo fallback. ${result.note} " +
                                    "The page is still served on this phone as ${HotspotController.FIXED_SSID}.",
                            )
                        }
                    } else {
                        graph.session.update {
                            it.copy(
                                hotspotNote = result.note,
                                error = result.note,
                                simulatedHotspot = false,
                            )
                        }
                    }
                }
            }
            republish()
            advertise()
        }
    }

    private fun advertise() {
        val state = graph.session.state.value
        val code = state.code ?: return
        val ssid = state.ssid.orEmpty()
        val room = state.roomName ?: "Unknown"
        val key = "$code|$ssid|$room"
        if (key == lastAdvert) return
        lastAdvert = key
        ble.start(SosBeacon(code = code, ssid = ssid, room = room)) { note ->
            graph.session.update { it.copy(bleNote = note) }
        }
    }

    private fun republish() {
        val state = graph.session.state.value
        val path = graph.settings.floorPlanPath
        val file = path?.let(::File)
        published = ResponderSnapshot(
            code = state.code.orEmpty(),
            roomId = state.roomId,
            roomName = state.roomName,
            confidence = state.confidence,
            updatedAt = state.updatedAt,
            ssid = state.ssid.orEmpty(),
            demoMode = state.demoMode,
            hasFloorPlan = file != null && file.exists() && file.length() > 0L,
            floorPlanPath = path,
            rooms = latestRooms,
        )
    }

    private fun shutdown() {
        running = false
        loopJob?.cancel()
        loopJob = null
        lastAdvert = null
        ble.stop()
        hotspot.stop()
        server?.stop()
        server = null
        graph.settings.activeIncidentCode = null
        graph.session.update { SosSnapshot() }
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun promote(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, foregroundTypes())
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            Log.e(TAG, "startForeground failed", error)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                throw error
            }
        }
    }

    private fun foregroundTypes(): Int {
        val location = granted(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val bluetooth = if (Build.VERSION.SDK_INT >= 31) {
            granted(android.Manifest.permission.BLUETOOTH_ADVERTISE) ||
                granted(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
        if (Build.VERSION.SDK_INT >= 34) {
            var types = 0
            if (location) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (bluetooth) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (types == 0) types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            return types
        }
        if (Build.VERSION.SDK_INT >= 31 && !location && bluetooth) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(): Notification {
        val state = graph.session.state.value
        val code = state.code ?: "SOS"
        val percent = "${(state.confidence * 100f).roundToInt().coerceIn(0, 100)}%"
        val where = state.roomName?.let { "$it · $percent" } ?: "Estimating the room"
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SosForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sos)
            .setContentTitle("SOS $code")
            .setContentText(where)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$where. The phone is sharing a hotspot and a room estimate."))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, "Stop SOS", stop)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "sos"
        const val ACTION_START = "edu.gatech.cs8803.roomsense.START_SOS"
        const val ACTION_STOP = "edu.gatech.cs8803.roomsense.STOP_SOS"
        private const val NOTIFICATION_ID = 42
        private const val LIVE_INTERVAL_MS = 32_000L
        private const val DEMO_INTERVAL_MS = 4_000L
        private const val TAG = "RoomSense"
    }
}
