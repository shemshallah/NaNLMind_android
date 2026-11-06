package org.nan0s.vm

import android.content.Context
import java.io.Closeable
import java.security.SecureRandom
import java.security.MessageDigest

/**
 * Persistent NaN16 N16T guest instance for Android.
 *
 * The host only stages opaque byte arrays, supplies session entropy, sets guest
 * call registers/PC, and reads result registers. Framing, CRC, state transitions,
 * replay protection and route selection execute in the NaN16 guest.
 */
internal class N16GuestProtocol(context: Context) : Closeable {
    data class Route(
        val gateway: Int,
        val interfaceId: Int,
        val logicalPoint: Int
    )

    private val vm = NanVm()
    private val labels: Map<String, Int>
    private val random = SecureRandom()
    private var sessionId = ByteArray(16).also(random::nextBytes)
    private var pending = false
    private var pendingType = 0
    private var closed = false

    init {
        val image = context.assets.open("n16_protocol.nan").use { it.readBytes() }
        val kind = vm.load(image)
        check(kind != null) { "NaN16 protocol guest failed to load" }
        val labelText = context.assets.open("n16_protocol.labels")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        labels = labelText.lineSequence().mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size != 2) null else fields[0] to fields[1].toIntOrNull()
        }.mapNotNull { (name, address) ->
            address?.let { name to it }
        }.toMap()
        listOf("n16_android_build", "n16_android_route",
            "n16_android_advance", "n16_android_abort", "n16_android_reset").forEach { name ->
            check(labels.containsKey(name)) { "Missing NaN16 guest entry: $name" }
        }
    }

    /** Build a complete N16T frame in the guest. A successful build is not yet a sent message. */
    @Synchronized
    fun buildFrame(type: Int, payload: ByteArray): ByteArray {
        checkOpen()
        require(!pending) { "An N16T send is already awaiting transport completion" }
        require(type in 1..13) { "Unsupported N16T message type" }
        require(payload.size <= MAX_PAYLOAD)
        check(vm.writeBytes(PAYLOAD_BASE, payload)) { "Could not stage N16T payload" }
        check(vm.writeBytes(SESSION_BASE, sessionId)) { "Could not stage N16T session identity" }
        vm.setRegister(1, FRAME_BASE)
        vm.setRegister(2, type)
        vm.setRegister(3, 0) // NaN16 assigns the outbound sequence number.
        vm.setRegister(4, PAYLOAD_BASE)
        vm.setRegister(5, payload.size)
        vm.setRegister(6, SESSION_BASE)
        runEntry("n16_android_build")
        val status = vm.register(3)
        check(status == 0) { "NaN16 rejected frame construction (status=$status, detail=${vm.register(2)})" }
        val length = vm.register(1)
        check(length == HEADER_AND_TRAILER + payload.size) { "NaN16 returned an invalid frame length" }
        val frame = vm.readBytes(FRAME_BASE, length)
            ?: throw IllegalStateException("Could not read guest-built N16T frame")
        pending = true
        pendingType = type
        return frame
    }

    /** Commit the outbound transition only after the HTTPS exchange succeeded. */
    @Synchronized
    fun commitSent() {
        checkOpen()
        check(pending) { "No pending N16T send" }
        runEntry("n16_android_advance")
        val status = vm.register(3)
        check(status == 0) {
            "NaN16 rejected outbound transition (status=$status, detail=${vm.register(2)})"
        }
        if (pendingType == 13) {
            // CLOSE resets the guest state machine; begin the next session with fresh entropy.
            sessionId = ByteArray(16).also(random::nextBytes)
        }
        pending = false
        pendingType = 0
    }

    /** Abandon an unsent frame without advancing the guest session state. */
    @Synchronized
    fun abortPending() {
        checkOpen()
        if (pending) {
            runEntry("n16_android_abort")
            val status = vm.register(3)
            check(status == 0) { "NaN16 failed to abort pending send (status=$status)" }
        }
        pending = false
        pendingType = 0
    }

    /** Stage and route a complete peer frame; validation and state changes happen only in NaN16. */
    @Synchronized
    fun acceptFrame(frame: ByteArray): Route {
        checkOpen()
        require(!pending) { "Commit or abort the outbound frame before accepting a response" }
        require(frame.size in HEADER_AND_TRAILER..MAX_FRAME)
        check(vm.writeBytes(FRAME_BASE, frame)) { "Could not stage peer N16T frame" }
        vm.setRegister(1, FRAME_BASE)
        vm.setRegister(2, frame.size)
        runEntry("n16_android_route")
        val status = vm.register(3)
        val logicalPoint = vm.register(4)
        if (status != 0) {
            // Point #1 is the runner portal. On Android its physical transport is
            // the host's default Wi-Fi/cellular route, so a guest with no explicit
            // synthetic route table still resolves this point through the host.
            if (status == 2 && logicalPoint == 0xA001) {
                return Route(0xA001, 0, logicalPoint)
            }
            throw IllegalStateException("NaN16 rejected peer frame (status=$status)")
        }
        return Route(vm.register(1), vm.register(2), logicalPoint)
    }

    @Synchronized
    fun sessionFingerprint(): String =
        MessageDigest.getInstance("SHA-256").digest(sessionId).take(8)
            .joinToString("") { "%02X".format(it) }

    @Synchronized
    fun resetSession() {
        checkOpen()
        pending = false
        pendingType = 0
        runEntry("n16_android_reset")
        sessionId = ByteArray(16).also(random::nextBytes)
    }

    private fun runEntry(name: String) {
        vm.setProgramCounter(labels.getValue(name))
        var remaining = MAX_CALL_STEPS
        while (remaining > 0) {
            val slice = minOf(remaining, CALL_SLICE)
            val result = vm.run(slice)
            remaining -= slice
            if (result == NanVm.HALTED) return
            check(result == NanVm.BUDGET) {
                "NaN16 guest call stopped unexpectedly (result=$result, trap=${vm.trap()})"
            }
        }
        throw IllegalStateException("NaN16 guest call exceeded its instruction budget")
    }

    private fun checkOpen() = check(!closed) { "NaN16 protocol guest is closed" }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            vm.close()
        }
    }

    companion object {
        private const val FRAME_BASE = 1_048_576
        private const val PAYLOAD_BASE = 1_179_648
        private const val SESSION_BASE = 1_310_720
        private const val HEADER_AND_TRAILER = 36
        private const val MAX_PAYLOAD = 65_536
        private const val MAX_FRAME = HEADER_AND_TRAILER + MAX_PAYLOAD
        private const val CALL_SLICE = 250_000
        private const val MAX_CALL_STEPS = 12_000_000
    }
}
