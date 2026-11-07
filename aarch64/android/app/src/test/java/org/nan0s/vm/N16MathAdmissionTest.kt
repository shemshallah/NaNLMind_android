package org.nan0s.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class N16MathAdmissionTest {
    @Test
    fun successfulDecisionCarriesTheCompleteRuleSnapshotReceipt() {
        val digest = intArrayOf(
            0x01020304, 0x11121314, 0x21222324, 0x31323334,
            0x41424344, 0x51525354, 0x61626364, 0x71727374
        )
        val decision = N16MathAdmission.Decision(
            status = 0,
            outputDescriptor = IntArray(8),
            guestSteps = 99,
            ruleEpoch = 17,
            ruleDigestWords = digest
        )
        assertTrue(decision.allowed)
        assertEquals(17, decision.ruleEpoch)
        assertEquals(8, decision.ruleDigestWords?.size)
        assertEquals(64, decision.ruleDigestHex?.length)
        assertEquals("0102030411121314212223243132333441424344515253546162636471727374",
            decision.ruleDigestHex)
    }

    @Test
    fun serializesRawGuestReceiptIntoNmpAdmissionSchema() {
        val digest = intArrayOf(
            0x01020304, 0x11121314, 0x21222324, 0x31323334,
            0x41424344, 0x51525354, 0x61626364, 0x71727374
        )
        val raw = intArrayOf(
            1312895286, 1, 0, 900, 17, 17,
            0x01020304, 0x11121314, 0x21222324, 0x31323334,
            0x41424344, 0x51525354, 0x61626364, 0x71727374
        )
        val decision = N16MathAdmission.Decision(
            status = 0,
            outputDescriptor = intArrayOf(3, 0, 1, 1, 1, 9000, 3, 0),
            guestSteps = 123,
            ruleEpoch = 17,
            ruleDigestWords = digest,
            guestSourceDigest = "a".repeat(64),
            guestImageDigest = "b".repeat(64),
            programId = 900,
            rawGuestReceipt = raw
        )
        val receipt = decision.toNmpAdmissionReceipt(
            packetId = "nmp:packet:1",
            candidateId = "candidate:1",
            functionId = "vector.dot",
            functionVersion = 1,
            snapshotId = "snapshot:1",
            rulesetSnapshotId = "rules:1",
            inputDigest = "c".repeat(64),
            capabilityDigest = "d".repeat(64),
            preflightDigest = "e".repeat(64),
            generation = 7
        )
        assertTrue(receipt.startsWith("{\"candidate_digest\":"))
        assertTrue(receipt.contains("\"schema\":\"nanrouter.native-admission-receipt.v1\""))
        assertTrue(receipt.contains("\"producer\":\"nan16_guest\""))
        assertTrue(receipt.contains("\"program_id\":900"))
        assertTrue(receipt.contains("\"output_generation\":8"))
        assertTrue(receipt.contains("\"guest_source_digest\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""))
        assertTrue(receipt.contains("\"guest_image_digest\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\""))
        assertTrue(receipt.contains("\"receipt_digest\":\""))
    }

    @Test
    fun keepsRuleAndTypedDenialsDisjoint() {
        val output = IntArray(8)
        val allowed = N16MathAdmission.Decision(0, output, 42)
        val staleRule = N16MathAdmission.Decision(18, output, 51)
        val badShape = N16MathAdmission.Decision(38, output, 57)
        val unrelated = N16MathAdmission.Decision(9, output, 10)

        assertTrue(allowed.allowed)
        assertEquals(null, allowed.ruleDenial)
        assertEquals(null, allowed.typedDenial)
        assertFalse(staleRule.allowed)
        assertEquals(2, staleRule.ruleDenial)
        assertEquals(null, staleRule.typedDenial)
        assertEquals(6, badShape.typedDenial)
        assertEquals(null, badShape.ruleDenial)
        assertEquals(null, unrelated.ruleDenial)
        assertEquals(null, unrelated.typedDenial)
    }
}
