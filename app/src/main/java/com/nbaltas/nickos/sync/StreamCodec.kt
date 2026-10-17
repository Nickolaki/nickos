package com.nbaltas.nickos.sync

import org.json.JSONObject

/** What one tablet can decode in hardware or software, as its MediaCodecList says. HEVC by profile, the rest by size or yes/no. */
data class NodeDec(
    val hevcMain: Boolean, val hevcMain10: Boolean, val hevcW: Int, val hevcH: Int,
    val avcW: Int, val avcH: Int,
    val av1: Boolean, val vp9: Boolean,
) {
    companion object {
        /** The `status.dec` field. */
        const val KEY = "dec"

        fun encode(d: NodeDec): JSONObject = JSONObject()
            .put("hevc", JSONObject().put("main", d.hevcMain).put("main10", d.hevcMain10).put("w", d.hevcW).put("h", d.hevcH))
            .put("avc", JSONObject().put("w", d.avcW).put("h", d.avcH))
            .put("av1", d.av1).put("vp9", d.vp9)

        /** Null when the tablet sent nothing usable (an old NickOS): "unknown", which is streamed the file as before. */
        fun decode(status: JSONObject?): NodeDec? {
            val o = status?.optJSONObject(KEY) ?: return null
            val hevc = o.optJSONObject("hevc") ?: return null
            val avc = o.optJSONObject("avc") ?: return null
            return NodeDec(
                hevcMain = hevc.optBoolean("main"), hevcMain10 = hevc.optBoolean("main10"),
                hevcW = hevc.optInt("w"), hevcH = hevc.optInt("h"),
                avcW = avc.optInt("w"), avcH = avc.optInt("h"),
                av1 = o.optBoolean("av1"), vp9 = o.optBoolean("vp9"),
            )
        }
    }
}

/** The video stream of a Jellyfin item. Unknown parts are null or 0. */
data class JfVideo(
    val codec: String?, val profile: String?, val bitDepth: Int, val width: Int, val height: Int,
    val sourceId: String? = null,
) {
    companion object {
        /** From the item JSON of `/Users/{uid}/Items/{id}`: MediaSources[0]'s first video stream. Null when there is none. */
        fun from(item: JSONObject): JfVideo? {
            val src = item.optJSONArray("MediaSources")?.optJSONObject(0) ?: return null
            val streams = src.optJSONArray("MediaStreams") ?: return null
            for (i in 0 until streams.length()) {
                val s = streams.optJSONObject(i) ?: continue
                if (s.optString("Type") != "Video") continue
                return JfVideo(
                    codec = s.optString("Codec").ifBlank { null }, profile = s.optString("Profile").ifBlank { null },
                    bitDepth = s.optInt("BitDepth"), width = s.optInt("Width"), height = s.optInt("Height"),
                    sourceId = src.optString("Id").ifBlank { null },
                )
            }
            return null
        }
    }
}

/** Which address a tablet gets for a Jellyfin movie: the file itself, or a converted (HLS) stream it can play. */
object StreamCodec {
    /** The widest picture a converted stream is made at. */
    const val MAX_CONVERT_WIDTH = 1920

    /** Unknown tablet or unknown video: assume it plays, as before. An unfamiliar codec is assumed to play too. */
    fun canDecode(dec: NodeDec?, v: JfVideo?): Boolean {
        if (dec == null || v == null) return true
        val codec = v.codec?.lowercase() ?: return true
        val tenBit = v.bitDepth > 8 || v.profile?.contains("10") == true
        return when (codec) {
            "hevc", "h265" -> {
                val profileOk = if (tenBit) dec.hevcMain10 else dec.hevcMain || dec.hevcMain10
                profileOk && fits(v, dec.hevcW, dec.hevcH)
            }
            "h264", "avc" -> !tenBit && fits(v, dec.avcW, dec.avcH)
            "av1" -> dec.av1
            "vp9" -> dec.vp9
            else -> true
        }
    }

    private fun fits(v: JfVideo, maxW: Int, maxH: Int) = (maxW <= 0 || v.width <= maxW) && (maxH <= 0 || v.height <= maxH)

    /** The address for one tablet. Carries the token: sealed link only, never logged. */
    fun urlFor(base: String, token: String, itemId: String, v: JfVideo?, dec: NodeDec?, nodeId: String): String =
        if (canDecode(dec, v)) JfStream.authedUrl(base, itemId, token)
        else JfStream.hlsUrl(base, itemId, v?.sourceId ?: itemId, token, minOf(MAX_CONVERT_WIDTH, dec?.avcW?.takeIf { it > 0 } ?: MAX_CONVERT_WIDTH), "nickos-$nodeId")

    /** The tablets of [decs] (id to what it can decode, null when unknown) that would get a converted stream. */
    fun converted(v: JfVideo?, decs: Map<String, NodeDec?>): Set<String> = decs.filter { !canDecode(it.value, v) }.keys

    /** "Leo's tablet gets a converted stream (it can't play this file directly)", or "Leo and Mia's tablets get ...". Null when none. */
    fun convertedLine(names: List<String>): String? = when (names.size) {
        0 -> null
        1 -> "${names[0]}'s tablet gets a converted stream (it can't play this file directly)"
        else -> "${TogetherText.join(names)}'s tablets get a converted stream (they can't play this file directly)"
    }
}
