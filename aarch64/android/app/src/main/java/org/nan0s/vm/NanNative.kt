package org.nan0s.vm

/** Raw JNI surface (see jni/nan_jni.c). The handle is a nan_vm* held in a Long. */
object NanNative {
    init { System.loadLibrary("nanjni") }
    @JvmStatic external fun create(): Long
    @JvmStatic external fun destroy(h: Long)
    @JvmStatic external fun reset(h: Long)
    @JvmStatic external fun load(h: Long, data: ByteArray): String?
    @JvmStatic external fun writeBytes(h: Long, address: Int, data: ByteArray): Boolean
    @JvmStatic external fun readBytes(h: Long, address: Int, length: Int): ByteArray?
    /** Exact 8-byte NaN16 word inspection (both heads + payload); no guest write. */
    @JvmStatic external fun readWordsRaw(h: Long, firstWord: Int, wordCount: Int): ByteArray?
    @JvmStatic external fun getReg(h: Long, index: Int): Int
    @JvmStatic external fun setReg(h: Long, index: Int, value: Int)
    @JvmStatic external fun setPc(h: Long, pc: Int)
    @JvmStatic external fun stageNetworkFrame(h: Long, address: Int, data: ByteArray): Boolean
    @JvmStatic external fun extractNetworkFrame(h: Long, address: Int, length: Int): ByteArray?
    @JvmStatic external fun run(h: Long, steps: Int): Int
    @JvmStatic external fun setTime(h: Long, ms: Int)
    @JvmStatic external fun keyChar(h: Long, ch: Int)
    @JvmStatic external fun keyEvent(h: Long, sc: Int, down: Boolean)
    @JvmStatic external fun rawKeyboard(h: Long): Int
    @JvmStatic external fun displayMode(h: Long): Int
    @JvmStatic external fun displayWidth(h: Long): Int
    @JvmStatic external fun displayHeight(h: Long): Int
    @JvmStatic external fun dirty(h: Long): Int
    @JvmStatic external fun speakerHz(h: Long): Int
    @JvmStatic external fun steps(h: Long): Long
    @JvmStatic external fun trap(h: Long): String
    @JvmStatic external fun pc(h: Long): Int
    @JvmStatic external fun textCells(h: Long, out: IntArray): Boolean
    @JvmStatic external fun textPalette(h: Long, out: IntArray): Boolean
    @JvmStatic external fun render(h: Long, out: IntArray): Boolean
    @JvmStatic external fun serial(h: Long): ByteArray
    @JvmStatic external fun saveState(h: Long): ByteArray?
    @JvmStatic external fun restoreState(h: Long, data: ByteArray): Boolean
    @JvmStatic external fun attachDiskFd(h: Long, fd: Int, size: Long)
    @JvmStatic external fun detachDisk(h: Long)
}
