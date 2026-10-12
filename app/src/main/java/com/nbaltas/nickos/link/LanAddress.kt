package com.nbaltas.nickos.link

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The address other tablets on the same WiFi reach this one at, for the urls a [MediaServer]
 * hands out. A phone hotspot counts (192.168.43.x, 10.x). Never a loopback or wildcard address:
 * no answer is better than a url that cannot work.
 */
object LanAddress {
    private val DOTTED = Regex("^(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})$")

    /**
     * The four bytes of a plain dotted IPv4 address, or null. Deliberately narrow: four decimal
     * numbers with no leading zeros, no hex, no short forms and no names, so there is exactly one
     * way to read what passes and nothing is left for a resolver to interpret differently.
     */
    fun octets(ip: String): ByteArray? {
        val m = DOTTED.matchEntire(ip) ?: return null
        val p = m.groupValues.drop(1).map { it.toInt() }
        if (p.any { it > 255 }) return null
        return ByteArray(4) { p[it].toByte() }
    }

    /** 10.x.x.x, 172.16 to 172.31, 192.168.x.x. */
    fun isSiteLocal(ip: String): Boolean {
        val p = octets(ip)?.map { it.toInt() and 0xff } ?: return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    /**
     * Picks from [found] (interface name to IPv4 address, in system order). [wifi] is what
     * WifiManager says, used when it agrees with a real interface; then a WiFi or hotspot
     * interface by name; then any private address at all (ethernet, USB tethering).
     */
    fun choose(found: List<Pair<String, String>>, wifi: String?): String? {
        val ok = found.filter { isSiteLocal(it.second) }
        ok.firstOrNull { it.second == wifi }?.let { return it.second }
        // An ordinary WiFi or hotspot interface beats a Wi-Fi Direct one: with the home WiFi up, the home LAN is the
        // single address callers want; the Direct group is reached through [all].
        ok.firstOrNull { (name, _) -> WIFI_NAMES.any { name.startsWith(it) } }?.let { return it.second }
        ok.firstOrNull { (name, _) -> isDirect(name) }?.let { return it.second }
        return ok.firstOrNull()?.second
    }

    /** Wi-Fi Direct group interfaces: `p2p-wlan0-0` on most devices, `p2p0` on others. */
    fun isDirect(interfaceName: String): Boolean = interfaceName.startsWith("p2p")

    /** Every site-local address, as (interface name, address), without repeats. The Master sweeps all of them. */
    fun all(found: List<Pair<String, String>>): List<Pair<String, String>> =
        found.filter { isSiteLocal(it.second) }.distinctBy { it.second }

    /** All of this tablet's site-local addresses: the home WiFi and a Wi-Fi Direct group it hosts can be up together. */
    fun all(ctx: Context): List<Pair<String, String>> = all(interfaces())

    /** The local end of a Node's socket when it is a real site-local address, else [fallback]. */
    fun localFor(socketLocal: String?, fallback: String?): String? =
        socketLocal?.takeIf { isSiteLocal(it) } ?: fallback

    private fun interfaces(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>().mapNotNull { a -> a.hostAddress?.let { nic.name to it } } }
    } catch (e: Exception) { emptyList() }

    /** This tablet's address on the WiFi, or null when it is not on one. */
    fun own(ctx: Context): String? {
        val found = interfaces()
        val wifi = try {
            @Suppress("DEPRECATION")
            (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ipAddress?.takeIf { it != 0 }
                ?.let { "${it and 0xff}.${it shr 8 and 0xff}.${it shr 16 and 0xff}.${it shr 24 and 0xff}" }
        } catch (e: Exception) { null }
        return choose(found, wifi)
    }

    private val WIFI_NAMES = listOf("wlan", "swlan", "ap", "softap", "wl")
}
