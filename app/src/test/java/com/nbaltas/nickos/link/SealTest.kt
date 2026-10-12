package com.nbaltas.nickos.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class SealTest {
    private val key = ByteArray(32) { it.toByte() }
    private fun pair() = Seal(key, "m2n", "n2m") to Seal(key, "n2m", "m2n")

    @Test
    fun `roundtrip both ways`() {
        val (m, n) = pair()
        assertArrayEquals("hello".toByteArray(), n.open(m.seal("hello".toByteArray())))
        assertArrayEquals("back".toByteArray(), m.open(n.seal("back".toByteArray())))
        assertArrayEquals("again".toByteArray(), n.open(m.seal("again".toByteArray())))
    }

    @Test
    fun `one flipped ciphertext byte opens to null`() {
        val (m, n) = pair()
        val raw = B64.dec(m.seal("secret".toByteArray()))!!
        raw[raw.size - 20] = (raw[raw.size - 20].toInt() xor 1).toByte()
        assertNull(n.open(B64.enc(raw)))
    }

    @Test
    fun `the same line twice opens null the second time`() {
        val (m, n) = pair()
        val line = m.seal("once".toByteArray())
        assertNotNull(n.open(line))
        assertNull(n.open(line))
    }

    @Test
    fun `lines swapped in order open null`() {
        val (m, n) = pair()
        val a = m.seal("a".toByteArray())
        val b = m.seal("b".toByteArray())
        assertNull(n.open(b))
        assertNotNull(n.open(a))
    }

    @Test
    fun `a line sealed for the wrong direction does not open`() {
        val (m, _) = pair()
        val other = Seal(key, "m2n", "n2m")
        assertNull(other.open(m.seal("x".toByteArray())))
    }

    @Test
    fun `garbage opens null`() {
        val (_, n) = pair()
        for (s in listOf("", "!!!!", "AAAA", "not base64 at all", B64.enc(ByteArray(40)))) assertNull(s, n.open(s))
    }

    @Test
    fun `base64 roundtrips and rejects junk`() {
        for (len in 0..40) {
            val b = ByteArray(len) { (it * 7 + len).toByte() }
            assertArrayEquals(b, B64.dec(B64.enc(b)))
        }
        assertEquals("TWFu", B64.enc("Man".toByteArray()))
        assertEquals("TWE=", B64.enc("Ma".toByteArray()))
        for (s in listOf("TWF", "TW=u", "T===", "TWFu\n", "TWF*")) assertNull(s, B64.dec(s))
    }

    @Test
    fun `ecdh agrees and refuses garbage and foreign curves`() {
        val a = Ecdh.keyPair()
        val b = Ecdh.keyPair()
        assertArrayEquals(
            Ecdh.shared(a.private, Ecdh.decode(Ecdh.encode(b.public))),
            Ecdh.shared(b.private, Ecdh.decode(Ecdh.encode(a.public))),
        )
        try { Ecdh.decode(ByteArray(10)); fail() } catch (_: Exception) {}
        val p384 = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }.generateKeyPair()
        try { Ecdh.decode(p384.public.encoded); fail("foreign curve accepted") } catch (_: Exception) {}
    }

    @Test
    fun `hmac is keyed and order sensitive`() {
        val x = hmac(key, "a".toByteArray(), "b".toByteArray())
        assertArrayEquals(x, hmac(key, "ab".toByteArray()))
        assertTrue(!x.contentEquals(hmac(key, "ba".toByteArray())))
        assertTrue(!x.contentEquals(hmac(ByteArray(32), "ab".toByteArray())))
    }
}
