package edu.gatech.cs8803.roomsense.domain.classifier

import edu.gatech.cs8803.roomsense.domain.model.FingerprintSample
import edu.gatech.cs8803.roomsense.domain.model.RoomPrediction

/**
 * Turns a live RSSI scan plus surveyed fingerprints into a room guess.
 * The SOS service depends on this interface, so a later model can replace kNN
 * without touching the broadcast path.
 */
interface RoomClassifier {
    fun predict(scan: Map<String, Int>, samples: List<FingerprintSample>): RoomPrediction
}
