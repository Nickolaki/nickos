package com.nbaltas.nickos.node

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nbaltas.nickos.R
import com.nbaltas.nickos.talk.Speaker
import com.nbaltas.nickos.talk.TalkMessages

/**
 * The speaker for push to talk: one AudioTrack and one audio focus request per session (see [TalkPlayerFactory]).
 *
 * Voice communication usage, not sonification: the parent's volume slider moves the media volume and the kid may have
 * system sounds turned right down, while the voice stream has its own level that is not muted by the usual quiet
 * settings. Focus is asked for as "transient, may duck" so the movie gets quieter while the parent speaks and
 * comes back when focus is given up at the end.
 *
 * [open] is all or nothing: if the track, the focus or play() fails, everything already taken is given back before the
 * error goes out.
 */
class AndroidSpeaker(ctx: Context) : Speaker {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var track: AudioTrack? = null
    private var request: AudioFocusRequest? = null
    private var tookFocus = false
    private var closed = false
    private var framesWritten = 0L

    @Synchronized
    override fun open() {
        try {
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(TalkMessages.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val min = AudioTrack.getMinBufferSize(TalkMessages.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs).setAudioFormat(fmt)
                .setBufferSizeInBytes(maxOf(min, TalkMessages.FRAME_BYTES * 4))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            track = t
            takeFocus()
            t.play()
        } catch (e: Throwable) {
            close(false)
            throw e
        }
    }

    override fun write(pcm: ByteArray, off: Int, len: Int): Int {
        val t = synchronized(this) { if (closed) return -1 else track } ?: return -1
        val n = t.write(pcm, off, len)
        if (n > 0) synchronized(this) { framesWritten += n / 2 }
        return n
    }

    @Synchronized
    override fun close(flush: Boolean) {
        if (closed) return
        closed = true
        val t = track
        track = null
        if (t != null) {
            try {
                if (flush) { t.stop(); waitPlayed(t) } else { t.pause(); t.flush() }
            } catch (_: Exception) {}
            try { t.release() } catch (_: Exception) {}
        }
        giveFocus()
    }

    /** stop() lets the buffered audio play out; release() right after would cut the end of the sentence. */
    private fun waitPlayed(t: AudioTrack) {
        val until = System.currentTimeMillis() + DRAIN_MS
        while (System.currentTimeMillis() < until) {
            if (t.playbackHeadPosition.toLong() >= framesWritten) return
            Thread.sleep(20)
        }
    }

    private fun takeFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).setOnAudioFocusChangeListener { }.build()
            request = r
            tookFocus = true
            audio.requestAudioFocus(r)
        } else {
            tookFocus = true
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun giveFocus() {
        if (!tookFocus) return
        tookFocus = false
        try {
            if (Build.VERSION.SDK_INT >= 26) request?.let { audio.abandonAudioFocusRequest(it) }
            else @Suppress("DEPRECATION") audio.abandonAudioFocus(null)
        } catch (e: Exception) { Log.w("NickOS.Talk", "audio focus", e) }
    }

    private companion object {
        /** The longest wait for the end of a sentence to play out. */
        const val DRAIN_MS = 800L
    }
}

/**
 * The fallback for "Parent is talking" when the banner cannot be drawn over the app the kid is in (the display over
 * other apps permission is missing): a loud notification for as long as the audio plays.
 */
class TalkNotice(private val ctx: Context) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    fun show(text: String) {
        try {
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Parent talking", NotificationManager.IMPORTANCE_HIGH))
            nm.notify(ID, NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(125_000)
                .build())
        } catch (e: Exception) { Log.w("NickOS.Talk", "notice", e) }
    }

    fun clear() { try { nm.cancel(ID) } catch (_: Exception) {} }

    private companion object {
        const val CHANNEL = "talk"
        const val ID = 2
    }
}
