package edu.gatech.cs8803.roomsense.domain.demo

import kotlin.random.Random

data class DemoRoom(
    val id: String,
    val name: String,
    val color: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rssi: List<Int>,
)

/**
 * A four-room wing with stable, separable RSSI centroids. Demo mode surveys
 * and live scans are noise around these vectors, so kNN works on an emulator
 * that has no Wi-Fi scan results.
 */
object DemoRadio {
    val accessPoints: List<Pair<String, String>> = listOf(
        "02:11:22:33:00:01" to "GT-Atrium",
        "02:11:22:33:00:02" to "GT-Studio",
        "02:11:22:33:00:03" to "GT-Office",
        "02:11:22:33:00:04" to "GT-Hall",
        "02:11:22:33:00:05" to "GT-Stairs",
    )

    val rooms: List<DemoRoom> = listOf(
        DemoRoom(
            id = "atrium",
            name = "Atrium",
            color = 0xFFC6A15B.toInt(),
            x = 0.06f,
            y = 0.08f,
            width = 0.42f,
            height = 0.38f,
            rssi = listOf(-35, -70, -75, -68, -80),
        ),
        DemoRoom(
            id = "studio",
            name = "Studio",
            color = 0xFF3F8F8A.toInt(),
            x = 0.52f,
            y = 0.08f,
            width = 0.42f,
            height = 0.38f,
            rssi = listOf(-72, -38, -66, -74, -77),
        ),
        DemoRoom(
            id = "office",
            name = "Office",
            color = 0xFFD4654A.toInt(),
            x = 0.06f,
            y = 0.54f,
            width = 0.42f,
            height = 0.36f,
            rssi = listOf(-78, -69, -36, -71, -82),
        ),
        DemoRoom(
            id = "corridor",
            name = "Corridor",
            color = 0xFF6E8CA0.toInt(),
            x = 0.52f,
            y = 0.54f,
            width = 0.42f,
            height = 0.36f,
            rssi = listOf(-60, -58, -64, -40, -55),
        ),
    )

    fun byId(id: String): DemoRoom? = rooms.find { it.id == id }

    fun nameOf(id: String): String? = byId(id)?.name

    fun cleanFingerprint(roomId: String): Map<String, Int> {
        val room = byId(roomId) ?: rooms.first()
        return accessPoints.mapIndexed { index, (bssid, _) -> bssid to room.rssi[index] }.toMap()
    }

    fun fingerprint(roomId: String, random: Random, noise: Int = 3): Map<String, Int> {
        val clean = cleanFingerprint(roomId)
        if (noise <= 0) return clean
        return clean.mapValues { (_, rssi) ->
            (rssi + random.nextInt(-noise, noise + 1)).coerceIn(-100, -20)
        }
    }

    /**
     * Demo reading for any room id. Known wing rooms use the designed
     * centroids. Other ids get a stable hash-based centroid so a survey of a
     * room the user named still matches a later "standing in" scan.
     */
    fun scan(roomKey: String, random: Random, noise: Int = 3): Map<String, Int> {
        if (byId(roomKey) != null) return fingerprint(roomKey, random, noise)
        val hash = roomKey.hashCode()
        return accessPoints.mapIndexed { index, (bssid, _) ->
            val shift = ((hash ushr (index * 4)) and 15) - 7
            val centroid = (-42 - index * 8 + shift).coerceIn(-92, -28)
            val heard = if (noise <= 0) {
                centroid
            } else {
                (centroid + random.nextInt(-noise, noise + 1)).coerceIn(-100, -20)
            }
            bssid to heard
        }.toMap()
    }
}
