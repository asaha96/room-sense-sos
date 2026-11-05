package edu.gatech.cs8803.roomsense.ui.preview

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import edu.gatech.cs8803.roomsense.ui.components.ScreenHeader

@Composable
fun ResponderPreviewScreen(
    url: String,
    onBack: () -> Unit,
) {
    val webViews = remember { mutableListOf<WebView>() }
    DisposableEffect(Unit) {
        onDispose {
            webViews.forEach { view ->
                view.stopLoading()
                view.destroy()
            }
            webViews.clear()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding()) {
            ScreenHeader(title = "Responder preview", onBack = onBack, subtitle = url)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                    webViews += this
                }
            },
        )
    }
}
