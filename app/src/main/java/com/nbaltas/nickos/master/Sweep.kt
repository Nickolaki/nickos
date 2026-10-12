package com.nbaltas.nickos.master

import android.util.Log
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.link.Subnet
import com.nbaltas.nickos.link.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Where each linked Node was last seen on this network, tagged with an EPOCH. Every network change bumps the
 * epoch and clears the table; a result from before the change (an NSD resolve still in flight, a sweep pass that
 * started on the old subnet) carries the old epoch and is dropped, so a late answer can never put a stale
 * address back after the reset.
 */
class FoundTable {
    private val map = ConcurrentHashMap<String, Pair<String, Int>>()
    @Volatile var epoch = 0L
        private set

    operator fun get(id: String): Pair<String, Int>? = map[id]
    fun ids(): Set<String> = map.keys.toSet()
    fun entries(): List<Pair<String, Pair<String, Int>>> = map.entries.map { it.key to it.value }

    /** Starts a new epoch with an empty table and returns it. */
    @Synchronized fun reset(): Long { epoch++; map.clear(); return epoch }

    /** Stores [addr] for [id] unless the network changed since [atEpoch]. True when it was stored. */
    @Synchronized fun put(atEpoch: Long, id: String, addr: Pair<String, Int>): Boolean {
        if (atEpoch != epoch) return false
        map[id] = addr
        return true
    }

    /** Removes [id] only if [atEpoch] is still current. */
    @Synchronized fun remove(atEpoch: Long, id: String): Boolean {
        if (atEpoch != epoch) return false
        return map.remove(id) != null
    }

    /** Drops [id] only if it still points at [addr]: a newer address found meanwhile must survive. */
    @Synchronized fun removeIf(id: String, addr: Pair<String, Int>): Boolean = map.remove(id, addr)
}

/** Opens a connection to [host] and asks "who" once. Returns the validated node id that answered, or null. */
fun interface Prober {
    suspend fun probe(host: String, port: Int): String?
}

/** The real prober: a short TCP connect, one line out, one bounded line back. */
class SocketProber(private val timeoutMs: Int = 400, private val totalMs: Long = 1_000) : Prober {
    /**
     * soTimeout only bounds ONE read, so a host trickling a byte every 300 ms could hold a probe for minutes, and a
     * blocking read ignores coroutine cancellation. The guard closes the socket when the overall deadline passes AND
     * when the probe is cancelled (the pass ended, the network changed), which makes the blocked read throw.
     */
    override suspend fun probe(host: String, port: Int): String? = coroutineScope {
        val s = Socket()
        val guard = launch { try { delay(totalMs) } finally { try { s.close() } catch (e: Exception) { } } }
        try {
            withContext(Dispatchers.IO) {
                try {
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                    s.soTimeout = timeoutMs
                    s.getOutputStream().apply { write((NetMessages.who().toString() + "\n").toByteArray()); flush() }
                    // A stranger may stream forever: take at most 512 bytes and stop at the first newline.
                    val buf = StringBuilder()
                    val ins = s.getInputStream()
                    while (buf.length < 512) {
                        val b = ins.read()
                        if (b < 0 || b == '\n'.code) break
                        buf.append(b.toChar())
                    }
                    NetMessages.decodeIamLine(buf.toString())?.nodeId
                } catch (e: Exception) { null }
            }
        } finally {
            guard.cancel()
            try { s.close() } catch (e: Exception) { }
        }
    }
}

/**
 * A second way to find a Node that needs no multicast: probe every host of this tablet's own /24 on [Wire.PORT].
 * [ownIp] is whatever LanAddress.own says: a WiFi address when this tablet joined someone's hotspot, or the
 * ap/swlan address when THIS tablet hosts the hotspot (the usual case, and the same maths either way).
 *
 * Races it is built around:
 *  - The network changes mid-pass: [now] cancels the running pass, and a result is only recorded when the epoch
 *    and the own address are still the ones the pass started with ([FoundTable.put]).
 *  - Two passes never run at once: [now] and the timer share one slot.
 *  - A pass stops as soon as nothing is missing any more, so a Node that links by mDNS mid-pass ends it.
 */
