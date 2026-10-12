package com.nbaltas.nickos.link

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** A map-backed [SecFlags] for tests. */
class MemFlags : SecFlags {
    val m = HashMap<String, Boolean>()
    override fun sec2(id: String) = m[id] ?: false
    override fun setSec2(id: String, v: Boolean) { m[id] = v }
    val cap = HashMap<String, Boolean>()
    override fun sec2Capable(id: String) = cap[id] ?: false
    override fun setSec2Capable(id: String, v: Boolean) { cap[id] = v }
}

/**
 * Two connected [Conn]s on localhost, with a recording relay in the middle: [toNode] holds every raw byte the
 * Master wrote, [toMaster] every raw byte the Node wrote. This is what a sniffer on the Wi-Fi would see.
 */
class Link : AutoCloseable {
    val toNode = ByteArrayOutputStream()
    val toMaster = ByteArrayOutputStream()
    val master: Conn
    val node: Conn
    private val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    private val relay = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }

    init {
        val nodeSide = pool.submit(Callable { server.accept() })
        val relayIn = pool.submit(Callable { relay.accept() })
        val masterSock = Socket(InetAddress.getByName("127.0.0.1"), relay.localPort)
        val inSock = relayIn.get(5, TimeUnit.SECONDS)
        val outSock = Socket(InetAddress.getByName("127.0.0.1"), server.localPort)
        val nodeSock = nodeSide.get(5, TimeUnit.SECONDS)
        pool.submit { pump(inSock, outSock, toNode) }
        pool.submit { pump(outSock, inSock, toMaster) }
        masterSock.soTimeout = 5000
        nodeSock.soTimeout = 5000
        master = Conn(masterSock)
        node = Conn(nodeSock)
    }

    private fun pump(from: Socket, to: Socket, rec: ByteArrayOutputStream) {
        try {
            val b = ByteArray(4096)
            while (true) {
                val n = from.getInputStream().read(b)
                if (n < 0) break
                synchronized(rec) { rec.write(b, 0, n) }
                to.getOutputStream().write(b, 0, n); to.getOutputStream().flush()
            }
        } catch (_: Exception) {}
        try { to.shutdownOutput() } catch (_: Exception) {}
    }

    fun <T> onNode(f: () -> T): Future<T> = pool.submit(Callable { f() })

    fun rawToNode(): String = synchronized(toNode) { toNode.toString(Charsets.ISO_8859_1) }
    fun rawToMaster(): String = synchronized(toMaster) { toMaster.toString(Charsets.ISO_8859_1) }

    override fun close() {
        master.close(); node.close()
        try { server.close() } catch (_: Exception) {}
        try { relay.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }
}
