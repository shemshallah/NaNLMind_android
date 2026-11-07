package org.nan0s.vm

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Runs the composed MTH admission gate in a fresh NaN16 VM.
 * Android only marshals bounded 32-bit words; all semantic checks execute in the guest.
 */
internal class N16MathAdmission(context: Context) {
    data class Decision(
        val status: Int,
        val outputDescriptor: IntArray,
        val guestSteps: Long,
        val ruleEpoch: Int? = null,
        val ruleDigestWords: IntArray? = null,
        val guestSourceDigest: String = "",
        val guestImageDigest: String = ""
    ) {
        val allowed: Boolean get() = status == 0
        val ruleDenial: Int? get() = if (status in 17..25) status - 16 else null
        val typedDenial: Int? get() = if (status in 33..41) status - 32 else null
        val ruleDigestHex: String?
            get() = ruleDigestWords?.joinToString("") { "%08x".format(it.toLong() and 0xffff_ffffL) }
    }

    private val image = context.assets.open(IMAGE_ASSET).use { it.readBytes() }
    private val guestSourceDigest = context.assets.open(SOURCE_DIGEST_ASSET)
        .bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
    private val guestImageDigest = sha256Hex(image)
    private val labels: Map<String, Int> = context.assets.open(LABEL_ASSET)
        .bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().mapNotNull { line ->
                val fields = line.trim().split(Regex("\\s+"))
                if (fields.size != 2) null
                else fields[0] to fields[1].toIntOrNull()
            }.mapNotNull { (name, address) -> address?.let { name to it } }.toMap()
        }

    init {
        check(guestSourceDigest.matches(Regex("[0-9a-f]{64}"))) {
            "NaN16 math-admission source digest is malformed"
        }
        check(guestImageDigest.matches(Regex("[0-9a-f]{64}"))) {
            "NaN16 math-admission image digest is malformed"
        }
        REQUIRED_LABELS.forEach { name ->
            check(labels.containsKey(name)) { "Missing NaN16 math-admission label: $name" }
        }
    }

    @Synchronized
    fun evaluate(
        candidate: IntArray,
        rule: IntArray,
        left: IntArray,
        right: IntArray,
        contract: IntArray,
        availableCapabilities: Int,
        leftPayload: IntArray,
        rightPayload: IntArray,
        output: IntArray
    ): Decision {
        require(candidate.size == CANDIDATE_WORDS) { "candidate must contain 22 NaN16 words" }
        require(rule.size == RULE_WORDS) { "rule must contain 17 NaN16 words" }
        require(left.size == LEAF_WORDS && right.size == LEAF_WORDS) {
            "each leaf descriptor must contain 8 NaN16 words"
        }
        require(contract.size == CONTRACT_WORDS) { "operator contract must contain 10 NaN16 words" }
        require(output.size == LEAF_WORDS) { "output descriptor must contain 8 NaN16 words" }
        require(left[5] != 0 && right[5] != 0 && output[5] != 0) {
            "leaf payload addresses must be nonzero"
        }
        require(left[6] in 1..MAX_PAYLOAD_WORDS && leftPayload.size == left[6]) {
            "left payload length must exactly match its bounded descriptor capacity"
        }
        require(right[6] in 1..MAX_PAYLOAD_WORDS && rightPayload.size == right[6]) {
            "right payload length must exactly match its bounded descriptor capacity"
        }
        require(output[6] in 1..MAX_PAYLOAD_WORDS) {
            "output payload capacity is outside the Android admission window"
        }

        val guestLeft = left.copyOf().also { it[5] = labels.getValue("n16_math_left_payload") }
        val guestRight = right.copyOf().also { it[5] = labels.getValue("n16_math_right_payload") }
        val guestOutput = output.copyOf().also { it[5] = labels.getValue("n16_math_output_payload") }
        val vm = NanVm()
        try {
            check(vm.load(image) != null) { "NaN16 mathematical admission guest failed to load" }
            writeWords(vm, "n16_math_candidate", candidate)
            writeWords(vm, "n16_math_rule", rule)
            writeWords(vm, "n16_math_left", guestLeft)
            writeWords(vm, "n16_math_right", guestRight)
            writeWords(vm, "n16_math_left_payload", leftPayload)
            writeWords(vm, "n16_math_right_payload", rightPayload)
            writeWords(vm, "n16_math_output_payload", IntArray(output[6]))
            writeWords(vm, "n16_math_contract", contract)
            writeWords(vm, "n16_math_capabilities", intArrayOf(availableCapabilities))
            writeWords(vm, "n16_math_output", guestOutput)

            vm.setProgramCounter(labels.getValue("n16_math_admission_entry"))
            var remaining = MAX_CALL_STEPS
            while (remaining > 0) {
                val slice = minOf(remaining, CALL_SLICE)
                val result = vm.run(slice)
                remaining -= slice
                if (result == NanVm.HALTED) {
                    val status = readWords(vm, "n16_math_result", 1)[0]
                    val receipt = readWords(vm, "n16_math_receipt", RECEIPT_WORDS)
                    check(receipt[0] == status) { "NaN16 result and receipt status disagree" }
                    val updatedOutput = readWords(vm, "n16_math_output", LEAF_WORDS)
                    updatedOutput[5] = output[5]
                    if (status != 0) return Decision(
                        status, updatedOutput, vm.steps(),
                        guestSourceDigest = guestSourceDigest,
                        guestImageDigest = guestImageDigest
                    )
                    val epoch = receipt[1]
                    val digest = receipt.copyOfRange(2, RECEIPT_WORDS)
                    check(epoch == candidate[1] && epoch == rule[1]) {
                        "NaN16 receipt epoch does not match the admitted rule snapshot"
                    }
                    check(digest.contentEquals(candidate.copyOfRange(14, 22)) &&
                        digest.contentEquals(rule.copyOfRange(9, 17))) {
                        "NaN16 receipt digest does not match the complete 256-bit rule snapshot"
                    }
                    return Decision(
                        status, updatedOutput, vm.steps(), epoch, digest,
                        guestSourceDigest, guestImageDigest
                    )
                }
                check(result == NanVm.BUDGET) {
                    "NaN16 math admission stopped unexpectedly (result=$result, trap=${vm.trap()})"
                }
            }
            error("NaN16 math admission exceeded its instruction budget")
        } finally {
            vm.close()
        }
    }

    private fun writeWords(vm: NanVm, label: String, words: IntArray) {
        val bytes = ByteBuffer.allocate(words.size * WORD_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        words.forEach { bytes.putInt(it) }
        check(vm.writeBytes(byteAddress(label), bytes.array())) {
            "Could not stage NaN16 words for $label"
        }
    }

    private fun readWords(vm: NanVm, label: String, count: Int): IntArray {
        val bytes = vm.readBytes(byteAddress(label), count * WORD_BYTES)
            ?: error("Could not read NaN16 words for $label")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(count) { buffer.int }
    }

    private fun byteAddress(label: String): Int =
        Math.multiplyExact(labels.getValue(label), WORD_BYTES)

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val IMAGE_ASSET = "n16_math_admission.nan"
        private const val LABEL_ASSET = "n16_math_admission.labels"
        private const val SOURCE_DIGEST_ASSET = "n16_math_admission.source.sha256"
        private const val WORD_BYTES = 4
        private const val CANDIDATE_WORDS = 22
        private const val RULE_WORDS = 17
        private const val LEAF_WORDS = 8
        private const val CONTRACT_WORDS = 10
        private const val RECEIPT_WORDS = 10
        private const val MAX_PAYLOAD_WORDS = 65_536
        private const val CALL_SLICE = 250_000
        private const val MAX_CALL_STEPS = 2_000_000
        private val REQUIRED_LABELS = setOf(
            "n16_math_admission_entry", "n16_math_candidate", "n16_math_rule",
            "n16_math_left", "n16_math_right", "n16_math_contract",
            "n16_math_capabilities", "n16_math_output", "n16_math_result", "n16_math_receipt",
            "n16_math_left_payload", "n16_math_right_payload", "n16_math_output_payload"
        )
    }
}
