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
    /** True only when tablet [id] has a live link that is sealed. False when offline. No default: every port says so. */
    fun isSealed(id: String): Boolean
    fun nodes(): List<LinkedNode>
    /** This tablet's own library, scanned now. Blocking: call from a background thread. */
    fun localVideos(): SyncMessages.VideosMsg
    /** This tablet's address on the WiFi, or null when it is not on one. */
    fun lanAddress(): String?
    /**
     * Where tablet [nodeId] fetches movie [fid] from this tablet. Starts the server. Null when there is no address.
     * The address is the one THAT tablet's live link runs over: with the home WiFi and the car network up together,
     * a single address is wrong for one of them. Not on the main thread.
     */
    fun localUrl(fid: String, nodeId: String): String?
}

/** [MasterPort] over the real runtime. */
class RuntimePort(private val rt: MasterRuntime, private val store: Store) : MasterPort {
    override fun send(id: String, o: JSONObject) = rt.send(id, o)
    override fun say(msg: String) = rt.say(msg)
    override fun isOnline(id: String) = id in rt.online.value
    override fun status(id: String) = rt.status.value[id]
    override fun isSealed(id: String) = rt.isSealed(id)
    override fun nodes() = store.nodes.value
    override fun localVideos() = rt.videos.reply()
    override fun lanAddress() = rt.lanAddress()
    override fun localUrl(fid: String, nodeId: String) = rt.localUrl(fid, nodeId)
}
