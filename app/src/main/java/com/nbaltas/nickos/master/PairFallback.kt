package com.nbaltas.nickos.master

import com.nbaltas.nickos.link.Subnet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

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
        val winner = AtomicReference<T?>(null)
        coroutineScope {
            repeat(minOf(maxInFlight, hosts.size)) {
                launch {
                    while (isActive && winner.get() == null) {
                        val host = queue.poll() ?: break
                        val id = prober.probe(host, port) ?: continue
                        val r = tryPair(id, host to port) ?: continue
                        if (winner.compareAndSet(null, r)) queue.clear()
                    }
                }
            }
        }
        return winner.get()
    }
}
