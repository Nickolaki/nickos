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
    private val ctx: Context,
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
    /** Movies being copied to this tablet. A finished copy puts the new library in front of every Master. */
    val jobs = DownloadJobs(
        AndroidJobIo(ctx, videos, hold), ctx.getSharedPreferences(DownloadJobs.PREFS, Context.MODE_PRIVATE), scope,
        report = { o -> broadcast(o) },
        onDone = { broadcast(videosMessage()) },
    )
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
            // Fetch looks through the library first (is the movie already here?), so not on the socket thread either.
            "fetch" -> SyncMessages.decodeFetchCmd(m)?.let { f -> scope.launch { jobs.fetch(f) } }
            "cancel" -> m.optString("fid").takeIf { it.isNotEmpty() }?.let { fid -> scope.launch { jobs.cancel(fid) } }
            "jobs" -> reply(jobs.message())
            // Deleting touches MediaStore, so not on the socket thread. The asking Master gets `deleted`, every Master the new list.
            "delete" -> m.optString("fid").takeIf { it.isNotEmpty() }?.let { fid -> scope.launch { reply(deleteMovie(fid)); broadcast(videosMessage()) } }
            else -> return false
        }
        return true
    }

    private fun videosMessage(): JSONObject = videos.reply().let { SyncMessages.encodeVideos(it.perm, it.list, it.free) }

    /** Stops the synced player if it is on [fid], then removes every copy of it. */
    private fun deleteMovie(fid: String): JSONObject {
        if (player.currentFid() == fid) player.stop()
        val o = videos.delete(fid)
        return SyncMessages.encodeDeleted(fid, o.ok, o.err, o.removed, videos.freeBytes())
    }

    /** Another tablet is about to fetch [fid] from this one: make sure the server is up and say where. */
    private fun serving(fid: String): JSONObject {
        val v = videos.find(fid) ?: return SyncMessages.encodeServing(fid, false, err = "missing")
        if (!server.start()) return SyncMessages.encodeServing(fid, false, err = "server")
        return SyncMessages.encodeServing(fid, true, server.port, server.grant(fid), v.size)
    }

    /** The runtime started: copies that were under way before a restart or reboot carry on. */
    fun start() = jobs.start()

    /** The runtime is stopping. */
    fun release() {
        player.release()
        jobs.stop()
        server.stop()
        hold.close()
    }

    companion object {
        /** What this build tells a Master it can do, inside `status`. */
        val CAPS = listOf("sync1", "del1")
        /** Commands that mean the parent wants this tablet doing something else: the movie stops. */
        val LEAVES_PLAYER = setOf("home", "open", "lockApp", "unlink")
    }
}
