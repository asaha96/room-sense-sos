package edu.gatech.cs8803.roomsense.domain.beacon

import java.util.UUID

/**
 * Everything a responder phone needs to reach the caller's map: the hotspot
 * credentials, the port the HTTP server actually bound, and the caller's own
 * addresses on the hotspot interface.
 *
 * The BLE advertisement cannot hold this, so the caller serves it from a
 * read-only GATT characteristic and the responder reads it after discovery.
 * The hotspot password is already shown on the caller's screen for anyone
 * nearby to type in, so serving it to nearby responders adds no new exposure.
 */
data class JoinInfo(
    val code: String,
    val ssid: String,
    val password: String?,
    val security: String = SECURITY_WPA2,
    val port: Int?,
    val hosts: List<String> = emptyList(),
) {
    companion object {
        const val SECURITY_OPEN = "open"
        const val SECURITY_WPA2 = "wpa2"
        const val SECURITY_WPA3_TRANSITION = "wpa3t"
        const val SECURITY_WPA3 = "wpa3"
    }
}

object JoinInfoCodec {
    val SERVICE_UUID: UUID = UUID.fromString("00005244-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("00005245-0000-1000-8000-00805f9b34fb")

    private const val HEADER = "RSJ1"

    fun encode(info: JoinInfo): ByteArray {
        val lines = listOf(
            HEADER,
            info.code,
            info.ssid,
            info.password.orEmpty(),
            info.security,
            info.port?.toString().orEmpty(),
            info.hosts.joinToString(","),
        ).map { it.replace("\n", "").replace("\r", "") }
        return lines.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray?): JoinInfo? {
        if (bytes == null || bytes.isEmpty()) return null
        val lines = String(bytes, Charsets.UTF_8).split("\n")
        if (lines.size < 7 || lines[0] != HEADER) return null
        val code = lines[1].trim()
        val ssid = lines[2]
        if (code.isEmpty() || ssid.isEmpty()) return null
        return JoinInfo(
            code = code,
            ssid = ssid,
            password = lines[3].ifEmpty { null },
            security = lines[4].ifEmpty { JoinInfo.SECURITY_WPA2 },
            port = lines[5].toIntOrNull()?.takeIf { it in 1..65535 },
            hosts = lines[6].split(",").map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}

/** Pure helpers for finding the caller's responder server on a Wi-Fi network. */
object ResponderProbe {
    const val DEFAULT_PORT = 8088

    /** The server tries 12 consecutive ports starting at 8088. */
    private val FALLBACK_PORTS = (DEFAULT_PORT until DEFAULT_PORT + 12).toList()

    private val CODE_PATTERN = Regex("\"incidentCode\"\\s*:\\s*\"([^\"]*)\"")
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    fun candidatePorts(reported: Int?): List<Int> =
        (listOfNotNull(reported) + FALLBACK_PORTS).distinct()

    /**
     * Gateways first (the caller is the access point), then addresses the
     * caller reported, then the .1 address of each local subnet.
     */
    fun candidateHosts(
        gateways: List<String>,
        reported: List<String>,
        localAddresses: List<String>,
    ): List<String> {
        val derived = localAddresses.mapNotNull { address ->
            val match = IPV4.matchEntire(address) ?: return@mapNotNull null
            val (a, b, c, d) = match.destructured
            "$a.$b.$c.1".takeIf { d != "1" }
        }
        return (gateways + reported + derived)
            .filter { IPV4.matches(it) && it != "0.0.0.0" && !it.startsWith("127.") }
            .distinct()
    }

    fun incidentCode(statusJson: String): String? =
        CODE_PATTERN.find(statusJson)?.groupValues?.get(1)

    fun matches(statusJson: String, expectedCode: String): Boolean =
        incidentCode(statusJson)?.equals(expectedCode, ignoreCase = true) == true

    fun baseUrl(host: String, port: Int): String = "http://$host:$port/"
}
