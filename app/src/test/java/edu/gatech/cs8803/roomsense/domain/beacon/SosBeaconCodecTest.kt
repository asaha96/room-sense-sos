package edu.gatech.cs8803.roomsense.domain.beacon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosBeaconCodecTest {
    @Test
    fun fullPacketRoundTrips() {
        val packet = SosBeaconCodec.encodeFull("K7NP4Q", "ROOMSENSE-SOS", "Studio")
        assertEquals(
            SosBeacon("K7NP4Q", "ROOMSENSE-SOS", "Studio"),
            SosBeaconCodec.decode(packet, null),
        )
    }

    @Test
    fun unicodeRoomRoundTrips() {
        val packet = SosBeaconCodec.encodeFull("ABCDEF", "Hall", "Café")
        assertEquals(SosBeacon("ABCDEF", "Hall", "Café"), SosBeaconCodec.decode(packet, null))
    }

    @Test
    fun legacySplitCarriesSsidInServiceData() {
        val room = SosBeaconCodec.encodeRoom("ABCDEF", "A very long room name that will not fit", maxBytes = 24)
        val ssid = SosBeaconCodec.encodeSsid("AndroidShare_1234")
        assertTrue(room.size <= 24)
        val decoded = SosBeaconCodec.decode(room, ssid)
        assertEquals("ABCDEF", decoded?.code)
        assertEquals("AndroidShare_1234", decoded?.ssid)
        assertTrue(decoded!!.room.isNotEmpty())
        assertTrue(decoded.room.length < "A very long room name that will not fit".length)
    }

    @Test
    fun garbageAndEmptyAreIgnored() {
        assertNull(SosBeaconCodec.decode(null, null))
        assertNull(SosBeaconCodec.decode(byteArrayOf(1, 2, 3, 4), null))
        assertNull(SosBeaconCodec.decode(byteArrayOf('R'.code.toByte(), 'S'.code.toByte(), 9), null))
    }
}
