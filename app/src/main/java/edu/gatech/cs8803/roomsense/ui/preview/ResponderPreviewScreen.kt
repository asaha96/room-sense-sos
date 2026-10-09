package edu.gatech.cs8803.roomsense.ui.preview

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import edu.gatech.cs8803.roomsense.ui.theme.PaperDim

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
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Responder preview", style = MaterialTheme.typography.titleLarge)
                Text(url, color = PaperDim, style = MaterialTheme.typography.bodySmall)
            }
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
