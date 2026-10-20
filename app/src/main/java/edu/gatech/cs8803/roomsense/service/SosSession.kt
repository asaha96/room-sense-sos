package edu.gatech.cs8803.roomsense.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SosSnapshot(
    val active: Boolean = false,
    val code: String? = null,
    val roomId: String? = null,
    val roomName: String? = null,
    val confidence: Float = 0f,
    val accessPointCount: Int = 0,
    val ssid: String? = null,
    val password: String? = null,
    val hotspotNote: String = "",
    val simulatedHotspot: Boolean = false,
    val bleNote: String = "",
    val port: Int? = null,
    val detectedHosts: List<String> = emptyList(),
    val scanNote: String = "",
    val updatedAt: Long = 0L,
    val demoMode: Boolean = false,
    val error: String? = null,
)

class SosSession {
    private val _state = MutableStateFlow(SosSnapshot())
    val state: StateFlow<SosSnapshot> = _state.asStateFlow()

    fun update(transform: (SosSnapshot) -> SosSnapshot) {
        _state.update(transform)
    }
}
