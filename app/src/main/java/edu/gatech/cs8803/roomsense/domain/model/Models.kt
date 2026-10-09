package edu.gatech.cs8803.roomsense.domain.model

data class RoomModel(
    val id: String,
    val name: String,
    val color: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val createdAt: Long,
)

data class FingerprintSample(
    val id: String,
    val roomId: String,
    val roomName: String,
    val readings: Map<String, Int>,
    val capturedAtMillis: Long,
    val fromCache: Boolean = false,
)

data class RoomPrediction(
    val roomId: String?,
    val roomName: String?,
    val confidence: Float,
    val neighborCount: Int,
    val nearestDistance: Double,
) {
    val known: Boolean get() = roomId != null

    companion object {
        fun unknown() = RoomPrediction(
            roomId = null,
            roomName = null,
            confidence = 0f,
            neighborCount = 0,
            nearestDistance = Double.POSITIVE_INFINITY,
        )
    }
}
