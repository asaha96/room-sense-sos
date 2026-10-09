package edu.gatech.cs8803.roomsense.data.wifi

data class ScanCapture(
    val readings: Map<String, Int>,
    val fromCache: Boolean,
    val throttled: Boolean,
    val message: String,
)

/**
 * Live RSSI source used by survey and by the SOS loop.
 *
 * [roomHint] is only meaningful for the demo source, which has to invent a
 * fingerprint for the room being labeled. A hardware scan ignores it.
 */
interface WifiFingerprintSource {
    suspend fun capture(roomHint: String? = null): ScanCapture
}
