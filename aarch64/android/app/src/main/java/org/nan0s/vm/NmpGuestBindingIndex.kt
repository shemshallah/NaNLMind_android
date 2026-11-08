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
    private val highestVersion = mutableMapOf<String, Int>()
    private val invalidated = mutableSetOf<Key>()

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
        require(key !in invalidated) { "invalidated canonical leaf version cannot be republished" }
        require(key.version >= (highestVersion[key.leafId] ?: key.version)) {
            "cannot publish a stale canonical leaf version"
        }
        val old = byLeaf[key]
        require(old == null || old == binding) {
            "canonical leaf version cannot be rebound"
        }
        val occupied = byObject[binding.objectId]
        require(occupied == null || occupied == key ||
            (occupied.leafId == key.leafId && occupied.version < key.version)) {
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
        highestVersion[key.leafId] = key.version
    }

    /**
     * Apply one *verified* canonical store transaction to this in-memory mirror.
     * The external ingestion owner must coordinate its guest-memory writes and
     * this update under an independent lock. This method provides index-level
     * atomicity only: it never authenticates an incoming transaction.
     *
     * An invalidation may release an object ID for a new published version in
     * the same transaction. Conflicts and rollbacks are checked on an isolated
     * copy; a failed operation changes none of the published index state.
     */
    @Synchronized
    fun applyCommittedTransaction(
        publications: List<NmpGuestLeafBinding>,
        invalidations: List<Pair<String, Int>>
    ) {
        val count = publications.size + invalidations.size
        require(count in 1..32) {
            "canonical binding transaction must contain 1..32 records"
        }
        val publishedKeys = publications.map { Key(it.leafId, it.version) }
        val removedKeys = invalidations.map { Key(it.first, it.second) }
        require(publishedKeys.distinct().size == publishedKeys.size &&
            removedKeys.distinct().size == removedKeys.size &&
            publishedKeys.none { it in removedKeys }) {
            "canonical transaction has duplicated or contradictory leaf identities"
        }
        val proposed = NmpGuestBindingIndex()
        proposed.byLeaf.putAll(byLeaf)
        proposed.byObject.putAll(byObject)
        proposed.highestVersion.putAll(highestVersion)
        proposed.invalidated.addAll(invalidated)
        invalidations.forEach { (leafId, version) ->
            proposed.invalidateCommitted(leafId, version)
        }
        publications.forEach(proposed::publishCommitted)
        byLeaf.clear()
        byLeaf.putAll(proposed.byLeaf)
        byObject.clear()
        byObject.putAll(proposed.byObject)
        highestVersion.clear()
        highestVersion.putAll(proposed.highestVersion)
        invalidated.clear()
        invalidated.addAll(proposed.invalidated)
    }

    /** Atomic all-publication convenience form for one committed store batch. */
    @Synchronized
    fun publishBatchCommitted(bindings: List<NmpGuestLeafBinding>) {
        applyCommittedTransaction(bindings, emptyList())
    }

    @Synchronized
    fun invalidateCommitted(leafId: String, version: Int): Boolean {
        require(leafId.isNotBlank() && version >= 0) {
            "invalid canonical invalidation identity"
        }
        val key = Key(leafId, version)
        // Invalidating an unseen newer version must also retire any older
        // locally bound leaf. Otherwise an old object remains readable even
        // though the durable canonical watermark has advanced past it.
        val retired = byLeaf.keys.filter { it.leafId == leafId && it.version <= version }
        for (retiredKey in retired) {
            val binding = byLeaf.remove(retiredKey)
            if (binding != null) byObject.remove(binding.objectId)
            invalidated.add(retiredKey)
        }
        // Persist a negative decision even when this exact version was absent:
        // delayed ingestion must not resurrect a revoked canonical version.
        invalidated.add(key)
        highestVersion[leafId] = maxOf(version, highestVersion[leafId] ?: version)
        return retired.isNotEmpty()
    }

    @Synchronized
    fun clearForGuestReload() {
        byLeaf.clear()
        byObject.clear()
        highestVersion.clear()
        invalidated.clear()
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
