package com.nbaltas.nickos.node

import android.content.Context
import android.os.SystemClock
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.sync.SyncMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The Node's side of Watch together, kept out of [NodeRuntime] so that class only gains hooks:
 * the movie library, the synced player and the answers to the Master's new commands.
 * [broadcast] writes to every connected Master and is only ever called off the main thread.
 */
class NodeTogether(
    ctx: Context,
    store: Store,
    device: NodeDevice,
    private val scope: CoroutineScope,
    homeVisible: () -> Boolean,
    private val broadcast: (JSONObject) -> Unit,
) {
    val videos = LocalVideos(ctx)
    val player = SyncPlayer(
        ctx, videos, device,
        isLocked = { store.lockedBy.value != null },
        homeVisible = homeVisible,
        report = { o -> scope.launch { broadcast(o) } },
    )

    /**
     * Handles command [c] if it is one of this feature's and says so. Runs on the socket thread of
     * the Master that sent it; [reply] answers that Master only (a pong must not reach another
     * Master, whose ping ids are its own).
     */
    fun command(c: String, m: JSONObject, reply: (JSONObject) -> Unit): Boolean {
        when (c) {
            "ping" -> {
                val t1 = SystemClock.elapsedRealtime()
                reply(SyncMessages.encodePong(m.optInt("id"), m.optLong("t0"), t1, SystemClock.elapsedRealtime()))
            }
            // Scanning hashes new files and makes thumbnails: not on the socket thread, pings wait behind it.
            "videos" -> scope.launch { reply(videosMessage()) }
            "sync" -> SyncMessages.decodeSyncCmd(m)?.let { player.onSync(it) }
            "syncPause" -> player.onPause()
            "syncStop" -> player.stop()
            else -> return false
        }
        return true
    }

    private fun videosMessage(): JSONObject = videos.reply().let { SyncMessages.encodeVideos(it.perm, it.list) }

    /** The runtime is stopping. */
    fun release() = player.release()

    companion object {
        /** What this build tells a Master it can do, inside `status`. */
        val CAPS = listOf("sync1")
        /** Commands that mean the parent wants this tablet doing something else: the movie stops. */
        val LEAVES_PLAYER = setOf("home", "open", "lockApp", "unlink")
    }
}
