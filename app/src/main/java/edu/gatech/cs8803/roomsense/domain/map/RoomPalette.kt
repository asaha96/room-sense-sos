package edu.gatech.cs8803.roomsense.domain.map

object RoomPalette {
    private val colors = listOf(
        0xFF7C3A12.toInt(),
        0xFF2A7570.toInt(),
        0xFFE06A4F.toInt(),
        0xFF6A8496.toInt(),
        0xFF8C6BB1.toInt(),
        0xFF4F7CAC.toInt(),
        0xFFC46B8A.toInt(),
        0xFF8FA15A.toInt(),
    )

    fun colorFor(index: Int): Int = colors[index % colors.size]

    /** Normalized rectangle: x, y, width, height on a 4:3 map. */
    fun defaultRect(index: Int): FloatArray {
        val column = index % 2
        val row = index / 2
        val width = 0.42f
        val height = 0.30f
        val x = 0.06f + column * 0.48f
        val y = (0.06f + row * 0.34f).coerceAtMost(0.64f)
        return floatArrayOf(x, y, width, height)
    }
}
