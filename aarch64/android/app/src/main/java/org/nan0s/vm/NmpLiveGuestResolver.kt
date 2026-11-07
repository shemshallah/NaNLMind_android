package org.nan0s.vm

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Trusted identity index entry. This MUST be emitted by the canonical guest
 * object-store ingestion path, never constructed from an NMP packet.
 *
 * payloadSha256 is SHA-256 of the exact little-endian i32 words in guest RAM;
 * it is independent of the NMP leaf's canonical content digest.
 */
internal data class NmpGuestLeafBinding(
    val leafId: String,
    val version: Int,
    val contentDigest: String,
    val objectId: Int,
    val payloadSha256: String
)

/**
 * Reads an already-running NaN16 guest; never stages packet-controlled data.
 *
 * 'labels' must come from the exact image loaded into 'vm'. The live object
 * store must also supply trusted leaf-to-registry bindings; the registry's
 * 32-bit digest marker cannot authenticate a 256-bit leaf digest.
 *
 * The VM read transaction excludes concurrent execution and writes through
 * the same NanVm instance. The caller must serialize modifications to its
 * trusted binding index with this resolve operation.
 */
internal class NmpLiveGuestResolver private constructor(
    private val labels: Map<String, Int>,
    private val snapshot: (
        (((Int, Int) -> ByteArray?) -> NmpResolvedVectorPair)
    ) -> NmpResolvedVectorPair
) {
    constructor(vm: NanVm, labels: Map<String, Int>) :
        this(labels, { block -> vm.inspectGuestMemory(block) })

    /** Test-only seam for a deterministic guest memory image. */
    internal constructor(
        labels: Map<String, Int>,
        read: (Int, Int) -> ByteArray?
    ) : this(labels, { block -> block(read) })

    fun resolve(left: NmpGuestLeafBinding, right: NmpGuestLeafBinding): NmpResolvedVectorPair {
        require(left.objectId > 0 && right.objectId > 0 && left.objectId != right.objectId) {
            "live resolver requires distinct registered object IDs"
        }
        require(left.version >= 0 && right.version >= 0 &&
            left.leafId.isNotBlank() && right.leafId.isNotBlank()) {
            "live resolver requires canonical leaf identities"
        }
        listOf(left, right).forEach {
            require(hexDigest(it.contentDigest).length == 64)
            require(hexDigest(it.payloadSha256).length == 64)
        }
        val objectsLabel = labels["nmr_obj_table"]
            ?: error("running guest has no canonical nmr_obj_table label")
        val shapesLabel = labels["nmr_shape_table"]
            ?: error("running guest has no canonical nmr_shape_table label")

        return snapshot { read ->
            val objects = words(read, objectsLabel, OBJECT_TABLE_WORDS)
            val shapes = words(read, shapesLabel, SHAPE_TABLE_WORDS)
            val lhs = extract(left, objects, shapes, read)
            val rhs = extract(right, objects, shapes, read)
            require(lhs.dimension == rhs.dimension) { "live guest vector dimensions differ" }
            require(lhs.payloadEnd <= rhs.payloadStart || rhs.payloadEnd <= lhs.payloadStart) {
                "live guest vectors have overlapping payload storage"
            }
            NmpResolvedVectorPair(
                left.leafId, left.version, hexDigest(left.contentDigest),
                right.leafId, right.version, hexDigest(right.contentDigest),
                left.objectId, right.objectId, lhs.owner, rhs.owner,
                lhs.dimension, lhs.payload, rhs.payload
            )
        }
    }

    private data class Vector(
        val owner: Int, val dimension: Int, val payload: IntArray,
        val payloadStart: Long, val payloadEnd: Long
    )

    private fun extract(
        binding: NmpGuestLeafBinding,
        objects: IntArray,
        shapes: IntArray,
        read: (Int, Int) -> ByteArray?
    ): Vector {
        val objectSlot = (0 until OBJECT_SLOTS).singleOrNull {
            objects[it * OBJECT_WORDS] == binding.objectId
        } ?: error("live guest object is absent or duplicated: ${binding.objectId}")
        val objectOffset = objectSlot * OBJECT_WORDS
        val kind = objects[objectOffset + 1]
        val owner = objects[objectOffset + 2]
        val marker = objects[objectOffset + 3]
        val pointer = objects[objectOffset + 4]
        require(kind == VECTOR_KIND && owner in 1..256 && pointer > 0) {
            "live guest object kind, owner, or data pointer is invalid"
        }
        val expectedMarker = hexDigest(binding.contentDigest).take(8).toLong(16).toInt()
        require(marker == expectedMarker) { "live guest digest marker differs from trusted leaf" }

        val shapeSlot = (0 until SHAPE_SLOTS).singleOrNull {
            shapes[it * SHAPE_WORDS] == binding.objectId
        } ?: error("live guest shape is absent or duplicated: ${binding.objectId}")
        val shapeOffset = shapeSlot * SHAPE_WORDS
        val rank = shapes[shapeOffset + 1]
        val dimension = shapes[shapeOffset + 2]
        val stride = shapes[shapeOffset + 3]
        val capacity = shapes[shapeOffset + 4]
        require(rank == 1 && dimension in 1..MAX_DIMENSION &&
            stride == 1 && capacity >= dimension) {
            "live guest vector has unsupported shape or capacity"
        }

        val raw = read(Math.multiplyExact(pointer, WORD_BYTES),
            Math.multiplyExact(dimension, WORD_BYTES))
            ?: error("live guest payload is outside readable memory")
        require(raw.size == dimension * WORD_BYTES) { "truncated live guest payload" }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(digest == hexDigest(binding.payloadSha256)) {
            "live guest payload differs from the trusted object-store payload digest"
        }
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return Vector(
            owner, dimension, IntArray(dimension) { buffer.int },
            pointer.toLong() * WORD_BYTES,
            (pointer.toLong() + dimension) * WORD_BYTES
        )
    }

    private fun words(
        read: (Int, Int) -> ByteArray?,
        wordAddress: Int,
        count: Int
    ): IntArray {
        require(wordAddress > 0) { "invalid guest registry label" }
        val byteCount = Math.multiplyExact(count, WORD_BYTES)
        val bytes = read(Math.multiplyExact(wordAddress, WORD_BYTES), byteCount)
            ?: error("canonical registry is not readable in running guest")
        require(bytes.size == byteCount) { "truncated canonical registry" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(count) { buffer.int }
    }

    private fun hexDigest(value: String): String {
        val v = value.removePrefix("sha256:").removePrefix("pf:").lowercase()
        require(v.matches(Regex("[0-9a-f]{64}"))) { "malformed trusted digest" }
        return v
    }

    companion object {
        private const val WORD_BYTES = 4
        private const val VECTOR_KIND = 4
        private const val MAX_DIMENSION = 16
        private const val OBJECT_SLOTS = 32
        private const val OBJECT_WORDS = 5
        private const val SHAPE_SLOTS = 32
        private const val SHAPE_WORDS = 5
        private const val OBJECT_TABLE_WORDS = OBJECT_SLOTS * OBJECT_WORDS
        private const val SHAPE_TABLE_WORDS = SHAPE_SLOTS * SHAPE_WORDS
    }
}