class Sweep(
    private val scope: CoroutineScope,
    private val ownIp: () -> String?,
    /** Linked Node ids that are neither online nor in the found table right now. */
    private val missing: () -> Set<String>,
    private val prober: Prober,
    private val table: FoundTable,
    private val onFound: (id: String, addr: Pair<String, Int>) -> Unit,
    private val config: Config = Config(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Config(
        val maxInFlight: Int = 32,
        /** A Node must be missing this long before the timer starts a pass. */
        val missingForMs: Long = 15_000,
        val repeatMs: Long = 20_000,
        /** Hard cut-off for one pass. 253 hosts at 32 in flight and 400 ms is about 3 s; this is the safety net. */
        val passBudgetMs: Long = 8_000,
        val tickMs: Long = 2_000,
        val port: Int = Wire.PORT,
    )

    private var passJob: Job? = null
    private var loopJob: Job? = null
    private val missingSince = ConcurrentHashMap<String, Long>()
    @Volatile private var lastPassAt = 0L
    /** Passes that ran to the end (cancelled ones do not count). */
    @Volatile var passCount = 0
        private set

    /** One pass. Returns the ids reported. Public for tests; the runtime uses [now] and [start]. */
    suspend fun pass(): Set<String> {
        val ip = ownIp() ?: return emptySet()
        val hosts = Subnet.hosts(ip) ?: return emptySet()
        val wanted = missing()
        if (wanted.isEmpty()) return emptySet()
        val epoch = table.epoch
        val reported = ConcurrentHashMap.newKeySet<String>()
        val queue = ConcurrentLinkedQueue(hosts)
        withTimeoutOrNull(config.passBudgetMs) {
            coroutineScope {
                repeat(minOf(config.maxInFlight, hosts.size)) {
                    launch {
                        while (isActive) {
                            // Nothing missing any more (it linked another way): stop probing.
                            if (missing().isEmpty()) break
                            val host = queue.poll() ?: break
                            val id = prober.probe(host, config.port) ?: continue
                            // A stranger, or a Node that is already online, is ignored; each id is reported once per pass.
                            if (id !in wanted || !reported.add(id)) continue
                            // The tablet moved networks while this pass ran: its answers describe the old one.
                            if (ownIp() != ip) { reported.remove(id); continue }
                            val addr = host to config.port
                            if (table.put(epoch, id, addr)) onFound(id, addr) else reported.remove(id)
                        }
                    }
                }
            }
        }
        return reported.toSet()
    }

    /** Sweep now, replacing any pass in flight (the network just changed). */
    @Synchronized
    fun now() {
        passJob?.cancel()
        passJob = scope.launch { runPass() }
    }

    private suspend fun runPass() {
        try { pass() } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; Log.w(TAG, "sweep failed", e) }
        lastPassAt = clock()
        passCount++
    }

    /** The timer: sweep while some Node has been missing for [Config.missingForMs], every [Config.repeatMs], never otherwise. */
    @Synchronized
    fun start() {
        if (loopJob != null) return
        loopJob = scope.launch {
            while (isActive) {
                delay(config.tickMs)
                tick()
            }
        }
    }

    @Synchronized
    fun stop() { loopJob?.cancel(); loopJob = null; passJob?.cancel(); passJob = null }

    /** One timer step; split out so tests drive it without waiting. */
    @Synchronized
    fun tick() {
        val now = clock()
        val missingNow = missing()
        missingSince.keys.retainAll(missingNow)
        missingNow.forEach { missingSince.putIfAbsent(it, now) }
        if (missingNow.isEmpty()) return
        if (missingSince.values.none { now - it >= config.missingForMs }) return
        if (passJob?.isActive == true) return
        if (lastPassAt != 0L && now - lastPassAt < config.repeatMs) return
        passJob = scope.launch { runPass() }
    }

    companion object { private const val TAG = "NickOS.Sweep" }
}
