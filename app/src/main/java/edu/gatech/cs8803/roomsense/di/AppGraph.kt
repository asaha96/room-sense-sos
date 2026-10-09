package edu.gatech.cs8803.roomsense.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.gatech.cs8803.roomsense.RoomSenseApplication
import edu.gatech.cs8803.roomsense.data.db.RoomSenseDatabase
import edu.gatech.cs8803.roomsense.data.repo.BuildingRepository
import edu.gatech.cs8803.roomsense.data.settings.SettingsStore
import edu.gatech.cs8803.roomsense.data.wifi.AndroidWifiSource
import edu.gatech.cs8803.roomsense.data.wifi.DemoWifiSource
import edu.gatech.cs8803.roomsense.data.wifi.WifiFingerprintSource
import edu.gatech.cs8803.roomsense.domain.classifier.KnnRoomClassifier
import edu.gatech.cs8803.roomsense.domain.classifier.RoomClassifier
import edu.gatech.cs8803.roomsense.service.SosSession

class AppGraph(val appContext: Context) {
    val database: RoomSenseDatabase = RoomSenseDatabase.create(appContext)
    val settings: SettingsStore = SettingsStore(appContext)
    val repository: BuildingRepository = BuildingRepository(database)
    val session: SosSession = SosSession()

    /**
     * Swap this binding when a better room model is ready. Survey storage and
     * the SOS loop only see [RoomClassifier].
     */
    val classifier: RoomClassifier = KnnRoomClassifier(k = 3)

    fun wifiSource(): WifiFingerprintSource {
        val current = settings.state.value
        return if (current.demoMode) {
            DemoWifiSource { settings.state.value.demoStandingRoomId }
        } else {
            AndroidWifiSource(appContext)
        }
    }
}

fun viewModelFactory(create: () -> ViewModel): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
            @Suppress("UNCHECKED_CAST")
            return create() as VM
        }
    }
}

val Context.roomSenseGraph: AppGraph
    get() = (applicationContext as RoomSenseApplication).graph
