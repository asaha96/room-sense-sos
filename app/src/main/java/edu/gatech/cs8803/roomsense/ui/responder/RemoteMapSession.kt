package edu.gatech.cs8803.roomsense.ui.responder

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import edu.gatech.cs8803.roomsense.data.ble.GattException
import edu.gatech.cs8803.roomsense.data.ble.GattLink
import edu.gatech.cs8803.roomsense.domain.beacon.JoinInfoCodec
import edu.gatech.cs8803.roomsense.domain.beacon.MapWire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

enum class RemotePhase { Connecting, Loading, Live, Reconnecting, Failed }

data class RemoteMapState(
    val code: String,
    val phase: RemotePhase = RemotePhase.Connecting,
    val message: String = "",
    val roomId: String? = null,
    val roomName: String? = null,
    val confidence: Float = 0f,
    val updatedAt: Long = 0L,
    val demoMode: Boolean = false,
    val rooms: List<MapWire.WireRoom> = emptyList(),
    val layoutLoaded: Boolean = false,
    /** 0..1 while the room layout is downloading, else null. */
    val layoutProgress: Float? = null,
    val floor: ImageBitmap? = null,
    /** 0..1 while the floor plan is downloading, else null. */
    val floorProgress: Float? = null,
    /** Last time a STATUS read succeeded. */
    val lastHeardAt: Long = 0L,
)

/**
 * Responder side. Keeps a GATT connection to the caller, downloads the room
 * layout and the floor plan, and follows the caller's room estimate. Needs
 * no hotspot and nothing from the caller beyond its running SOS.
 */
