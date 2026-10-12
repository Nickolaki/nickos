package com.nbaltas.nickos.master

import android.util.Log
import com.nbaltas.nickos.link.Subnet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

/** Opens [host]:[port], sends one RTSP OPTIONS, true if the first line back starts with "RTSP/". */
fun interface Knock {
    suspend fun rtsp(host: String, port: Int): Boolean
}

/**
 * The real knock: a short TCP connect, one OPTIONS request, one bounded line back. The guard is the one
 * [SocketProber] uses: soTimeout only bounds one read and a blocking read ignores coroutine cancellation, so
 * the socket is closed at the overall deadline and when the knock is cancelled.
 */
class RtspKnock(private val timeoutMs: Int = 400, private val totalMs: Long = 1_500) : Knock {
    override suspend fun rtsp(host: String, port: Int): Boolean = coroutineScope {
        val s = Socket()
        val guard = launch { try { delay(totalMs) } finally { try { s.close() } catch (e: Exception) { } } }
        try {
            withContext(Dispatchers.IO) {
                try {
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                    s.soTimeout = timeoutMs
                    s.getOutputStream().apply { write("OPTIONS rtsp://$host:$port/ RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray()); flush() }
                    // A stranger may stream forever: take at most 512 bytes and stop at the first newline.
                    val buf = StringBuilder()
                    val ins = s.getInputStream()
                    while (buf.length < 512) {
                        val b = ins.read()
                        if (b < 0 || b == '\n'.code) break
                        buf.append(b.toChar())
                    }
                    buf.startsWith("RTSP/")
                } catch (e: Exception) { false }
            }
        } finally {
            guard.cancel()
            try { s.close() } catch (e: Exception) { }
        }
    }
}

/**
 * Finds cameras on the car network: every host of the /24 of each of this tablet's own addresses that
 * answers RTSP on [Config.port]. The caller holds one Job, so two finders never run at once.
 */
class CamFinder(
    private val ownIps: () -> List<String>,
    private val knock: Knock,
    private val config: Config = Config(),
) {
    data class Config(val maxInFlight: Int = 32, val budgetMs: Long = 12_000, val port: Int = 554)

    /**
     * Every RTSP host on the /24 of each own address, the Direct group (192.168.49.) first, own addresses
     * excluded. [onFound] fires as each one answers so the editor can show it at once. Returns them in the
     * order found. A pass cut short by the budget has covered the Direct /24 first.
     */
    suspend fun find(onFound: (String) -> Unit = {}): List<String> {
        val ips = ownIps().distinct()
        val hosts = ips.sortedByDescending { it.startsWith(DIRECT_PREFIX) }
            .flatMap { Subnet.hosts(it).orEmpty() }.distinct().filterNot { it in ips }
        if (hosts.isEmpty()) return emptyList()
        val found = java.util.Collections.synchronizedList(ArrayList<String>())
        val queue = ConcurrentLinkedQueue(hosts)
        withTimeoutOrNull(config.budgetMs) {
            coroutineScope {
                repeat(minOf(config.maxInFlight, hosts.size)) {
                    launch {
                        while (isActive) {
                            val host = queue.poll() ?: break
                            if (knock.rtsp(host, config.port)) { found += host; onFound(host) }
                        }
                    }
                }
            }
        }
        Log.i(TAG, "found ${found.size} rtsp host(s)")
        return found.toList()
    }

    companion object {
        private const val TAG = "NickOS.CamFinder"
        private const val DIRECT_PREFIX = "192.168.49."
    }
}
