package edu.gatech.cs8803.roomsense.domain.beacon

import java.io.ByteArrayOutputStream
import java.util.UUID

data class SosBeacon(
    val code: String,
    val ssid: String,
    val room: String,
)

/**
 * Compact beacon used as BLE manufacturer data, with the hotspot name split
 * into service data when the legacy 31-byte advertisement cannot hold it.
 *
 * Layout: magic "RS", version byte, then length-prefixed UTF-8 fields.
 * Version 1 is code + ssid + room. Version 2 is code + room. Version 3 is ssid.
 *
 * [COMPANY_ID] and [SERVICE_UUID] are prototype identifiers for this class
 * project. They are not registered with the Bluetooth SIG.
 */
object SosBeaconCodec {
    const val COMPANY_ID: Int = 0x5243
    val SERVICE_UUID: UUID = UUID.fromString("00005243-0000-1000-8000-00805f9b34fb")

    const val VERSION_FULL: Int = 1
    const val VERSION_ROOM: Int = 2
    const val VERSION_SSID: Int = 3

    private const val MAGIC_0: Byte = 'R'.code.toByte()
    private const val MAGIC_1: Byte = 'S'.code.toByte()

    fun encodeFull(code: String, ssid: String, room: String, maxBytes: Int = 180): ByteArray {
        return fit(maxBytes) { roomName, ssidName ->
            frame(VERSION_FULL, listOf(code, ssidName, roomName))
        }.invoke(room, ssid)
    }

    fun encodeRoom(code: String, room: String, maxBytes: Int = 24): ByteArray {
        var roomName = room
        var encoded = frame(VERSION_ROOM, listOf(code, roomName))
        while (encoded.size > maxBytes && roomName.isNotEmpty()) {
            roomName = roomName.dropLast(1)
            encoded = frame(VERSION_ROOM, listOf(code, roomName))
        }
        return encoded
    }

    fun encodeSsid(ssid: String, maxBytes: Int = 27): ByteArray {
        var name = ssid
        var encoded = frame(VERSION_SSID, listOf(name))
        while (encoded.size > maxBytes && name.isNotEmpty()) {
            name = name.dropLast(1)
            encoded = frame(VERSION_SSID, listOf(name))
        }
        return encoded
    }

    fun decode(manufacturer: ByteArray?, serviceData: ByteArray?): SosBeacon? {
        val primary = manufacturer?.let(::parse) ?: return null
        return when (primary.version) {
            VERSION_FULL -> {
                if (primary.parts.size < 3) return null
                SosBeacon(primary.parts[0], primary.parts[1], primary.parts[2])
            }
            VERSION_ROOM -> {
                if (primary.parts.size < 2) return null
                val ssid = serviceData?.let(::parse)
                    ?.takeIf { it.version == VERSION_SSID }
                    ?.parts
                    ?.firstOrNull()
                    .orEmpty()
                SosBeacon(primary.parts[0], ssid, primary.parts[1])
            }
            else -> null
        }
    }

    private fun fit(
        maxBytes: Int,
        encode: (roomName: String, ssidName: String) -> ByteArray,
    ): (String, String) -> ByteArray = { room, ssid ->
        var roomName = room
        var ssidName = ssid
        var encoded = encode(roomName, ssidName)
        while (encoded.size > maxBytes && roomName.isNotEmpty()) {
            roomName = roomName.dropLast(1)
            encoded = encode(roomName, ssidName)
        }
        while (encoded.size > maxBytes && ssidName.isNotEmpty()) {
            ssidName = ssidName.dropLast(1)
            encoded = encode(roomName, ssidName)
        }
        encoded
    }

    private fun frame(version: Int, parts: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MAGIC_0.toInt())
        out.write(MAGIC_1.toInt())
        out.write(version)
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            val length = bytes.size.coerceAtMost(255)
            out.write(length)
            out.write(bytes, 0, length)
        }
        return out.toByteArray()
    }

    private data class Parsed(val version: Int, val parts: List<String>)

    private fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size < 3) return null
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) return null
        val version = bytes[2].toInt() and 0xFF
        val parts = mutableListOf<String>()
        var index = 3
        while (index < bytes.size) {
            val length = bytes[index].toInt() and 0xFF
            index += 1
            if (index + length > bytes.size) return null
            parts += String(bytes, index, length, Charsets.UTF_8)
            index += length
        }
        return Parsed(version, parts)
    }
}
