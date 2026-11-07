package org.nan0s.vm

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Bounded HTTPS transport for the NaNROUTER NMP control-plane API.
 *
 * This class transports canonical packet JSON and opaque server responses. It
 * does not construct NMP packets, validate native receipts, evaluate admission,
 * choose workers, or interpret result digests. Those semantics remain in the
 * NaN16 guest and NaNROUTER.
 */
internal class NmpHttpsClient(
    private val origin: URI,
    private val bearerToken: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxBodyBytes: Int = 1_048_576
) {
    init {
        require(origin.scheme.equals("https", ignoreCase = true)) {
            "NMP endpoint must use HTTPS"
        }
        require(!origin.host.isNullOrBlank()) { "NMP endpoint host is missing" }
        require(origin.userInfo == null && origin.query == null && origin.fragment == null) {
            "NMP endpoint must be an origin URL without credentials, query or fragment"
        }
        require(origin.path.isNullOrBlank() || origin.path == "/") {
            "NMP endpoint must be the service origin, not an N16T route"
        }
        require(bearerToken.length in 16..4096 && bearerToken.none(Char::isWhitespace)) {
            "NMP bearer token is malformed"
        }
        require(connectTimeoutMs in 1..120_000)
        require(readTimeoutMs in 1..120_000)
        require(maxBodyBytes in 1024..1_048_576)
    }

    fun submit(packet: JSONObject, nowTick: Long): JSONObject {
        require(nowTick >= 0)
        return post("/v1/nmp/packets", JSONObject()
            .put("packet", packet)
            .put("now_tick", nowTick))
    }

    fun lease(
        owner: String,
        nowTick: Long,
        leaseTicks: Long = 64,
        jobId: String? = null,
        expectedGeneration: Long? = null,
        maxAttempts: Int = 8
    ): JSONObject {
        require(owner.isNotBlank() && nowTick >= 0 && leaseTicks > 0 && maxAttempts > 0)
        val body = JSONObject()
            .put("owner", owner)
            .put("now_tick", nowTick)
            .put("lease_ticks", leaseTicks)
            .put("max_attempts", maxAttempts)
        if (jobId != null) body.put("job_id", jobId)
        if (expectedGeneration != null) {
            require(expectedGeneration >= 0)
            body.put("expected_generation", expectedGeneration)
        }
        return post("/v1/nmp/lease", body)
    }

    fun complete(
        packet: JSONObject,
        jobId: String,
        leaseToken: String,
        leaseEpoch: Long,
        leaseOwner: String,
        resultDigest: String,
        nowTick: Long,
        resultGeneration: Long? = null
    ): JSONObject {
        require(jobId.isNotBlank() && leaseToken.isNotBlank() && leaseOwner.isNotBlank())
        require(leaseEpoch > 0 && nowTick >= 0)
        require(resultDigest.matches(Regex("[0-9a-fA-F]{64}"))) {
            "result digest must be SHA-256 hex"
        }
        val body = JSONObject()
            .put("packet", packet)
            .put("job_id", jobId)
            .put("lease_token", leaseToken)
            .put("lease_epoch", leaseEpoch)
            .put("lease_owner", leaseOwner)
            .put("result_digest", resultDigest.lowercase())
            .put("now_tick", nowTick)
        if (resultGeneration != null) {
            require(resultGeneration >= 0)
            body.put("result_generation", resultGeneration)
        }
        return post("/v1/nmp/complete", body)
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > maxBodyBytes) {
            throw IOException("NMP request body exceeds the configured limit")
        }
        val endpoint = URI(
            origin.scheme, null, origin.host, origin.port, path, null, null
        ).toURL()
        val connection = (endpoint.openConnection() as? HttpsURLConnection)
            ?: throw IOException("HTTPS connection unavailable")
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("X-Content-Type-Options", "nosniff")
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }

            val status = connection.responseCode
            val responseBytes = if (status in 200..299) {
                connection.inputStream.use { readBounded(it, maxBodyBytes) }
            } else {
                connection.errorStream?.use { readBounded(it, 2048) } ?: ByteArray(0)
            }
            if (status !in 200..299) {
                val detail = responseBytes.toString(Charsets.UTF_8).take(320)
                throw IOException(
                    "NaNROUTER NMP returned HTTP $status" +
                        if (detail.isBlank()) "" else ": $detail"
                )
            }
            val mediaType = connection.contentType?.substringBefore(';')?.trim()
            if (mediaType != "application/json") {
                throw IOException("NMP service returned an unexpected content type")
            }
            return try {
                JSONObject(responseBytes.toString(Charsets.UTF_8))
            } catch (failure: Exception) {
                throw IOException("NMP service returned invalid JSON", failure)
            }
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
            if (output.size() + count > limit) {
                throw IOException("NMP response exceeds the configured limit")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    internal fun endpointForTest(path: String): URL {
        require(path in ROUTES)
        return URI(origin.scheme, null, origin.host, origin.port, path, null, null).toURL()
    }

    companion object {
        private val ROUTES = setOf(
            "/v1/nmp/packets",
            "/v1/nmp/lease",
            "/v1/nmp/complete"
        )
    }
}
