package com.nbaltas.nickos.cam

/** The headers of one picture in a tablet camera stream (multipart/x-mixed-replace). */
data class PartHeader(val length: Int, val rotation: Int = 0, val seq: Long = 0)

object MjpegParser {
    /** No single picture is anywhere near this big; a longer claim is a broken or hostile stream. */
    const val MAX_PART = 1024 * 1024

    /**
     * Reads the header lines of one part. A boundary line ("--nickos") and blank lines before
     * the headers are skipped. Null when Content-Length is missing, not a number, not positive
     * or absurdly large. X-Rotation defaults to 0.
     */
    fun parsePartHeaders(lines: List<String>): PartHeader? {
        var length: Int? = null
        var rotation = 0
        var seq = 0L
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("--")) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val value = line.substring(colon + 1).trim()
            when (line.substring(0, colon).trim().lowercase()) {
                "content-length" -> length = value.toIntOrNull() ?: return null
                "x-rotation" -> rotation = value.toIntOrNull()?.takeIf { it in 0..359 } ?: 0
                "x-seq" -> seq = value.toLongOrNull() ?: 0L
            }
        }
        val n = length ?: return null
        if (n <= 0 || n > MAX_PART) return null
        return PartHeader(n, rotation, seq)
    }
}
