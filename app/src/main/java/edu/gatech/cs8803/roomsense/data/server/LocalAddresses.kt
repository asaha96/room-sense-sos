package edu.gatech.cs8803.roomsense.data.server

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalAddresses {
    data class InterfaceAddress(val name: String, val host: String)

    fun interfaces(): List<InterfaceAddress> = buildList {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@buildList
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (address in nif.inetAddresses) {
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    address.hostAddress?.let { add(InterfaceAddress(nif.name, it)) }
                }
            }
        }
    }

    // Android does not expose the local-only hotspot IP in its reservation.
    // Prefer addresses added to Wi-Fi interfaces after the hotspot starts. Never
    // substitute a cellular/VPN address or a guessed gateway for a QR payload.
    fun hotspotHosts(before: List<InterfaceAddress>, current: List<InterfaceAddress>): List<String> {
        val wifi = current.filter {
            val name = it.name.lowercase()
            name.startsWith("ap") || name.startsWith("swlan") ||
                name.startsWith("wlan") || name.startsWith("softap") ||
                name.startsWith("wifi") || name.startsWith("p2p")
        }
        val added = wifi.filter { it !in before }
        val candidates = added.ifEmpty {
            wifi.filter {
                val name = it.name.lowercase()
                name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap")
            }
        }
        return candidates.map { it.host }.distinct()
    }

    fun ipv4(): List<String> {
        val found = linkedSetOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return listOf("127.0.0.1")
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (address in nif.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    address.hostAddress?.let(found::add)
                }
            }
        }
        return listOf("127.0.0.1") + found
    }
}
