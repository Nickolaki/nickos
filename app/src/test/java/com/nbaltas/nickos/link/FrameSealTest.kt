package com.nbaltas.nickos.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameSealTest {
    private val key = ByteArray(32) { it.toByte() }
    private fun pair() = FrameSeal(key, "n2m-screen", "m2n-screen") to FrameSeal(key, "m2n-screen", "n2m-screen")

    @Test
    fun `roundtrip both ways`() {
        val (n, m) = pair()
        assertArrayEquals("hello".toByteArray(), m.open(n.seal("hello".toByteArray())))
        assertArrayEquals("back".toByteArray(), n.open(m.seal("back".toByteArray())))
        assertArrayEquals("again".toByteArray(), m.open(n.seal("again".toByteArray())))
    }

    @Test
    fun `binary data and an empty frame roundtrip`() {
        val (n, m) = pair()
        val bin = ByteArray(5000) { (it * 7).toByte() }
        assertArrayEquals(bin, m.open(n.seal(bin)))
        assertArrayEquals(ByteArray(0), m.open(n.seal(ByteArray(0))))
    }

    @Test
    fun `a frame is nonce, ciphertext and tag with no base64`() {
        val (n, _) = pair()
        val raw = n.seal(ByteArray(10))
        assertEquals(12 + 10 + 16, raw.size)
        assertArrayEquals(ByteArray(11), raw.copyOfRange(0, 11))
        assertEquals(1, raw[11].toInt())
    }

    @Test
    fun `one flipped byte in the ciphertext, the tag or the nonce opens to null`() {
        for (at in listOf(14, 25, 30)) {
            val (n, m) = pair()
            val raw = n.seal("secret".toByteArray())
            raw[at] = (raw[at].toInt() xor 1).toByte()
            assertNull("byte $at", m.open(raw))
        }
    }

    @Test
    fun `a failed open does not move the counter`() {
        val (n, m) = pair()
        val good = n.seal("one".toByteArray())
        val bad = good.copyOf().also { it[15] = (it[15].toInt() xor 1).toByte() }
        assertNull(m.open(bad))
        assertArrayEquals("one".toByteArray(), m.open(good))
    }

    @Test
    fun `a replayed frame opens to null`() {
        val (n, m) = pair()
        val f = n.seal("once".toByteArray())
        assertArrayEquals("once".toByteArray(), m.open(f))
        assertNull(m.open(f))
    }

    @Test
    fun `a reordered or dropped frame opens to null`() {
        val (n, m) = pair()
        val f1 = n.seal("1".toByteArray())
        val f2 = n.seal("2".toByteArray())
        val f3 = n.seal("3".toByteArray())
        assertNull("2 before 1", m.open(f2))
        assertNull("3 skips ahead", m.open(f3))
        assertArrayEquals("1".toByteArray(), m.open(f1))
        assertArrayEquals("2".toByteArray(), m.open(f2))
    }

    @Test
    fun `the wrong key opens to null`() {
        val (n, _) = pair()
        val other = FrameSeal(ByteArray(32) { 99 }, "m2n-screen", "n2m-screen")
        assertNull(other.open(n.seal("secret".toByteArray())))
    }

    @Test
    fun `a frame sealed for one direction does not open in the same direction`() {
        val (n, _) = pair()
        val twin = FrameSeal(key, "n2m-screen", "m2n-screen")
        assertNull(twin.open(n.seal("x".toByteArray())))
    }

    @Test
    fun `short and garbage input opens to null`() {
        val (_, m) = pair()
        assertNull(m.open(ByteArray(0)))
        assertNull(m.open(ByteArray(27)))
        assertNull(m.open(ByteArray(100) { 7 }))
    }
}
