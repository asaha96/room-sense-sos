package edu.gatech.cs8803.roomsense.data.wifi

import edu.gatech.cs8803.roomsense.domain.demo.DemoRadio
import kotlinx.coroutines.delay
import kotlin.random.Random

class DemoWifiSource(
    private val standingRoomId: () -> String?,
) : WifiFingerprintSource {
    override suspend fun capture(roomHint: String?): ScanCapture {
        delay(280)
        val id = roomHint ?: standingRoomId() ?: DemoRadio.rooms.first().id
        val readings = DemoRadio.scan(id, Random.Default)
        val label = DemoRadio.nameOf(id) ?: "the selected room"
        return ScanCapture(
            readings = readings,
            fromCache = false,
            throttled = false,
            message = "Demo scan for $label. No Wi-Fi hardware was used.",
        )
    }
}
