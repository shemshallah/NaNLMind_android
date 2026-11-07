package org.nan0s.vm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Joins the NaN16 native admission guest to the NMP HTTPS submit client.
 * Descriptors and payloads must be resolved from canonical NaN0S guest state.
 */
internal data class NmpNativeAdmissionInput(
    val candidate: IntArray, val rule: IntArray, val left: IntArray,
    val right: IntArray, val contract: IntArray, val availableCapabilities: Int,
    val programId: Int, val leftPayload: IntArray, val rightPayload: IntArray,
    val output: IntArray
)

internal class NmpNativeReceiptBridge {
    private val evaluate: (NmpNativeAdmissionInput) -> N16MathAdmission.Decision

    constructor(context: Context) {
        val guest = N16MathAdmission(context)
        evaluate = { input ->
            guest.evaluate(
                input.candidate, input.rule, input.left, input.right,
                input.contract, input.availableCapabilities, input.programId,
                input.leftPayload, input.rightPayload, input.output
            )
        }
    }

    internal constructor(evaluator: (NmpNativeAdmissionInput) -> N16MathAdmission.Decision) {
        evaluate = evaluator
    }

    /** Run NaN16, attach its verified receipt, and return a PREFLIGHTED packet. */
    fun preparePacket(proposedPacket: JSONObject, input: NmpNativeAdmissionInput): JSONObject {
        require(proposedPacket.optString("state") == "PROPOSED") {
            "native receipt bridge requires a PROPOSED NMP packet"
        }
        require(!proposedPacket.has("admission_receipt") || proposedPacket.isNull("admission_receipt")) {
            "packet already contains an admission receipt"
        }
        require(proposedPacket.optString("admission_verdict").equals("VALID", true)) {
            "packet must carry the candidate admission verdict"
        }

        val decision = evaluate(input)
        check(decision.allowed) {
            "NaN16 admission denied packet (status=" + decision.status + ")"
        }

        val packet = JSONObject(proposedPacket.toString())
        val generation = if (packet.has("generation") && !packet.isNull("generation")) {
            packet.getInt("generation")
        } else {
            packet.getInt("base_generation")
        }
        val capabilitySet = packet.getJSONArray("capability_set")
        val capabilityDigest = sha256(canonicalJson(capabilitySet))
        require(capabilityDigest == packet.getString("capability_set_digest")) {
            "capability_set_digest does not match the canonical capability_set"
        }
        val receipt = decision.toNmpAdmissionReceipt(
            packetId = packet.getString("packet_id"),
            candidateId = packet.getString("candidate_id"),
            functionId = packet.getString("function_id"),
            functionVersion = packet.getInt("function_version"),
            snapshotId = packet.getString("snapshot_id"),
            rulesetSnapshotId = packet.getString("ruleset_snapshot_id"),
            inputDigest = inputDigest(packet.getJSONArray("inputs")),
            capabilityDigest = capabilityDigest,
            preflightDigest = packet.getString("preflight_digest"),
            generation = generation
        )

        packet.put("admission_receipt", JSONObject(receipt))
        packet.put("preflight_result", "VALID")
        packet.put("state", "PREFLIGHTED")
        packet.put("status", "PREFLIGHTED")
        val preflight = packet.optJSONObject("preflight") ?: JSONObject()
        preflight.put("result", "VALID")
            .put("digest", packet.getString("preflight_digest"))
            .put("rule_snapshot_id", packet.getString("ruleset_snapshot_id"))
        packet.put("preflight", preflight)
        val metadata = packet.optJSONObject("metadata") ?: JSONObject()
        metadata.put("native_admission_required", true)
        packet.put("metadata", metadata)
        return packet
    }

    /** No HTTP request is made unless the NaN16 guest returned ALLOW and a bound receipt. */
    fun submit(
        client: NmpHttpsClient, proposedPacket: JSONObject,
        input: NmpNativeAdmissionInput, nowTick: Long
    ): JSONObject = client.submit(preparePacket(proposedPacket, input), nowTick)

    private fun inputDigest(inputs: JSONArray): String {
        val identities = ArrayList<String>(inputs.length())
        for (index in 0 until inputs.length()) {
            val item = inputs.getJSONObject(index)
            val selected = INPUT_IDENTITY_KEYS.filter { item.has(it) && !item.isNull(it) }
            val keys = (if (selected.isEmpty()) item.keys().asSequence().toList() else selected).sorted()
            val identity = JSONObject()
            keys.forEach { key -> identity.put(key, item.get(key)) }
            identities += canonicalJson(identity)
        }
        return sha256("[" + identities.joinToString(",") + "]")
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = value.keys().asSequence().toList().sorted()
            keys.joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
            }
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonicalJson(value.get(it))
        }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> error("unsupported NMP input identity value")
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private val INPUT_IDENTITY_KEYS = listOf(
            "leaf_id", "version", "digest", "content_digest", "kind", "type"
        )
    }
}
