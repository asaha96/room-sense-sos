package edu.gatech.cs8803.roomsense.data.db

import org.json.JSONObject

internal object RssiJson {
    fun encode(readings: Map<String, Int>): String {
        val obj = JSONObject()
        readings.forEach { (bssid, rssi) -> obj.put(bssid, rssi) }
        return obj.toString()
    }

    fun decode(json: String): Map<String, Int> {
        if (json.isBlank()) return emptyMap()
        val obj = JSONObject(json)
        val map = linkedMapOf<String, Int>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.getInt(key)
        }
        return map
    }
}
