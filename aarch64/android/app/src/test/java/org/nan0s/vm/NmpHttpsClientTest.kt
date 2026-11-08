package org.nan0s.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONObject
import java.net.URI

class NmpHttpsClientTest {
    private val token = "nmp-submit-token-test-000000"

    @Test
    fun buildsOnlyTheThreeCanonicalNmpRoutes() {
        val client = NmpHttpsClient(URI("https://router.example"), token)
        assertEquals(
            "https://router.example/v1/nmp/packets",
            client.endpointForTest("/v1/nmp/packets").toString()
        )
        assertEquals(
            "https://router.example/v1/nmp/lease",
            client.endpointForTest("/v1/nmp/lease").toString()
        )
        assertEquals(
            "https://router.example/v1/nmp/complete",
            client.endpointForTest("/v1/nmp/complete").toString()
        )
    }

    @Test
    fun rejectsNonHttpsAndPathBearingOrigins() {
        assertThrows(IllegalArgumentException::class.java) {
            NmpHttpsClient(URI("http://router.example"), token)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NmpHttpsClient(URI("https://router.example/n16t"), token)
        }
    }

    @Test
    fun rejectsCredentialsEmbeddedInEndpointUri() {
        assertThrows(IllegalArgumentException::class.java) {
            NmpHttpsClient(URI("https://user:pass@router.example"), token)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NmpHttpsClient(URI("https://router.example/?token=secret"), token)
        }
    }


    @Test
    fun completionBodyCarriesTheNativeKernelEvidenceAndLeaseBinding() {
        val client = NmpHttpsClient(URI("https://router.example"), token)
        val packet = JSONObject().put("packet_id", "nmp:packet:complete")
        val evidence = JSONObject()
            .put("schema", "nanrouter.native-kernel-execution.v1")
            .put("packet_id", "nmp:packet:complete")
            .put("function_id", "math.vector.dot.v1")
            .put("program_id", 79)
            .put("output_kind", 3)
            .put("dimension", 4)
            .put("output_count", 1)
            .put("output_digest", "a".repeat(64))
            .put("guest_steps", 4096)
            .put("guest_image_digest", "b".repeat(64))
            .put("guest_source_digest", "c".repeat(64))
            .put("lease_epoch", 12)
            .put("lease_owner", "worker-1")

        val body = client.buildCompletionBody(
            packet = packet,
            jobId = "nmp:job:complete",
            leaseToken = "lease-token",
            leaseEpoch = 12,
            leaseOwner = "worker-1",
            resultDigest = "A".repeat(64),
            nowTick = 500,
            executionEvidence = evidence
        )

        assertEquals("nmp:job:complete", body.getString("job_id"))
        assertEquals("lease-token", body.getString("lease_token"))
        assertEquals(12L, body.getLong("lease_epoch"))
        assertEquals("worker-1", body.getString("lease_owner"))
        assertEquals("a".repeat(64), body.getString("result_digest"))
        assertEquals(
            "nanrouter.native-kernel-execution.v1",
            body.getJSONObject("execution_evidence").getString("schema")
        )
        assertEquals(79, body.getJSONObject("execution_evidence").getInt("program_id"))
        assertEquals("a".repeat(64),
            body.getJSONObject("execution_evidence").getString("output_digest"))
    }

    @Test
    fun onlyAllowsKnownRoutePaths() {
        val client = NmpHttpsClient(URI("https://router.example"), token)
        assertThrows(IllegalArgumentException::class.java) {
            client.endpointForTest("/n16t")
        }
    }
}
