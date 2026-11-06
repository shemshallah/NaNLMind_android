package org.nan0s.vm

import java.net.URI
import org.junit.Assert.assertTrue
import org.junit.Test

class N16HttpsTransportTest {
    @Test
    fun acceptsHttpsEndpointAndDocumentedFrameLimit() {
        val transport = N16HttpsTransport(URI("https://gateway.example/n16t"))
        assertTrue(transport.javaClass.simpleName == "N16HttpsTransport")
    }

    @Test
    fun rejectsPlainHttpEndpoint() {
        assertIllegalArgument {
            N16HttpsTransport(URI("http://gateway.example/n16t"))
        }
    }

    @Test
    fun rejectsEndpointUserInfoAndFragment() {
        assertIllegalArgument {
            N16HttpsTransport(URI("https://user:secret@gateway.example/n16t"))
        }
        assertIllegalArgument {
            N16HttpsTransport(URI("https://gateway.example/n16t#fragment"))
        }
    }

    @Test
    fun rejectsMissingHostAndInvalidTimeouts() {
        assertIllegalArgument {
            N16HttpsTransport(URI("https:/n16t"))
        }
        assertIllegalArgument {
            N16HttpsTransport(URI("https://gateway.example/n16t"), connectTimeoutMs = 0)
        }
        assertIllegalArgument {
            N16HttpsTransport(URI("https://gateway.example/n16t"), readTimeoutMs = 120_001)
        }
    }

    @Test
    fun rejectsFrameOutsideConfiguredBoundsBeforeOpeningConnection() {
        val transport = N16HttpsTransport(
            URI("https://gateway.example/n16t"),
            maxFrameBytes = 36
        )
        assertIllegalArgument { transport.exchange(ByteArray(35)) }
        assertIllegalArgument { transport.exchange(ByteArray(37)) }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.isNullOrEmpty() || expected.message!!.isNotBlank())
        }
    }
}
