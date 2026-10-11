package com.nbaltas.nickos.master

import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.sync.SyncMessages
import org.json.JSONObject

/**
 * The little of [MasterRuntime] the Watch together controllers use, as an interface so their
 * logic runs in tests against a fake with no sockets.
 */
interface MasterPort {
    /** Queue a command for a tablet. False when it is not connected. Safe on any thread. */
    fun send(id: String, o: JSONObject): Boolean
    /** One-line toast on the Master. */
    fun say(msg: String)
    fun isOnline(id: String): Boolean
    fun status(id: String): NodeStatus?
    fun nodes(): List<LinkedNode>
    /** This tablet's own library, scanned now. Blocking: call from a background thread. */
    fun localVideos(): SyncMessages.VideosMsg
}

/** [MasterPort] over the real runtime. */
class RuntimePort(private val rt: MasterRuntime, private val store: Store) : MasterPort {
    override fun send(id: String, o: JSONObject) = rt.send(id, o)
    override fun say(msg: String) = rt.say(msg)
    override fun isOnline(id: String) = id in rt.online.value
    override fun status(id: String) = rt.status.value[id]
    override fun nodes() = store.nodes.value
    override fun localVideos() = rt.videos.reply()
}
