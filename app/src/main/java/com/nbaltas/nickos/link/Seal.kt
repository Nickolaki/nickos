package com.nbaltas.nickos.link

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * The crypto of the "sec2" link. Pure JVM (no Android imports) so it runs in plain unit tests, and only
 * JDK primitives that exist on Android 7 (API 24): HmacSHA256, AES/GCM/NoPadding, EC P-256, ECDH.
 * Nothing in here logs, and nothing here prints a key.
 */

/** HmacSHA256 over the concatenation of [parts]. */
fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
    val m = Mac.getInstance("HmacSHA256")
    m.init(SecretKeySpec(key, "HmacSHA256"))
    for (p in parts) m.update(p)
    return m.doFinal()
}

/** Strict standard base64 with padding, no line breaks (java.util.Base64 needs API 26). */
object B64 {
    private const val A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val rev = IntArray(128) { -1 }.also { for (i in A.indices) it[A[i].code] = i }

    fun enc(b: ByteArray): String {
        val sb = StringBuilder((b.size + 2) / 3 * 4)
        var i = 0
        while (i < b.size) {
            val b0 = b[i].toInt() and 0xff
            val b1 = if (i + 1 < b.size) b[i + 1].toInt() and 0xff else 0
            val b2 = if (i + 2 < b.size) b[i + 2].toInt() and 0xff else 0
            sb.append(A[b0 shr 2]).append(A[(b0 and 3) shl 4 or (b1 shr 4)])
            sb.append(if (i + 1 < b.size) A[(b1 and 15) shl 2 or (b2 shr 6)] else '=')
            sb.append(if (i + 2 < b.size) A[b2 and 63] else '=')
            i += 3
        }
        return sb.toString()
    }

    /** Null on any malformed input. */
    fun dec(s: String): ByteArray? {
        if (s.length % 4 != 0) return null
        val out = java.io.ByteArrayOutputStream(s.length / 4 * 3)
        var i = 0
        while (i < s.length) {
            val last = i + 4 == s.length
            val pad = if (last) s.substring(i).count { it == '=' } else 0
            if (pad > 2 || (pad > 0 && !s.endsWith("=".repeat(pad)))) return null
            var acc = 0
            for (k in 0 until 4) {
                val ch = s[i + k]
                val v = if (ch == '=') { if (!last || k < 4 - pad) return null; 0 } else if (ch.code < 128) rev[ch.code] else -1
                if (v < 0) return null
                acc = acc shl 6 or v
            }
            out.write(acc shr 16 and 0xff)
            if (pad < 2) out.write(acc shr 8 and 0xff)
            if (pad < 1) out.write(acc and 0xff)
            i += 4
        }
        return out.toByteArray()
    }

    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}

/** EC P-256 key agreement. */
object Ecdh {
    private val curve by lazy { (keyPair().public as ECPublicKey).params }

    fun keyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** X.509 SubjectPublicKeyInfo bytes. */
    fun encode(pub: PublicKey): ByteArray = pub.encoded

    /** Throws on garbage, and on any key that is not on P-256 (a foreign curve is how invalid-curve attacks start). */
    fun decode(bytes: ByteArray): PublicKey {
        val k = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
        val p = (k as? ECPublicKey)?.params ?: throw IllegalArgumentException("not an EC key")
        require(p.curve == curve.curve && p.order == curve.order && p.generator == curve.generator) { "wrong curve" }
        return k
    }

    fun shared(priv: PrivateKey, pub: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").apply { init(priv); doPhase(pub, true) }.generateSecret()
}

/**
 * One direction pair of an AES-GCM sealed line stream. Every line is base64(nonce12 || ciphertext || 16-byte tag),
 * the nonce being 4 zero bytes then an 8-byte big-endian counter that starts at 1 and rises by one per message.
 * The receiver demands the exact next counter, so a replayed, reordered, dropped or tampered line opens to null.
 * Master uses ("m2n","n2m"), Node ("n2m","m2n"). Never reuse one Seal across sockets.
 */
class Seal(key: ByteArray, sendLabel: String, recvLabel: String) {
    private val sendKey = SecretKeySpec(hmac(key, sendLabel.toByteArray()).copyOf(16), "AES")
    private val recvKey = SecretKeySpec(hmac(key, recvLabel.toByteArray()).copyOf(16), "AES")
    private var sent = 0L
    private var expected = 1L

    @Synchronized
    fun seal(plain: ByteArray): String {
        sent++
        val nonce = nonce(sent)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, sendKey, GCMParameterSpec(128, nonce))
        return B64.enc(nonce + c.doFinal(plain))
    }

    /** Null on ANY failure: bad base64, wrong counter, tamper. */
    @Synchronized
    fun open(line: String): ByteArray? = try {
        val raw = B64.dec(line)
        if (raw == null || raw.size < 12 + 16) null
        else {
            val nonce = raw.copyOfRange(0, 12)
            if (nonce.copyOfRange(0, 4).any { it != 0.toByte() } || counter(nonce) != expected) null
            else {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, recvKey, GCMParameterSpec(128, nonce))
                val plain = c.doFinal(raw, 12, raw.size - 12)
                expected++
                plain
            }
        }
    } catch (e: Exception) { null }

    private fun nonce(n: Long): ByteArray = ByteArray(12).also { for (i in 0 until 8) it[11 - i] = (n ushr (8 * i)).toByte() }

    private fun counter(nonce: ByteArray): Long {
        var n = 0L
        for (i in 4 until 12) n = n shl 8 or (nonce[i].toLong() and 0xff)
        return n
    }
}
