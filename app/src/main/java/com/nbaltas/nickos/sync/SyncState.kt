package com.nbaltas.nickos.sync

/**
 * What a Node's player is doing, as it reports it in `syncState` and as its own screen shows it.
 * [wire] is the lowercase name on the link. IDLE never goes on the wire (nothing is loaded).
 * STOPPED is the Node's last word when it leaves a session by itself (the parent sent it home,
 * opened another app on it, or its player closed after an error), so the Master can drop it
 * without waiting for a timeout.
 */
enum class SyncState {
    IDLE, LOADING, READY, PLAYING, PAUSED, BUFFERING, ENDED, ERROR, LOCKED, STOPPED;

    val wire: String get() = name.lowercase()

    /** The tablet has the movie loaded and is part of the group (playing or able to resume). */
    val active: Boolean get() = this == LOADING || this == READY || this == PLAYING || this == PAUSED || this == BUFFERING || this == LOCKED

    companion object {
        /** Null for a state this build does not know, so a newer peer cannot crash an older one. */
        fun of(wire: String?): SyncState? = entries.firstOrNull { it.wire == wire }
    }
}
