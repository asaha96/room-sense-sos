package edu.gatech.cs8803.roomsense.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import edu.gatech.cs8803.roomsense.domain.beacon.JoinInfo
import edu.gatech.cs8803.roomsense.domain.beacon.JoinInfoCodec
import edu.gatech.cs8803.roomsense.domain.beacon.MapWire
import java.util.concurrent.ConcurrentHashMap

/**
 * Caller side GATT server. Serves the live room status, the room layout, the
 * compressed floor plan, and the hotspot details (fallback only) to any
 * responder that connects. Nothing here needs pairing, so the caller never
 * has to confirm a connection after starting the SOS.
 *
 * [start] checks BLUETOOTH_CONNECT first, and each callback catches
 * SecurityException in case the permission is revoked while running.
 */
@SuppressLint("MissingPermission")
class SosGattServer(
    private val context: Context,
    /** Called on connect and disconnect, so the caller can restart advertising. */
    private val onCentralChanged: (connected: Int) -> Unit = {},
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private var server: BluetoothGattServer? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile private var join: ByteArray = ByteArray(0)
    @Volatile private var status: ByteArray = ByteArray(0)
    @Volatile private var layout: ByteArray = ByteArray(0)
    @Volatile private var layoutVersion: Long = 0L
    @Volatile private var floor: ByteArray = ByteArray(0)
    @Volatile private var floorVersion: Long = 0L

    private val connected = ConcurrentHashMap.newKeySet<BluetoothDevice>()
    private val subscribed = ConcurrentHashMap.newKeySet<BluetoothDevice>()
    private val selection = ConcurrentHashMap<String, Pair<Int, Int>>()
    private var notifySeq = 0

    private var stateReceiver: BroadcastReceiver? = null

    /** Reopens the server when Bluetooth is switched off and on during an SOS. */
    private fun watchAdapter() {
        if (stateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> closeServer()
                    BluetoothAdapter.STATE_ON -> if (start() == null) onCentralChanged(connected.size)
                }
            }
        }
        stateReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Returns a note for logs, or null when the server is up. */
    fun start(): String? {
        watchAdapter()
        if (server != null) return null
        if (Build.VERSION.SDK_INT >= 31 && !granted(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            return "Nearby devices permission is missing, so responders cannot load the map over Bluetooth."
        }
        val adapter = manager?.adapter ?: return "This device has no Bluetooth radio."
        if (!adapter.isEnabled) return "Bluetooth is off, so responders cannot load the map over Bluetooth."
        return try {
            val opened = manager.openGattServer(context, callback)
                ?: return "Could not open the Bluetooth map service."
            val service = BluetoothGattService(JoinInfoCodec.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    JoinInfoCodec.CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                ),
            )
            val statusChar = BluetoothGattCharacteristic(
                MapWire.STATUS_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).apply {
                addDescriptor(
                    BluetoothGattDescriptor(
                        MapWire.CCCD_UUID,
                        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                    ),
                )
            }
            service.addCharacteristic(statusChar)
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    MapWire.BLOB_SELECT_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE,
                ),
            )
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    MapWire.BLOB_DATA_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                ),
            )
            opened.addService(service)
            statusCharacteristic = statusChar
            server = opened
            null
        } catch (error: SecurityException) {
            "Bluetooth map service was denied. ${error.message.orEmpty()}".trim()
        }
    }

    fun updateJoin(info: JoinInfo) {
        join = JoinInfoCodec.encode(info)
    }

    fun updateLayout(rooms: List<MapWire.WireRoom>) {
        val bytes = MapWire.encodeLayout(rooms)
        layout = bytes
        layoutVersion = MapWire.version(bytes)
    }

    fun updateFloor(bytes: ByteArray) {
        floor = bytes
        floorVersion = MapWire.version(bytes)
    }

    fun layoutInfo(): Pair<Long, Int> = layoutVersion to layout.size
    fun floorInfo(): Pair<Long, Int> = floorVersion to floor.size

    /** Stores the new status and pings subscribed responders when it changed. */
    fun updateStatus(next: MapWire.Status) {
        val bytes = MapWire.encodeStatus(next)
        if (bytes.contentEquals(status)) return
        status = bytes
        notifySubscribers()
    }

    fun stop() {
        stateReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        stateReceiver = null
        closeServer()
        status = ByteArray(0)
    }

    private fun closeServer() {
        val active = server ?: return
        runCatching { active.clearServices() }
        runCatching { active.close() }
        server = null
        statusCharacteristic = null
        connected.clear()
        subscribed.clear()
        selection.clear()
    }

    private fun notifySubscribers() {
        val active = server ?: return
        val characteristic = statusCharacteristic ?: return
        if (subscribed.isEmpty()) return
        notifySeq = (notifySeq + 1) and 0xFFFF
        val ping = byteArrayOf((notifySeq shr 8).toByte(), notifySeq.toByte())
        subscribed.forEach { device ->
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    active.notifyCharacteristicChanged(device, characteristic, false, ping)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = ping
                    @Suppress("DEPRECATION")
                    active.notifyCharacteristicChanged(device, characteristic, false)
                }
            } catch (_: Exception) {
                // The responder also polls STATUS, so a missed ping only delays it.
            }
        }
    }

    private fun valueFor(device: BluetoothDevice, uuid: java.util.UUID): ByteArray? = when (uuid) {
        JoinInfoCodec.CHARACTERISTIC_UUID -> join
        MapWire.STATUS_UUID -> status
        MapWire.BLOB_DATA_UUID -> {
            val (blob, index) = selection[device.address] ?: (MapWire.BLOB_LAYOUT to 0)
            when (blob) {
                MapWire.BLOB_LAYOUT -> MapWire.frame(blob, layoutVersion, index, layout)
                MapWire.BLOB_FLOOR -> MapWire.frame(blob, floorVersion, index, floor)
                else -> null
            }
        }
        else -> null
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected += device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected -= device
                subscribed -= device
                selection.remove(device.address)
            }
            onCentralChanged(connected.size)
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val active = server ?: return
            try {
                val bytes = valueFor(device, characteristic.uuid)
                when {
                    bytes == null ->
                        active.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                    offset > bytes.size ->
                        active.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                    else ->
                        active.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes.copyOfRange(offset, bytes.size))
                }
            } catch (_: SecurityException) {
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val active = server ?: return
            val ok = characteristic.uuid == MapWire.BLOB_SELECT_UUID && !preparedWrite && offset == 0
            val select = if (ok) MapWire.decodeSelect(value) else null
            if (select != null) selection[device.address] = select
            try {
                if (responseNeeded) {
                    active.sendResponse(
                        device,
                        requestId,
                        if (select != null) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                        offset,
                        null,
                    )
                }
            } catch (_: SecurityException) {
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            val value = if (device in subscribed) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            try {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            } catch (_: SecurityException) {
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (descriptor.uuid == MapWire.CCCD_UUID) {
                if (value != null && value.isNotEmpty() && (value[0].toInt() and 0x01) != 0) {
                    subscribed += device
                } else {
                    subscribed -= device
                }
            }
            try {
                if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            } catch (_: SecurityException) {
            }
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
