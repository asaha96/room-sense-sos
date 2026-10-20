package edu.gatech.cs8803.roomsense.data.server

import edu.gatech.cs8803.roomsense.domain.model.RoomModel

data class ResponderSnapshot(
    val code: String = "",
    val roomId: String? = null,
    val roomName: String? = null,
    val confidence: Float = 0f,
    val updatedAt: Long = 0L,
    val ssid: String = "",
    val demoMode: Boolean = false,
    val hasFloorPlan: Boolean = false,
    val floorPlanPath: String? = null,
    val rooms: List<RoomModel> = emptyList(),
)
