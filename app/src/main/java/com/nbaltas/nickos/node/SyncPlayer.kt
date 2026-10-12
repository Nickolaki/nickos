package com.nbaltas.nickos.node

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nbaltas.nickos.sync.DriftAction
import com.nbaltas.nickos.sync.DriftPolicy
import com.nbaltas.nickos.sync.PlayerState
import com.nbaltas.nickos.sync.SyncMessages
import com.nbaltas.nickos.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import kotlin.math.abs

/**
 * What the Node's player screen shows. [fid] is null when nothing is loaded (show the kid home).
 * [err] is "missing" (the movie is not on this tablet) or "play" (the file will not decode).
 * [group] is how many tablets are watching, 1 when it is only this one, 0 when unknown.
 */
data class PlayerUi(
    val fid: String? = null,
    val title: String = "",
    val state: SyncState = SyncState.IDLE,
    val err: String? = null,
    val group: Int = 0,
)

/**
 * The Node's half of Watch together: one ExoPlayer that keeps itself on the anchor the Master
 * gave it. The anchor says "at [anchorAt] on THIS tablet's clock, be at [anchorPos]"; every
 * 500 ms the player measures itself against that line and nudges its speed or seeks (see
 * [DriftPolicy]). It never waits for the Master to say it is behind.
 *
 * Threads: [onSync], [onPause], [stop] and [release] may be called from any thread (commands
 * arrive on the socket thread). The player itself is only ever touched on the main thread.
 * [report] is called on the main thread with a `syncState` message and must hand it to a
 * background thread before writing to a socket.
 */
