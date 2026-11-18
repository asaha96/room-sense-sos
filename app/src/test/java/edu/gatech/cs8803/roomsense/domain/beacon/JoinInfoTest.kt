package edu.gatech.cs8803.roomsense.domain.beacon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinInfoTest {
    @Test
    fun roundTripsAllFields() {
        val info = JoinInfo(
            code = "TEW7A2",
            ssid = "AndroidShare_4821",
            password = "k3x9 pq,2",
            security = JoinInfo.SECURITY_WPA3_TRANSITION,
            port = 8090,
            hosts = listOf("192.168.49.1", "10.0.0.1"),
        )
        assertEquals(info, JoinInfoCodec.decode(JoinInfoCodec.encode(info)))
    }

    @Test
    fun openNetworkWithoutPortDecodesNulls() {
        val info = JoinInfo("ABC234", "Room", null, JoinInfo.SECURITY_OPEN, null, emptyList())
        val decoded = JoinInfoCodec.decode(JoinInfoCodec.encode(info))!!
        assertNull(decoded.password)
        assertNull(decoded.port)
        assertTrue(decoded.hosts.isEmpty())
    }

    @Test
    fun rejectsGarbage() {
        assertNull(JoinInfoCodec.decode(null))
        assertNull(JoinInfoCodec.decode(ByteArray(0)))
        assertNull(JoinInfoCodec.decode("hello".toByteArray()))
        assertNull(JoinInfoCodec.decode("RSJ1\n\nssid\n\nwpa2\n\n".toByteArray()))
    }

    @Test
    fun portsPreferReportedThenServerRange() {
        val ports = ResponderProbe.candidatePorts(8093)
        assertEquals(8093, ports.first())
        assertEquals(12, ports.size)
        assertTrue(ports.containsAll((8088..8099).toList()))
        assertEquals(8088, ResponderProbe.candidatePorts(null).first())
    }

    @Test
    fun hostsOrderGatewayReportedDerived() {
        val hosts = ResponderProbe.candidateHosts(
            gateways = listOf("192.168.49.1", "0.0.0.0"),
            reported = listOf("192.168.49.1", "10.20.30.1", "fe80::1"),
            localAddresses = listOf("192.168.49.37", "172.16.5.1"),
        )
        assertEquals(listOf("192.168.49.1", "10.20.30.1"), hosts)
    }

    @Test
    fun derivesDotOneWhenNoGateway() {
        val hosts = ResponderProbe.candidateHosts(emptyList(), emptyList(), listOf("192.168.43.120"))
        assertEquals(listOf("192.168.43.1"), hosts)
    }

    @Test
    fun matchesIncidentCodeInStatus() {
        val json = """{"incidentCode":"TEW7A2","roomId":"a","rooms":[]}"""
        assertEquals("TEW7A2", ResponderProbe.incidentCode(json))
        assertTrue(ResponderProbe.matches(json, "tew7a2"))
        assertFalse(ResponderProbe.matches(json, "ABC234"))
        assertFalse(ResponderProbe.matches("Not found", "TEW7A2"))
    }
}
