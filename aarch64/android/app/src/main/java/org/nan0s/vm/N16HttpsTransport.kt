package org.nan0s.vm

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * Bounded HTTPS byte transport for NaN16 N16T frames.
 *
 * This class deliberately knows nothing about message types, sessions, corpus policy,
 * candidate acceptance, or model updates. The NaN16 guest owns all of those semantics.
 * The caller must supply a complete frame produced by the guest and validate the
 * returned frame in the guest before acting on it.
 */
internal class N16HttpsTransport(
    private val endpoint: URI,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxFrameBytes: Int = 65_572
) {
    init {
        require(endpoint.scheme.equals("https", ignoreCase = true)) {
            "N16 gateway must use HTTPS"
        }
        require(!endpoint.host.isNullOrBlank()) { "N16 gateway host is missing" }
        require(endpoint.userInfo == null && endpoint.fragment == null) {
            "N16 gateway URL must not contain user-info or a fragment"
        }
        require(connectTimeoutMs in 1..120_000)
        require(readTimeoutMs in 1..120_000)
        require(maxFrameBytes in 36..65_572)
    }

    @Throws(IOException::class)
    fun exchange(frame: ByteArray): ByteArray {
        require(frame.size in 36..maxFrameBytes) { "N16T frame size is outside the transport limit" }
        val connection = (endpoint.toURL().openConnection() as? HttpsURLConnection)
            ?: throw IOException("HTTPS connection unavailable")
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", MEDIA_TYPE)
            connection.setRequestProperty("Accept", MEDIA_TYPE)
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setFixedLengthStreamingMode(frame.size)
            connection.outputStream.use { it.write(frame) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.use { readBounded(it, 1024) }
                val detail = error?.toString(Charsets.UTF_8)?.take(256).orEmpty()
                throw IOException("N16 gateway returned HTTP $status" +
                    if (detail.isEmpty()) "" else ": $detail")
            }
            val responseType = connection.contentType?.substringBefore(';')?.trim()
            if (responseType != MEDIA_TYPE) {
                throw IOException("N16 gateway returned an unexpected content type")
            }
            val body = connection.inputStream.use { readBounded(it, maxFrameBytes) }
            if (body.size < 36) throw IOException("N16 gateway returned a truncated frame")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw IOException("N16 gateway response exceeds limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val MEDIA_TYPE = "application/x-n16t"
    }
}