@OptIn(UnstableApi::class)
class SyncPlayer(
    private val ctx: Context,
    private val videos: LocalVideos,
    private val device: NodeDevice,
    private val isLocked: () -> Boolean,
    private val homeVisible: () -> Boolean,
    private val report: (JSONObject) -> Unit,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val main = Handler(Looper.getMainLooper())
    private val policy = DriftPolicy()
    private var exo: ExoPlayer? = null

    private val _player = MutableStateFlow<Player?>(null)
    /** The player to hand to a PlayerView. Null until the first movie loads and after [release]. */
    val player: StateFlow<Player?> = _player

    private val _ui = MutableStateFlow(PlayerUi())
    /** Drives the Node's player screen. `ui.value.fid != null` means the player is in front. */
    val ui: StateFlow<PlayerUi> = _ui

    // Main thread only, except the two volatile reads in onSync.
    @Volatile private var fid: String? = null
    @Volatile private var playing = false   // the group is playing (as far as this tablet was told)
    private var started = false             // play() was issued for the current anchor
    private var anchorPos = 0L
    private var anchorAt = 0L
    private var title = ""
    private var group = 0
    private var err: String? = null
    private var ended = false
    private var lastState = SyncState.IDLE
    private var ticks = 0

    private val startRunnable = Runnable { begin() }
    private val closeRunnable = Runnable { doStop() }
    private val tickRunnable = Runnable { tick() }

    /** `sync`: load [SyncMessages.SyncCmdMsg.fid] if it is not loaded yet, then follow the anchor. */
    fun onSync(msg: SyncMessages.SyncCmdMsg) {
        // Looked up here, off the main thread: a miss rescans the library.
        val video = videos.find(msg.fid, rescan = false) ?: videos.find(msg.fid)
        // Bring the player to the front for a new movie or a resume, not for the minute re-anchor.
        if (msg.fid != fid || !playing) try {
            device.wake()
            if (!homeVisible()) device.goHome()
        } catch (e: Exception) { Log.w(TAG, "front", e) }
        main.post { apply(msg, video) }
    }

    /** `syncPause`: stop where the group is and wait for the next `sync`. */
    fun onPause() = main.post {
        if (fid == null || err != null || ended) return@post
        val at = expected(clock())
        main.removeCallbacks(startRunnable)
        exo?.let { it.pause(); it.setPlaybackSpeed(1f) }
        anchorPos = at
        playing = false
        started = false
        publish()
    }

    /** Back to the kid home. Also called for `syncStop`, and when the parent sends the tablet elsewhere. */
    fun stop() { main.post { doStop() } }

    /** The runtime is shutting down: free the decoder. */
    fun release() = main.post {
        doStop()
        exo?.release()
        exo = null
        _player.value = null
    }

    // ---- main thread from here ----

    /** Position the anchor asks for at [now]. Holds at the anchor until its start time. */
    private fun expected(now: Long): Long = when {
        !playing -> anchorPos
        started -> anchorPos + (now - anchorAt)
        else -> anchorPos + (now - anchorAt).coerceAtLeast(0)
    }

    private fun apply(msg: SyncMessages.SyncCmdMsg, video: LocalVideo?) {
        main.removeCallbacks(startRunnable)
        main.removeCallbacks(closeRunnable)
        title = msg.title
        group = msg.n
        if (msg.fid != fid || err != null || ended) {
            fid = msg.fid
            err = null; ended = false; started = false
            policy.reset()
            if (video == null) return fail("missing")
            val p = player()
            try {
                p.playWhenReady = false
                p.setMediaItem(MediaItem.fromUri(video.uri))
                p.prepare()
            } catch (e: Exception) { Log.w(TAG, "load", e); return fail("play") }
            main.removeCallbacks(tickRunnable)
            main.postDelayed(tickRunnable, TICK_MS)
        }
        val p = exo ?: return
        val now = clock()
        anchorPos = msg.pos
        anchorAt = msg.at
        if (isLocked()) {
            // Loaded and parked. It reports "locked", then "paused" once unlocked, until the next Play.
            playing = false; started = false
            p.pause(); p.setPlaybackSpeed(1f); p.seekTo(msg.pos.coerceAtLeast(0))
            return publish()
        }
        playing = true
        // Already playing on a line that agrees with the new one (the minute re-anchor, or the plan
        // the Master resends on reconnect): keep going, the drift loop does the rest.
        if (started && p.playWhenReady && abs(p.currentPosition - (anchorPos + (now - anchorAt))) <= SEEK_OVER_MS) return publish()
        started = false
        p.pause()
        p.setPlaybackSpeed(1f)
        p.seekTo(anchorPos.coerceAtLeast(0))
        arm()
        publish()
    }

    /** Once the player is ready: start at the anchor time, or straight away when that has passed. */
    private fun arm() {
        val p = exo ?: return
        if (!playing || started || fid == null || err != null) return
        main.removeCallbacks(startRunnable)
        if (p.playbackState != Player.STATE_READY) return // onPlaybackStateChanged comes back here
        val wait = anchorAt - clock()
        if (wait > 0) main.postDelayed(startRunnable, wait) else begin()
    }

    private fun begin() {
        val p = exo ?: return
        if (!playing || started || fid == null) return
        val now = clock()
        val want = anchorPos + (now - anchorAt)
        policy.reset(keepLead = true)
        // A late join (or a slow load): jump to where the group is now, a little ahead for the seek itself.
        if (abs(p.currentPosition - want) > SEEK_OVER_MS) {
            p.seekTo((want + policy.seekLeadMs).coerceAtLeast(0))
            policy.noteSeek(now)
        }
        started = true
        p.play()
        publish()
    }

    private fun tick() {
        if (fid == null) return
        val p = exo
        if (p != null && err == null && !ended) {
            if (playing && isLocked()) localPause()
            if (playing && started && p.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                val now = clock()
                val want = expected(now)
                when (val a = policy.decide(p.currentPosition - want, playerState(p), true, now, want)) {
                    is DriftAction.Rate -> p.setPlaybackSpeed(a.speed)
                    is DriftAction.Seek -> { p.setPlaybackSpeed(1f); p.seekTo(a.targetPos.coerceAtLeast(0)) }
                    DriftAction.None -> {}
                }
            }
        }
        publish()
        if (++ticks % 2 == 0) send()
        main.postDelayed(tickRunnable, TICK_MS)
    }

    /** This tablet stops by itself (lock wall, bedtime, lost audio focus). The group carries on. */
    private fun localPause() {
        val p = exo ?: return
        main.removeCallbacks(startRunnable)
        anchorPos = if (started) p.currentPosition else expected(clock())
        playing = false
        started = false
        p.pause()
        p.setPlaybackSpeed(1f)
    }

    private fun fail(code: String) {
        err = code
        playing = false; started = false
        main.removeCallbacks(startRunnable)
        try { exo?.stop() } catch (_: Exception) {}
        publish()
        main.postDelayed(closeRunnable, CLOSE_MS)
    }

    private fun doStop() {
        main.removeCallbacks(startRunnable); main.removeCallbacks(closeRunnable); main.removeCallbacks(tickRunnable)
        val had = fid
        try { exo?.let { it.stop(); it.clearMediaItems(); it.setPlaybackSpeed(1f) } } catch (e: Exception) { Log.w(TAG, "stop", e) }
        fid = null
        playing = false; started = false; err = null; ended = false
        policy.reset()
        lastState = SyncState.IDLE
        _ui.value = PlayerUi()
        if (had != null) report(SyncMessages.encodeSyncState(had, SyncState.STOPPED.wire, 0, clock(), 0, 1f))
    }

    private fun player(): ExoPlayer = exo ?: ExoPlayer.Builder(ctx).build().also { p ->
        // Media usage on STREAM_MUSIC, the stream the parent's volume slider moves. Taking audio focus
        // pauses a Jellyfin stream left running in the background.
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        p.setWakeMode(C.WAKE_MODE_LOCAL)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (fid == null) return
                if (state == Player.STATE_READY) arm()
                if (state == Player.STATE_ENDED && !ended && err == null) {
                    ended = true; playing = false; started = false
                    main.postDelayed(closeRunnable, CLOSE_MS)
                }
                publish()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "player: ${error.errorCodeName}")
                if (fid != null && err == null) fail("play")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Something else took audio focus for good, or the headphones came out.
                val lost = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                if (!playWhenReady && lost && started) { localPause(); publish() }
            }
        })
        exo = p
        _player.value = p
    }

    private fun playerState(p: Player) = when (p.playbackState) {
        Player.STATE_READY -> PlayerState.READY
        Player.STATE_BUFFERING -> PlayerState.BUFFERING
        Player.STATE_ENDED -> PlayerState.ENDED
        else -> PlayerState.IDLE
    }

    private fun state(): SyncState {
        val p = exo
        return when {
            fid == null -> SyncState.IDLE
            err != null -> SyncState.ERROR
            ended -> SyncState.ENDED
            isLocked() -> SyncState.LOCKED
            !playing -> SyncState.PAUSED
            p == null || p.playbackState != Player.STATE_READY -> if (started) SyncState.BUFFERING else SyncState.LOADING
            started -> SyncState.PLAYING
            else -> SyncState.READY
        }
    }

    /** Refresh [ui]; a change of state is reported to the Master at once, not on the next second. */
    private fun publish() {
        val s = state()
        _ui.value = PlayerUi(fid, title, s, err, group)
        if (s != lastState) { lastState = s; send() }
    }

    private fun send() {
        val f = fid ?: return
        val p = exo
        val dur = p?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val pos = if (p != null && err == null) p.currentPosition else anchorPos
        report(SyncMessages.encodeSyncState(f, lastState.wire, pos, clock(), dur, p?.playbackParameters?.speed ?: 1f, err))
    }

    private companion object {
        const val TAG = "NickOS.SyncPlayer"
        const val TICK_MS = 500L
        /** Beyond this the player seeks instead of nudging its speed; same figure as [DriftPolicy]. */
        const val SEEK_OVER_MS = 400L
        /** How long "The end" or an error stays up before the kid home comes back. */
        const val CLOSE_MS = 3000L
    }
}
