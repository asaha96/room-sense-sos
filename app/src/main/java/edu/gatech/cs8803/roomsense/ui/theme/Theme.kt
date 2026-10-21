package edu.gatech.cs8803.roomsense.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF12171C)
val InkRaised = Color(0xFF1C242C)
val InkHigh = Color(0xFF27313B)
val Paper = Color(0xFFF4EFE6)
val PaperDim = Color(0xFFC9C2B6)
val Signal = Color(0xFFE23B2F)
val Gold = Color(0xFFC6A15B)
val Moss = Color(0xFF8FCBB0)
val Line = Color(0xFF33404C)

private val Colors = darkColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = InkHigh,
    onSurfaceVariant = PaperDim,
    error = Signal,
    onError = Color.White,
    outline = Line,
)

private val BaseType = Typography()

private val AppType = Typography(
    displayLarge = BaseType.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineLarge = BaseType.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.Medium),
    bodyLarge = BaseType.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = BaseType.bodyMedium.copy(lineHeight = 20.sp),
    labelLarge = BaseType.labelLarge.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium),
)

val CodeFamily = FontFamily.Monospace

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun RoomSenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = AppType,
        shapes = AppShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            content()
        }
    }
}
