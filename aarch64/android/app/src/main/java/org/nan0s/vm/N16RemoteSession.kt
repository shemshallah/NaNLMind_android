package org.nan0s.vm

import android.content.Context
import java.io.Closeable
import java.net.URI

/**
 * One serialized N16T exchange over the opaque HTTPS adapter.
 *
 * The guest builds the request. The transport moves bytes only. After a successful
 * HTTP exchange, the guest commits the outbound transition and validates/routes the
 * peer frame. No protocol fields are decoded in this Kotlin coordinator.
 */
internal class N16RemoteSession(
    context: Context,
    endpoint: URI
) : Closeable {
    data class ExchangeResult(
        val peerFrame: ByteArray,
        val route: N16GuestProtocol.Route
    )

    private val guest = N16GuestProtocol(context)
    private val transport = N16HttpsTransport(endpoint)
    private var closed = false
    private var lastRoute: N16GuestProtocol.Route? = null

    /** Send one guest-built frame and return the opaque peer frame without decoding it. */
    @Synchronized
    fun send(type: Int, payload: ByteArray): ByteArray {
        check(!closed) { "N16 remote session is closed" }
        val request = guest.buildFrame(type, payload)
        try {
            val response = transport.exchange(request)
            guest.commitSent()
            return response
        } catch (failure: Exception) {
            // A failed HTTPS exchange has not committed the outbound guest transition.
            try { guest.abortPending() } catch (_: Exception) { }
            throw failure
        }
    }

    /** Validate/route a saved peer frame. This can be retried after a rejected response. */
    @Synchronized
    fun acceptPeerFrame(frame: ByteArray): N16GuestProtocol.Route {
        check(!closed) { "N16 remote session is closed" }
        return guest.acceptFrame(frame)
    }

    /** CLOSE is terminal and may have no peer frame to route. */
    @Synchronized
    fun closeRemote(): ByteArray = send(13, ByteArray(0))

    @Synchronized
    fun exchange(type: Int, payload: ByteArray): ExchangeResult {
        require(type != 13) { "Use closeRemote() for the terminal CLOSE message" }
        val response = send(type, payload)
        val route = acceptPeerFrame(response)
        lastRoute = route
        return ExchangeResult(response, route)
    }

    @Synchronized
    fun connectionSummary(): String = buildString {
        append("STATUS: CONNECTED\\n")
        append("ENDPOINT: ").append(transport.endpointText).append('\\n')
        append("HTTP: ").append(transport.lastHttpStatus).append('\\n')
        lastRoute?.let {
            append("N16T ROUTE: gateway=0x").append(it.gateway.toString(16).uppercase())
                .append(" interface=").append(it.interfaceId)
                .append(" logical-point=0x").append(it.logicalPoint.toString(16).uppercase()).append('\\n')
        }
        append("SESSION FINGERPRINT (SHA-256/64): ").append(guest.sessionFingerprint()).append('\\n')
        append("TLS CIPHER: ").append(transport.lastCipherSuite).append('\\n')
        append("SERVER PUBLIC KEY SHA-256/96: ").append(transport.lastPeerKeyFingerprint)
    }

    @Synchronized
    fun resetSession() {
        check(!closed) { "N16 remote session is closed" }
        guest.resetSession()
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            guest.close()
        }
    }
}
