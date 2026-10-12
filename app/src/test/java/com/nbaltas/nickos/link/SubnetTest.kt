package com.nbaltas.nickos.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetTest {
    @Test
    fun `a hotspot address gives the other 253 hosts of its slash 24`() {
        val hosts = Subnet.hosts("192.168.43.17")!!
        assertEquals(253, hosts.size)
        assertFalse("192.168.43.17" in hosts)
        assertFalse("192.168.43.0" in hosts)
        assertFalse("192.168.43.255" in hosts)
        assertTrue("192.168.43.1" in hosts && "192.168.43.254" in hosts)
        assertTrue(hosts.all { it.startsWith("192.168.43.") })
        assertEquals(hosts.size, hosts.toSet().size)
    }

    @Test
    fun `the tablet hosting the hotspot is dot 1 and still gets 253 others`() {
        val hosts = Subnet.hosts("192.168.43.1")!!
        assertEquals(253, hosts.size)
        assertFalse("192.168.43.1" in hosts)
        assertTrue("192.168.43.2" in hosts)
    }

    @Test
    fun `addresses that are not site local give nothing`() {
        for (ip in listOf("8.8.8.8", "127.0.0.1", "169.254.1.1", "0.0.0.0", "fe80::1", "", "192.168.1", "172.32.0.1", "not an ip")) {
            assertNull(ip, Subnet.hosts(ip))
        }
    }

    @Test
    fun `an iphone slash 28 address still enumerates the whole slash 24`() {
        val hosts = Subnet.hosts("172.20.10.3")!!
        assertEquals(253, hosts.size)
        assertNotNull(hosts.firstOrNull { it == "172.20.10.2" })
        assertTrue(hosts.all { it.startsWith("172.20.10.") })
    }
}
