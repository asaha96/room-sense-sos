package edu.gatech.cs8803.roomsense.domain.classifier

import edu.gatech.cs8803.roomsense.domain.model.FingerprintSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnnRoomClassifierTest {
    @Test
    fun missingAccessPointsAreFilledAtMinus100() {
        val vector = rssiVector(listOf("aa", "bb"), mapOf("aa" to -42))
        assertEquals(-42.0, vector[0], 0.001)
        assertEquals(KnnRoomClassifier.MISSING_RSSI.toDouble(), vector[1], 0.001)
        assertEquals(-100, KnnRoomClassifier.MISSING_RSSI)
    }

    @Test
    fun predictsRoomWhenQueryOmitsAnApSeenInTraining() {
        val samples = listOf(
            sample("a", "Atrium", mapOf("ap1" to -40, "ap2" to -50)),
            sample("a", "Atrium", mapOf("ap1" to -42, "ap2" to -51)),
            sample("b", "Office", mapOf("ap1" to -80, "ap2" to -50)),
            sample("b", "Office", mapOf("ap1" to -78, "ap2" to -52)),
        )
        val prediction = KnnRoomClassifier(k = 3).predict(mapOf("ap1" to -41), samples)
        assertEquals("a", prediction.roomId)
        assertEquals("Atrium", prediction.roomName)
        assertTrue(prediction.confidence > 0.5f)
    }

    @Test
    fun extraAccessPointDoesNotHideTheMatchingRoom() {
        val samples = listOf(
            sample("lab", "Lab", mapOf("ap1" to -40)),
            sample("hall", "Hall", mapOf("ap1" to -80)),
        )
        val prediction = KnnRoomClassifier(k = 1).predict(mapOf("ap1" to -40, "ap3" to -30), samples)
        assertEquals("lab", prediction.roomId)
    }

    @Test
    fun exactMatchHasFullConfidence() {
        val readings = mapOf("ap1" to -40, "ap2" to -60)
        val samples = (1..3).map { sample("lab", "Lab", readings, "s$it") }
        val prediction = KnnRoomClassifier(k = 3).predict(readings, samples)
        assertEquals("lab", prediction.roomId)
        assertEquals(1f, prediction.confidence, 0.001f)
        assertEquals(0.0, prediction.nearestDistance, 0.001)
        assertEquals(3, prediction.neighborCount)
    }

    @Test
    fun splitNeighborsLowerConfidence() {
        val readings = mapOf("ap1" to -50)
        val samples = listOf(
            sample("a", "A", readings, "1"),
            sample("b", "B", readings, "2"),
        )
        val prediction = KnnRoomClassifier(k = 2).predict(readings, samples)
        assertEquals(0.5f, prediction.confidence, 0.001f)
        assertEquals("a", prediction.roomId)
    }

    @Test
    fun kLargerThanTheLibraryUsesEverySample() {
        val readings = mapOf("ap1" to -55)
        val prediction = KnnRoomClassifier(k = 9).predict(readings, listOf(sample("a", "A", readings)))
        assertEquals("a", prediction.roomId)
        assertEquals(1, prediction.neighborCount)
    }

    @Test
    fun emptyScanOrEmptyLibraryIsUnknown() {
        val samples = listOf(sample("a", "A", mapOf("ap1" to -40)))
        val noScan = KnnRoomClassifier().predict(emptyMap(), samples)
        val noLibrary = KnnRoomClassifier().predict(mapOf("ap1" to -40), emptyList())
        assertNull(noScan.roomId)
        assertEquals(0f, noScan.confidence, 0f)
        assertNull(noLibrary.roomId)
        assertEquals(0f, noLibrary.confidence, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveK() {
        KnnRoomClassifier(k = 0)
    }

    private fun sample(
        roomId: String,
        roomName: String,
        readings: Map<String, Int>,
        id: String = roomId + readings.hashCode(),
    ) = FingerprintSample(
        id = id,
        roomId = roomId,
        roomName = roomName,
        readings = readings,
        capturedAtMillis = 0L,
    )
}
