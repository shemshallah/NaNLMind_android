package org.nan0s.vm

import org.json.JSONArray
import org.json.JSONObject

/**
 * In-memory mirror of committed canonical leaf-to-guest object bindings.
 *
 * Only the trusted canonical object-store ingestion owner may call publish or
 * invalidate. Never construct index entries from NMP packet JSON. The index
 * cannot independently prove an ingestion record is canonical: the upstream
 * store must verify provenance and atomically coordinate guest mutations.
 *
 * Lookup and guest inspection share this monitor with publish/invalidate.
 * The resolver separately locks the running NanVm while reading its registry.
 * The ingestion owner must update the guest and this index under its own
 * transaction lock, and clear the index on guest image reload/reset.
 */
internal class NmpGuestBindingIndex {
    private data class Key(val leafId: String, val version: Int)
    private val byLeaf = mutableMapOf<Key, NmpGuestLeafBinding>()
    private val byObject = mutableMapOf<Int, Key>()

    @Synchronized
    fun publishCommitted(binding: NmpGuestLeafBinding) {
        val key = Key(binding.leafId, binding.version)
        require(binding.leafId.isNotBlank() && binding.version >= 0 && binding.objectId > 0) {
            "canonical binding has invalid identity or object ID"
        }
        require(normalize(binding.contentDigest).isNotEmpty() &&
            normalize(binding.payloadSha256).isNotEmpty()) {
            "canonical binding requires full SHA-256 digests"
        }
        require(byLeaf.keys.none { it.leafId == key.leafId && it.version > key.version }) {
            "cannot publish a stale canonical leaf version"
        }
        val old = byLeaf[key]
        require(old == null || old == binding) {
            "canonical leaf version cannot be rebound"
        }
        val occupied = byObject[binding.objectId]
        require(occupied == null || occupied == key) {
            "canonical object ID is already assigned to another leaf"
        }
        // A newer version may replace an older version of the same leaf after
        // the canonical owner has installed and committed its guest state.
        val superseded = byLeaf.keys.filter {
            it.leafId == key.leafId && it.version < key.version
        }
        for (previous in superseded) {
            val removed = byLeaf.remove(previous)
            if (removed != null) byObject.remove(removed.objectId)
        }
        byLeaf[key] = binding
        byObject[binding.objectId] = key
    }

    @Synchronized
    fun invalidateCommitted(leafId: String, version: Int): Boolean {
        val key = Key(leafId, version)
        val removed = byLeaf.remove(key) ?: return false
        byObject.remove(removed.objectId)
        return true
    }

    @Synchronized
    fun clearForGuestReload() {
        byLeaf.clear()
        byObject.clear()
    }

    /**
     * Resolve only the two ordered identities named in a leased packet.
     * Packet JSON selects keys but never supplies object IDs or payload hashes.
     */
    @Synchronized
    fun resolve(inputs: JSONArray, resolver: NmpLiveGuestResolver): NmpResolvedVectorPair {
        require(inputs.length() == 2) { "NaN16 NMP requires exactly two leaf inputs" }
        val left = lookup(inputs.getJSONObject(0))
        val right = lookup(inputs.getJSONObject(1))
        require(left.objectId != right.objectId) {
            "NMP inputs resolve to the same canonical guest object"
        }
        return resolver.resolve(left, right)
    }

    private fun lookup(input: JSONObject): NmpGuestLeafBinding {
        val key = Key(input.getString("leaf_id"), input.getInt("version"))
        val binding = byLeaf[key] ?: error("NMP input has no committed canonical guest binding")
        val first = optionalDigest(input, "digest")
        val second = optionalDigest(input, "content_digest")
        require(first != null || second != null) { "NMP input is missing its content digest" }
        require(first == null || second == null || first == second) {
            "NMP input contains conflicting canonical digest aliases"
        }
        require((first ?: second) == normalize(binding.contentDigest)) {
            "NMP input digest differs from committed canonical binding"
        }
        return binding
    }

    private fun optionalDigest(input: JSONObject, key: String): String? =
        if (!input.has(key) || input.isNull(key)) null else normalize(input.getString(key))

    private fun normalize(value: String): String {
        val normalized = value.removePrefix("sha256:").removePrefix("pf:").lowercase()
        require(Regex("[0-9a-f]{64}").matches(normalized)) {
            "canonical binding digest must be SHA-256 hex"
        }
        return normalized
    }
}
