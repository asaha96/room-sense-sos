package edu.gatech.cs8803.roomsense.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.gatech.cs8803.roomsense.di.AppGraph
import edu.gatech.cs8803.roomsense.di.viewModelFactory
import edu.gatech.cs8803.roomsense.domain.incident.IncidentCodeGenerator
import edu.gatech.cs8803.roomsense.service.SosForegroundService
import edu.gatech.cs8803.roomsense.service.SosSnapshot
import edu.gatech.cs8803.roomsense.ui.home.HomeScreen
import edu.gatech.cs8803.roomsense.ui.home.HomeViewModel
import edu.gatech.cs8803.roomsense.ui.map.MapEditorScreen
import edu.gatech.cs8803.roomsense.ui.map.MapViewModel
import edu.gatech.cs8803.roomsense.ui.preview.ResponderPreviewScreen
import edu.gatech.cs8803.roomsense.ui.responder.ResponderRoute
import edu.gatech.cs8803.roomsense.ui.sos.SosScreen
import edu.gatech.cs8803.roomsense.ui.survey.SurveyScreen
import edu.gatech.cs8803.roomsense.ui.survey.SurveyViewModel

@Composable
fun RoomSenseNav(graph: AppGraph) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var previewUrl by rememberSaveable { mutableStateOf("") }
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (!awaitingPermission) return@rememberLauncherForActivityResult
        awaitingPermission = false
        val allowed = graph.settings.demoMode || granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (allowed && !graph.session.state.value.active) {
            startIncident(context, graph)
            screen = "sos"
        }
    }

    BackHandler(enabled = screen != "home") {
        screen = if (screen == "preview") "sos" else "home"
    }

    when (screen) {
        "survey" -> {
            val model: SurveyViewModel = viewModel(factory = viewModelFactory { SurveyViewModel(graph) })
            SurveyScreen(model, onBack = { screen = "home" })
        }
        "map" -> {
            val model: MapViewModel = viewModel(factory = viewModelFactory { MapViewModel(graph) })
            MapEditorScreen(model, onBack = { screen = "home" })
        }
        "sos" -> SosScreen(
            session = graph.session,
            onBack = { screen = "home" },
            onStop = {
                stopIncident(context)
                screen = "home"
            },
            onPreview = { url ->
                previewUrl = url
                screen = "preview"
            },
        )
        "responder" -> ResponderRoute(graph, onBack = { screen = "home" })
        "preview" -> ResponderPreviewScreen(url = previewUrl, onBack = { screen = "sos" })
        else -> {
            val model: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(graph) })
            HomeScreen(
                viewModel = model,
                onSurvey = { screen = "survey" },
                onMap = { screen = "map" },
                onResponder = { screen = "responder" },
                onOpenSos = { screen = "sos" },
                onActivateSos = {
                    if (graph.session.state.value.active) {
                        screen = "sos"
                        return@HomeScreen
                    }
                    val missing = RuntimePermissions.missing(context)
                    val demo = graph.settings.demoMode
                    if (missing.isNotEmpty() && !demo) {
                        awaitingPermission = true
                        launcher.launch(missing.toTypedArray())
                        return@HomeScreen
                    }
                    if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
                    startIncident(context, graph)
                    screen = "sos"
                },
            )
        }
    }
}

private fun startIncident(context: Context, graph: AppGraph) {
    val code = IncidentCodeGenerator.generate()
    graph.settings.activeIncidentCode = code
    graph.session.update {
        SosSnapshot(
            active = true,
            code = code,
            demoMode = graph.settings.demoMode,
        )
    }
    ContextCompat.startForegroundService(
        context,
        Intent(context, SosForegroundService::class.java).setAction(SosForegroundService.ACTION_START),
    )
}

private fun stopIncident(context: Context) {
    context.startService(
        Intent(context, SosForegroundService::class.java).setAction(SosForegroundService.ACTION_STOP),
    )
}
