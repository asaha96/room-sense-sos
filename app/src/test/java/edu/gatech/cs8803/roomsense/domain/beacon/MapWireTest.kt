package edu.gatech.cs8803.roomsense.domain.beacon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MapWireTest {
    private val status = MapWire.Status(
        code = "TEW7A2",
        roomId = "r-2",
        roomName = "Meeting room",
        confidence = 0.75f,
        updatedAt = 1_763_400_000_000L,
        layoutVersion = 0xFEDCBA98L,
        layoutSize = 321,
        floorVersion = 12345L,
        floorSize = 70_000,
        demoMode = false,
    )

    @Test
    fun statusRoundTripsAndFitsAttribute() {
        val bytes = MapWire.encodeStatus(status)
        assertTrue(bytes.size < 512)
        assertEquals(status, MapWire.decodeStatus(bytes))
    }

    @Test
    fun statusWithoutRoomAndLongNameStaysSmall() {
        val unknown = status.copy(roomId = null, roomName = null)
        assertEquals(unknown, MapWire.decodeStatus(MapWire.encodeStatus(unknown)))
        val long = status.copy(roomName = "x".repeat(600) + "\nsecond line")
        val decoded = MapWire.decodeStatus(MapWire.encodeStatus(long))!!
        assertEquals(60, decoded.roomName!!.length)
        assertTrue(MapWire.encodeStatus(long).size < 512)
    }

    @Test
    fun statusRejectsGarbage() {
        assertNull(MapWire.decodeStatus(null))
        assertNull(MapWire.decodeStatus("RSS1\n\n".toByteArray()))
        assertNull(MapWire.decodeStatus("nope".toByteArray()))
    }

    @Test
    fun layoutRoundTripsWithAwkwardNames() {
        val rooms = listOf(
            MapWire.WireRoom("a", "换衣室", 0xFFC6A15B.toInt(), 0.06f, 0.08f, 0.42f, 0.4f),
            MapWire.WireRoom("b", "Bed\troom\n2", 0xFF3F8F8A.toInt(), 0.52f, 0.08f, 0.42f, 0.4f),
        )
        val decoded = MapWire.decodeLayout(MapWire.encodeLayout(rooms))!!
        assertEquals(2, decoded.size)
        assertEquals(rooms[0], decoded[0])
        assertEquals("Bed room 2", decoded[1].name)
        assertEquals(rooms[1].color, decoded[1].color)
        assertEquals(emptyList<MapWire.WireRoom>(), MapWire.decodeLayout(MapWire.encodeLayout(emptyList())))
        assertNull(MapWire.decodeLayout("junk".toByteArray()))
    }

    @Test
    fun chunksReassembleBlob() {
        val data = Random(7).nextBytes(70_001)
        val version = MapWire.version(data)
        val count = MapWire.chunkCount(data.size)
        assertEquals(146, count)
        val out = java.io.ByteArrayOutputStream()
        for (index in 0 until count) {
            val framed = MapWire.frame(MapWire.BLOB_FLOOR, version, index, data)
            assertTrue(framed.size <= 512)
            val chunk = MapWire.unframe(framed)!!
            assertEquals(MapWire.BLOB_FLOOR, chunk.blob)
            assertEquals(version, chunk.version)
            assertEquals(index, chunk.index)
            out.write(chunk.payload)
        }
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun chunkPastEndIsEmpty() {
        val chunk = MapWire.unframe(MapWire.frame(MapWire.BLOB_LAYOUT, 1L, 9, ByteArray(10)))!!
        assertEquals(0, chunk.payload.size)
        assertEquals(0, MapWire.chunkCount(0))
    }

    @Test
    fun selectRoundTrips() {
        assertEquals(MapWire.BLOB_FLOOR to 40_000, MapWire.decodeSelect(MapWire.encodeSelect(MapWire.BLOB_FLOOR, 40_000)))
        assertNull(MapWire.decodeSelect(byteArrayOf(1)))
    }
}
