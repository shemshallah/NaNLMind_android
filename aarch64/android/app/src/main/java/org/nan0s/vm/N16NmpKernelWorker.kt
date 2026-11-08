package org.nan0s.vm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Canonical guest-state projection for the two-vector NaN16 worker ABI. */
internal data class NmpResolvedVectorPair(
    val leftLeafId: String,
    val leftVersion: Int,
    val leftDigest: String,
    val rightLeafId: String,
    val rightVersion: Int,
    val rightDigest: String,
    val leftObjectId: Int,
    val rightObjectId: Int,
    val leftOwnerPoint: Int,
    val rightOwnerPoint: Int,
    val dimension: Int,
    val leftPayload: IntArray,
    val rightPayload: IntArray
)

internal data class N16NmpGuestExecution(
    val programId: Int,
    val outputKind: Int,
    val dimension: Int,
    val outputWords: IntArray,
    val guestSteps: Long,
    val guestImageDigest: String,
    val guestSourceDigest: String
)

internal data class N16NmpKernelResult(
    val packetId: String,
    val jobId: String,
    val leaseOwner: String,
    val leaseEpoch: Long,
    val functionId: String,
    val programId: Int,
    val outputKind: Int,
    val dimension: Int,
    val outputWords: IntArray,
    val resultDigest: String,
    val guestSteps: Long,
    val guestImageDigest: String,
    val guestSourceDigest: String
)


internal fun N16NmpKernelResult.toExecutionEvidence(): JSONObject =
    JSONObject()
        .put("schema", "nanrouter.native-kernel-execution.v1")
        .put("packet_id", packetId)
        .put("function_id", functionId)
        .put("program_id", programId)
        .put("output_kind", outputKind)
        .put("dimension", dimension)
        .put("output_count", outputWords.size)
        .put("output_digest", resultDigest)
        .put("guest_steps", guestSteps)
        .put("guest_image_digest", guestImageDigest)
        .put("guest_source_digest", guestSourceDigest)
        .put("lease_epoch", leaseEpoch)
        .put("lease_owner", leaseOwner)

/**
 * Executes the allowlisted vector kernel inside a dedicated NaN16 guest.
 *
 * This class accepts only a resolver projection; it does not parse arbitrary
 * vector payloads out of packet JSON. The resolver must obtain the object IDs,
 * owners, and bounded payloads from canonical guest state.
 */
internal class N16NmpKernelWorker {
    private val guestRunner: (NmpResolvedVectorPair, Int) -> N16NmpGuestExecution

    constructor(context: Context) {
        val image = context.assets.open(IMAGE_ASSET).use { it.readBytes() }
        val sourceDigest = context.assets.open(SOURCE_DIGEST_ASSET)
            .bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
        val labels = context.assets.open(LABEL_ASSET)
            .bufferedReader(Charsets.UTF_8).use { reader ->
                reader.lineSequence().mapNotNull { line ->
                    val fields = line.trim().split(Regex("\\s+"))
                    if (fields.size != 2) null
                    else fields[0] to fields[1].toIntOrNull()
                }.mapNotNull { (name, address) -> address?.let { name to it } }.toMap()
            }
        require(sourceDigest.matches(Regex("[0-9a-f]{64}"))) {
            "NaN16 NMP kernel source digest is malformed"
        }
        REQUIRED_LABELS.forEach { name ->
            check(labels.containsKey(name)) { "Missing NaN16 NMP kernel label: $name" }
        }
        val imageDigest = sha256Hex(image)
        guestRunner = { input, programId ->
            runGuest(image, imageDigest, sourceDigest, labels, input, programId)
        }
    }

    /** Test seam for packet/lease validation; production always uses the NaN16 guest. */
    internal constructor(runner: (NmpResolvedVectorPair, Int) -> N16NmpGuestExecution) {
        guestRunner = runner
    }

