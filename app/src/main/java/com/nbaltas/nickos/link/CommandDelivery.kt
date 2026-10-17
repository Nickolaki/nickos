package com.nbaltas.nickos.link

/** Tracks queued and in flight writes on one connection. A closed connection never accepts another command. */
class CommandDelivery(private val failed: () -> Unit) {
    private val pending = mutableSetOf<Any>()
    private var closed = false

    @Synchronized fun enqueue(): Any? {
        if (closed) return null
        return Any().also { pending += it }
    }

    @Synchronized fun maySend(ticket: Any) = !closed && ticket in pending

    @Synchronized fun complete(ticket: Any, written: Boolean) {
        if (!pending.remove(ticket)) return
        if (!written) failed()
    }

    @Synchronized fun close() {
        closed = true
        if (pending.isNotEmpty()) { pending.clear(); failed() }
    }
}
