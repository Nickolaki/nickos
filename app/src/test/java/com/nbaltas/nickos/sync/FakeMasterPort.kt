package com.nbaltas.nickos.sync

import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.master.MasterPort
import com.nbaltas.nickos.master.NodeStatus
import org.json.JSONArray
import org.json.JSONObject

/** A Master with no sockets: records what the controllers send and say. Needs Robolectric (org.json). */
class FakeMasterPort : MasterPort {
    /** Everything sent, in order, as (tablet id, message). */
    val sent = ArrayList<Pair<String, JSONObject>>()
    val said = ArrayList<String>()
    val online = HashSet<String>()
    val statuses = HashMap<String, NodeStatus>()
    var linked = listOf<LinkedNode>()
    var local = SyncMessages.VideosMsg(true, emptyList())

    /** Adds an online tablet. [capable] false is a tablet on a NickOS from before Watch together. */
    fun add(id: String, name: String, capable: Boolean = true, host: String? = "192.168.1.${20 + linked.size}", canDelete: Boolean = capable) {
        linked = linked + LinkedNode(id, name, 0, "initial", "token", host = host)
        online += id
        val o = JSONObject().put("volume", 50)
        if (capable) o.put("caps", JSONArray(if (canDelete) listOf("sync1", "del1") else listOf("sync1")))
        statuses[id] = NodeStatus.from(o)
    }

    override fun send(id: String, o: JSONObject): Boolean {
        if (id !in online) return false
        sent += id to o
        return true
    }

    override fun say(msg: String) { said += msg }
    override fun isOnline(id: String) = id in online
    override fun status(id: String) = statuses[id]
    override fun nodes() = linked
    override fun localVideos() = local
    /** This Master's WiFi address. Null: not on WiFi. */
    var lan: String? = "192.168.1.2"
    override fun lanAddress() = lan
    override fun localUrl(fid: String) = lan?.let { "http://$it:47471/v/$fid?k=selfkey" }

    /** Commands named [c] sent to [id], oldest first. */
    fun cmds(id: String, c: String): List<JSONObject> = sent.filter { it.first == id && it.second.optString("c") == c }.map { it.second }
}
