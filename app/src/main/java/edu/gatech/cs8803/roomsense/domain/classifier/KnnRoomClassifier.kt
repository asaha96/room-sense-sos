package edu.gatech.cs8803.roomsense.domain.classifier

import edu.gatech.cs8803.roomsense.domain.model.FingerprintSample
import edu.gatech.cs8803.roomsense.domain.model.RoomPrediction
import java.util.LinkedHashSet
import kotlin.math.sqrt

/**
 * k-nearest neighbors over RSSI vectors.
 *
 * The axis set is the union of BSSIDs in the live scan and the training samples.
 * An access point that is missing from one side is filled at [MISSING_RSSI]
 * (-100 dBm), which is weaker than a real hearing and keeps the vectors aligned.
 * Votes are inverse-distance weighted. An exact match gets a very large weight
 * so confidence reaches 1 when every neighbor is that room.
 */
class KnnRoomClassifier(
    private val k: Int = 3,
) : RoomClassifier {

    init {
        require(k >= 1) { "k must be at least 1" }
    }

    override fun predict(scan: Map<String, Int>, samples: List<FingerprintSample>): RoomPrediction {
        if (scan.isEmpty() || samples.isEmpty()) return RoomPrediction.unknown()

        val accessPoints = LinkedHashSet<String>().apply {
            addAll(scan.keys)
            samples.forEach { addAll(it.readings.keys) }
        }.toList()

        val query = rssiVector(accessPoints, scan)
        val ranked = samples
            .map { sample -> sample to euclidean(query, rssiVector(accessPoints, sample.readings)) }
            .sortedBy { it.second }
        val neighbors = ranked.take(k.coerceAtMost(ranked.size))

        val weights = linkedMapOf<String, Double>()
        val bestDistance = mutableMapOf<String, Double>()
        val names = mutableMapOf<String, String>()
        for ((sample, distance) in neighbors) {
            val weight = if (distance < 1e-9) EXACT_WEIGHT else 1.0 / distance
            weights[sample.roomId] = (weights[sample.roomId] ?: 0.0) + weight
            val previous = bestDistance[sample.roomId]
            if (previous == null || distance < previous) bestDistance[sample.roomId] = distance
            names[sample.roomId] = sample.roomName
        }

        val winner = weights.entries.sortedWith(
            compareByDescending<Map.Entry<String, Double>> { it.value }
                .thenBy { bestDistance[it.key] ?: Double.POSITIVE_INFINITY }
                .thenBy { it.key },
        ).first()
        val total = weights.values.sum()
        val confidence = if (total <= 0.0) 0f else (winner.value / total).toFloat().coerceIn(0f, 1f)

        return RoomPrediction(
            roomId = winner.key,
            roomName = names[winner.key],
            confidence = confidence,
            neighborCount = neighbors.size,
            nearestDistance = neighbors.first().second,
        )
    }

    companion object {
        const val MISSING_RSSI: Int = -100
        private const val EXACT_WEIGHT = 1_000_000.0
    }
}

internal fun rssiVector(accessPoints: List<String>, readings: Map<String, Int>): DoubleArray {
    return DoubleArray(accessPoints.size) { index ->
        (readings[accessPoints[index]] ?: KnnRoomClassifier.MISSING_RSSI).toDouble()
    }
}

internal fun euclidean(left: DoubleArray, right: DoubleArray): Double {
    var sum = 0.0
    for (index in left.indices) {
        val delta = left[index] - right[index]
        sum += delta * delta
    }
    return sqrt(sum)
}
