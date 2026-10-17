package com.nbaltas.nickos.master

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.parentName
import com.nbaltas.nickos.sync.ClockSync
import com.nbaltas.nickos.sync.GroupWait
import com.nbaltas.nickos.sync.JfStream
import com.nbaltas.nickos.sync.StreamGate
import com.nbaltas.nickos.sync.WaitAct
import com.nbaltas.nickos.sync.Library
import com.nbaltas.nickos.sync.LibraryItem
import com.nbaltas.nickos.sync.SyncMessages
import com.nbaltas.nickos.sync.SyncPlan
import com.nbaltas.nickos.sync.SyncQuality
import com.nbaltas.nickos.sync.SyncSession
import com.nbaltas.nickos.sync.SyncState
import com.nbaltas.nickos.sync.TogetherText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** What one tablet (or this one, under [Library.SELF]) has. [perm] false: it may not read its videos. */
data class DeviceLibrary(val perm: Boolean, val list: List<SyncMessages.VideoEntry>, val free: Long = -1)

/** A tablet's last `syncState`. [at] is on the tablet's clock, [receivedAt] on the Master's. */
data class NodeSyncState(
    val state: SyncState,
    val pos: Long,
    val at: Long,
    val dur: Long,
    val rate: Float,
    val err: String?,
    val receivedAt: Long,
)

/**
 * A tablet that could not play a streamed movie. [code] is cant_reach, auth, unsupported or timeout,
 * never an address. [fid] and [title] say which movie, so "Save to tablets instead" knows what to save.
 */
data class StreamFail(val id: String, val code: String, val fid: String, val title: String) {
    /** The Save fallback makes sense only when the tablet could not get the stream, not when it cannot play it. */
    val canSaveInstead: Boolean get() = code == CANT_REACH || code == TIMEOUT

    companion object {
        const val CANT_REACH = "cant_reach"
        const val AUTH = "auth"
        const val UNSUPPORTED = "unsupported"
        const val TIMEOUT = "timeout"
        const val OTHER = "other"
        val CODES = setOf(CANT_REACH, AUTH, UNSUPPORTED, TIMEOUT, OTHER)
        /** Anything a tablet says that is not one of the fixed codes becomes [OTHER]; its own words are never kept. */
        fun clean(code: String?): String = if (code != null && code in CODES) code else OTHER
    }
}

/** A tablet's clock against the Master's: tabletClock = masterClock + [offset]. */
data class ClockInfo(val offset: Long, val rtt: Long, val quality: SyncQuality)

/**
 * The Master's side of Watch together: the merged library, each tablet's clock offset, and the
 * one session. The Master is the clock authority. It tells each tablet where to be and when, in
 * that tablet's own clock, on Play, seek, resume, reconnect and once a minute; in between each
 * tablet keeps itself in step. Reports coming back are shown and used to end the session. They
 * are never answered with a corrective seek: that would fight the tablet's own correction.
 *
 * Screens read the StateFlows and call the plain functions; every function is safe on the main
 * thread (nothing here blocks or writes to a socket directly). All times handed out or taken are
 * milliseconds; "now" is always this controller's own monotonic clock, never wall-clock time.
 */
