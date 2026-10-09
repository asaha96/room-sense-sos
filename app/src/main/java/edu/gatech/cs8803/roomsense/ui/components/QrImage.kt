package edu.gatech.cs8803.roomsense.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import edu.gatech.cs8803.roomsense.ui.theme.Paper

@Composable
fun QrImage(
    payload: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "QR code",
) {
    val bitmap = remember(payload) { encodeQr(payload) }
    if (bitmap == null) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Paper)
            .size(168.dp),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier.size(168.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

private fun encodeQr(payload: String): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val size = 480
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF12171C.toInt() else 0xFFF4EFE6.toInt())
            }
        }
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
