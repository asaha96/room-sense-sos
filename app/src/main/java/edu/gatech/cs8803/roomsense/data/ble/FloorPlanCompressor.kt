package edu.gatech.cs8803.roomsense.data.ble

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Shrinks the imported floor plan for BLE. The map is drawn in a 4:3 box, so
 * anything past 960x720 is wasted. At roughly 10 KB/s over GATT, a 40-90 KB
 * JPEG arrives in a few seconds, after the room layout is already showing.
 */
object FloorPlanCompressor {
    private const val MAX_WIDTH = 960
    private const val MAX_HEIGHT = 720
    private const val TARGET_BYTES = 90_000

    fun compress(path: String?): ByteArray {
        if (path.isNullOrBlank()) return ByteArray(0)
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return ByteArray(0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ByteArray(0)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH || bounds.outHeight / (sample * 2) >= MAX_HEIGHT) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return ByteArray(0)
        val scale = minOf(MAX_WIDTH.toFloat() / decoded.width, MAX_HEIGHT.toFloat() / decoded.height, 1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        var quality = 70
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 15
        } while (bytes.size > TARGET_BYTES && quality >= 25)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return bytes
    }
}
