package edu.gatech.cs8803.roomsense.ui

import kotlin.math.roundToInt

fun confidencePercent(confidence: Float): String {
    return "${(confidence * 100f).roundToInt().coerceIn(0, 100)}%"
}

fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0L) return "not yet"
    val seconds = ((now - millis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        else -> "${seconds / 3600}h ago"
    }
}
