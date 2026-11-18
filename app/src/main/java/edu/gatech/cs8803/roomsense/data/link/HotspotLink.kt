package edu.gatech.cs8803.roomsense.data.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import edu.gatech.cs8803.roomsense.domain.beacon.JoinInfo
import edu.gatech.cs8803.roomsense.domain.beacon.ResponderProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import kotlin.coroutines.resume

/** A responder server that answered with the expected incident code. */
data class FoundServer(val network: Network, val baseUrl: String)

sealed interface JoinResult {
    data class Joined(val network: Network) : JoinResult
    data class Failed(val reason: String) : JoinResult
}

/**
 * Responder side Wi-Fi plumbing. Finds the caller's server on a Wi-Fi network,
 * asks Android to join the caller's hotspot, and binds this app to that
 * network while the map is open.
 *
 * All requests go through [Network.openConnection] or a process binding, so a
 * hotspot without internet still works while mobile data stays the default.
 */
class HotspotLink(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    private var specifierCallback: ConnectivityManager.NetworkCallback? = null
    private var legacyNetworkId: Int = -1
    private var lostCallback: ConnectivityManager.NetworkCallback? = null

    /** Looks at every Wi-Fi network this phone is on, including the system one. */
    suspend fun findOnWifi(code: String, info: JoinInfo?): FoundServer? {
        @Suppress("DEPRECATION")
        val networks = connectivity?.allNetworks.orEmpty().filter { network ->
            connectivity?.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        for (network in networks) {
            probe(network, code, info)?.let { return it }
        }
        return null
    }

    /** Probes every host/port candidate on [network] in parallel. */
    suspend fun probe(network: Network, code: String, info: JoinInfo?): FoundServer? = withContext(Dispatchers.IO) {
        val properties = connectivity?.getLinkProperties(network) ?: return@withContext null
        val hosts = ResponderProbe.candidateHosts(
            gateways = gateways(properties),
            reported = info?.hosts.orEmpty(),
            localAddresses = properties.linkAddresses.mapNotNull { link ->
                (link.address as? Inet4Address)?.hostAddress
            },
        )
        val ports = ResponderProbe.candidatePorts(info?.port)
        val candidates = hosts.flatMap { host -> ports.map { port -> host to port } }
        if (candidates.isEmpty()) return@withContext null
        coroutineScope {
            val attempts = candidates.map { (host, port) ->
                async { if (statusMatches(network, host, port, code)) ResponderProbe.baseUrl(host, port) else null }
            }
            // Keep candidate order so the gateway and reported port win ties.
            attempts.firstNotNullOfOrNull { it.await() }
        }?.let { FoundServer(network, it) }
    }

    /** Waits for DHCP on a just-joined network, then probes until [timeoutMs]. */
    suspend fun probeUntil(network: Network, code: String, info: JoinInfo?, timeoutMs: Long): FoundServer? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            probe(network, code, info)?.let { return it }
            delay(1_000)
        }
        return null
    }

    /**
     * Asks Android to join the caller's hotspot. On Android 10 and newer the
     * system shows its own confirmation dialog.
     */
    suspend fun join(info: JoinInfo): JoinResult {
        release()
        return if (Build.VERSION.SDK_INT >= 29) joinWithSpecifier(info) else joinLegacy(info)
    }

    @RequiresApi(29)
    private suspend fun joinWithSpecifier(info: JoinInfo): JoinResult {
        val cm = connectivity ?: return JoinResult.Failed("Connectivity service is not available.")
        val builder = WifiNetworkSpecifier.Builder().setSsid(info.ssid)
        val password = info.password
        if (password != null && info.security != JoinInfo.SECURITY_OPEN) {
            if (info.security == JoinInfo.SECURITY_WPA3) {
                builder.setWpa3Passphrase(password)
            } else {
                builder.setWpa2Passphrase(password)
            }
        }
        val specifier = try {
            builder.build()
        } catch (error: IllegalArgumentException) {
            return JoinResult.Failed("Hotspot details were not valid. ${error.message.orEmpty()}".trim())
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        return suspendCancellableCoroutine { cont ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (cont.isActive) cont.resume(JoinResult.Joined(network))
                }

                override fun onUnavailable() {
                    specifierCallback = null
                    if (cont.isActive) {
                        cont.resume(JoinResult.Failed("${info.ssid} was not joined. The request was cancelled, timed out, or the hotspot is out of range."))
                    }
                }
            }
            specifierCallback = callback
            cont.invokeOnCancellation { release() }
            try {
                // The system dialog stays open while the user decides, so allow time.
                cm.requestNetwork(request, callback, 60_000)
            } catch (error: SecurityException) {
                specifierCallback = null
                if (cont.isActive) cont.resume(JoinResult.Failed("Wi-Fi permission was denied. ${error.message.orEmpty()}".trim()))
            } catch (error: RuntimeException) {
                specifierCallback = null
                if (cont.isActive) cont.resume(JoinResult.Failed(error.message ?: "Could not ask Android to join the hotspot."))
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun joinLegacy(info: JoinInfo): JoinResult {
        val manager = wifi ?: return JoinResult.Failed("Wi-Fi is not available.")
        if (!manager.isWifiEnabled && !manager.setWifiEnabled(true)) {
            return JoinResult.Failed("Turn Wi-Fi on, then retry.")
        }
        val config = WifiConfiguration().apply {
            SSID = "\"${info.ssid}\""
            val password = info.password
            if (password == null || info.security == JoinInfo.SECURITY_OPEN) {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
                preSharedKey = "\"$password\""
            }
        }
        val id = manager.addNetwork(config)
        if (id == -1) return JoinResult.Failed("Android refused to add ${info.ssid}.")
        legacyNetworkId = id
        manager.disconnect()
        manager.enableNetwork(id, true)
        manager.reconnect()
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            delay(1_000)
            val network = connectivity?.allNetworks.orEmpty().firstOrNull { network ->
                connectivity?.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    manager.connectionInfo?.networkId == id
            }
            if (network != null) return JoinResult.Joined(network)
        }
        return JoinResult.Failed("Could not join ${info.ssid} within 25 seconds.")
    }

    /**
     * Routes this app's traffic, including the WebView, through [network].
     * [onLost] fires if the hotspot disappears while bound.
     */
    fun bind(network: Network, onLost: () -> Unit) {
        val cm = connectivity ?: return
        cm.bindProcessToNetwork(network)
        lostCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(lost: Network) {
                if (lost == network) onLost()
            }
        }
        lostCallback = callback
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }
    }

    /** Unbinds and drops any hotspot connection this app requested. */
    fun release() {
        val cm = connectivity
        cm?.bindProcessToNetwork(null)
        lostCallback?.let { callback -> runCatching { cm?.unregisterNetworkCallback(callback) } }
        lostCallback = null
        specifierCallback?.let { callback -> runCatching { cm?.unregisterNetworkCallback(callback) } }
        specifierCallback = null
        if (legacyNetworkId != -1) {
            @Suppress("DEPRECATION")
            runCatching {
                wifi?.removeNetwork(legacyNetworkId)
                wifi?.reconnect()
            }
            legacyNetworkId = -1
        }
    }

    private fun gateways(properties: LinkProperties): List<String> {
        val fromRoutes = properties.routes.mapNotNull { route ->
            (route.gateway as? Inet4Address)?.hostAddress
        }
        val dhcp = if (Build.VERSION.SDK_INT >= 30) {
            (properties.dhcpServerAddress as? Inet4Address)?.hostAddress
        } else {
            null
        }
        return fromRoutes + listOfNotNull(dhcp)
    }

    private fun statusMatches(network: Network, host: String, port: Int, code: String): Boolean {
        return try {
            val connection = network.openConnection(URL("http://$host:$port/api/status")) as HttpURLConnection
            connection.connectTimeout = 1_200
            connection.readTimeout = 1_500
            connection.useCaches = false
            try {
                if (connection.responseCode != 200) return false
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                ResponderProbe.matches(body, code)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }
}
