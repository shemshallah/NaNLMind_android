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
    fun onlyAllowsKnownRoutePaths() {
        val client = NmpHttpsClient(URI("https://router.example"), token)
        assertThrows(IllegalArgumentException::class.java) {
            client.endpointForTest("/n16t")
        }
    }
}
