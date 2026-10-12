package com.nbaltas.nickos.sync

import java.security.MessageDigest

/** A video filename's episode code, when it has one. Anything without a match is a movie. */
data class Episode(val show: String, val season: Int, val number: Int, val label: String)

/**
 * Pure helpers for identifying and naming video files. No android.* or org.json imports so this
 * runs as a plain JVM test; the MediaStore layer feeds it bytes and strings.
 */
object VideoId {
    private val EPISODE_REGEX = Regex("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b")
    private val FORBIDDEN_NAME_CHARS = Regex("[\\\\/:*?\"<>|]")
    private val WHITESPACE = Regex("\\s+")

    /**
     * 16 lowercase hex chars of SHA-256 over an 8-byte big-endian [size] then [head] then
     * [tail]. For a file no bigger than 2 MiB the caller passes the whole file as [head] and an
     * empty [tail] (the two never overlap on the wire).
     */
    fun fingerprint(size: Long, head: ByteArray, tail: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sizeBytes = ByteArray(8)
        for (i in 0 until 8) sizeBytes[7 - i] = ((size shr (8 * i)) and 0xFF).toByte()
        digest.update(sizeBytes)
        digest.update(head)
        digest.update(tail)
        val bytes = digest.digest()
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) hex.append("%02x".format(b))
        return hex.substring(0, 16)
    }

    /** "Big.Buck.Bunny_1080p.mp4" -> "Big Buck Bunny 1080p". Strips only the last extension. */
    fun title(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        val noExt = if (dot > 0) displayName.substring(0, dot) else displayName
        return noExt.replace('.', ' ').replace('_', ' ').replace(WHITESPACE, " ").trim()
    }

    /** "Star Harbour S02E05 The Lighthouse" -> show "Star Harbour", season 2, number 5. */
    fun episode(text: String): Episode? {
        val m = EPISODE_REGEX.find(text) ?: return null
        val show = text.substring(0, m.range.first).trim()
        val label = text.substring(m.range.last + 1).trim()
        return Episode(show, m.groupValues[1].toInt(), m.groupValues[2].toInt(), label)
    }

    /** Strips filesystem-hostile characters; never returns an empty string. */
    fun safeName(name: String): String {
        val cleaned = name.replace(FORBIDDEN_NAME_CHARS, "_").trim()
        return cleaned.ifEmpty { "video" }
    }
}
