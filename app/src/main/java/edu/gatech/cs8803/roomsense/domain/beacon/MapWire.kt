package edu.gatech.cs8803.roomsense.domain.beacon

import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.CRC32

/**
 * Wire format for sending the caller's map to a responder over BLE GATT, so
 * the responder never has to join the hotspot or look at the caller's screen.
 *
 * One primary service ([JoinInfoCodec.SERVICE_UUID]) holds:
 * - STATUS (read, notify): incident code, current room, and blob versions.
 *   Notifications carry only a sequence number; the client re-reads STATUS.
 * - BLOB_SELECT (write): which blob and chunk the next BLOB_DATA read returns.
 * - BLOB_DATA (read): one framed chunk of the selected blob.
 * - JOIN (read): hotspot details, only for the web-page fallback.
 *
 * Every value stays under the 512-byte ATT attribute limit.
 */
object MapWire {
    val STATUS_UUID: UUID = UUID.fromString("00005246-0000-1000-8000-00805f9b34fb")
    val BLOB_SELECT_UUID: UUID = UUID.fromString("00005247-0000-1000-8000-00805f9b34fb")
    val BLOB_DATA_UUID: UUID = UUID.fromString("00005248-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val BLOB_LAYOUT: Int = 1
    const val BLOB_FLOOR: Int = 2

    /** Payload bytes per chunk. Header is 7 bytes, so a frame is at most 487. */
    const val CHUNK_PAYLOAD: Int = 480
    private const val CHUNK_HEADER: Int = 7
    private const val MAX_ROOM_NAME: Int = 60

    data class Status(
        val code: String,
        val roomId: String?,
        val roomName: String?,
        val confidence: Float,
        val updatedAt: Long,
        val layoutVersion: Long,
        val layoutSize: Int,
        val floorVersion: Long,
        val floorSize: Int,
        val demoMode: Boolean,
    )

    data class WireRoom(
        val id: String,
        val name: String,
        val color: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    )

    data class Chunk(val blob: Int, val version: Long, val index: Int, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Chunk && blob == other.blob && version == other.version &&
                index == other.index && payload.contentEquals(other.payload)

        override fun hashCode(): Int = ((blob * 31 + version.hashCode()) * 31 + index) * 31 + payload.contentHashCode()
    }

    fun version(bytes: ByteArray): Long {
        if (bytes.isEmpty()) return 0L
        return CRC32().apply { update(bytes) }.value
    }

    fun chunkCount(size: Int): Int = if (size <= 0) 0 else (size + CHUNK_PAYLOAD - 1) / CHUNK_PAYLOAD

    // ---- STATUS ----

    fun encodeStatus(status: Status): ByteArray {
        val lines = listOf(
            "RSS1",
            clean(status.code),
            clean(status.roomId.orEmpty()),
            clean(status.roomName.orEmpty()).take(MAX_ROOM_NAME),
            status.confidence.coerceIn(0f, 1f).toString(),
            status.updatedAt.toString(),
            status.layoutVersion.toString(),
            status.layoutSize.toString(),
            status.floorVersion.toString(),
            status.floorSize.toString(),
            if (status.demoMode) "1" else "0",
        )
        return lines.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    fun decodeStatus(bytes: ByteArray?): Status? {
        if (bytes == null || bytes.isEmpty()) return null
        val lines = String(bytes, Charsets.UTF_8).split("\n")
        if (lines.size < 11 || lines[0] != "RSS1" || lines[1].isBlank()) return null
        return Status(
            code = lines[1],
            roomId = lines[2].ifEmpty { null },
            roomName = lines[3].ifEmpty { null },
            confidence = lines[4].toFloatOrNull() ?: 0f,
            updatedAt = lines[5].toLongOrNull() ?: 0L,
            layoutVersion = lines[6].toLongOrNull() ?: 0L,
            layoutSize = lines[7].toIntOrNull() ?: 0,
            floorVersion = lines[8].toLongOrNull() ?: 0L,
            floorSize = lines[9].toIntOrNull() ?: 0,
            demoMode = lines[10] == "1",
        )
    }

    // ---- LAYOUT blob ----

    fun encodeLayout(rooms: List<WireRoom>): ByteArray {
        val body = rooms.joinToString("") { room ->
            listOf(
                clean(room.id).replace("\t", " "),
                clean(room.name).replace("\t", " "),
                room.color.toString(),
                room.x.toString(),
                room.y.toString(),
                room.width.toString(),
                room.height.toString(),
            ).joinToString("\t") + "\n"
        }
        return ("RSL1\n" + body).toByteArray(Charsets.UTF_8)
    }

    fun decodeLayout(bytes: ByteArray): List<WireRoom>? {
        val lines = String(bytes, Charsets.UTF_8).split("\n")
        if (lines.isEmpty() || lines[0] != "RSL1") return null
        return lines.drop(1).filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 7) return@mapNotNull null
            WireRoom(
                id = parts[0],
                name = parts[1],
                color = parts[2].toIntOrNull() ?: return@mapNotNull null,
                x = parts[3].toFloatOrNull() ?: return@mapNotNull null,
                y = parts[4].toFloatOrNull() ?: return@mapNotNull null,
                width = parts[5].toFloatOrNull() ?: return@mapNotNull null,
                height = parts[6].toFloatOrNull() ?: return@mapNotNull null,
            )
        }
    }

    // ---- chunk selection and framing ----

    fun encodeSelect(blob: Int, index: Int): ByteArray =
        byteArrayOf(blob.toByte(), (index shr 8).toByte(), index.toByte())

    fun decodeSelect(bytes: ByteArray?): Pair<Int, Int>? {
        if (bytes == null || bytes.size != 3) return null
        val blob = bytes[0].toInt() and 0xFF
        val index = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        return blob to index
    }

    /** Frame chunk [index] of [blob]. An index past the end yields an empty payload. */
    fun frame(blob: Int, version: Long, index: Int, data: ByteArray): ByteArray {
        val start = (index.toLong() * CHUNK_PAYLOAD).coerceAtMost(data.size.toLong()).toInt()
        val end = (start + CHUNK_PAYLOAD).coerceAtMost(data.size)
        val payload = data.copyOfRange(start, end)
        return ByteBuffer.allocate(CHUNK_HEADER + payload.size)
            .put(blob.toByte())
            .putInt(version.toInt())
            .putShort(index.toShort())
            .put(payload)
            .array()
    }

    fun unframe(bytes: ByteArray?): Chunk? {
        if (bytes == null || bytes.size < CHUNK_HEADER) return null
        val buffer = ByteBuffer.wrap(bytes)
        val blob = buffer.get().toInt() and 0xFF
        val version = buffer.int.toLong() and 0xFFFFFFFFL
        val index = buffer.short.toInt() and 0xFFFF
        val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return Chunk(blob, version, index, payload)
    }

    private fun clean(value: String): String = value.replace("\n", " ").replace("\r", " ")
}
