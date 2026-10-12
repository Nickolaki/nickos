package com.nbaltas.nickos.master

import android.os.SystemClock
import android.util.Log
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.sync.DownloadPolicy
import com.nbaltas.nickos.sync.JobState
import com.nbaltas.nickos.sync.Library
import com.nbaltas.nickos.sync.LibraryItem
import com.nbaltas.nickos.sync.SyncMessages
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

/**
 * The Master's side of sending movies between tablets. It never carries the bytes itself: it
 * picks a tablet that has the movie (this one when it does, since it is the one that is always
 * here), gets an address for it, and tells each tablet that lacks it to fetch from there. From
 * then on each receiving tablet runs its own job and reports back; this class keeps those
 * reports for the screens and steps in only when a job says its source has gone.
 *
 * Screens read [jobs] and call the plain functions, all of which are safe on the main thread.
 */
class TransferController(
    private val port: MasterPort,
    private val sync: SyncController,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    constructor(rt: MasterRuntime, store: Store) : this(RuntimePort(rt, store), rt.sync)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var ticker: Job? = null

    /** A `serve` sent to a source tablet, and who is waiting for its answer. */
    private class Ask(val item: LibraryItem, val targets: MutableSet<String>, val at: Long)
    private val asks = HashMap<String, HashMap<String, Ask>>()
    /** Connected, `jobs` not asked yet: its first status has not shown it can do Watch together. */
    private val greet = HashSet<String>()
    /** "tablet:fid" for copies this Master started and has not yet seen finish. */
    private val started = HashSet<String>()
    private val resent = HashMap<String, Long>()

    private val _jobs = MutableStateFlow<Map<String, List<SyncMessages.JobEntry>>>(emptyMap())
    /** Each tablet's download jobs as it last reported them, by tablet id. Offline tablets are absent. */
    val jobs: StateFlow<Map<String, List<SyncMessages.JobEntry>>> = _jobs

    /** Called once from [MasterRuntime.start]. */
    fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch { while (isActive) { delay(TICK_MS); try { tick() } catch (e: Exception) { Log.w(TAG, "tick", e) } } }
    }

    // ---- what the screens call ----

    /**
     * Send movie [fid] to [targets] (tablet ids). Tablets that are offline, too old, or already
     * have it are skipped. Returns at once; progress arrives in [jobs].
     */
    fun send(fid: String, targets: Set<String>) { scope.launch { sendNow(fid, targets) } }

    /** "Try again" on a failed job. */
    fun retry(fid: String, id: String) = send(fid, setOf(id))

    /** Stop a copy on tablet [id] and remove what it has so far. */
    fun cancel(fid: String, id: String) { port.send(id, SyncMessages.cmdCancel(fid)) }

    /** A `delete` sent and not yet answered. */
    private class Deleting(val title: String, val at: Long)
    private val pendingDelete = HashMap<String, Deleting>()
    private val _deleting = MutableStateFlow<Set<String>>(emptySet())
    /** "tablet:fid" for every delete waiting on its answer, for the button's "Deleting..." state. */
    val deletingNow: StateFlow<Set<String>> = _deleting

    /** True while a delete of [fid] on tablet [id] is waiting for its answer. */
    fun deleting(id: String, fid: String): Boolean = "$id:$fid" in _deleting.value

    /**
     * Delete movie [fid] from tablet [id]. Nothing is sent to a tablet that is offline or on a
     * NickOS from before deleting; the screens do not offer the button then. The answer arrives
     * as a toast and the grid updates at once; no answer in [DELETE_WAIT_MS] says so.
     */
    fun delete(fid: String, id: String) {
        if (!(port.isOnline(id) && port.status(id)?.supportsDelete == true)) return
        val title = sync.library().firstOrNull { it.fid == fid }?.title ?: "That movie"
        val key = "$id:$fid"
        // Both records are made BEFORE sending: a reply can arrive on the link's thread before send() returns.
        synchronized(lock) { if (key in pendingDelete) return; pendingDelete[key] = Deleting(title, clock()) }
        _deleting.update { it + key }
        if (!port.send(id, SyncMessages.cmdDelete(fid))) {
            synchronized(lock) { pendingDelete -= key }
            _deleting.update { it - key }
        }
    }

    /** The blocking half of [send]. */
    internal fun sendNow(fid: String, targets: Set<String>) {
        val item = sync.library().firstOrNull { it.fid == fid } ?: return port.say("That movie isn't on any tablet any more")
        val to = targets.filter { able(it) && it !in item.owners }.toSet()
        if (to.isEmpty()) return
        if (dispatch(item, to, quiet = false)) {
            val names = port.nodes().filter { it.id in to }.map { sync.kid(it.id) }
            port.say("Sending ${item.title} to ${TogetherText.join(names)}")
        }
    }

    /**
     * Tablet [id]'s job for [fid] while it matters to the pick panel: on its way, or failed.
     * Null when there is none, or it finished or was cancelled.
     */
    fun jobFor(id: String, fid: String): SyncMessages.JobEntry? =
        _jobs.value[id]?.firstOrNull { it.fid == fid }?.takeIf { JobState.of(it.state).let { s -> s != JobState.DONE && s != JobState.CANCELLED } }

    /** Every tablet's live or failed job for [fid], as (kid name, job): feed to [TogetherText.jobLine]. */
    fun cardJobs(fid: String): List<Pair<String, SyncMessages.JobEntry>> =
        port.nodes().mapNotNull { n -> jobFor(n.id, fid)?.let { sync.kid(n.id) to it } }

    /** How many copies tablet [id] has on the go, for its "Downloading 2" chip. */
    fun downloading(id: String): Int = _jobs.value[id].orEmpty().count { live(it) }

    /** The copy tablet [id] is receiving right now, for its home card. Null when none. */
    fun receiving(id: String): SyncMessages.JobEntry? = _jobs.value[id].orEmpty().let { l ->
        l.firstOrNull { JobState.of(it.state) == JobState.RUNNING || JobState.of(it.state) == JobState.VERIFYING } ?: l.firstOrNull { live(it) }
    }

    /** Online tablets that could take [fid]: they lack it and have no copy of it under way. */
    fun lacking(fid: String): Set<String> {
        val item = sync.library().firstOrNull { it.fid == fid } ?: return emptySet()
        return port.nodes().map { it.id }.filter { id -> able(id) && id !in item.owners && _jobs.value[id].orEmpty().none { it.fid == fid && live(it) } }.toSet()
    }

    /**
     * Why [fid] cannot be sent right now, as the line to show next to a disabled Send button.
     * Null when it can.
     */
    fun sendBlocked(fid: String): String? {
        val item = sync.library().firstOrNull { it.fid == fid } ?: return NO_SOURCE
        return when {
            Library.SELF in item.owners -> if (port.lanAddress() == null) NO_WIFI else null
            source(item, emptySet()) == null -> NO_SOURCE
            else -> null
        }
    }

    // ---- link hooks, called by MasterRuntime ----

    fun onConnected(id: String) = synchronized(lock) { greet += id; Unit }

    fun onDisconnected(id: String) {
        synchronized(lock) {
            greet -= id; asks -= id
            started.removeAll { it.startsWith("$id:") }
            resent.keys.removeAll { it.startsWith("$id:") }
        }
        _jobs.update { it - id }
        val gone = synchronized(lock) { pendingDelete.keys.filter { it.startsWith("$id:") }.also { k -> pendingDelete.keys.removeAll(k.toSet()) } }
        if (gone.isNotEmpty()) _deleting.update { it - gone.toSet() }
    }

    private fun onDeleted(id: String, msg: SyncMessages.DeletedMsg) {
        val key = "$id:${msg.fid}"
        val ask = synchronized(lock) { pendingDelete.remove(key) }
        _deleting.update { it - key }
        // The grid first: the Node's own fresh `videos` comes after it has rescanned.
        if (msg.ok) sync.drop(id, msg.fid)
        if (ask != null) port.say(TogetherText.deleteResult(if (msg.ok) null else msg.err ?: "io", ask.title, sync.kid(id)))
    }

    /** `serving`, `jobs` and `deleted` from tablet [id]. Runs on that link's reader thread. */
    fun onMessage(id: String, m: JSONObject) {
        when (m.optString("t")) {
            "deleted" -> SyncMessages.decodeDeleted(m)?.let { onDeleted(id, it) }
            "serving" -> SyncMessages.decodeServing(m)?.let { onServing(id, it) }
            "jobs" -> SyncMessages.decodeJobs(m, MAX_JOBS)?.let { onJobs(id, it.list) }
        }
    }

    private fun onServing(src: String, msg: SyncMessages.ServingMsg) {
        val ask = synchronized(lock) { asks[src]?.remove(msg.fid) } ?: return // nobody asked: ignore
        val key = msg.key
        val host = port.nodes().firstOrNull { it.id == src }?.host
        if (!msg.ok || key == null || host == null || msg.port !in 1..65535 || !key.all { it.isLetterOrDigit() }) {
            return port.say("${sync.kid(src)} can't find ${ask.item.title} any more")
        }
        // Built here from parts, never taken whole from a tablet: the host is where this Master reaches it.
        val url = "http://$host:${msg.port}/v/${ask.item.fid}?k=$key"
        ask.targets.forEach { fetch(it, ask.item, url) }
    }

    private fun onJobs(id: String, list: List<SyncMessages.JobEntry>) {
        _jobs.update { it + (id to list) }
        val now = clock()
        for (j in list) {
            val key = "$id:${j.fid}"
            when (JobState.of(j.state)) {
                JobState.DONE -> if (synchronized(lock) { resent -= key; started.remove(key) }) port.say("${sync.kid(id)} has ${j.title.take(80)} now")
                JobState.FAILED, JobState.CANCELLED -> synchronized(lock) { started -= key; resent -= key }
                JobState.WAITING -> if (j.err == "source") {
                    // The tablet it was copying from has gone quiet. Find the movie somewhere else, not too often.
                    // Only for a copy the parent started from here: a tablet's report alone never makes
                    // this Master ask another tablet to serve a movie.
                    val due = synchronized(lock) {
                        key in started && (now - (resent[key] ?: Long.MIN_VALUE / 2) >= RESEND_MS).also { if (it) resent[key] = now }
                    }
                    if (due) sync.library().firstOrNull { it.fid == j.fid }?.let { dispatch(it, setOf(id), quiet = true) }
                }
                else -> {}
            }
        }
    }

    // ---- internals ----

    private fun able(id: String) = port.isOnline(id) && port.status(id)?.supportsSync == true

    private fun live(j: SyncMessages.JobEntry) = JobState.of(j.state)?.let { !DownloadPolicy.isTerminal(it) } == true

    /** The first online tablet that has it and can be reached, leaving out [not]. */
    private fun source(item: LibraryItem, not: Set<String>): String? =
        port.nodes().firstOrNull { it.id in item.owners && it.id !in not && it.host != null && able(it.id) }?.id

    /**
     * Gets [item] moving towards [to]. From this tablet when it has the movie, else through one
     * `serve` to a tablet that does, however many tablets are waiting. False when there is no way.
     */
    private fun dispatch(item: LibraryItem, to: Set<String>, quiet: Boolean): Boolean {
        if (Library.SELF in item.owners) {
            // One url per tablet: each reaches this one over its own link, and the addresses differ across networks.
            val urls = to.associateWith { port.localUrl(item.fid, it) }
            urls.forEach { (id, url) -> if (url != null) fetch(id, item, url) }
            if (urls.values.none { it != null }) return false.also { if (!quiet) port.say(NO_WIFI) }
            return true
        }
        val src = source(item, to) ?: return false.also { if (!quiet) port.say(NO_SOURCE) }
        val first = synchronized(lock) {
            val pending = asks.getOrPut(src) { HashMap() }
            val ask = pending[item.fid]
            if (ask != null) { ask.targets += to; false } else { pending[item.fid] = Ask(item, to.toMutableSet(), clock()); true }
        }
        if (first) port.send(src, SyncMessages.cmdServe(item.fid))
        return true
    }

    private fun fetch(id: String, item: LibraryItem, url: String) {
        if (port.send(id, SyncMessages.cmdFetch(item.fid, url, item.size, item.title, item.name))) synchronized(lock) { started += "$id:${item.fid}" }
    }

    /** Housekeeping, every [TICK_MS]. Public so tests drive time themselves. */
    fun tick(now: Long = clock()) {
        val (hello, late) = synchronized(lock) {
            // Like SyncController: a tablet on an old NickOS is never sent a new command.
            val hello = greet.filter { port.status(it)?.supportsSync == true }.also { greet -= it.toSet() }
            val late = ArrayList<Pair<String, Ask>>()
            for ((src, pending) in asks) pending.values.removeAll { a -> (now - a.at > SERVE_WAIT_MS).also { if (it) late += src to a } }
            hello to late
        }
        val timedOut = synchronized(lock) {
            pendingDelete.filterValues { now - it.at > DELETE_WAIT_MS }.keys.toList().also { k -> pendingDelete.keys.removeAll(k.toSet()) }
        }
        if (timedOut.isNotEmpty()) {
            _deleting.update { it - timedOut.toSet() }
            timedOut.forEach { port.say(TogetherText.deleteTimeout(sync.kid(it.substringBefore(':')))) }
        }
        hello.forEach { port.send(it, SyncMessages.cmdJobs()) }
        late.forEach { (src, a) -> port.say("${sync.kid(src)} didn't answer. Try sending ${a.item.title} again.") }
    }

    companion object {
        private const val TAG = "NickOS.Transfer"
        const val TICK_MS = 500L
        const val SERVE_WAIT_MS = 5000L
        const val DELETE_WAIT_MS = 10_000L
        const val RESEND_MS = 15_000L
        private const val MAX_JOBS = 50
        const val NO_WIFI = "This tablet isn't on WiFi"
        const val NO_SOURCE = "No tablet that has it is online"
    }
}
