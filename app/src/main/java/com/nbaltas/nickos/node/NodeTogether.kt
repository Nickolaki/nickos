package com.nbaltas.nickos.node

import android.content.Context
import android.os.SystemClock
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.link.MediaServer
import com.nbaltas.nickos.link.WifiHold
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
    private val hold = WifiHold(ctx)
    /** Hands this tablet's movies to other tablets. Started by the first `serve`, then kept for the life of the runtime. */
    val server = MediaServer.of(videos, hold)
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
            "serve" -> m.optString("fid").takeIf { it.isNotEmpty() }?.let { fid -> scope.launch { reply(serving(fid)) } }
            else -> return false
        }
        return true
    }

    private fun videosMessage(): JSONObject = videos.reply().let { SyncMessages.encodeVideos(it.perm, it.list) }

    /** Another tablet is about to fetch [fid] from this one: make sure the server is up and say where. */
    private fun serving(fid: String): JSONObject {
        val v = videos.find(fid) ?: return SyncMessages.encodeServing(fid, false, err = "missing")
        if (!server.start()) return SyncMessages.encodeServing(fid, false, err = "server")
        return SyncMessages.encodeServing(fid, true, server.port, server.key, v.size)
    }

    /** The runtime is stopping. */
    fun release() {
        player.release()
        server.stop()
        hold.close()
    }

    companion object {
        /** What this build tells a Master it can do, inside `status`. */
        val CAPS = listOf("sync1")
        /** Commands that mean the parent wants this tablet doing something else: the movie stops. */
        val LEAVES_PLAYER = setOf("home", "open", "lockApp", "unlink")
    }
}
