package edu.gatech.cs8803.roomsense.data.server

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAddressesTest {
    private fun address(name: String, host: String) = LocalAddresses.InterfaceAddress(name, host)

    @Test fun selectsNewHotspotInsteadOfExistingWifiCellularOrVpn() {
        val before = listOf(address("wlan0", "192.168.1.5"))
        val current = before + listOf(
            address("rmnet0", "10.1.2.3"),
            address("tun0", "10.8.0.1"),
            address("wlan1", "192.168.157.1"),
        )
        assertEquals(listOf("192.168.157.1"), LocalAddresses.hotspotHosts(before, current))
    }

    @Test fun waitsForHotspotInsteadOfGuessingOrUsingExistingWifi() {
        val before = listOf(address("wlan0", "192.168.1.5"))
        assertEquals(emptyList<String>(), LocalAddresses.hotspotHosts(before, before))
        assertEquals(emptyList<String>(), LocalAddresses.hotspotHosts(before, emptyList()))
    }

    @Test fun detectsChangedAddressOnSameWifiInterface() {
        assertEquals(listOf("192.168.231.1"), LocalAddresses.hotspotHosts(
            listOf(address("wlan0", "192.168.1.5")),
            listOf(address("wlan0", "192.168.231.1")),
        ))
    }

    @Test fun recognizesAlreadyPresentDedicatedHotspotInterface() {
        val interfaces = listOf(address("ap0", "192.168.12.1"))
        assertEquals(listOf("192.168.12.1"), LocalAddresses.hotspotHosts(interfaces, interfaces))
    }
}
