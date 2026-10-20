package edu.gatech.cs8803.roomsense.domain.incident

/**
 * ZXing WIFI: payload. Special characters are escaped so a responder's camera
 * joins the hotspot the SOS screen is actually showing.
 */
object WifiQr {
    fun payload(ssid: String, password: String?): String {
        val escapedSsid = escape(ssid)
        return if (password.isNullOrEmpty()) {
            "WIFI:T:nopass;S:$escapedSsid;;"
        } else {
            "WIFI:T:WPA;S:$escapedSsid;P:${escape(password)};;"
        }
    }

    fun escape(value: String): String = buildString(value.length) {
        for (character in value) {
            if (character == '\\' || character == ';' || character == ',' || character == ':' || character == '"') {
                append('\\')
            }
            append(character)
        }
    }
}
