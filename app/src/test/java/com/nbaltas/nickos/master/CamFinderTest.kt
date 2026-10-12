package com.nbaltas.nickos.master

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CamFinderTest {
    private class Fake(val answering: Set<String>) : Knock {
        val knocked: MutableList<String> = Collections.synchronizedList(ArrayList())
        override suspend fun rtsp(host: String, port: Int): Boolean { knocked += host; return host in answering }
    }

    @Test
    fun `finds the two hosts that answer out of a slash 24`() = runBlocking {
        val k = Fake(setOf("192.168.49.23", "192.168.49.40"))
        val found = CamFinder({ listOf("192.168.49.1") }, k).find()
        assertEquals(setOf("192.168.49.23", "192.168.49.40"), found.toSet())
        assertEquals(2, found.size)
    }

    @Test
    fun `the direct slash 24 is knocked before a home one`() = runBlocking {
        val k = Fake(emptySet())
        // maxInFlight 1 makes the knock order the queue order.
        CamFinder({ listOf("192.168.1.50", "192.168.49.1") }, k, CamFinder.Config(maxInFlight = 1)).find()
        val firstHome = k.knocked.indexOfFirst { it.startsWith("192.168.1.") }
        val lastDirect = k.knocked.indexOfLast { it.startsWith("192.168.49.") }
        assertTrue(lastDirect in 0 until firstHome)
    }

    @Test
    fun `own addresses are never knocked`() = runBlocking {
        val k = Fake(emptySet())
        CamFinder({ listOf("192.168.49.1", "192.168.1.50") }, k).find()
        assertFalse("192.168.49.1" in k.knocked)
        assertFalse("192.168.1.50" in k.knocked)
        assertEquals(253 + 253, k.knocked.size)
    }

    @Test
    fun `onFound fires before find returns`() = runBlocking {
        val seen = ArrayList<String>()
        val k = Fake(setOf("192.168.49.23"))
        val found = CamFinder({ listOf("192.168.49.1") }, k).find { seen += it }
        assertEquals(listOf("192.168.49.23"), seen)
        assertEquals(seen, found)
    }

    @Test
    fun `no network means nothing found and nothing knocked`() = runBlocking {
        val k = Fake(emptySet())
        assertEquals(emptyList<String>(), CamFinder({ emptyList() }, k).find())
        assertTrue(k.knocked.isEmpty())
    }

    @Test
    fun `a knock that hangs past the budget does not hang find`() = runBlocking {
        val hang = Knock { _, _ -> delay(60_000); true }
        val t0 = System.nanoTime()
        val found = CamFinder({ listOf("192.168.49.1") }, hang, CamFinder.Config(budgetMs = 300)).find()
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(found.isEmpty())
        assertTrue("took $ms ms", ms < 2_000)
    }
}
