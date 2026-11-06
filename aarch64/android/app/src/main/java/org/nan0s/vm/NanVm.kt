package org.nan0s.vm

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Thread-safe wrapper: the UI thread (input, drawing) and the run thread share one nan_vm. All calls take the same lock. */
class NanVm : AutoCloseable {
    companion object {
        const val BUDGET = 0; const val WAIT_KEY = 1; const val VSYNC = 2; const val HALTED = 3; const val TIME_WAIT = 4
        const val UP = 128; const val DOWN = 129; const val LEFT = 130; const val RIGHT = 131
        const val HOME = 132; const val END = 133; const val PGUP = 134; const val PGDN = 135; const val INS = 136; const val DEL = 137
    }
    private val lock = Any()
    private var h: Long = NanNative.create()
    private var pointerSequence: Int = 0
    private val cells = IntArray(2000)
    private val pal = IntArray(16)

    val alive: Boolean get() = h != 0L
    /** returns the detected kind ("floppy", "nan-text", "words", "state") or null if the data is not a NaN16 program */
    fun load(data: ByteArray): String? = synchronized(lock) { if (h == 0L) null else NanNative.load(h, data) }
    fun reset() = synchronized(lock) { if (h != 0L) NanNative.reset(h) }
    fun writeBytes(address: Int, data: ByteArray): Boolean = synchronized(lock) { h != 0L && NanNative.writeBytes(h, address, data) }
    fun readBytes(address: Int, length: Int): ByteArray? = synchronized(lock) { if (h == 0L) null else NanNative.readBytes(h, address, length) }
    fun register(index: Int): Int = synchronized(lock) { if (h == 0L) 0 else NanNative.getReg(h, index) }
    fun setRegister(index: Int, value: Int) = synchronized(lock) { if (h != 0L) NanNative.setReg(h, index, value) }
    fun setProgramCounter(pc: Int) = synchronized(lock) { if (h != 0L && pc >= 0) NanNative.setPc(h, pc) }
    /** Guest-owned pointer mailbox: byte offset 0x168000 (word 0x5A000).
     * Android sends coordinates/actions only; NaN16DE owns all window behaviour.
     * action: 1 down, 2 move, 3 up, 4 cancel.
     */
    fun pointerEvent(x: Int, y: Int, action: Int): Boolean = synchronized(lock) {
        if (h == 0L) return@synchronized false
        pointerSequence += 1
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(pointerSequence).putInt(x).putInt(y).putInt(action).array()
        NanNative.writeBytes(h, 0x168000, payload)
    }
    fun stageNetworkFrame(address: Int, frame: ByteArray): Boolean = synchronized(lock) { h != 0L && NanNative.stageNetworkFrame(h, address, frame) }
    fun extractNetworkFrame(address: Int, length: Int): ByteArray? = synchronized(lock) { if (h == 0L) null else NanNative.extractNetworkFrame(h, address, length) }
    fun run(steps: Int): Int = synchronized(lock) { if (h == 0L) HALTED else NanNative.run(h, steps) }
    fun setTime(ms: Int) = synchronized(lock) { if (h != 0L) NanNative.setTime(h, ms) }
    fun keyChar(c: Int) = synchronized(lock) { if (h != 0L) NanNative.keyChar(h, c) }
    fun keyEvent(sc: Int, down: Boolean) = synchronized(lock) { if (h != 0L) NanNative.keyEvent(h, sc, down) }
    fun rawKeyboard(): Boolean = synchronized(lock) { h != 0L && NanNative.rawKeyboard(h) != 0 }
    fun graphics(): Boolean = synchronized(lock) { h != 0L && NanNative.displayMode(h) == 1 }
    fun width(): Int = synchronized(lock) { if (h == 0L) 80 else NanNative.displayWidth(h) }
    fun height(): Int = synchronized(lock) { if (h == 0L) 25 else NanNative.displayHeight(h) }
    fun dirty(): Int = synchronized(lock) { if (h == 0L) 0 else NanNative.dirty(h) }
    fun speakerHz(): Int = synchronized(lock) { if (h == 0L) 0 else NanNative.speakerHz(h) }
    fun steps(): Long = synchronized(lock) { if (h == 0L) 0 else NanNative.steps(h) }
    fun trap(): String = synchronized(lock) { if (h == 0L) "" else NanNative.trap(h) }
    fun serial(): ByteArray = synchronized(lock) { if (h == 0L) ByteArray(0) else NanNative.serial(h) }
    fun saveState(): ByteArray? = synchronized(lock) { if (h == 0L) null else NanNative.saveState(h) }
    fun restoreState(d: ByteArray): Boolean = synchronized(lock) { h != 0L && NanNative.restoreState(h, d) }
    fun attachDiskFd(fd: Int, size: Long) = synchronized(lock) { if (h != 0L) NanNative.attachDiskFd(h, fd, size) }
    fun detachDisk() = synchronized(lock) { if (h != 0L) NanNative.detachDisk(h) }

    /** Text screen as 25 lines (CP437 mapped to Unicode, trailing blanks trimmed). */
    fun textLines(): List<String> = synchronized(lock) {
        if (h == 0L || !NanNative.textCells(h, cells)) return emptyList()
        (0 until 25).map { y ->
            val sb = StringBuilder()
            for (x in 0 until 80) sb.append(Cp437.map[cells[y * 80 + x] and 255])
            sb.toString().trimEnd()
        }
    }
    /** Copies the 2000 cells (char | attr << 8) and the 16-colour palette; returns false if closed. */
    fun snapshotText(outCells: IntArray, outPal: IntArray): Boolean = synchronized(lock) {
        h != 0L && NanNative.textCells(h, outCells) && NanNative.textPalette(h, outPal)
    }
    /** ARGB framebuffer of the current graphics mode into out (size must be >= width*height). */
    fun snapshotGfx(out: IntArray): Boolean = synchronized(lock) { h != 0L && NanNative.render(h, out) }

    override fun close() = synchronized(lock) { if (h != 0L) { NanNative.destroy(h); h = 0L } }
}
