package org.nan0s.vm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class N16NmpKernelWorkerTest {
    private val leftDigest = "a".repeat(64)
    private val rightDigest = "b".repeat(64)

    private fun resolved() = NmpResolvedVectorPair(
        leftLeafId = "nml:U0:left:1",
        leftVersion = 1,
        leftDigest = leftDigest,
        rightLeafId = "nml:U0:right:1",
        rightVersion = 1,
        rightDigest = rightDigest,
        leftObjectId = 1401,
        rightObjectId = 1402,
        leftOwnerPoint = 1,
        rightOwnerPoint = 2,
        dimension = 2,
        leftPayload = intArrayOf(2, 3),
        rightPayload = intArrayOf(4, 5)
    )

    private fun leaseResponse(
        functionId: String = "math.vector.add.v1",
        programId: Int = 78,
        leaseEpoch: Long = 3,
        packetEpoch: Long = leaseEpoch
    ) = JSONObject()
        .put("packet", JSONObject()
            .put("packet_id", "nmp:packet:worker:1")
            .put("job_id", "nmp:nmp:packet:worker:1")
            .put("state", "EXECUTING")
            .put("lease_epoch", packetEpoch)
            .put("function_id", functionId)
            .put("function_version", 1)
            .put("inputs", JSONArray()
                .put(JSONObject().put("leaf_id", "nml:U0:left:1")
                    .put("version", 1).put("digest", leftDigest))
                .put(JSONObject().put("leaf_id", "nml:U0:right:1")
                    .put("version", 1).put("digest", rightDigest)))
            .put("admission_receipt", JSONObject()
                .put("schema", "nanrouter.native-admission-receipt.v1")
                .put("packet_id", "nmp:packet:worker:1")
                .put("guest_status", 0).put("program_id", programId)))
        .put("job", JSONObject().put("job_id", "nmp:nmp:packet:worker:1"))
        .put("lease", JSONObject()
            .put("owner", "worker-1").put("token", "lease-token-secret")
            .put("epoch", leaseEpoch))

    private fun fakeWorker() = N16NmpKernelWorker { input, programId ->
        val output = when (programId) {
            78 -> intArrayOf(
                input.leftPayload[0] + input.rightPayload[0],
                input.leftPayload[1] + input.rightPayload[1]
            )
            79 -> intArrayOf(
                input.leftPayload[0] * input.rightPayload[0] +
                    input.leftPayload[1] * input.rightPayload[1]
            )
            else -> error("unexpected program")
        }
        N16NmpGuestExecution(
            programId = programId,
            outputKind = if (programId == 78) 4 else 3,
            dimension = input.dimension,
            outputWords = output,
            guestSteps = 4096,
            guestImageDigest = "c".repeat(64),
            guestSourceDigest = "d".repeat(64)
        )
    }

    @Test
    fun dispatchesOnlyTheReceiptBoundVectorAddKernel() {
        val result = fakeWorker().execute(leaseResponse(), resolved())
        assertEquals(78, result.programId)
        assertEquals(4, result.outputKind)
        assertEquals(listOf(6, 8), result.outputWords.toList())
        assertEquals("math.vector.add.v1", result.functionId)
        assertEquals("worker-1", result.leaseOwner)
        assertEquals(3L, result.leaseEpoch)
        assertEquals(64, result.resultDigest.length)
        assertEquals(4096L, result.guestSteps)
        val evidence = result.toExecutionEvidence()
        assertEquals("nanrouter.native-kernel-execution.v1", evidence.getString("schema"))
        assertEquals("nmp:packet:worker:1", evidence.getString("packet_id"))
        assertEquals(78, evidence.getInt("program_id"))
        assertEquals(4, evidence.getInt("output_kind"))
        assertEquals(2, evidence.getInt("output_count"))
        assertEquals(result.resultDigest, evidence.getString("output_digest"))
        assertEquals(3L, evidence.getLong("lease_epoch"))
        assertEquals("worker-1", evidence.getString("lease_owner"))
    }

    @Test
    fun dispatchesDotKernelAndReturnsScalarOutput() {
        val result = fakeWorker().execute(
            leaseResponse(functionId = "math.vector.dot.v1", programId = 79),
            resolved()
        )
        assertEquals(79, result.programId)
        assertEquals(3, result.outputKind)
        assertEquals(listOf(23), result.outputWords.toList())
    }

    @Test
    fun rejectsReceiptForDifferentAllowlistedProgram() {
        try {
            fakeWorker().execute(leaseResponse(programId = 79), resolved())
            throw AssertionError("Expected receipt/program mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("does not authorize"))
        }
    }

    @Test
    fun rejectsLeaseEpochMismatchBeforeGuestDispatch() {
        var called = false
        val worker = N16NmpKernelWorker { _, _ ->
            called = true
            error("guest must not run")
        }
        try {
            worker.execute(leaseResponse(leaseEpoch = 4, packetEpoch = 3), resolved())
            throw AssertionError("Expected lease epoch mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("lease epoch"))
        }
        assertEquals(false, called)
    }

    @Test
    fun rejectsResolverIdentityThatDoesNotMatchPacketInput() {
        val changed = resolved().copy(leftDigest = "e".repeat(64))
        var called = false
        val worker = N16NmpKernelWorker { _, _ ->
            called = true
            error("guest must not run")
        }
        try {
            worker.execute(leaseResponse(), changed)
            throw AssertionError("Expected input identity mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("canonical NMP input identity"))
        }
        assertEquals(false, called)
    }


    @Test
    fun acceptsContentDigestAliasFromCanonicalPacketInput() {
        val response = leaseResponse()
        val inputs = response.getJSONObject("packet").getJSONArray("inputs")
        val left = inputs.getJSONObject(0)
        left.put("content_digest", left.getString("digest"))
        left.remove("digest")
        val result = fakeWorker().execute(response, resolved())
        assertEquals(78, result.programId)
    }

    @Test
    fun rejectsConflictingPacketDigestAliasesBeforeDispatch() {
        val response = leaseResponse()
        val left = response.getJSONObject("packet").getJSONArray("inputs").getJSONObject(0)
        left.put("content_digest", "e".repeat(64))
        var called = false
        val worker = N16NmpKernelWorker { _, _ ->
            called = true
            error("guest must not run")
        }
        try {
            worker.execute(response, resolved())
            throw AssertionError("Expected conflicting digest aliases")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("conflicting digest aliases"))
        }
        assertEquals(false, called)
    }

    @Test
    fun rejectsReceiptBoundToAnotherPacket() {
        val response = leaseResponse()
        response.getJSONObject("packet").getJSONObject("admission_receipt")
            .put("packet_id", "nmp:other-packet")
        var called = false
        val worker = N16NmpKernelWorker { _, _ ->
            called = true
            error("guest must not run")
        }
        try {
            worker.execute(response, resolved())
            throw AssertionError("Expected receipt packet binding rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("does not authorize"))
        }
        assertEquals(false, called)
    }

    @Test
    fun rejectsUnknownFunctionBeforeGuestDispatch() {
        var called = false
        val worker = N16NmpKernelWorker { _, _ ->
            called = true
            error("guest must not run")
        }
        try {
            worker.execute(
                leaseResponse(functionId = "math.vector.sqrt.v1", programId = 78),
                resolved()
            )
            throw AssertionError("Expected allowlist rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("allowlist"))
        }
        assertEquals(false, called)
    }
}
