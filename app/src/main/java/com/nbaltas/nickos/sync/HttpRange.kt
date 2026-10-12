package com.nbaltas.nickos.sync

/** Pure parsing of an HTTP "Range" request header for [MediaServer]. */
object HttpRange {
    private val PATTERN = Regex("^bytes=(\\d*)-(\\d*)$")

    /**
     * [header] against a known [size]. Null for an absent header, a header that does not
     * match the single-range form, or a range that cannot be satisfied (the caller answers 416
     * in that last case, same as a null result for a malformed one: there is nothing more to
     * tell the client either way).
     */
    fun parse(header: String?, size: Long): LongRange? {
        if (header == null || size <= 0) return null
        val m = PATTERN.find(header.trim()) ?: return null
        val startStr = m.groupValues[1]
        val endStr = m.groupValues[2]
        if (startStr.isEmpty() && endStr.isEmpty()) return null

        if (startStr.isEmpty()) {
            val suffixLength = endStr.toLongOrNull() ?: return null
            if (suffixLength <= 0) return null
            val start = (size - suffixLength).coerceAtLeast(0)
            return start..(size - 1)
        }

        val start = startStr.toLongOrNull() ?: return null
        if (start < 0 || start >= size) return null
        val end = if (endStr.isEmpty()) size - 1 else {
            val e = endStr.toLongOrNull() ?: return null
            e.coerceAtMost(size - 1)
        }
        if (end < start) return null
        return start..end
    }
}
