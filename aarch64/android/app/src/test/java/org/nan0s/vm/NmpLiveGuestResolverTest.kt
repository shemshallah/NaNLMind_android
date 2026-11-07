package org.nan0s.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class NmpLiveGuestResolverTest {
    private val obj = 20000
    private val shape = 20528
    private val labels = mapOf("nmr_obj_table" to obj, "nmr_shape_table" to shape)
    private val data = ByteArray(100000)

    private fun put(wordAddress: Int, vararg values: Int) {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        values.forEachIndexed { index, value -> b.putInt((wordAddress + index) * 4, value) }
    }

    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun leaf(id: Int, slot: Int, payloadPointer: Int, values: IntArray): NmpGuestLeafBinding {
        val contentDigest = digest("leaf-$id".toByteArray())
        val marker = contentDigest.take(8).toLong(16).toInt()
        put(obj + 5 * slot, id, 4, slot + 1, marker, payloadPointer)
        put(shape + 5 * slot, id, 1, values.size, 1, values.size)
        put(payloadPointer, *values)
        val payload = data.copyOfRange(payloadPointer * 4, (payloadPointer + values.size) * 4)
        return NmpGuestLeafBinding("leaf-$id", 1, contentDigest, id, digest(payload))
    }

    private fun resolver() = NmpLiveGuestResolver(labels) { address, length ->
        if (address < 0 || length < 0 || address.toLong() + length > data.size) null
        else data.copyOfRange(address, address + length)
    }

    @Test fun resolvesActualRegistryRecordsAndPayloads() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val resolved = resolver().resolve(a, b)
        assertEquals(1401, resolved.leftObjectId)
        assertEquals(1402, resolved.rightObjectId)
        assertEquals(1, resolved.leftOwnerPoint)
        assertEquals(2, resolved.rightOwnerPoint)
        assertEquals(2, resolved.dimension)
        assertEquals(listOf(2, 3), resolved.leftPayload.toList())
        assertEquals(listOf(4, 5), resolved.rightPayload.toList())
    }

    @Test fun rejectsTamperedLivePayload() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        put(22000, 99, 3)
        try {
            resolver().resolve(a, b)
            throw AssertionError("expected payload integrity failure")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("payload differs"))
        }
    }

    @Test fun rejectsMissingOrWrongShape() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        put(shape + 1, 2)
        try {
            resolver().resolve(a, b)
            throw AssertionError("expected rank mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("shape or capacity"))
        }
    }

    @Test fun rejectsForgedLeafDigestMarker() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        put(obj + 3, 0)
        try {
            resolver().resolve(a, b)
            throw AssertionError("expected marker mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("digest marker"))
        }
    }

    @Test fun rejectsAbsentRegistryObject() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        put(obj + 5, 0)
        try {
            resolver().resolve(a, b)
            throw AssertionError("expected missing object")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("absent or duplicated"))
        }
    }

    @Test fun rejectsMismatchedVectorDimensions() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5, 6))
        try {
            resolver().resolve(a, b)
            throw AssertionError("expected dimension mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("dimensions differ"))
        }
    }
}
