package com.nbaltas.nickos.node

/**
 * Keeps a `sync` that looked a movie up BEFORE it was deleted from loading it afterwards. A sync
 * notes [now] when it looks the movie up; a delete calls [forget]; the queued load asks [stale].
 * A sync that looked up after the delete (the movie was sent again) is not stale. Main thread
 * for [forget] and [stale]; [now] is a volatile read.
 */
class ForgetGate {
    @Volatile private var gen = 0
    private val forgotten = HashMap<String, Int>()

    /** The generation to remember when a lookup starts. */
    fun now(): Int = gen

    /** [fid] is being deleted: every lookup that started so far is stale for it. */
    fun forget(fid: String) { forgotten[fid] = ++gen }

    /** True when a lookup of [fid] made at generation [at] predates its deletion. */
    fun stale(fid: String, at: Int): Boolean = (forgotten[fid] ?: -1) > at
}
