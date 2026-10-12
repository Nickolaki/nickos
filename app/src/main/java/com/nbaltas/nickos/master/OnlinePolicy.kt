package com.nbaltas.nickos.master

import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.link.NetMessages
import org.json.JSONObject

/** What the Master last learned about a Node's WiFi, from its status. */
data class NodeNet(val supportsWifi: Boolean, val wifi: NetMessages.WifiStatus) {
    companion object {
        fun from(status: JSONObject) = NodeNet(NetMessages.supportsWifi(status), NetMessages.decodeWifi(status))
    }
}

/**
 * Why a linked tablet is offline, for the home banner. Three reasons, in priority order, no more:
 * a) this tablet has no LAN address of its own, so nothing can be found;
 * b) the Node never got the hotspot details (or predates the feature);
 * c) everything is in place, so we are looking for it.
 */
sealed class Reason {
    object MasterNotOnWifi : Reason() { override fun toString() = "MasterNotOnWifi" }
    object NoHotspotDetails : Reason() { override fun toString() = "NoHotspotDetails" }
    data class Looking(val lastSeenAgoMs: Long?) : Reason()
}

object OnlinePolicy {
    /**
     * [masterLan] is this tablet's own LAN address (the ap/swlan address counts when it hosts the hotspot).
     * [wifiSaved] is the Node's last report: true, false, or null when never reported since linking.
     * Null counts as "no details": there is no evidence the Node has them.
     */
    fun reason(masterLan: String?, wifiSaved: Boolean?, lastSeen: Long, now: Long): Reason = when {
        masterLan == null -> Reason.MasterNotOnWifi
        wifiSaved != true -> Reason.NoHotspotDetails
        else -> Reason.Looking(if (lastSeen > 0) (now - lastSeen).coerceAtLeast(0) else null)
    }

    /** The saved flag to show and persist: a live status wins; an old Node (no wifi1) never has details. */
    fun wifiSaved(net: NodeNet?, persisted: Boolean?): Boolean? =
        if (net == null) persisted else net.supportsWifi && net.wifi.saved

    /** Linked tablets that are not online right now, in list order. */
    fun offline(nodes: List<LinkedNode>, online: Set<String>): List<LinkedNode> = nodes.filter { it.id !in online }
}
