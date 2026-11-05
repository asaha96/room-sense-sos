@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package edu.gatech.cs8803.roomsense.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.gatech.cs8803.roomsense.R

val Ink = Color(0xFFF7F8FA)
val InkRaised = Color(0xFFFFFFFF)
val InkHigh = Color(0xFFEDF0F4)
val Paper = Color(0xFF17212F)
val PaperDim = Color(0xFF526071)
val Signal = Color(0xFFDC2F2A)
val Action = Color(0xFF2457A7)
val ActionDanger = Color(0xFFB32622)
val Gold = Color(0xFF7C3A12)
val Moss = Color(0xFF0F6B3C)
val Line = Color(0xFFCBD2DC)
val MapSurface = Color(0xFFEEF1F5)
val White = Color(0xFFFFFFFF)

val RoomButtonShape = RoundedCornerShape(24.dp)
val RoomFieldShape = RoundedCornerShape(16.dp)
val RoomChipShape = RoundedCornerShape(16.dp)

private val Colors = lightColorScheme(
    primary = Action,
    onPrimary = White,
    primaryContainer = Action,
    onPrimaryContainer = White,
    secondary = Action,
    onSecondary = White,
    secondaryContainer = Action,
    onSecondaryContainer = White,
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = InkHigh,
    onSurfaceVariant = PaperDim,
    outline = Line,
    error = ActionDanger,
    onError = White,
)

private val SourceSans = FontFamily(
    Font(R.font.source_sans_3, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.source_sans_3, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.source_sans_3, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.source_sans_3, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun TextStyle.asRoom(
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: Float = 0f,
) = copy(
    fontFamily = SourceSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val BaseType = Typography()

private val AppType = Typography(
    displayLarge = BaseType.displayLarge.asRoom(FontWeight.Bold, 34, 40, -0.6f),
    headlineLarge = BaseType.headlineLarge.asRoom(FontWeight.Bold, 34, 40, -0.6f),
    headlineMedium = BaseType.headlineMedium.asRoom(FontWeight.Bold, 34, 40, -0.6f),
    headlineSmall = BaseType.headlineSmall.asRoom(FontWeight.Bold, 30, 38, -0.4f),
    titleLarge = BaseType.titleLarge.asRoom(FontWeight.SemiBold, 30, 38, -0.3f),
    titleMedium = BaseType.titleMedium.asRoom(FontWeight.SemiBold, 16, 22),
    bodyLarge = BaseType.bodyLarge.asRoom(FontWeight.Normal, 16, 24),
    bodyMedium = BaseType.bodyMedium.asRoom(FontWeight.Normal, 16, 24),
    bodySmall = BaseType.bodySmall.asRoom(FontWeight.Normal, 14, 20),
    labelLarge = BaseType.labelLarge.asRoom(FontWeight.SemiBold, 14, 20, 1.1f),
    labelMedium = BaseType.labelMedium.asRoom(FontWeight.SemiBold, 14, 20, 1.1f),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoomFieldShape,
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

fun Modifier.roomButtonHeight(): Modifier = height(48.dp)

@Composable
fun roomOutlinedBorder(color: Color = Action) = BorderStroke(1.dp, color)

@Composable
fun roomOutlinedColors(contentColor: Color = Action) = ButtonDefaults.outlinedButtonColors(
    contentColor = contentColor,
)

@Composable
fun roomFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = InkHigh,
    labelColor = Paper,
    iconColor = Paper,
    selectedContainerColor = Action,
    selectedLabelColor = White,
    selectedLeadingIconColor = White,
)

@Composable
fun roomFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = Line,
    selectedBorderColor = Action,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp,
)

@Composable
fun roomSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = White,
    checkedTrackColor = Action,
    checkedBorderColor = Action,
    uncheckedThumbColor = Paper,
    uncheckedTrackColor = InkHigh,
    uncheckedBorderColor = Line,
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
