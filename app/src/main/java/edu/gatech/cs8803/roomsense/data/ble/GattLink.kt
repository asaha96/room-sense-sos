package edu.gatech.cs8803.roomsense.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

class GattException(message: String) : Exception(message)

/**
 * One GATT client connection with suspend functions. Android allows a single
 * outstanding GATT operation, so every call is serialized behind [mutex] and
 * completed from the callback. A disconnect fails whatever is pending.
 *
 * The owner checks BLUETOOTH_CONNECT before creating this. Calls still catch
 * SecurityException in case the permission is revoked mid-session.
 */
@SuppressLint("MissingPermission")
class GattLink private constructor() {
    private var gatt: BluetoothGatt? = null
    private val mutex = Mutex()

    @Volatile private var pending: CompletableDeferred<Any?>? = null
    private val connected = CompletableDeferred<Unit>()

    /** Fires once with a reason when the link drops. */
    val closed = CompletableDeferred<String>()

    /** Receives a value every time the peer notifies. */
    val notifications = Channel<Unit>(Channel.CONFLATED)

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val reason = "Bluetooth link closed (status $status)."
                connected.completeExceptionally(GattException(reason))
                pending?.completeExceptionally(GattException(reason))
                closed.complete(reason)
                runCatching { g.close() }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            pending?.complete(mtu)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) pending?.complete(Unit)
            else pending?.completeExceptionally(GattException("Service discovery failed (status $status)."))
        }

        @Deprecated("Android 12 and older")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            finishRead(c.value, status)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            finishRead(value, status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) pending?.complete(Unit)
            else pending?.completeExceptionally(GattException("Write failed (status $status)."))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) pending?.complete(Unit)
            else pending?.completeExceptionally(GattException("Subscribe failed (status $status)."))
        }

        @Deprecated("Android 12 and older")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            notifications.trySend(Unit)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            notifications.trySend(Unit)
        }

        private fun finishRead(value: ByteArray?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) pending?.complete(value ?: ByteArray(0))
            else pending?.completeExceptionally(GattException("Read failed (status $status)."))
        }
    }

    private suspend fun <T> op(timeoutMs: Long = OP_TIMEOUT_MS, start: (BluetoothGatt) -> Boolean): T = mutex.withLock {
        val g = gatt ?: throw GattException("Not connected.")
        if (closed.isCompleted) throw GattException("Bluetooth link closed.")
        val deferred = CompletableDeferred<Any?>()
        pending = deferred
        try {
            val started = try {
                start(g)
            } catch (_: SecurityException) {
                throw GattException("Bluetooth permission was denied.")
            }
            if (!started) throw GattException("Android refused the Bluetooth request.")
            @Suppress("UNCHECKED_CAST")
            try {
                withTimeout(timeoutMs) { deferred.await() } as T
            } catch (_: TimeoutCancellationException) {
                // Not a cancellation of the caller: report it as a link failure.
                throw GattException("The caller's phone stopped answering over Bluetooth.")
            }
        } finally {
            pending = null
        }
    }

    suspend fun requestMtu(mtu: Int): Int = op { it.requestMtu(mtu) }

    suspend fun discover() {
        op<Any?>(timeoutMs = 15_000) { it.discoverServices() }
    }

    fun has(service: UUID, characteristic: UUID): Boolean =
        gatt?.getService(service)?.getCharacteristic(characteristic) != null

    suspend fun read(service: UUID, characteristic: UUID): ByteArray {
        val c = characteristic(service, characteristic)
        return op {
            @Suppress("DEPRECATION")
            it.readCharacteristic(c)
        }
    }

    suspend fun write(service: UUID, characteristic: UUID, value: ByteArray) {
        val c = characteristic(service, characteristic)
        op<Any?> { g ->
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                c.value = value
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        }
    }

    suspend fun subscribe(service: UUID, characteristic: UUID, cccd: UUID) {
        val c = characteristic(service, characteristic)
        val descriptor = c.getDescriptor(cccd) ?: throw GattException("The caller does not offer live updates.")
        op<Any?> { g ->
            if (!g.setCharacteristicNotification(c, true)) return@op false
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(descriptor, enable) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = enable
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }
    }

    fun highPriority() {
        runCatching { gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
    }

    fun close() {
        val g = gatt ?: return
        runCatching { g.disconnect() }
        runCatching { g.close() }
        gatt = null
        pending?.completeExceptionally(GattException("Closed."))
        closed.complete("Closed.")
    }

    private fun characteristic(service: UUID, characteristic: UUID): BluetoothGattCharacteristic =
        gatt?.getService(service)?.getCharacteristic(characteristic)
            ?: throw GattException("The caller's app does not offer the map over Bluetooth. Update it to the same version.")

    companion object {
        private const val OP_TIMEOUT_MS = 8_000L

        /** Connects, or throws [GattException] after [timeoutMs]. */
        suspend fun connect(context: Context, device: BluetoothDevice, timeoutMs: Long = 12_000): GattLink {
            val link = GattLink()
            try {
                link.gatt = device.connectGatt(context.applicationContext, false, link.callback, BluetoothDevice.TRANSPORT_LE)
                    ?: throw GattException("Could not start a Bluetooth connection.")
            } catch (_: SecurityException) {
                throw GattException("Bluetooth permission was denied.")
            }
            try {
                withTimeout(timeoutMs) { link.connected.await() }
            } catch (_: TimeoutCancellationException) {
                link.close()
                throw GattException("The caller's phone did not answer over Bluetooth.")
            } catch (error: GattException) {
                link.close()
                throw error
            } catch (error: kotlinx.coroutines.CancellationException) {
                link.close()
                throw error
            }
            return link
        }
    }
}