class RemoteMapSession(
    context: Context,
    val code: String,
    initialDevice: BluetoothDevice,
    /** The newest advertiser address for [code], since it can rotate. */
    private val latestDevice: () -> BluetoothDevice?,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var lastDevice: BluetoothDevice = initialDevice
    private val _state = MutableStateFlow(RemoteMapState(code = code))
    val state: StateFlow<RemoteMapState> = _state.asStateFlow()
    private var job: Job? = null

    private var layoutVersion = -1L
    private var floorVersion = -1L
    private var floorPartialVersion = -1L
    private val floorPartial = mutableListOf<ByteArray>()

    private class Fatal(message: String) : Exception(message)

    fun start() {
        job?.cancel()
        job = scope.launch { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun run() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            fail("Nearby devices permission is needed to load the map over Bluetooth.")
            return
        }
        var failures = 0
        var everConnected = false
        while (currentCoroutineContext().isActive) {
            val device = latestDevice() ?: lastDevice
            lastDevice = device
            _state.update {
                it.copy(
                    phase = if (everConnected || failures > 0) RemotePhase.Reconnecting else RemotePhase.Connecting,
                    message = if (failures == 0) {
                        "Connecting to the caller's phone over Bluetooth…"
                    } else {
                        "Reconnecting over Bluetooth (attempt ${failures + 1})… ${it.message}".trim()
                    },
                    layoutProgress = null,
                    floorProgress = null,
                )
            }
            var link: GattLink? = null
            try {
                link = GattLink.connect(appContext, device)
                runCatching { link.requestMtu(517) }
                link.highPriority()
                link.discover()
                if (!link.has(JoinInfoCodec.SERVICE_UUID, MapWire.STATUS_UUID)) {
                    throw Fatal("The caller's app does not send the map over Bluetooth. Install the same version on both phones, or use the hotspot page.")
                }
                everConnected = true
                failures = 0
                follow(link)
            } catch (error: Fatal) {
                fail(error.message.orEmpty())
                return
            } catch (error: GattException) {
                failures += 1
                _state.update { it.copy(message = error.message.orEmpty()) }
            } finally {
                link?.close()
            }
            if (failures >= MAX_FAILURES) {
                fail("Lost the caller's phone over Bluetooth. Move closer and retry, or use the hotspot page.")
                return
            }
            delay(minOf(1_000L shl (failures - 1).coerceAtLeast(0), 8_000L))
        }
    }

    /** Runs until the link fails. Throws [GattException] or [Fatal]. */
    private suspend fun follow(link: GattLink) {
        var status = readStatus(link)
        if (status.layoutVersion != layoutVersion || !_state.value.layoutLoaded) {
            loadLayout(link, status)
            status = readStatus(link)
        }
        apply(status)
        val subscribed = runCatching {
            link.subscribe(JoinInfoCodec.SERVICE_UUID, MapWire.STATUS_UUID, MapWire.CCCD_UUID)
        }.isSuccess
        _state.update {
            it.copy(
                phase = RemotePhase.Live,
                message = if (subscribed) "" else "Live updates every few seconds.",
            )
        }
        while (currentCoroutineContext().isActive) {
            if (status.layoutVersion != layoutVersion) loadLayout(link, status)
            if (status.floorSize > 0 && status.floorVersion != floorVersion) {
                loadFloor(link, status)
            } else if (status.floorSize == 0 && floorVersion != 0L) {
                floorVersion = 0L
                _state.update { it.copy(floor = null) }
            }
            // Wait for a notification, or poll every few seconds as a safety net.
            withTimeoutOrNull(POLL_MS) {
                select<Unit> {
                    link.notifications.onReceive { }
                    link.closed.onAwait { reason -> throw GattException(reason) }
                }
            }
            if (link.closed.isCompleted) throw GattException("Bluetooth link closed.")
            status = readStatus(link)
            apply(status)
        }
    }

    private suspend fun readStatus(link: GattLink): MapWire.Status {
        val status = MapWire.decodeStatus(link.read(JoinInfoCodec.SERVICE_UUID, MapWire.STATUS_UUID))
            ?: throw GattException("The caller has not published its status yet.")
        if (!status.code.equals(code, ignoreCase = true)) {
            throw Fatal("That phone is now sending incident ${status.code}, not $code. Go back and pick it again.")
        }
        return status
    }

    private fun apply(status: MapWire.Status) {
        _state.update {
            it.copy(
                roomId = status.roomId,
                roomName = status.roomName,
                confidence = status.confidence,
                updatedAt = status.updatedAt,
                demoMode = status.demoMode,
                lastHeardAt = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun loadLayout(link: GattLink, status: MapWire.Status) {
        _state.update {
            it.copy(
                phase = if (it.layoutLoaded) it.phase else RemotePhase.Loading,
                message = if (it.layoutLoaded) it.message else "Loading the room layout…",
                layoutProgress = 0f,
            )
        }
        val parts = mutableListOf<ByteArray>()
        val bytes = fetch(link, MapWire.BLOB_LAYOUT, status.layoutVersion, status.layoutSize, parts) { progress ->
            _state.update { it.copy(layoutProgress = progress) }
        } ?: throw GattException("The room layout changed while loading. Retrying.")
        val rooms = MapWire.decodeLayout(bytes) ?: throw GattException("The room layout could not be read.")
        layoutVersion = status.layoutVersion
        _state.update { it.copy(rooms = rooms, layoutLoaded = true, layoutProgress = null) }
    }

    /** Resumable: chunks already received for this version are kept across reconnects. */
    private suspend fun loadFloor(link: GattLink, status: MapWire.Status) {
        if (floorPartialVersion != status.floorVersion) {
            floorPartial.clear()
            floorPartialVersion = status.floorVersion
        }
        _state.update { it.copy(floorProgress = progressOf(floorPartial.size, status.floorSize)) }
        val bytes = fetch(
            link,
            MapWire.BLOB_FLOOR,
            status.floorVersion,
            status.floorSize,
            floorPartial,
            onTick = {
                // Keep the highlight fresh during a long download.
                apply(readStatus(link))
            },
        ) { progress ->
            _state.update { it.copy(floorProgress = progress) }
        }
        if (bytes == null) {
            floorPartial.clear()
            _state.update { it.copy(floorProgress = null) }
            return
        }
        val image = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        floorVersion = status.floorVersion
        floorPartial.clear()
        _state.update { it.copy(floor = image, floorProgress = null) }
    }

    /**
     * Downloads [blob] chunk by chunk into [parts], resuming after the chunks
     * it already holds. Returns null if the caller replaced the blob mid-way.
     */
    private suspend fun fetch(
        link: GattLink,
        blob: Int,
        version: Long,
        size: Int,
        parts: MutableList<ByteArray>,
        onTick: suspend () -> Unit = {},
        onProgress: (Float) -> Unit,
    ): ByteArray? {
        val count = MapWire.chunkCount(size)
        for (index in parts.size until count) {
            link.write(JoinInfoCodec.SERVICE_UUID, MapWire.BLOB_SELECT_UUID, MapWire.encodeSelect(blob, index))
            val chunk = MapWire.unframe(link.read(JoinInfoCodec.SERVICE_UUID, MapWire.BLOB_DATA_UUID))
                ?: throw GattException("A map chunk was damaged.")
            if (chunk.blob != blob || chunk.index != index) throw GattException("Map chunks arrived out of order.")
            if (chunk.version != version) return null
            parts += chunk.payload
            onProgress(progressOf(parts.size, size))
            if (index % 12 == 11) onTick()
        }
        val out = ByteArrayOutputStream(size)
        parts.forEach(out::write)
        val bytes = out.toByteArray()
        if (bytes.size != size || MapWire.version(bytes) != version) {
            parts.clear()
            return null
        }
        return bytes
    }

    private fun progressOf(chunks: Int, size: Int): Float {
        val count = MapWire.chunkCount(size)
        return if (count == 0) 1f else chunks.toFloat() / count
    }

    private fun fail(message: String) {
        _state.update { it.copy(phase = RemotePhase.Failed, message = message, layoutProgress = null, floorProgress = null) }
    }

    private companion object {
        const val MAX_FAILURES = 6
        const val POLL_MS = 5_000L
    }
}