    fun execute(leaseResponse: JSONObject, resolved: NmpResolvedVectorPair): N16NmpKernelResult {
        val packet = leaseResponse.getJSONObject("packet")
        val job = leaseResponse.getJSONObject("job")
        val lease = leaseResponse.getJSONObject("lease")
        require(packet.getString("state") == "EXECUTING") {
            "NMP worker requires a server-leased EXECUTING packet"
        }
        val packetId = packet.getString("packet_id")
        val jobId = job.getString("job_id")
        require(jobId.isNotBlank() && packet.optString("job_id") == jobId) {
            "NMP packet/job identity mismatch"
        }
        val leaseOwner = lease.getString("owner")
        val leaseToken = lease.getString("token")
        val leaseEpoch = lease.getLong("epoch")
        require(leaseOwner.isNotBlank() && leaseToken.isNotBlank() && leaseEpoch > 0) {
            "NMP worker lease binding is incomplete"
        }
        require(packet.optLong("lease_epoch", -1) == leaseEpoch) {
            "NMP packet lease epoch does not match the server lease"
        }

        val functionId = packet.getString("function_id")
        val programId = ALLOWLIST[functionId]
            ?: throw IllegalArgumentException("NMP function is not in the NaN16 worker allowlist")
        require(packet.getInt("function_version") == 1) {
            "unsupported NMP function version"
        }
        val receipt = packet.getJSONObject("admission_receipt")
        require(receipt.getString("schema") == RECEIPT_SCHEMA &&
            receipt.getString("packet_id") == packetId &&
            receipt.getInt("guest_status") == 0 &&
            receipt.getInt("program_id") == programId) {
            "native admission receipt does not authorize this NaN16 kernel"
        }

        validatePacketInputs(packet.getJSONArray("inputs"), resolved)
        require(resolved.leftObjectId > 0 && resolved.rightObjectId > 0 &&
            resolved.leftObjectId != resolved.rightObjectId) {
            "resolved vector object IDs must be distinct positive registry IDs"
        }
        require(resolved.leftOwnerPoint in 1..256 && resolved.rightOwnerPoint in 1..256) {
            "resolved vector owners must be valid logical points"
        }
        require(resolved.dimension in 1..MAX_DIMENSION &&
            resolved.leftPayload.size == resolved.dimension &&
            resolved.rightPayload.size == resolved.dimension) {
            "resolved vector payloads must exactly match the bounded dimension"
        }

        val execution = guestRunner(resolved, programId)
        val expectedKind = if (programId == VECTOR_ADD_PROGRAM) VECTOR_KIND else SCALAR_KIND
        val expectedCount = if (programId == VECTOR_ADD_PROGRAM) resolved.dimension else 1
        check(execution.programId == programId &&
            execution.outputKind == expectedKind &&
            execution.dimension == resolved.dimension &&
            execution.outputWords.size == expectedCount) {
            "NaN16 worker guest returned an inconsistent execution descriptor"
        }
        check(execution.guestSteps > 0) { "NaN16 worker guest returned no execution steps" }
        requireDigest(execution.guestImageDigest, "guest image digest")
        requireDigest(execution.guestSourceDigest, "guest source digest")
        val resultDigest = digestWords(execution.outputWords)
        return N16NmpKernelResult(
            packetId, jobId, leaseOwner, leaseEpoch, functionId, programId,
            expectedKind, resolved.dimension, execution.outputWords.copyOf(), resultDigest,
            execution.guestSteps, execution.guestImageDigest, execution.guestSourceDigest
        )
    }

    /** Submit completion using the exact durable lease returned by /v1/nmp/lease. */
    fun executeAndComplete(
        client: NmpHttpsClient,
        leaseResponse: JSONObject,
        resolved: NmpResolvedVectorPair,
        nowTick: Long
    ): Pair<N16NmpKernelResult, JSONObject> {
        val result = execute(leaseResponse, resolved)
        val packet = leaseResponse.getJSONObject("packet")
        val lease = leaseResponse.getJSONObject("lease")
        val evidence = result.toExecutionEvidence()
        val completion = client.complete(
            packet = packet,
            jobId = result.jobId,
            leaseToken = lease.getString("token"),
            leaseEpoch = result.leaseEpoch,
            leaseOwner = result.leaseOwner,
            resultDigest = result.resultDigest,
            nowTick = nowTick,
            executionEvidence = evidence
        )
        return result to completion
    }

