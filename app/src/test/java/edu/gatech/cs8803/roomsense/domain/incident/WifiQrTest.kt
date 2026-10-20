package edu.gatech.cs8803.roomsense.domain.incident

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiQrTest {
    @Test
    fun wpaPayload() {
        assertEquals(
            "WIFI:T:WPA;S:ROOMSENSE-SOS;P:sense-sos;;",
            WifiQr.payload("ROOMSENSE-SOS", "sense-sos"),
        )
    }

    @Test
    fun openNetworkOmitsPassword() {
        assertEquals("WIFI:T:nopass;S:OpenNet;;", WifiQr.payload("OpenNet", null))
        assertEquals("WIFI:T:nopass;S:OpenNet;;", WifiQr.payload("OpenNet", ""))
    }

    @Test
    fun escapesReservedCharacters() {
        assertEquals("WIFI:T:WPA;S:a\\;b;P:x\\:y;;", WifiQr.payload("a;b", "x:y"))
        assertEquals("a\\\\b", WifiQr.escape("a\\b"))
    }
}
