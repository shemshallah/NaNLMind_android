package org.nan0s.vm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NmpNativeReceiptBridgeTest {
    private val digestWords = intArrayOf(
        0x01020304, 0x11121314, 0x21222324, 0x31323334,
        0x41424344, 0x51525354, 0x61626364, 0x71727374
    )

    private fun allowedDecision() = N16MathAdmission.Decision(
        status = 0,
        outputDescriptor = intArrayOf(3, 0, 1, 1, 1, 9000, 3, 0),
        guestSteps = 123,
        ruleEpoch = 17,
        ruleDigestWords = digestWords,
        guestSourceDigest = "a".repeat(64),
        guestImageDigest = "b".repeat(64),
        programId = 900,
        rawGuestReceipt = intArrayOf(
            1312895286, 1, 0, 900, 17, 17, *digestWords
        )
    )

    private fun packet() = JSONObject()
        .put("packet_id", "nmp:packet:1")
        .put("state", "PROPOSED")
        .put("status", "PROPOSED")
        .put("snapshot_id", "snp:" + "a".repeat(64))
        .put("base_generation", 7)
        .put("generation", 7)
        .put("candidate_id", "candidate:1")
        .put("function_id", "math.vector.dot.v1")
        .put("function_version", 1)
        .put("admission_id", "admission:1")
        .put("admission_verdict", "VALID")
        .put("preflight_digest", "e".repeat(64))
        .put("ruleset_snapshot_id", "rules:" + "c".repeat(64))
        .put("capability_set", JSONArray().put(JSONObject()
            .put("required", "vector.dot").put("workspace", 256).put("precision", "float32")))
        .put("capability_set_digest", "202dedb12832162fd48a0321f1409b495bcf638dcac4249ac60dc0e9c21c1d6e")
        .put("inputs", JSONArray()
            .put(JSONObject().put("leaf_id", "nml:U0:vector:1")
                .put("version", 1).put("digest", "1".repeat(64)))
            .put(JSONObject().put("leaf_id", "nml:U0:vector:2")
                .put("version", 1).put("digest", "2".repeat(64))))
        .put("metadata", JSONObject())

    private fun input() = NmpNativeAdmissionInput(
        candidate = IntArray(22), rule = IntArray(17), left = IntArray(8),
        right = IntArray(8), contract = IntArray(10), availableCapabilities = 1,
        programId = 900, leftPayload = intArrayOf(1, 2, 3),
        rightPayload = intArrayOf(1, 2, 3), output = IntArray(8)
    )

    @Test
    fun nativeAllowAttachesReceiptAndAdvancesPacketToPreflighted() {
        val bridge = NmpNativeReceiptBridge { allowedDecision() }
        val proposed = packet()
        val prepared = bridge.preparePacket(proposed, input())

        assertEquals("PREFLIGHTED", prepared.getString("state"))
        assertEquals("VALID", prepared.getString("preflight_result"))
        assertTrue(prepared.getJSONObject("metadata").getBoolean("native_admission_required"))
        assertEquals("nanrouter.native-admission-receipt.v1",
            prepared.getJSONObject("admission_receipt").getString("schema"))
        assertEquals(900, prepared.getJSONObject("admission_receipt").getInt("program_id"))
        assertEquals("196d2fa4908293a127768140d38700528bdf18a9401d9185643adfd835c74c4e",
            prepared.getJSONObject("admission_receipt").getString("input_digest"))
        assertTrue(prepared.getJSONObject("admission_receipt").has("receipt_digest"))
        assertFalse(proposed.has("admission_receipt"))
    }

    @Test
    fun guestDenialNeverProducesASubmittablePacket() {
        val denied = N16MathAdmission.Decision(18, IntArray(8), 51)
        val bridge = NmpNativeReceiptBridge { denied }
        try {
            bridge.preparePacket(packet(), input())
            throw AssertionError("Expected native admission denial")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("denied"))
        }
    }

    @Test
    fun refusesToReplaceAnExistingReceipt() {
        val proposed = packet().put("admission_receipt", JSONObject())
        val bridge = NmpNativeReceiptBridge { allowedDecision() }
        try {
            bridge.preparePacket(proposed, input())
            throw AssertionError("Expected existing receipt rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already contains"))
        }
    }
}
