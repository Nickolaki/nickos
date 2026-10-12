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
 * Why a linked tablet is offline, for the home banner. Four reasons, in priority order, no more:
 * a) the car network this tablet hosts is down (one line for all tablets, not one per tablet);
 * b) with a typed-in hotspot: this tablet has no LAN address of its own, so nothing can be found;
 * c) the Node never got the network details (or predates the feature);
 * d) everything is in place, so we are looking for it.
 */
sealed class Reason {
    data class CarNetDown(val why: Why) : Reason()
    object MasterNotOnWifi : Reason() { override fun toString() = "MasterNotOnWifi" }
    object NoHotspotDetails : Reason() { override fun toString() = "NoHotspotDetails" }
    data class Looking(val lastSeenAgoMs: Long?) : Reason()
}

object OnlinePolicy {
    /** How long a car network may stay Starting before it counts as failed. */
    const val STARTING_GRACE_MS = 20_000L

    /**
     * [carNet] is the state of the network this tablet hosts and [manual] says the parent typed a hotspot in instead
     * (so the hosted network is deliberately off). [startingSince] is when [carNet] became Starting.
     * [masterLan] is this tablet's own LAN address, which only matters for a typed-in hotspot.
     * [wifiSaved] is the Node's last report: true, false, or null when never reported since linking.
     * Null counts as "no details": there is no evidence the Node has them.
     */
    fun reason(carNet: CarNetState, manual: Boolean, masterLan: String?, wifiSaved: Boolean?, lastSeen: Long, now: Long,
               startingSince: Long = now): Reason = when {
        !manual && carNet is CarNetState.Off && carNet.why != Why.MANUAL -> Reason.CarNetDown(carNet.why)
        !manual && carNet is CarNetState.Starting && now - startingSince > STARTING_GRACE_MS -> Reason.CarNetDown(Why.FAILED)
        manual && masterLan == null -> Reason.MasterNotOnWifi
        wifiSaved != true -> Reason.NoHotspotDetails
        else -> Reason.Looking(if (lastSeen > 0) (now - lastSeen).coerceAtLeast(0) else null)
    }

    /** The saved flag to show and persist: a live status wins; an old Node (no wifi1) never has details. */
    fun wifiSaved(net: NodeNet?, persisted: Boolean?): Boolean? =
        if (net == null) persisted else net.supportsWifi && net.wifi.saved

    /** Linked tablets that are not online right now, in list order. */
    fun offline(nodes: List<LinkedNode>, online: Set<String>): List<LinkedNode> = nodes.filter { it.id !in online }
}
