package edu.gatech.cs8803.roomsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import edu.gatech.cs8803.roomsense.di.roomSenseGraph
import edu.gatech.cs8803.roomsense.ui.RoomSenseNav
import edu.gatech.cs8803.roomsense.ui.theme.RoomSenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF12171C.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF12171C.toInt()),
        )
        val graph = roomSenseGraph
        setContent {
            RoomSenseTheme {
                RoomSenseNav(graph)
            }
        }
    }
}
