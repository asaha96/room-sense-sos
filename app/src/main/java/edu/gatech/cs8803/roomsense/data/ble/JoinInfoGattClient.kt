package edu.gatech.cs8803.roomsense.data.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import edu.gatech.cs8803.roomsense.domain.beacon.JoinInfo
import androidx.core.content.ContextCompat
import edu.gatech.cs8803.roomsense.domain.beacon.JoinInfoCodec
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Responder side. Connects to the caller that sent the beacon and reads the
 * hotspot details from its join characteristic.
 *
 * [read] checks BLUETOOTH_CONNECT up front, and every call below also catches
 * SecurityException in case the permission is revoked mid-read.
 */
@SuppressLint("MissingPermission")
class JoinInfoGattClient(private val context: Context) {

    sealed interface Result {
        data class Ok(val info: JoinInfo) : Result
        data class Failed(val reason: String) : Result
    }

    /** Two attempts, because the first GATT connect often fails with status 133. */
    suspend fun read(device: BluetoothDevice): Result {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.Failed("Nearby devices permission is needed to read hotspot details over Bluetooth.")
        }
        var last: Result = Result.Failed("No answer over Bluetooth.")
        repeat(2) { attempt ->
            last = withTimeoutOrNull(12_000) { readOnce(device) }
                ?: Result.Failed("The caller's phone did not answer over Bluetooth.")
            if (last is Result.Ok) return last
            if (attempt == 0) delay(600)
        }
        return last
    }

    private suspend fun readOnce(device: BluetoothDevice): Result = suspendCancellableCoroutine { cont ->
        val done = AtomicBoolean(false)
        var gatt: BluetoothGatt? = null
        fun finish(result: Result) {
            if (!done.compareAndSet(false, true)) return
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            if (cont.isActive) cont.resume(result)
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                try {
                    when {
                        newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS ->
                            if (!g.requestMtu(247)) g.discoverServices()
                        newState == BluetoothProfile.STATE_DISCONNECTED ->
                            finish(Result.Failed("Bluetooth connection closed (status $status)."))
                        status != BluetoothGatt.GATT_SUCCESS ->
                            finish(Result.Failed("Bluetooth connection failed (status $status)."))
                    }
                } catch (error: SecurityException) {
                    finish(Result.Failed("Bluetooth permission was denied."))
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                try {
                    if (!g.discoverServices()) finish(Result.Failed("Could not list the caller's Bluetooth services."))
                } catch (_: SecurityException) {
                    finish(Result.Failed("Bluetooth permission was denied."))
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val characteristic = g.getService(JoinInfoCodec.SERVICE_UUID)
                    ?.getCharacteristic(JoinInfoCodec.CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    finish(Result.Failed("The caller's app does not share hotspot details over Bluetooth. Update both phones, or use the QR code."))
                    return
                }
                try {
                    @Suppress("DEPRECATION")
                    if (!g.readCharacteristic(characteristic)) {
                        finish(Result.Failed("Could not read the hotspot details."))
                    }
                } catch (_: SecurityException) {
                    finish(Result.Failed("Bluetooth permission was denied."))
                }
            }

            @Deprecated("Kept for Android 12 and older; API 33 routes here by default.")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                @Suppress("DEPRECATION")
                deliver(characteristic.value, status)
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                deliver(value, status)
            }

            private fun deliver(value: ByteArray?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(Result.Failed("Reading hotspot details failed (status $status)."))
                    return
                }
                val info = JoinInfoCodec.decode(value)
                if (info == null) {
                    finish(Result.Failed("The caller has not started its hotspot yet."))
                } else {
                    finish(Result.Ok(info))
                }
            }
        }

        cont.invokeOnCancellation {
            done.set(true)
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
        }
        Handler(Looper.getMainLooper()).post {
            try {
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                if (gatt == null) finish(Result.Failed("Could not start a Bluetooth connection."))
            } catch (_: SecurityException) {
                finish(Result.Failed("Bluetooth permission was denied."))
            }
        }
    }
}
