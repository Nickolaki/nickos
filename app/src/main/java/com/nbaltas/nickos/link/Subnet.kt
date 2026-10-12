package com.nbaltas.nickos.link

/**
 * Subnet maths for the Master's sweep. Phone hotspots hand out a /24 (Android picks a random
 * 192.168.x.0/24 per session, iPhone uses 172.20.10.0/28 which sits inside one), and a tablet hosting its
 * own hotspot is the .1 of a /24, so the whole /24 of the Master's own site-local address is the search space.
 */
object Subnet {
    /** Every host of [ownIp]'s /24 except itself, .0 and .255; null when [ownIp] is not a site-local IPv4 address. */
    fun hosts(ownIp: String): List<String>? {
        if (!LanAddress.isSiteLocal(ownIp)) return null
        val o = LanAddress.octets(ownIp) ?: return null
        val a = o[0].toInt() and 0xff
        val b = o[1].toInt() and 0xff
        val c = o[2].toInt() and 0xff
        val me = o[3].toInt() and 0xff
        return (1..254).filter { it != me }.map { "$a.$b.$c.$it" }
    }
}
