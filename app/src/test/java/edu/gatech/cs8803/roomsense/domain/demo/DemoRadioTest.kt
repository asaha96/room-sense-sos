package edu.gatech.cs8803.roomsense.domain.demo

import edu.gatech.cs8803.roomsense.domain.classifier.KnnRoomClassifier
import edu.gatech.cs8803.roomsense.domain.model.FingerprintSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DemoRadioTest {
    @Test
    fun noisyDemoScansStillClassify() {
        val classifier = KnnRoomClassifier(k = 3)
        val samples = DemoRadio.rooms.flatMap { room ->
            (0 until 4).map { index ->
                FingerprintSample(
                    id = "${room.id}-$index",
                    roomId = room.id,
                    roomName = room.name,
                    readings = DemoRadio.fingerprint(room.id, Random(index * 17L + room.id.hashCode())),
                    capturedAtMillis = 0L,
                )
            }
        }
        DemoRadio.rooms.forEach { room ->
            val scan = DemoRadio.fingerprint(room.id, Random(99))
            val prediction = classifier.predict(scan, samples)
            assertEquals(room.name, room.id, prediction.roomId)
            assertEquals(room.name, prediction.roomName)
            assertTrue(prediction.confidence >= 0.5f)
        }
    }

    @Test
    fun cleanFingerprintsUseEveryAccessPoint() {
        val fingerprint = DemoRadio.cleanFingerprint("office")
        assertEquals(DemoRadio.accessPoints.size, fingerprint.size)
        assertTrue(fingerprint.values.all { it in -100..-20 })
    }
}
