package com.nbaltas.nickos.master

import com.nbaltas.nickos.link.Subnet
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * One pair() call. Its sockets are closed on cancel or timeout, and [commit] (the persistence step) runs only while the
 * attempt is live, under the same lock the cancel takes, so a handshake finishing late can never save a node.
 */
class PairAttempt {
    private val lock = Any()
    private var cancelled = false
    private val open = HashSet<java.io.Closeable>()

    /** Registers [c]; false (and [c] closed) when the attempt is already cancelled. */
    fun track(c: java.io.Closeable): Boolean {
        val ok = synchronized(lock) { if (!cancelled) { open += c; true } else false }
        if (!ok) runCatching { c.close() }
        return ok
    }

    fun untrack(c: java.io.Closeable) { synchronized(lock) { open -= c } }

    fun cancel() {
        val toClose = synchronized(lock) { cancelled = true; open.toList().also { open.clear() } }
        toClose.forEach { runCatching { it.close() } }
    }

    /** Runs [block] under the lock if not cancelled; null otherwise. */
    fun <T : Any> commit(block: () -> T): T? = synchronized(lock) { if (cancelled) null else block() }
}

/**
 * Pairing without mDNS. If the NSD search found nothing for [WAIT_MS] and no address was typed, the /24 is swept once
 * on the Node port, and each open host gets one pair try. The first success wins and stops the rest.
 */
object PairFallback {
    const val WAIT_MS = 3_000L

    /** True only after the NSD miss: time is up, nothing found, nothing typed, and the sweep has not run yet. */
    fun due(elapsedMs: Long, foundCount: Int, typed: Boolean, alreadySwept: Boolean): Boolean =
        !alreadySwept && !typed && foundCount == 0 && elapsedMs >= WAIT_MS

    /** One pass over every host of the own addresses' /24s. Returns the first non-null [tryPair] result, or null. */
    suspend fun <T : Any> sweep(
        ownIps: List<String>,
        prober: Prober,
        port: Int,
        maxInFlight: Int = Sweep.Config().maxInFlight,
        tryPair: suspend (id: String, addr: Pair<String, Int>) -> T?,
    ): T? {
        val hosts = ownIps.distinct().flatMap { Subnet.hosts(it).orEmpty() }.distinct().filterNot { it in ownIps }
        if (hosts.isEmpty()) return null
        val queue = ConcurrentLinkedQueue(hosts)
        val lock = Mutex()
        val winner = AtomicReference<T?>(null)
        coroutineScope {
            val workers = java.util.concurrent.CopyOnWriteArrayList<Job>()
            repeat(minOf(maxInFlight, hosts.size)) {
                workers += launch {
                    while (isActive && winner.get() == null) {
                        val host = queue.poll() ?: break
                        val id = prober.probe(host, port) ?: continue
                        // Another worker may have won while this probe ran: no more pair tries then.
                        if (!isActive || winner.get() != null) break
                        // One handshake at a time; the checks are repeated inside the lock.
                        // The win is recorded inside the lock too, so the next worker in sees it.
                        val won = lock.withLock {
                            if (!isActive || winner.get() != null) return@withLock false
                            val r = tryPair(id, host to port) ?: return@withLock false
                            winner.compareAndSet(null, r)
                        }
                        if (won) {
                            queue.clear()
                            val me = coroutineContext[Job]
                            workers.forEach { if (it !== me) it.cancel() }
                        }
                    }
                }
            }
        }
        return winner.get()
    }
}
