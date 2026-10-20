package edu.gatech.cs8803.roomsense

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.service.SosForegroundService

class RoomSenseApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                SosForegroundService.CHANNEL_ID,
                "SOS broadcast",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Stays up while your phone is sharing a room with responders."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
