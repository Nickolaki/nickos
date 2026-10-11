package com.nbaltas.nickos.sync

import com.nbaltas.nickos.link.LanAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressTest {
    @Test
    fun `private ranges only`() {
        for (ip in listOf("192.168.1.20", "192.168.43.1", "10.0.0.5", "172.16.0.1", "172.31.255.254")) assertTrue(ip, LanAddress.isSiteLocal(ip))
        for (ip in listOf("127.0.0.1", "0.0.0.0", "8.8.8.8", "172.32.0.1", "172.15.0.1", "169.254.3.4", "100.72.1.1", "192.168.1", "192.168.1.999", "fe80::1", "")) {
            assertFalse(ip, LanAddress.isSiteLocal(ip))
        }
    }

    @Test
    fun `only the one plain way of writing an address is accepted`() {
        for (odd in listOf("192.168.01.5", "010.0.0.1", "0xc0.168.1.5", "192.168.1.5.", "192.168.1.5.example.com", "192.168.1", "10.1", "3232235781", " 10.0.0.1", "10.0.0.1 ", "10.0.0.-1", "10.0.0.1/8")) {
            assertFalse(odd, LanAddress.isSiteLocal(odd))
            assertNull(odd, LanAddress.octets(odd))
        }
        assertEquals(listOf(192, 168, 43, 1), LanAddress.octets("192.168.43.1")!!.map { it.toInt() and 0xff })
    }

    @Test
    fun `what WifiManager says wins when an interface really has it`() {
        val found = listOf("rmnet0" to "10.20.30.40", "wlan0" to "192.168.1.20")
        assertEquals("192.168.1.20", LanAddress.choose(found, "192.168.1.20"))
        assertEquals("192.168.1.20", LanAddress.choose(found, "192.168.9.9"))
    }

    @Test
    fun `a hotspot interface is found without WifiManager`() {
        assertEquals("192.168.43.1", LanAddress.choose(listOf("rmnet_data0" to "10.11.12.13", "ap0" to "192.168.43.1"), null))
        assertEquals("192.168.43.1", LanAddress.choose(listOf("rmnet_data0" to "100.80.1.2", "swlan0" to "192.168.43.1"), null))
    }

    @Test
    fun `any private address is better than none`() {
        assertEquals("10.0.2.15", LanAddress.choose(listOf("eth0" to "10.0.2.15"), null))
    }

    @Test
    fun `never a loopback, wildcard or public address`() {
        assertNull(LanAddress.choose(listOf("lo" to "127.0.0.1", "any" to "0.0.0.0", "rmnet0" to "100.80.1.2"), "0.0.0.0"))
        assertNull(LanAddress.choose(emptyList(), "192.168.1.20"))
    }
}
