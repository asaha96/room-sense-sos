package edu.gatech.cs8803.roomsense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.gatech.cs8803.roomsense.ui.theme.ActionDanger
import edu.gatech.cs8803.roomsense.ui.theme.Signal
import edu.gatech.cs8803.roomsense.ui.theme.White
import kotlinx.coroutines.delay
import android.view.HapticFeedbackConstants

private const val HOLD_NANOS = 1_100_000_000f
private val SignalTrack = Color(0xFFF4CCC9)

@Composable
fun HoldToActivateButton(
    onActivated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current

    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        val start = System.nanoTime()
        while (true) {
            progress = ((System.nanoTime() - start) / HOLD_NANOS).coerceIn(0f, 1f)
            if (progress >= 1f) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onActivated()
                break
            }
            delay(16)
        }
    }

    Box(
        modifier = modifier
            .size(232.dp)
            .semantics { contentDescription = "SOS. Hold for about one second to activate." }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            holding = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = SignalTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            if (progress > 0f) {
                val closed = progress >= 1f
                drawArc(
                    color = ActionDanger,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = stroke,
                        cap = if (closed) StrokeCap.Butt else StrokeCap.Round,
                    ),
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(188.dp)
                .scale(if (holding) 0.96f else 1f),
            shape = CircleShape,
            color = Signal,
            shadowElevation = if (holding) 0.dp else 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "SOS",
                    color = White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                )
            }
        }
    }
}