class SyncController(private val port: MasterPort, private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    constructor(rt: MasterRuntime, store: Store) : this(RuntimePort(rt, store))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var ticker: Job? = null

    private val clocks = HashMap<String, ClockSync>()
    private val connected = HashSet<String>()
    /** Connected, but not yet seen to be capable: its first status has not arrived. */
    private val greet = HashSet<String>()
    private val burst = HashMap<String, Int>()
    private val lastPing = HashMap<String, Long>()
    /** In the session and owed a plan as soon as its clock offset is known. */
    private val needPlan = HashSet<String>()
    private val offlineSince = HashMap<String, Long>()
    private val scanAsked = HashMap<String, Long>()
    /** Told to stop: its late reports must not bring a session back until this time. */
    private val quiet = HashMap<String, Long>()
    private var lastAnchor = 0L
    /** The group wait for the current streamed movie. Disabled (does nothing) for a file on the tablets. */
    private var wait = GroupWait(false)
    /** When the current start or seek happened: a report made before it belongs to the previous generation. */
    private var genAt = 0L

    private val _waiting = MutableStateFlow<String?>(null)
    /** "Waiting for Leo" while the group is held for a tablet that is buffering, else null. */
    val waiting: StateFlow<String?> = _waiting

    private val _failed = MutableStateFlow<Map<String, StreamFail>>(emptyMap())
    /** Tablets that could not play the streamed movie, by tablet id. Kept after the tablet leaves the session. */
    val failed: StateFlow<Map<String, StreamFail>> = _failed

    private val _session = MutableStateFlow<SyncSession?>(null)
    /** The session, or null. Non-null means the remote is what Watch together shows. */
    val session: StateFlow<SyncSession?> = _session

    private val _states = MutableStateFlow<Map<String, NodeSyncState>>(emptyMap())
    /** Last report per tablet in the session, by tablet id. */
    val states: StateFlow<Map<String, NodeSyncState>> = _states

    private val _videos = MutableStateFlow<Map<String, DeviceLibrary>>(emptyMap())
    /** What each tablet has, by tablet id, plus [Library.SELF]. A tablet is absent until it answers. */
    val videos: StateFlow<Map<String, DeviceLibrary>> = _videos

    private val _offsets = MutableStateFlow<Map<String, ClockInfo>>(emptyMap())
    /** Clock offset per tablet. Absent until the first good ping. Shown nowhere; here for debugging. */
    val offsets: StateFlow<Map<String, ClockInfo>> = _offsets

    private val _thumbs = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    /** Thumbnail per fid, from whichever tablet sent one first. No entry: draw the placeholder art. */
    val thumbs: StateFlow<Map<String, Bitmap>> = _thumbs

    private val _scanning = MutableStateFlow<Set<String>>(emptySet())
    /** Tablets (and [Library.SELF]) asked for their videos that have not answered yet, for up to 15 s. */
    val scanning: StateFlow<Set<String>> = _scanning

    /** Set while the Watch together screen is showing: tablets are pinged every 2 s instead of 30. */
    @Volatile var screenOpen = false

    /** Called once from [MasterRuntime.start]. */
    fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch { while (isActive) { delay(TICK_MS); try { tick() } catch (e: Exception) { Log.w(TAG, "tick", e) } } }
    }

    // ---- library ----

    /** The merged library, from the latest [videos]. Screens: `remember(videos) { sync.library() }`. */
    fun library(): List<LibraryItem> = Library.merge(_videos.value.mapValues { it.value.list })

    /** "Look again": rescan this tablet and ask every online capable tablet for its list. */
    fun rescan() {
        rescanSelf()
        synchronized(lock) { connected.filter { capable(it) }.forEach { askVideos(it, clock()) } }
    }

    /** Rescan this tablet only (after the parent allows video access, say). */
    fun rescanSelf() {
        _scanning.update { it + Library.SELF }
        scope.launch { scanSelf() }
    }

    /** The blocking half of [rescanSelf]. */
    internal fun scanSelf() {
        val msg = try { port.localVideos() } catch (e: Exception) { Log.w(TAG, "scan", e); SyncMessages.VideosMsg(false, emptyList()) }
        putVideos(Library.SELF, msg)
    }

    private fun putVideos(id: String, msg: SyncMessages.VideosMsg) {
        val fresh = HashMap<String, Bitmap>()
        for (v in msg.list) {
            val t = v.thumb ?: continue
            if (v.fid in _thumbs.value || v.fid in fresh) continue
            try { Base64.decode(t, Base64.DEFAULT).let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) }?.let { fresh[v.fid] = it } } catch (_: Exception) {}
        }
        if (fresh.isNotEmpty()) _thumbs.update { fresh + it }
        // The base64 is not kept: a few megabytes per tablet, and the bitmaps are what the screens draw.
        _videos.update { it + (id to DeviceLibrary(msg.perm, msg.list.map { v -> v.copy(thumb = null) }, msg.free)) }
        _scanning.update { it - id }
    }

    /** A delete on tablet [id] worked: take [fid] out of its list now, before its own fresh `videos` arrives. */
    fun drop(id: String, fid: String) {
        _videos.update { m -> m[id]?.let { d -> m + (id to d.copy(list = d.list.filterNot { it.fid == fid })) } ?: m }
    }

    private fun askVideos(id: String, now: Long) {
        if (port.send(id, SyncMessages.cmdVideos())) { scanAsked[id] = now; _scanning.update { it + id } }
    }

    // ---- link hooks, called by MasterRuntime ----

    /** A tablet finished `welcome`. Nothing is sent until its status shows it can do Watch together. */
    fun onConnected(id: String) = synchronized(lock) {
        connected += id
        greet += id
        clocks[id] = ClockSync() // its clock may have restarted with it: old samples are worthless
        _offsets.update { it - id }
        offlineSince -= id
    }

    fun onDisconnected(id: String) = synchronized(lock) {
        connected -= id; greet -= id; burst -= id; scanAsked -= id
        _scanning.update { it - id }
        if (_session.value?.nodes?.contains(id) == true) offlineSince.getOrPut(id) { clock() }
    }

    /** `pong`, `videos` and `syncState` from tablet [id]. Runs on that link's reader thread. */
    fun onMessage(id: String, m: JSONObject) {
        when (m.optString("t")) {
            "pong" -> {
                val t3 = clock()
                val p = SyncMessages.decodePong(m) ?: return
                synchronized(lock) {
                    val cs = clocks[id] ?: return
                    cs.onPong(p.id, p.t0, p.t1, p.t2, t3)
                    val off = cs.offset ?: return
                    _offsets.update { it + (id to ClockInfo(off, cs.rtt ?: 0, cs.quality ?: SyncQuality.POOR)) }
                }
            }
            "videos" -> SyncMessages.decodeVideos(m)?.let { putVideos(id, it); synchronized(lock) { scanAsked -= id } }
            "syncState" -> SyncMessages.decodeSyncState(m)?.let { onState(id, it) }
        }
    }

    private fun onState(id: String, msg: SyncMessages.SyncStateMsg) {
        synchronized(lock) {
            val st = SyncState.of(msg.state) ?: return
            val now = clock()
            // A report can put a tablet into a session only for a movie the Master already knows that
            // tablet has, and only from a tablet that can do Watch together. Anything else is ignored.
            val known = capable(id) && _videos.value[id]?.list?.any { it.fid == msg.fid } == true
            val joinable = st.active && known && now >= (quiet[id] ?: 0L)
            val dur = msg.dur.coerceAtLeast(0)
            val pos = if (dur > 0) msg.pos.coerceIn(0, dur) else msg.pos.coerceAtLeast(0)
            var s = _session.value
            // A streamed movie has no file to know about, and a tablet's say-so must never put it in a
            // session (it would be sent the address). Only a tablet the Master put there is heard.
            if (JfStream.isStreamFid(msg.fid) && (s == null || s.fid != msg.fid || id !in s.nodes)) return
            if (s == null) {
                // This app was restarted while tablets kept playing: take the session back from their reports.
                if (!joinable) return
                val at = clocks[id]?.offset?.let { msg.at - it } ?: now
                val title = library().firstOrNull { it.fid == msg.fid }?.title ?: ""
                s = SyncSession(msg.fid, title, dur, setOf(id), pos, at, st != SyncState.PAUSED && st != SyncState.LOCKED)
                _session.value = s
                lastAnchor = now - REANCHOR_MS + ADOPT_SETTLE_MS // pull everyone onto one line shortly
            }
            if (msg.fid != s.fid) return // still on the previous movie; the new plan is on its way
            // A report made before the last start or seek (on the tablet's clock, mapped to ours) is from the old line.
            val off = clocks[id]?.offset
            val stale = off != null && msg.at - off < genAt
            // For a stream, an old report of any kind (even ERROR or STOPPED) must not touch the current session.
            if (s.streaming && stale) return
            if (id !in s.nodes) {
                if (!joinable) return
                s = s.copy(nodes = s.nodes + id)
                _session.value = s
            }
            // What a tablet says about an error is never kept as it came: only a fixed code (or "missing") gets through.
            val err = if (s.streaming) StreamFail.clean(msg.err).takeIf { msg.err != null } else msg.err.takeIf { it == "missing" }
            _states.update { it + (id to NodeSyncState(st, pos, msg.at, dur, msg.rate, err, now)) }
            if (dur > 0 && s.durMs <= 0) _session.value = s.copy(durMs = dur)
            // A tablet that failed or left is taken out below, which also lets the group go on without it.
            if (wait.enabled && st != SyncState.ERROR && st != SyncState.STOPPED && st != SyncState.ENDED) {
                val gen = if (stale) wait.gen - 1 else wait.gen
                act(wait.report(id, st, now, gen), now)
            }
            when (st) {
                SyncState.ENDED -> drop(id, ended = true)
                SyncState.ERROR -> {
                    if (s.streaming) {
                        val f = StreamFail(id, StreamFail.clean(msg.err), s.fid, s.title)
                        _failed.update { it + (id to f) }
                        port.say(TogetherText.streamError(kid(id), f.code))
                    } else port.say("${kid(id)}: ${TogetherText.error(err)}")
                    drop(id)
                }
                SyncState.STOPPED -> drop(id)
                else -> {}
            }
        }
    }

    // ---- the session ----

    /**
     * Play [fid] from the start on [nodes] (one tablet is simply a group of one). Replaces any
     * session; tablets of the old one that are not in the new one are stopped.
     */
    fun start(fid: String, title: String, durMs: Long, nodes: Set<String>, url: String? = null, art: String? = null) {
        synchronized(lock) {
            // A stream goes only to tablets that are online, sealed and say stream1. The rest are left out.
            val go = if (url == null) nodes else nodes.filter { eligible(it, true) }.toSet()
            if (go.isEmpty()) return
            val now = clock()
            _session.value?.nodes?.minus(go)?.forEach { halt(it, now) }
            _states.value = emptyMap()
            needPlan.clear(); offlineSince.clear()
            go.forEach { quiet -= it }
            _failed.value = emptyMap()
            val streaming = url != null
            // A stream starts later than a file: every tablet opens the connection and fills its buffer first.
            val lead = if (streaming) STREAM_LEAD_MS else LEAD_MS
            wait = GroupWait(streaming).also { it.start(go) }
            genAt = now
            _waiting.value = null
            _session.value = SyncSession(fid, title, durMs, go, 0, now + lead, true, url, art)
            planAll(now)
        }
    }

    /** Pause every tablet where the group is now. */
    fun pause() {
        synchronized(lock) {
            val s = _session.value ?: return
            wait.manualPause(); refreshWait()
            if (!s.playing) return
            _session.value = s.copy(anchorPos = s.expectedPos(clock()), playing = false)
            s.nodes.forEach { port.send(it, SyncMessages.cmdSyncPause()) }
        }
    }

    /**
     * Play from where the group is. Also re-aligns everyone when already playing, which is how a
     * tablet that paused by itself (it was locked, or lost audio focus) is brought back.
     */
    fun resume() {
        synchronized(lock) {
            val s = _session.value ?: return
            val now = clock()
            wait.manualResume(); refreshWait()
            // Already playing: the line stays where it is, so tablets in step do not even blink.
            if (!s.playing) _session.value = s.copy(anchorAt = now + leadFor(s), playing = true)
            planAll(now)
        }
    }

    /** The big play/pause button. */
    fun toggle() { if (_session.value?.playing == true) pause() else resume() }

    /** Slider commit: everyone jumps to [posMs] and starts together (or waits there, when paused). */
    fun seek(posMs: Long) {
        synchronized(lock) {
            val s = _session.value ?: return
            val now = clock()
            val target = if (s.durMs > 0) posMs.coerceIn(0, s.durMs) else posMs.coerceAtLeast(0)
            _session.value = s.copy(anchorPos = target, anchorAt = now + leadFor(s))
            wait.seek(s.playing); genAt = now; refreshWait()
            planAll(now)
        }
    }

    /** Skip buttons: [deltaMs] is -10 000 or +10 000. */
    fun skip(deltaMs: Long) { seek(expectedPos() + deltaMs) }

    /** "Stop for everyone". */
    fun stop() {
        synchronized(lock) {
            val s = _session.value ?: return
            val now = clock()
            s.nodes.forEach { halt(it, now) }
            end()
        }
    }

    /** Take one tablet out; the rest carry on. Removing the last one ends the session. */
    fun remove(id: String) {
        synchronized(lock) {
            if (_session.value?.nodes?.contains(id) != true) return
            halt(id, clock())
            drop(id)
        }
    }

    /** Send one tablet the current plan again, for a tablet showing "Paused" while the group plays. */
    fun rejoin(id: String) = synchronized(lock) { plan(id, _session.value?.let { leadFor(it) } ?: LEAD_MS) }

    /** The parent has seen the failure of tablet [id] (or chose "Save to tablets instead"). */
    fun dismissFailure(id: String) { _failed.update { it - id } }

    /** The next episode every tablet in the session has, or null. Drives "Next episode". */
    fun next(): LibraryItem? = _session.value?.let { Library.next(library(), it.fid, it.nodes) }

    /** Start [next] on the same tablets. False when there is none. */
    fun playNext(): Boolean {
        val s = _session.value ?: return false
        val n = next() ?: return false
        start(n.fid, n.title, n.durMs, s.nodes, art = s.art)
        return true
    }

    /** Where the group is, for the slider. 0 with no session. Tick it with a 500 ms recomposition. */
    fun expectedPos(): Long = _session.value?.expectedPos(clock()) ?: 0L

    /**
     * How far tablet [id] is from the group, in ms, positive when ahead. Null until it has both
     * reported and been pinged. For display only.
     */
    fun drift(id: String): Long? {
        val s = _session.value ?: return null
        val st = _states.value[id] ?: return null
        val off = _offsets.value[id]?.offset ?: return null
        val now = clock()
        val pos = st.pos + if (st.state == SyncState.PLAYING) now - (st.at - off) else 0
        return pos - s.expectedPos(now)
    }

    /** The tablet's line in the remote ("In sync", "0.4 s behind", "Catching up", ...). */
    fun stateLine(id: String): String {
        val st = _states.value[id]
        return TogetherText.stateLine(port.isOnline(id), st?.state, drift(id), st?.err, st?.let { clock() - it.receivedAt })
    }

    /** True when the tablet's line is "In sync": draw the green dot. */
    fun inSync(id: String): Boolean = port.isOnline(id) && TogetherText.inSync(_states.value[id]?.state, drift(id))

    /** How many tablets in the session are still getting ready ("Starting on 2 tablets..."). */
    fun starting(): Int {
        val s = _session.value ?: return 0
        return s.nodes.count { _states.value[it]?.state.let { st -> st == SyncState.LOADING || st == SyncState.READY } }
    }

    /** "Hailey" for "Hailey's Tablet". */
    fun kid(id: String): String = port.nodes().firstOrNull { it.id == id }?.name?.let { parentName(it) } ?: "A tablet"

    // ---- internals (hold the lock) ----

    private fun capable(id: String) = port.status(id)?.supportsSync == true

    private fun halt(id: String, now: Long) {
        port.send(id, SyncMessages.cmdSyncStop())
        quiet[id] = now + QUIET_MS
    }

    /** How far ahead a start, seek or resume is scheduled: longer for a stream, which has to buffer first. */
    private fun leadFor(s: SyncSession) = if (s.streaming) STREAM_LEAD_MS else LEAD_MS

    /** Checked on every send of the address: online, a sealed link, says stream1, and in the session. */
    private fun eligible(id: String, inSession: Boolean) =
        port.isOnline(id) && StreamGate.masterSends(port.isSealed(id), port.status(id)?.supportsStream == true, inSession)

    private fun planAll(now: Long) {
        lastAnchor = now
        _session.value?.let { s -> s.nodes.forEach { plan(it, leadFor(s)) } }
    }

    /** Carry out what the group wait asked for. Holds the lock. */
    private fun act(acts: List<WaitAct>, now: Long) {
        for (a in acts) when (a) {
            is WaitAct.Pause -> {
                val s = _session.value ?: continue
                if (s.playing) {
                    _session.value = s.copy(anchorPos = s.expectedPos(now), playing = false)
                    s.nodes.forEach { port.send(it, SyncMessages.cmdSyncPause()) }
                }
            }
            WaitAct.Resume -> {
                val s = _session.value ?: continue
                if (!s.playing) _session.value = s.copy(anchorAt = now + leadFor(s), playing = true)
                planAll(now)
            }
            is WaitAct.TooSlow -> port.say(TogetherText.tooSlow(kid(a.id)))
        }
        refreshWait()
    }

    private fun refreshWait() {
        val who = wait.waitingFor().map { kid(it) }
        _waiting.value = if (who.isEmpty()) null else TogetherText.waitingFor(who)
    }

    private fun plan(id: String, lead: Long) { if (sendPlan(id, lead)) needPlan -= id else needPlan += id }

    /** False when the plan is still owed: the tablet is offline or its clock offset is not known yet. */
    private fun sendPlan(id: String, lead: Long): Boolean {
        val s = _session.value ?: return true
        if (id !in s.nodes) return true
        if (!port.isOnline(id)) return false
        // The address has the Master's Jellyfin token in it. A tablet that is not sealed and stream1 never gets it.
        if (s.streaming && !eligible(id, true)) {
            port.say(TogetherText.cantStreamYet(kid(id)))
            halt(id, clock())
            drop(id)
            return true
        }
        val off = clocks[id]?.offset ?: return false
        val c = SyncPlan.planFor(s, off, clock(), lead)
        if (!port.send(id, SyncMessages.cmdSync(c.fid, c.pos, c.at, c.title, s.nodes.size, c.url))) return false
        // A paused group: the tablet loads the movie, goes to the spot and waits there.
        if (!s.playing) port.send(id, SyncMessages.cmdSyncPause())
        return true
    }

    private fun drop(id: String, ended: Boolean = false) {
        val s = _session.value ?: return
        needPlan -= id; offlineSince -= id
        _states.update { it - id }
        val left = s.nodes - id
        if (left.isNotEmpty()) {
            _session.value = _session.value?.copy(nodes = left)
            act(wait.gone(id, clock()), clock())
            return
        }
        end()
        if (ended) port.say("Finished on everyone's tablet")
    }

    private fun end() {
        wait.stop(); _waiting.value = null
        _session.value = null
        _states.value = emptyMap()
        needPlan.clear(); offlineSince.clear()
    }

    /** Housekeeping, every [TICK_MS]. Public so tests drive time themselves. */
    fun tick(now: Long = clock()) = synchronized(lock) {
        // A tablet is greeted once its status says "sync1": an old NickOS is never sent a new command.
        for (id in greet.toList()) if (capable(id)) {
            greet -= id
            burst[id] = BURST
            askVideos(id, now)
            if (_session.value?.nodes?.contains(id) == true) needPlan += id
        }
        val period = if (_session.value != null || screenOpen) PING_ACTIVE_MS else PING_IDLE_MS
        for (id in connected) {
            if (id in greet) continue
            val left = burst[id] ?: 0
            if (left > 0 || now - (lastPing[id] ?: 0L) >= period) {
                if (left > 0) burst[id] = left - 1
                lastPing[id] = now
                clocks[id]?.let { port.send(id, SyncMessages.cmdPing(it.nextPingId(), clock())) }
            }
        }
        val lead = _session.value?.let { leadFor(it) } ?: LEAD_MS
        for (id in needPlan.toList()) if (sendPlan(id, lead)) needPlan -= id
        _session.value?.let { s ->
            for (id in s.nodes) {
                if (port.isOnline(id)) offlineSince -= id
                else {
                    // A tablet that lost its link does not hold the group: the wait leaves it out at once.
                    if (wait.enabled) act(wait.offline(id, now), now)
                    if (now - offlineSince.getOrPut(id) { now } > OFFLINE_DROP_MS) drop(id)
                }
            }
        }
        if (wait.enabled) act(wait.tick(now), now)
        _session.value?.let { s ->
            if (s.playing && now - lastAnchor >= REANCHOR_MS) {
                lastAnchor = now
                s.nodes.forEach { sendPlan(it, 0) }
            }
        }
        scanAsked.entries.removeAll { (id, at) -> (now - at > SCAN_WAIT_MS).also { late -> if (late) _scanning.update { it - id } } }
    }

    companion object {
        private const val TAG = "NickOS.Sync"
        const val TICK_MS = 150L
        /** How far ahead a start is scheduled, so every tablet has the command before it is due. */
        const val LEAD_MS = 1500L
        /** The same for a streamed movie: time for every tablet to connect and fill its buffer. */
        const val STREAM_LEAD_MS = 4000L
        const val REANCHOR_MS = 60_000L
        const val OFFLINE_DROP_MS = 60_000L
        const val SCAN_WAIT_MS = 15_000L
        const val PING_ACTIVE_MS = 2000L
        const val PING_IDLE_MS = 30_000L
        const val BURST = 5
        const val QUIET_MS = 5000L
        private const val ADOPT_SETTLE_MS = 5000L
    }
}
