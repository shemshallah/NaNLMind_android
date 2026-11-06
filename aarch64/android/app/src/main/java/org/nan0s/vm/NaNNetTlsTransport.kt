package org.nan0s.vm

import java.io.ByteArrayOutputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * HTTPS boundary adapter for N16T. It transports opaque bounded frames only;
 * all frame semantics, replay checks, routing and training remain in NaN16.
 * The caller supplies a short-lived scoped bearer token; this class never
 * persists it or logs it.
 */
object NaNNetTlsTransport {
    private const val HEADER_BYTES = 32
    private const val TRAILER_BYTES = 4
    private const val MAX_PAYLOAD = 1_048_576
    private const val MAX_FRAME = HEADER_BYTES + MAX_PAYLOAD + TRAILER_BYTES

    fun exchange(host: String, port: Int, bearerToken: String, request: ByteArray): ByteArray {
        require(host.isNotBlank() && port in 1..65535)
        require(bearerToken.isNotBlank() && !bearerToken.contains('\r') && !bearerToken.contains('\n'))
        require(request.size in (HEADER_BYTES + TRAILER_BYTES)..MAX_FRAME)
        val endpoint = URI("https", null, host, port, "/n16t", null, null).toURL()
        val connection = endpoint.openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $bearerToken")
        connection.setRequestProperty("Content-Type", "application/x-n16t")
        connection.setRequestProperty("Accept", "application/x-n16t")
        connection.setFixedLengthStreamingMode(request.size)
        try {
            connection.outputStream.use { it.write(request) }
            val status = connection.responseCode
            if (status != HttpsURLConnection.HTTP_OK) {
                throw IllegalStateException("Remote training gateway returned HTTP $status")
            }
            val response = connection.inputStream.use { readBounded(it, MAX_FRAME) }
            require(response.size >= HEADER_BYTES + TRAILER_BYTES) { "N16T response is truncated" }
            val payloadLength = (response[28].toInt() and 255) or
                ((response[29].toInt() and 255) shl 8) or
                ((response[30].toInt() and 255) shl 16) or
                ((response[31].toInt() and 255) shl 24)
            require(payloadLength in 0..MAX_PAYLOAD) { "N16T response length rejected" }
            require(response.size == HEADER_BYTES + payloadLength + TRAILER_BYTES) {
                "N16T response frame length mismatch"
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun exchangeFromGuest(
        vm: NanVm,
        host: String,
        port: Int,
        bearerToken: String,
        requestAddress: Int,
        requestLength: Int,
        responseAddress: Int
    ): ByteArray {
        val request = vm.extractNetworkFrame(requestAddress, requestLength)
            ?: throw IllegalStateException("NaN16 request frame unavailable")
        val response = exchange(host, port, bearerToken, request)
        if (!vm.stageNetworkFrame(responseAddress, response)) {
            throw IllegalStateException("NaN16 response frame could not be staged")
        }
        return response
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(out.size() + count <= limit) { "Remote training response exceeds frame limit" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
