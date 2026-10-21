package io.shamash.data.security

import okhttp3.OkHttpClient
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * Sockets that refuse to connect to a local address. The check runs on the address the socket is actually about to
 * connect to, after DNS and on every redirect hop, so a name that resolved public when the feed was added and private
 * now (DNS rebinding), an IP literal, or a redirect into the LAN are all refused before a packet is sent. Used for
 * user-supplied URLs (feeds, podcasts, news). SPEC: CMP-10, PEX-22
 */
class PublicSockets(
    private val refused: (InetAddress) -> Boolean = PrivateNet::isLocalAddress,
) : SocketFactory() {
    override fun createSocket(): Socket = GuardedSocket(refused)

    override fun createSocket(host: String, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        createSocket().apply { bind(InetSocketAddress(localHost, localPort)); connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        createSocket().apply { bind(InetSocketAddress(localAddress, localPort)); connect(InetSocketAddress(address, port)) }

    private class GuardedSocket(private val refused: (InetAddress) -> Boolean) : Socket() {
        override fun connect(endpoint: SocketAddress?) = connect(endpoint, 0)

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            val address = (endpoint as? InetSocketAddress)?.address
            // Unresolved or non-IP endpoints can't be checked, so they are refused too. Message: no host, no URL.
            if (address == null || refused(address)) throw ConnectException(REFUSED)
            super.connect(endpoint, timeout)
        }
    }

    companion object {
        const val REFUSED = "private address refused"

        /** A client for user-supplied URLs: guarded sockets, and no proxy (a proxy would hide the real address). */
        fun applyTo(builder: OkHttpClient.Builder, sockets: PublicSockets): OkHttpClient.Builder =
            builder.socketFactory(sockets).proxy(Proxy.NO_PROXY)
    }
}