    /**
     * Live-guest entry point: resolve the registered objects from the running
     * VM before the existing packet/lease/receipt checks and NaN16 dispatch.
     * Binding entries must originate from the canonical object-store index.
     */
    fun executeAndCompleteFromGuest(
        client: NmpHttpsClient,
        leaseResponse: JSONObject,
        resolver: NmpLiveGuestResolver,
        left: NmpGuestLeafBinding,
        right: NmpGuestLeafBinding,
        nowTick: Long
    ): Pair<N16NmpKernelResult, JSONObject> =
        executeAndComplete(client, leaseResponse, resolver.resolve(left, right), nowTick)

    /**
     * Index-backed leased execution. The packet selects committed leaf identities
     * but cannot supply guest object IDs, memory addresses or payload digests.
     * The canonical ingestion owner must populate/index bindings before use.
     */
    fun executeAndCompleteFromIndex(
        client: NmpHttpsClient,
        leaseResponse: JSONObject,
        resolver: NmpLiveGuestResolver,
        index: NmpGuestBindingIndex,
        nowTick: Long
    ): Pair<N16NmpKernelResult, JSONObject> {
        val inputs = leaseResponse.getJSONObject("packet").getJSONArray("inputs")
        val resolved = index.resolve(inputs, resolver)
        return executeAndComplete(client, leaseResponse, resolved, nowTick)
    }

    private fun validatePacketInputs(inputs: JSONArray, resolved: NmpResolvedVectorPair) {
        require(inputs.length() == 2) { "NaN16 vector kernels require exactly two packet inputs" }
        validateInput(inputs.getJSONObject(0), resolved.leftLeafId, resolved.leftVersion, resolved.leftDigest)
        validateInput(inputs.getJSONObject(1), resolved.rightLeafId, resolved.rightVersion, resolved.rightDigest)
    }

    private fun validateInput(item: JSONObject, leafId: String, version: Int, digest: String) {
        val hasDigest = item.has("digest") && !item.isNull("digest")
        val hasContentDigest = item.has("content_digest") && !item.isNull("content_digest")
        require(hasDigest || hasContentDigest) {
            "NMP input is missing its content digest"
        }
        if (hasDigest && hasContentDigest) {
            require(normalizeDigest(item.getString("digest")) ==
                normalizeDigest(item.getString("content_digest"))) {
                "NMP input has conflicting digest aliases"
            }
        }
        val packetDigest =
            if (hasDigest) item.getString("digest") else item.getString("content_digest")
        require(item.getString("leaf_id") == leafId &&
            item.getInt("version") == version &&
            normalizeDigest(packetDigest) == normalizeDigest(digest)) {
            "resolved guest object does not match its canonical NMP input identity"
        }
    }

    private fun runGuest(
        image: ByteArray,
        imageDigest: String,
        sourceDigest: String,
        labels: Map<String, Int>,
        input: NmpResolvedVectorPair,
        programId: Int
    ): N16NmpGuestExecution {
        val vm = NanVm()
        try {
            check(vm.load(image) != null) { "NaN16 NMP worker guest failed to load" }
            writeWords(vm, labels, "n16_nmp_operation", intArrayOf(if (programId == VECTOR_ADD_PROGRAM) 1 else 2))
            writeWords(vm, labels, "n16_nmp_left_id", intArrayOf(input.leftObjectId))
            writeWords(vm, labels, "n16_nmp_right_id", intArrayOf(input.rightObjectId))
            writeWords(vm, labels, "n16_nmp_left_owner", intArrayOf(input.leftOwnerPoint))
            writeWords(vm, labels, "n16_nmp_right_owner", intArrayOf(input.rightOwnerPoint))
            writeWords(vm, labels, "n16_nmp_left_digest_marker", intArrayOf(digestMarker(input.leftDigest)))
            writeWords(vm, labels, "n16_nmp_right_digest_marker", intArrayOf(digestMarker(input.rightDigest)))
            writeWords(vm, labels, "n16_nmp_dimension", intArrayOf(input.dimension))
            writeWords(vm, labels, "n16_nmp_left_payload", input.leftPayload)
            writeWords(vm, labels, "n16_nmp_right_payload", input.rightPayload)
            writeWords(vm, labels, "n16_nmp_output_payload", IntArray(input.dimension))
            vm.setRegister(15, labels.getValue("n16_nmp_kernel_halt"))
            vm.setProgramCounter(labels.getValue("n16_nmp_kernel_entry"))
            var remaining = MAX_CALL_STEPS
            while (remaining > 0) {
                val slice = minOf(remaining, CALL_SLICE)
                val status = vm.run(slice)
                remaining -= slice
                if (status == NanVm.HALTED) {
                    val result = readWords(vm, labels, "n16_nmp_result", 5)
                    check(result[0] == 0) {
                        "NaN16 worker guest denied execution (status=\${result[0]})"
                    }
                    val outputCount = result[4]
                    val expectedOutputCount =
                        if (programId == VECTOR_ADD_PROGRAM) input.dimension else 1
                    check(result[1] == programId &&
                        result[2] == (if (programId == VECTOR_ADD_PROGRAM) VECTOR_KIND else SCALAR_KIND) &&
                        result[3] == input.dimension &&
                        outputCount == expectedOutputCount) {
                        "NaN16 worker guest returned an invalid output descriptor"
                    }
                    val output = readWords(vm, labels, "n16_nmp_output_payload", outputCount)
                    return N16NmpGuestExecution(
                        result[1], result[2], result[3], output, vm.steps(),
                        imageDigest, sourceDigest
                    )
                }
                check(status == NanVm.BUDGET) {
                    "NaN16 worker guest stopped unexpectedly (result=$status, trap=\${vm.trap()})"
                }
            }
            error("NaN16 worker guest exceeded its instruction budget")
        } finally {
            vm.close()
        }
    }

