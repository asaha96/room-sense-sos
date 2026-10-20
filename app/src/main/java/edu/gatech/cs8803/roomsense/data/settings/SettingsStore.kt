package edu.gatech.cs8803.roomsense.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val demoMode: Boolean = false,
    val attemptFixedSsid: Boolean = false,
    val demoStandingRoomId: String? = null,
    val floorPlanPath: String? = null,
    val activeIncidentCode: String? = null,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("roomsense", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    var demoMode: Boolean
        get() = _state.value.demoMode
        set(value) = write(_state.value.copy(demoMode = value)) { putBoolean(KEY_DEMO, value) }

    var attemptFixedSsid: Boolean
        get() = _state.value.attemptFixedSsid
        set(value) = write(_state.value.copy(attemptFixedSsid = value)) { putBoolean(KEY_FIXED, value) }

    var demoStandingRoomId: String?
        get() = _state.value.demoStandingRoomId
        set(value) = write(_state.value.copy(demoStandingRoomId = value)) {
            if (value == null) remove(KEY_STANDING) else putString(KEY_STANDING, value)
        }

    var floorPlanPath: String?
        get() = _state.value.floorPlanPath
        set(value) = write(_state.value.copy(floorPlanPath = value)) {
            if (value == null) remove(KEY_PLAN) else putString(KEY_PLAN, value)
        }

    var activeIncidentCode: String?
        get() = _state.value.activeIncidentCode
        set(value) = write(_state.value.copy(activeIncidentCode = value)) {
            if (value == null) remove(KEY_CODE) else putString(KEY_CODE, value)
        }

    private fun read(): AppSettings = AppSettings(
        demoMode = prefs.getBoolean(KEY_DEMO, false),
        attemptFixedSsid = prefs.getBoolean(KEY_FIXED, false),
        demoStandingRoomId = prefs.getString(KEY_STANDING, null),
        floorPlanPath = prefs.getString(KEY_PLAN, null),
        activeIncidentCode = prefs.getString(KEY_CODE, null),
    )

    private fun write(next: AppSettings, edit: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(edit).apply()
        _state.value = next
    }

    private companion object {
        const val KEY_DEMO = "demo_mode"
        const val KEY_FIXED = "fixed_ssid"
        const val KEY_STANDING = "standing_room"
        const val KEY_PLAN = "floor_plan"
        const val KEY_CODE = "active_code"
    }
}
