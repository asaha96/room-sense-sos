package edu.gatech.cs8803.roomsense.data.server

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalAddresses {
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
