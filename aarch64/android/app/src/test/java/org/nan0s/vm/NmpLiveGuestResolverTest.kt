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

    @Test fun rejectsDistinctObjectsAliasingTheSameLivePayload() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 22000, intArrayOf(2, 3))
        try {
            resolver().resolve(a, b)
            throw AssertionError("expected aliased payload rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("overlapping payload storage"))
        }
    }

    @Test fun rejectsPartiallyOverlappingLivePayloads() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 22001, intArrayOf(3, 4))
        // Rebuild the trusted SHA-256 for the first object's now-overwritten words.
        val firstPayload = data.copyOfRange(22000 * 4, 22002 * 4)
        val first = a.copy(payloadSha256 = digest(firstPayload))
        try {
            resolver().resolve(first, b)
            throw AssertionError("expected partially overlapping payload rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("overlapping payload storage"))
        }
    }


    @Test fun rejectsPayloadOverlappingObjectRegistry() {
        val a = leaf(1401, 0, obj + 30, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        try {
            resolver().resolve(a, b)
            throw AssertionError("expected registry overlap rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("overlaps registry metadata"))
        }
    }

    @Test fun rejectsPayloadOverlappingShapeRegistry() {
        val a = leaf(1401, 0, shape + 30, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        try {
            resolver().resolve(a, b)
            throw AssertionError("expected shape table overlap rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("overlaps registry metadata"))
        }
    }


    private fun packetInputs(a: NmpGuestLeafBinding, b: NmpGuestLeafBinding) =
        org.json.JSONArray()
            .put(org.json.JSONObject()
                .put("leaf_id", a.leafId)
                .put("version", a.version)
                .put("digest", a.contentDigest))
            .put(org.json.JSONObject()
                .put("leaf_id", b.leafId)
                .put("version", b.version)
                .put("content_digest", b.contentDigest))

    @Test fun committedIndexResolvesOnlyTrustedGuestRecords() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        index.publishCommitted(b)
        val output = index.resolve(packetInputs(a, b), resolver())
        assertEquals(listOf(2, 3), output.leftPayload.toList())
        assertEquals(listOf(4, 5), output.rightPayload.toList())
        assertEquals(1401, output.leftObjectId)
    }

    @Test fun committedIndexRejectsMissingOrForgedPacketIdentity() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        try {
            index.resolve(packetInputs(a, b), resolver())
            throw AssertionError("expected missing committed binding")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("no committed canonical"))
        }
        index.publishCommitted(b)
        val inputs = packetInputs(a, b)
        inputs.getJSONObject(0).put("digest", "f".repeat(64))
        try {
            index.resolve(inputs, resolver())
            throw AssertionError("expected forged packet digest rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("differs from committed"))
        }
    }

    @Test fun committedIndexRejectsConflictingDigestAliases() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        index.publishCommitted(b)
        val inputs = packetInputs(a, b)
        inputs.getJSONObject(0).put("content_digest", "f".repeat(64))
        try {
            index.resolve(inputs, resolver())
            throw AssertionError("expected digest alias conflict")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("conflicting canonical digest"))
        }
    }

    @Test fun committedIndexPreventsObjectIdHijackingAndStaleVersions() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        try {
            index.publishCommitted(a.copy(leafId = "other-leaf"))
            throw AssertionError("expected object ID ownership rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already assigned"))
        }
        val updated = a.copy(version = 2)
        index.publishCommitted(updated)
        try {
            index.publishCommitted(a)
            throw AssertionError("expected stale-version rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("stale canonical"))
        }
        try {
            index.publishCommitted(updated.copy(payloadSha256 = "f".repeat(64)))
            throw AssertionError("expected immutable version rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("cannot be rebound"))
        }
    }

    @Test fun committedIndexDoesNotRollBackAfterDeletingNewerVersion() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        val newer = a.copy(version = 2)
        index.publishCommitted(newer)
        assertTrue(index.invalidateCommitted(newer.leafId, newer.version))
        try {
            index.publishCommitted(a)
            throw AssertionError("expected version rollback rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("stale canonical"))
        }
    }

    @Test fun committedIndexInvalidationAndGuestReloadClearBindings() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        index.publishCommitted(b)
        assertTrue(index.invalidateCommitted(a.leafId, a.version))
        try {
            index.resolve(packetInputs(a, b), resolver())
            throw AssertionError("expected invalidated leaf rejection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("no committed canonical"))
        }
        try {
            index.publishCommitted(a)
            throw AssertionError("expected invalidation tombstone to prevent resurrection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("cannot be republished"))
        }
        index.clearForGuestReload()
        index.publishCommitted(a)
        index.publishCommitted(b)
        index.clearForGuestReload()
        try {
            index.resolve(packetInputs(a, b), resolver())
            throw AssertionError("expected reloaded guest to lack bindings")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("no committed canonical"))
        }
    }

    @Test fun batchPublicationIsAtomicWhenLaterBindingConflicts() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        try {
            index.publishBatchCommitted(listOf(a, b.copy(objectId = a.objectId)))
            throw AssertionError("expected intra-batch object ownership conflict")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already assigned"))
        }
        try {
            index.resolve(packetInputs(a, b), resolver())
            throw AssertionError("failed batch must publish no object")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("no committed canonical"))
        }
        index.publishBatchCommitted(listOf(a, b))
        assertEquals(1401, index.resolve(packetInputs(a, b), resolver()).leftObjectId)
    }

    @Test fun batchPublicationRejectsRollbackWithoutMutatingOtherLeaves() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a.copy(version = 3))
        try {
            index.publishBatchCommitted(listOf(b, a.copy(version = 2)))
            throw AssertionError("expected stale version to abort transaction")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("stale canonical"))
        }
        try {
            index.resolve(packetInputs(a, b), resolver())
            throw AssertionError("failed batch must not publish unrelated leaf")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("no committed canonical"))
        }
        index.publishCommitted(b)
        assertEquals(1402, index.resolve(packetInputs(a.copy(version = 3), b), resolver()).rightObjectId)
    }

    @Test fun batchPublicationRejectsEmptyAndOversizedTransactions() {
        val index = NmpGuestBindingIndex()
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        for (batch in listOf(emptyList(), List(33) { a })) {
            try {
                index.publishBatchCommitted(batch)
                throw AssertionError("expected transaction bound rejection")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("1..32"))
            }
        }
    }

    @Test fun absentInvalidationCreatesTombstoneAndVersionHighWatermark() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val index = NmpGuestBindingIndex()
        assertTrue(!index.invalidateCommitted(a.leafId, 4))
        try {
            index.publishCommitted(a.copy(version = 4))
            throw AssertionError("expected invalidated absent record rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("cannot be republished"))
        }
        try {
            index.publishCommitted(a.copy(version = 3))
            throw AssertionError("expected earlier version rollback rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("stale canonical"))
        }
        index.publishCommitted(a.copy(version = 5))
    }

    @Test fun mixedTransactionReleasesInvalidatedObjectForNewLeaf() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        index.applyCommittedTransaction(
            listOf(b.copy(objectId = a.objectId)),
            listOf(a.leafId to a.version)
        )
        try {
            index.publishCommitted(a)
            throw AssertionError("invalidated version resurrected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("cannot be republished"))
        }
        assertTrue(index.invalidateCommitted(b.leafId, b.version))
    }

    @Test fun failedMixedTransactionRollsBackInvalidationsAndPublications() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        try {
            index.applyCommittedTransaction(
                listOf(b.copy(contentDigest = "not-a-digest")),
                listOf(a.leafId to a.version)
            )
            throw AssertionError("expected malformed record to reject transaction")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("SHA-256"))
        }
        index.publishCommitted(b)
        assertEquals(1401, index.resolve(packetInputs(a, b), resolver()).leftObjectId)
    }

    @Test fun mixedTransactionRejectsContradictoryAndDuplicateKeys() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val index = NmpGuestBindingIndex()
        val invalidations = listOf(a.leafId to a.version)
        for (publication in listOf(listOf(a), listOf(a, a))) {
            try {
                index.applyCommittedTransaction(publication, invalidations)
                throw AssertionError("expected contradictory transaction rejection")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("duplicated or contradictory"))
            }
        }
        index.publishCommitted(a)
        assertTrue(index.invalidateCommitted(a.leafId, a.version))
    }

    @Test fun higherInvalidationRetiresOlderLocallyBoundVersion() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3))
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        index.publishCommitted(b)
        assertTrue(index.invalidateCommitted(a.leafId, 4))
        try {
            index.resolve(packetInputs(a, b), resolver())
            throw AssertionError("older version remained readable after newer invalidation")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("no committed canonical"))
        }
        for (version in 1..4) {
            try {
                index.publishCommitted(a.copy(version = version))
                throw AssertionError("revoked version $version was restored")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("stale canonical") ||
                    expected.message!!.contains("cannot be republished"))
            }
        }
        index.publishCommitted(a.copy(version = 5))
        assertEquals(1401, index.resolve(
            packetInputs(a.copy(version = 5), b), resolver()
        ).leftObjectId)
    }

    @Test fun invalidatingOlderVersionKeepsNewerBoundVersionLive() {
        val a = leaf(1401, 0, 22000, intArrayOf(2, 3)).copy(version = 5)
        val b = leaf(1402, 1, 23000, intArrayOf(4, 5))
        val index = NmpGuestBindingIndex()
        index.publishCommitted(a)
        index.publishCommitted(b)
        assertTrue(!index.invalidateCommitted(a.leafId, 3))
        assertEquals(1401, index.resolve(packetInputs(a, b), resolver()).leftObjectId)
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
