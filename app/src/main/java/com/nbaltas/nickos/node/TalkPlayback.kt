package com.nbaltas.nickos.node

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.nbaltas.nickos.talk.TalkMessages
import com.nbaltas.nickos.talk.TalkSink
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The speaker for push to talk. Each session gets its own AudioTrack and writer thread, so the link's reading thread
 * only ever hands over a frame and never waits on audio, and a new session cannot trip over the old one's teardown.
 *
 * Voice communication usage, not sonification: the parent's volume slider moves the media volume and the kid may have
 * system sounds turned right down, while the voice stream has its own level that is not muted by the usual quiet
 * settings. Focus is asked for as "transient, may duck" so the movie gets quieter while the parent speaks and
 * comes back when focus is given up at the end.
 * [banner] shows or clears the "Dad is talking" line; [nameOf] turns a Master id into that line.
 */
class TalkPlayback(
    private val ctx: Context,
    private val banner: (String?) -> Unit,
    private val nameOf: (String) -> String,
) : TalkSink {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var current: Playing? = null

    @Synchronized
    override fun begin(sid: String, masterId: String) {
        current?.close(flush = false)
        current = try { Playing(masterId) } catch (e: Exception) { Log.w(TAG, "talk playback", e); null }
        banner(nameOf(masterId))
    }

    @Synchronized
    override fun play(pcm: ByteArray) { current?.offer(pcm) }

    @Synchronized
    override fun end(flush: Boolean) {
        current?.close(flush)
        current = null
        banner(null)
    }

    private inner class Playing(masterId: String) {
        private val queue = LinkedBlockingQueue<ByteArray>(MAX_WAITING)
        @Volatile private var open = true
        @Volatile private var flush = false
        private val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        private val track: AudioTrack
        private var request: AudioFocusRequest? = null
        private val thread: Thread

        init {
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(TalkMessages.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val min = AudioTrack.getMinBufferSize(TalkMessages.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            track = AudioTrack.Builder()
                .setAudioAttributes(attrs).setAudioFormat(fmt)
                .setBufferSizeInBytes(maxOf(min, TalkMessages.FRAME_BYTES * 4))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            takeFocus()
            track.play()
            thread = Thread({ run() }, "NickOS.Talk").also { it.isDaemon = true; it.start() }
            Log.i(TAG, "talk from $masterId")
        }

        private fun takeFocus() {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attrs).setOnAudioFocusChangeListener { }.build()
                    request = r
                    audio.requestAudioFocus(r)
                } else {
                    @Suppress("DEPRECATION")
                    audio.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                }
            } catch (e: Exception) { Log.w(TAG, "audio focus", e) }
        }

        private fun giveFocus() {
            try {
                if (Build.VERSION.SDK_INT >= 26) request?.let { audio.abandonAudioFocusRequest(it) }
                else @Suppress("DEPRECATION") audio.abandonAudioFocus(null)
            } catch (e: Exception) { Log.w(TAG, "audio focus", e) }
        }

        /** Never blocks: when the speaker is behind, the oldest waiting frame goes. */
        fun offer(pcm: ByteArray) {
            while (!queue.offer(pcm)) queue.poll()
        }

        fun close(flush: Boolean) {
            this.flush = flush
            open = false
            thread.interrupt()
        }

        private fun run() {
            try {
                while (true) {
                    val f = (if (open) queue.poll(200, TimeUnit.MILLISECONDS) else queue.poll()) ?: if (open) continue else break
                    if (!open && !flush) break
                    track.write(f, 0, f.size)
                }
            } catch (_: InterruptedException) {
                // close() woke us. A normal end plays what is left; anything else is cut off.
                while (flush) { val f = queue.poll() ?: break; try { track.write(f, 0, f.size) } catch (_: Exception) { break } }
            } catch (e: Exception) {
                Log.w(TAG, "talk write", e)
            } finally {
                try { if (flush) track.stop() else { track.pause(); track.flush() } } catch (_: Exception) {}
                track.release()
                giveFocus()
            }
        }
    }

    companion object {
        private const val TAG = "NickOS.Talk"
        /** About a second of audio: more than that is the speaker falling behind, and old audio is no use. */
        private const val MAX_WAITING = 25
    }
}
