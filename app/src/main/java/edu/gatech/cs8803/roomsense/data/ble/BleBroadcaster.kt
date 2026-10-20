package edu.gatech.cs8803.roomsense.data.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import edu.gatech.cs8803.roomsense.domain.beacon.SosBeacon
import edu.gatech.cs8803.roomsense.domain.beacon.SosBeaconCodec

/**
 * Legacy advertisements, so a second phone on Android 8 or newer can hear them.
 * The incident code and room ride in manufacturer data. The hotspot SSID rides
 * in scan-response service data, because a legacy packet cannot hold both
 * once the SSID is a normal AndroidShare name.
 */
class BleBroadcaster(private val context: Context) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    fun start(beacon: SosBeacon, onStatus: (String) -> Unit) {
        stop()
        val adapter = manager?.adapter
        if (adapter == null) {
            onStatus("This device has no Bluetooth radio.")
            return
        }
        if (!adapter.isEnabled) {
            onStatus("Bluetooth is off, so nearby phones cannot hear this SOS yet.")
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && !granted(android.Manifest.permission.BLUETOOTH_ADVERTISE)) {
            onStatus("Bluetooth advertise permission is missing.")
            return
        }
        val le = adapter.bluetoothLeAdvertiser
        if (le == null) {
            onStatus("This device cannot send BLE advertisements.")
            return
        }
        advertiser = le

        val full = SosBeaconCodec.encodeFull(beacon.code, beacon.ssid, beacon.room, maxBytes = 24)
        val fullDecoded = SosBeaconCodec.decode(full, null)
        val fits = fullDecoded?.code == beacon.code &&
            fullDecoded.ssid == beacon.ssid &&
            fullDecoded.room == beacon.room

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(
                SosBeaconCodec.COMPANY_ID,
                if (fits) full else SosBeaconCodec.encodeRoom(beacon.code, beacon.room, maxBytes = 24),
            )
            .build()
        val scanResponse = if (fits) {
            null
        } else {
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData(
                    ParcelUuid(SosBeaconCodec.SERVICE_UUID),
                    SosBeaconCodec.encodeSsid(beacon.ssid, maxBytes = 27),
                )
                .build()
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                onStatus(
                    if (fits) {
                        "Broadcasting code, room, and hotspot name over BLE."
                    } else {
                        "Broadcasting over BLE. The hotspot name is in the scan response."
                    },
                )
            }

            override fun onStartFailure(errorCode: Int) {
                onStatus("BLE advertising failed (code $errorCode).")
            }
        }
        callback = advertiseCallback
        try {
            le.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        } catch (error: SecurityException) {
            onStatus("BLE advertising was denied. ${error.message.orEmpty()}".trim())
        } catch (error: IllegalArgumentException) {
            onStatus("The BLE packet was rejected. ${error.message.orEmpty()}".trim())
        }
    }

    fun stop() {
        val le = advertiser
        val active = callback
        if (le != null && active != null) {
            runCatching { le.stopAdvertising(active) }
        }
        callback = null
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
