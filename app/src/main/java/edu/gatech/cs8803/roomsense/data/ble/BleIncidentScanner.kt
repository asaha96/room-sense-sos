package edu.gatech.cs8803.roomsense.data.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import edu.gatech.cs8803.roomsense.domain.beacon.SosBeaconCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NearbyIncident(
    val code: String,
    val room: String,
    val ssid: String,
    val rssi: Int,
    val lastSeenMillis: Long,
)

class BleIncidentScanner(private val context: Context) {
    private val _incidents = MutableStateFlow<List<NearbyIncident>>(emptyList())
    val incidents: StateFlow<List<NearbyIncident>> = _incidents.asStateFlow()

    private var callback: ScanCallback? = null

    fun start(): String {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return "This device has no Bluetooth radio."
        if (!adapter.isEnabled) return "Turn Bluetooth on to listen for SOS beacons."
        if (Build.VERSION.SDK_INT >= 31 &&
            !granted(android.Manifest.permission.BLUETOOTH_SCAN)
        ) {
            return "Bluetooth scan permission is missing."
        }
        val scanner = adapter.bluetoothLeScanner ?: return "BLE scanning is not available."
        if (callback != null) return "Listening for SOS beacons."
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                absorb(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::absorb)
            }

            override fun onScanFailed(errorCode: Int) = Unit
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        return try {
            scanner.startScan(null, settings, scanCallback)
            callback = scanCallback
            "Listening for SOS beacons."
        } catch (error: SecurityException) {
            "Bluetooth scan was denied. ${error.message.orEmpty()}".trim()
        }
    }

    fun stop() {
        val scanCallback = callback ?: return
        val scanner = context.getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.bluetoothLeScanner
        runCatching { scanner?.stopScan(scanCallback) }
        callback = null
    }

    fun prune(now: Long = System.currentTimeMillis(), maxAgeMs: Long = 20_000) {
        _incidents.update { current -> current.filter { now - it.lastSeenMillis <= maxAgeMs } }
    }

    private fun absorb(result: ScanResult) {
        val record = result.scanRecord ?: return
        val manufacturer = record.getManufacturerSpecificData(SosBeaconCodec.COMPANY_ID)
        val service = record.getServiceData(ParcelUuid(SosBeaconCodec.SERVICE_UUID))
        val beacon = SosBeaconCodec.decode(manufacturer, service) ?: return
        if (beacon.code.isBlank()) return
        val seen = NearbyIncident(
            code = beacon.code,
            room = beacon.room.ifBlank { "Unknown room" },
            ssid = beacon.ssid,
            rssi = result.rssi,
            lastSeenMillis = System.currentTimeMillis(),
        )
        _incidents.update { current ->
            (current.filter { it.code != seen.code } + seen).sortedByDescending { it.rssi }
        }
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
