package edu.gatech.cs8803.roomsense.data.hotspot

import android.content.Context
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiSsid
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

sealed interface HotspotStartResult {
    data class Running(
        val ssid: String,
        val password: String?,
        val fixedSsidApplied: Boolean,
        val note: String,
    ) : HotspotStartResult

    data class Failed(val note: String) : HotspotStartResult
}

/**
 * Starts a local-only hotspot.
 *
 * On Android 8 and later the public [WifiManager.startLocalOnlyHotspot] API
 * lets the system pick the SSID and passphrase. A custom [SoftApConfiguration]
 * exists, but the overload that accepts one is a privileged API (it wants
 * something like NETWORK_SETTINGS). This class tries that path through
 * reflection when asked, and always falls back to the public API.
 */
class HotspotController(private val context: Context) {
    private val lock = Any()
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun start(preferFixedSsid: Boolean, listener: (HotspotStartResult) -> Unit) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        if (wifi == null) {
            listener(HotspotStartResult.Failed("Wi-Fi is not available on this device."))
            return
        }
        val once = AtomicBoolean(false)
        val deliver: (HotspotStartResult) -> Unit = { result ->
            if (once.compareAndSet(false, true)) listener(result)
        }
        if (preferFixedSsid && Build.VERSION.SDK_INT >= 30) {
            val immediate = tryStartFixed(wifi, deliver)
            if (immediate == null) return
            startSystem(wifi, deliver, immediate)
        } else if (preferFixedSsid) {
            startSystem(
                wifi,
                deliver,
                "Fixed SSID needs Android 10 or newer. Using the system-assigned hotspot.",
            )
        } else {
            startSystem(
                wifi,
                deliver,
                "Android assigns this hotspot a random SSID. A normal app cannot choose one.",
            )
        }
    }

    fun stop() {
        synchronized(lock) {
            runCatching { reservation?.close() }
            reservation = null
        }
    }

    @RequiresApi(30)
    private fun tryStartFixed(
        wifi: WifiManager,
        deliver: (HotspotStartResult) -> Unit,
    ): String? {
        return try {
            val config = buildFixedConfig()
            val method = wifi.javaClass.getMethod(
                "startLocalOnlyHotspot",
                SoftApConfiguration::class.java,
                Executor::class.java,
                WifiManager.LocalOnlyHotspotCallback::class.java,
            )
            val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    keep(reservation)
                    val (ssid, password) = identityOf(reservation)
                    deliver(
                        HotspotStartResult.Running(
                            ssid = ssid,
                            password = password,
                            fixedSsidApplied = true,
                            note = "Hotspot name set to $FIXED_SSID.",
                        ),
                    )
                }

                override fun onStopped() = Unit

                override fun onFailed(reason: Int) {
                    startSystem(
                        wifi,
                        deliver,
                        "Fixed SSID was rejected (${reasonText(reason)}). Using the system SSID.",
                    )
                }
            }
            method.invoke(wifi, config, ContextCompat.getMainExecutor(context), callback)
            null
        } catch (error: Throwable) {
            explain(error)
        }
    }

    /**
     * SoftApConfiguration.Builder is not in the public SDK stub. It is a
     * privileged type, so the only way a normal app can even ask is reflection.
     */
    @RequiresApi(30)
    private fun buildFixedConfig(): SoftApConfiguration {
        val builderClass = Class.forName("android.net.wifi.SoftApConfiguration\$Builder")
        val builder = builderClass.getDeclaredConstructor().newInstance()
        if (Build.VERSION.SDK_INT >= 33) {
            applyModernSsid(builder, builderClass)
        } else {
            builderClass.getMethod("setSsid", String::class.java).invoke(builder, FIXED_SSID)
        }
        builderClass
            .getMethod("setPassphrase", String::class.java, Int::class.javaPrimitiveType)
            .invoke(builder, FIXED_PASSPHRASE, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
        return builderClass.getMethod("build").invoke(builder) as SoftApConfiguration
    }

    @RequiresApi(33)
    private fun applyModernSsid(builder: Any, builderClass: Class<*>) {
        val wifiSsid = WifiSsid.fromBytes(FIXED_SSID.toByteArray(Charsets.UTF_8))
        try {
            builderClass.getMethod("setWifiSsid", WifiSsid::class.java).invoke(builder, wifiSsid)
        } catch (_: NoSuchMethodException) {
            builderClass.getMethod("setSsid", String::class.java).invoke(builder, FIXED_SSID)
        }
    }

    private fun startSystem(
        wifi: WifiManager,
        deliver: (HotspotStartResult) -> Unit,
        note: String,
    ) {
        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                keep(reservation)
                val (ssid, password) = identityOf(reservation)
                deliver(
                    HotspotStartResult.Running(
                        ssid = ssid,
                        password = password,
                        fixedSsidApplied = false,
                        note = note,
                    ),
                )
            }

            override fun onStopped() = Unit

            override fun onFailed(reason: Int) {
                deliver(HotspotStartResult.Failed("${reasonText(reason)} $note".trim()))
            }
        }
        try {
            wifi.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
        } catch (error: SecurityException) {
            deliver(HotspotStartResult.Failed("Hotspot permission was denied. ${error.message.orEmpty()}".trim()))
        } catch (error: Exception) {
            deliver(HotspotStartResult.Failed(error.message ?: "Could not start the hotspot."))
        }
    }

    private fun keep(next: WifiManager.LocalOnlyHotspotReservation) {
        synchronized(lock) {
            reservation?.let { runCatching { it.close() } }
            reservation = next
        }
    }

    private fun identityOf(reservation: WifiManager.LocalOnlyHotspotReservation): Pair<String, String?> {
        if (Build.VERSION.SDK_INT >= 30) return readModern(reservation)
        return readLegacy(reservation)
    }

    @Suppress("DEPRECATION")
    private fun readLegacy(reservation: WifiManager.LocalOnlyHotspotReservation): Pair<String, String?> {
        val config = reservation.wifiConfiguration
        val ssid = config?.SSID?.trim()?.trim('"').orEmpty().ifBlank { "Unknown SSID" }
        val password = config?.preSharedKey?.trim()?.trim('"')
        return ssid to password
    }

    @RequiresApi(30)
    private fun readModern(reservation: WifiManager.LocalOnlyHotspotReservation): Pair<String, String?> {
        val config = reservation.softApConfiguration
        val ssid = if (Build.VERSION.SDK_INT >= 33) {
            readSsid33(config)
        } else {
            @Suppress("DEPRECATION")
            config.ssid
        }?.trim()?.trim('"').orEmpty().ifBlank { "Unknown SSID" }
        return ssid to config.passphrase
    }

    @RequiresApi(33)
    private fun readSsid33(config: SoftApConfiguration): String? {
        val fromBytes = config.wifiSsid?.bytes
            ?.let { raw -> String(raw, Charsets.UTF_8) }
            ?.trim()
            ?.trim('"')
            ?.ifBlank { null }
        return fromBytes ?: config.wifiSsid?.toString()?.trim()?.trim('"')
    }

    private fun reasonText(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL ->
            "No Wi-Fi channel is free for a hotspot."
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC ->
            "The system could not start a hotspot."
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE ->
            "Turn off Wi-Fi tethering and any VPN, then try the SOS again."
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED ->
            "This device does not allow a local-only hotspot."
        else -> "Hotspot failed (code $reason)."
    }

    private fun explain(error: Throwable): String {
        val cause = (error as? InvocationTargetException)?.targetException ?: error
        return "Fixed SSID $FIXED_SSID is not available to a normal app " +
            "(${cause.javaClass.simpleName}). Setting a SoftApConfiguration requires a " +
            "privileged permission such as NETWORK_SETTINGS. Using the system-assigned hotspot instead."
    }

    companion object {
        const val FIXED_SSID = "ROOMSENSE-SOS"
        const val FIXED_PASSPHRASE = "sense-sos"
    }
}
