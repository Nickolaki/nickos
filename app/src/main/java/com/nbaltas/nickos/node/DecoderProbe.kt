package com.nbaltas.nickos.node

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log
import com.nbaltas.nickos.sync.NodeDec

/** Asks the tablet's MediaCodecList what it can decode, once. The answer goes out in `status.dec`. */
object DecoderProbe {
    private const val TAG = "NickOS.Dec"
    @Volatile private var cached: NodeDec? = null

    /** Computed on first use and kept; a tablet's decoders do not change while the app runs. */
    fun get(): NodeDec? = cached ?: synchronized(this) { cached ?: probe().also { cached = it } }

    private fun probe(): NodeDec? = try {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        fun caps(mime: String) = decoders.filter { i -> i.supportedTypes.any { it.equals(mime, true) } }
            .mapNotNull { i -> try { i.getCapabilitiesForType(mime) } catch (_: Exception) { null } }
        fun maxW(c: List<MediaCodecInfo.CodecCapabilities>) = c.maxOfOrNull { it.videoCapabilities?.supportedWidths?.upper ?: 0 } ?: 0
        fun maxH(c: List<MediaCodecInfo.CodecCapabilities>) = c.maxOfOrNull { it.videoCapabilities?.supportedHeights?.upper ?: 0 } ?: 0
        fun has(c: List<MediaCodecInfo.CodecCapabilities>, profile: Int) = c.any { cc -> cc.profileLevels.any { it.profile == profile } }
        val hevc = caps("video/hevc")
        val avc = caps("video/avc")
        NodeDec(
            hevcMain = has(hevc, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain),
            hevcMain10 = has(hevc, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10),
            hevcW = maxW(hevc), hevcH = maxH(hevc),
            avcW = maxW(avc), avcH = maxH(avc),
            av1 = caps("video/av01").isNotEmpty(), vp9 = caps("video/x-vnd.on2.vp9").isNotEmpty(),
        )
    } catch (e: Exception) {
        Log.w(TAG, "probe: ${e.javaClass.simpleName}")
        null
    }
}