    private fun writeWords(vm: NanVm, labels: Map<String, Int>, label: String, words: IntArray) {
        val bytes = ByteBuffer.allocate(words.size * WORD_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        words.forEach { bytes.putInt(it) }
        check(vm.writeBytes(Math.multiplyExact(labels.getValue(label), WORD_BYTES), bytes.array())) {
            "Could not stage NaN16 worker words for $label"
        }
    }

    private fun readWords(vm: NanVm, labels: Map<String, Int>, label: String, count: Int): IntArray {
        val bytes = vm.readBytes(Math.multiplyExact(labels.getValue(label), WORD_BYTES), count * WORD_BYTES)
            ?: error("Could not read NaN16 worker words for $label")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(count) { buffer.int }
    }

    private fun digestWords(words: IntArray): String {
        val bytes = ByteBuffer.allocate(words.size * WORD_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        words.forEach { bytes.putInt(it) }
        return sha256Hex(bytes.array())
    }

    private fun digestMarker(value: String): Int =
        normalizeDigest(value).take(8).toLong(16).toInt()

    private fun normalizeDigest(value: String): String {
        val bare = value.removePrefix("sha256:").removePrefix("pf:").lowercase()
        require(bare.matches(Regex("[0-9a-f]{64}"))) { "NMP input digest is malformed" }
        return bare
    }

    private fun requireDigest(value: String, label: String) {
        require(value.matches(Regex("[0-9a-f]{64}"))) { "$label is malformed" }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val IMAGE_ASSET = "n16_nmp_kernel.nan"
        private const val LABEL_ASSET = "n16_nmp_kernel.labels"
        private const val SOURCE_DIGEST_ASSET = "n16_nmp_kernel.source.sha256"
        private const val RECEIPT_SCHEMA = "nanrouter.native-admission-receipt.v1"
        private const val VECTOR_ADD_PROGRAM = 78
        private const val VECTOR_DOT_PROGRAM = 79
        private const val VECTOR_KIND = 4
        private const val SCALAR_KIND = 3
        private const val MAX_DIMENSION = 16
        private const val WORD_BYTES = 4
        private const val CALL_SLICE = 250_000
        private const val MAX_CALL_STEPS = 2_000_000
        private val ALLOWLIST = mapOf(
            "math.vector.add.v1" to VECTOR_ADD_PROGRAM,
            "math.vector.dot.v1" to VECTOR_DOT_PROGRAM
        )
        private val REQUIRED_LABELS = setOf(
            "n16_nmp_kernel_entry", "n16_nmp_kernel_halt", "n16_nmp_operation", "n16_nmp_left_id",
            "n16_nmp_right_id", "n16_nmp_left_owner", "n16_nmp_right_owner",
            "n16_nmp_left_digest_marker", "n16_nmp_right_digest_marker",
            "n16_nmp_dimension", "n16_nmp_left_payload", "n16_nmp_right_payload",
            "n16_nmp_output_payload", "n16_nmp_result"
        )
    }
}
