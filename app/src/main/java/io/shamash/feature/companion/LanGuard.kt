package io.shamash.feature.companion

import io.shamash.data.security.PrivateNet
import java.net.InetAddress

/**
 * Keeps a feed added from the phone off the home network, so the phone can't use the TV to probe it. The rules live
 * in [PrivateNet], shared with the fetch-time check (ServerHttp's public reach). SPEC: CMP-10, PEX-12
 */
object LanGuard {
    /** True when [host] names the local network without any DNS lookup. */
    fun isLocalName(host: String): Boolean = PrivateNet.isLocalName(host)

    /** True when [address] is anywhere a phone should not reach through the TV. */
    fun isLocalAddress(address: InetAddress): Boolean = PrivateNet.isLocalAddress(address)

    /** [isLocalName], then every address [resolve] gives; a name that does not resolve is refused too. */
    fun isLocal(host: String, resolve: ((String) -> List<InetAddress>)?): Boolean = PrivateNet.isLocal(host, resolve)
}
