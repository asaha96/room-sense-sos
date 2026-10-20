package edu.gatech.cs8803.roomsense.data.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Foreground Wi-Fi scans on Android 9 and later are limited to 4 scans every
 * 2 minutes per app. [WifiManager.startScan] returns false once that quota is
 * gone, while [WifiManager.getScanResults] still returns the cached batch.
 * Callers are told when a reading was cached so a survey does not store the
 * same sighting over and over under a new timestamp.
 */
class AndroidWifiSource(
    private val context: Context,
) : WifiFingerprintSource {

    private val wifi: WifiManager? =
        context.applicationContext.getSystemService(WifiManager::class.java)

    override suspend fun capture(roomHint: String?): ScanCapture {
        val manager = wifi
            ?: return ScanCapture(emptyMap(), false, false, "Wi-Fi is not available on this device.")
        if (!hasLocationPermission()) {
            return ScanCapture(
                readings = emptyMap(),
                fromCache = false,
                throttled = false,
                message = "Location permission is required before Android will return Wi-Fi scans.",
            )
        }
        if (!locationEnabled()) {
            return ScanCapture(
                readings = emptyMap(),
                fromCache = false,
                throttled = false,
                message = "Turn on system Location. Android hides scan results while it is off.",
            )
        }
        if (!manager.isWifiEnabled) {
            return ScanCapture(
                readings = emptyMap(),
                fromCache = false,
                throttled = false,
                message = "Wi-Fi is off. Turn it on, then scan again.",
            )
        }

        return withContext(Dispatchers.Main) {
            val started = try {
                @Suppress("DEPRECATION")
                manager.startScan()
            } catch (_: SecurityException) {
                false
            }
            if (started) awaitScanBroadcast()
            val readings = readResults(manager)
            val throttled = !started
            val message = when {
                throttled -> THROTTLE_MESSAGE
                readings.isEmpty() -> "The scan finished, but no access points were heard."
                else -> "Heard ${readings.size} access points."
            }
            ScanCapture(
                readings = readings,
                fromCache = throttled,
                throttled = throttled,
                message = message,
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine) return false
        if (Build.VERSION.SDK_INT >= 33) {
            val nearby = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
            if (!nearby) return false
        }
        return true
    }

    private fun locationEnabled(): Boolean {
        val location = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) {
            location.isLocationEnabled
        } else {
            providerOn(location, LocationManager.GPS_PROVIDER) ||
                providerOn(location, LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun providerOn(location: LocationManager, provider: String): Boolean {
        return try {
            location.isProviderEnabled(provider)
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun readResults(manager: WifiManager): Map<String, Int> {
        val results = try {
            manager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        return results.mapNotNull { result ->
            val bssid = result.BSSID ?: return@mapNotNull null
            if (bssid.isBlank()) null else bssid to result.level
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, levels) -> levels.max() }
    }

    private suspend fun awaitScanBroadcast() {
        var receiver: BroadcastReceiver? = null
        try {
            withTimeoutOrNull(4_000) {
                suspendCancellableCoroutine { continuation ->
                    val registered = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                    if (Build.VERSION.SDK_INT >= 33) {
                        context.registerReceiver(registered, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(registered, filter)
                    }
                    receiver = registered
                }
            }
        } finally {
            receiver?.let { active ->
                runCatching { context.unregisterReceiver(active) }
            }
        }
    }
}

const val THROTTLE_MESSAGE: String =
    "Android allows about 4 Wi-Fi scans every 2 minutes in the foreground. This reading is the cached scan."
