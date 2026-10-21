package io.shamash.feature.companion

import io.shamash.data.ServerUrl
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

/**
 * Keeps a feed added from the phone off the home network, so the phone can't use the TV to probe it: private,
 * loopback, link-local, carrier-grade NAT, multicast and unique-local addresses, local names, and numeric host forms
 * a resolver might read as one of those. Names are checked as typed and, when a resolver is given, as resolved.
 * SPEC: CMP-10, PEX-12
 */
object LanGuard {
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".internal", ".localdomain", ".home.arpa", ".intranet", ".localhost")
    private val NUMERIC = Regex("^(0x[0-9a-f]*|\\d+)(\\.(0x[0-9a-f]*|\\d+)){0,3}$")
    private val DOTTED = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** True when [host] names the local network without any DNS lookup. */
    fun isLocalName(host: String): Boolean {
        val h = host.trim().trim('[', ']').trimEnd('.').lowercase(Locale.ROOT)
        if (h.isEmpty() || ServerUrl.isPrivateHost(h)) return true
        if (LOCAL_SUFFIXES.any { h.endsWith(it) } || h == "localhost") return true
        if (':' in h) return literal(h)?.let(::isLocalAddress) ?: true
        if (DOTTED.matches(h)) return literal(h)?.let(::isLocalAddress) ?: true
        // "0x7f.1", "2130706433": not a name a feed has, and a resolver may read them as an address.
        return NUMERIC.matches(h)
    }

    /** True when [address] is anywhere a phone should not reach through the TV. */
    fun isLocalAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true
        val b = address.address.map { it.toInt() and 0xFF }
        return when (address) {
            is Inet4Address -> b[0] == 0 || (b[0] == 100 && b[1] in 64..127) || (b[0] == 198 && b[1] in 18..19) || b[0] >= 240
            is Inet6Address -> (b[0] and 0xFE) == 0xFC || embeddedV4(b)?.let(::isLocalAddress) == true
            else -> true
        }
    }

    /** [isLocalName], then every address [resolve] gives; a name that does not resolve is refused too. */
    fun isLocal(host: String, resolve: ((String) -> List<InetAddress>)?): Boolean {
        if (isLocalName(host)) return true
        if (resolve == null) return false
        val addresses = runCatching { resolve(host) }.getOrNull() ?: return true
        return addresses.isEmpty() || addresses.any(::isLocalAddress)
    }

    /** An IP literal, parsed without DNS (null when [h] isn't one). */
    private fun literal(h: String): InetAddress? = runCatching {
        if (':' !in h && !DOTTED.matches(h)) null else InetAddress.getByName(h)
    }.getOrNull()

    /** `::ffff:a.b.c.d` and `::a.b.c.d` carry an IPv4 address. */
    private fun embeddedV4(b: List<Int>): InetAddress? {
        if (b.size != 16 || b.subList(0, 10).any { it != 0 }) return null
        val mapped = b[10] == 0xFF && b[11] == 0xFF
        val compat = b[10] == 0 && b[11] == 0
        if (!mapped && !compat) return null
        return InetAddress.getByAddress(ByteArray(4) { b[12 + it].toByte() })
    }
}
