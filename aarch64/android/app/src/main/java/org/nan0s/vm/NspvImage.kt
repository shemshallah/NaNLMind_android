package org.nan0s.vm

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/** Sparse NSPV image bootstrap for Android storage tests. */
object NspvImage {
    const val SECTOR = 512
    const val DEFAULT_BYTES = 1L shl 30
    private const val MAGIC = 0x4E53505631L

    private fun geometry(bytes: Long): Triple<Int, Int, Int>? {
        val total = (bytes / SECTOR).toInt()
        if (total < 4) return null
        var cap = ((total - 1) * 10) / 11
        while (cap > 0) {
            val tag = (cap + 9) / 10
            val dataLba = 1 + tag
            if (dataLba + cap <= total) return Triple(cap, tag, dataLba)
            cap--
        }
        return null
    }

    fun ensure(file: File, keyFile: File, bytes: Long = DEFAULT_BYTES): Boolean {
        if (file.exists() && check(file)) return true
        val g = geometry(bytes) ?: return false
        val rnd = SecureRandom()
        val nonce = LongArray(2) { rnd.nextLong() }
        val key = LongArray(4) { rnd.nextLong() }
        val h = LongArray(16)
        h[0] = MAGIC
        h[1] = (1L shl 16) or 1L
        h[2] = SECTOR.toLong()
        h[3] = g.third.toLong()
        h[4] = g.first.toLong()
        h[5] = 1L
        h[6] = g.second.toLong()
        h[7] = nonce[0]
        h[8] = nonce[1]
        h[11] = 0L
        h[12] = 1L
        val buf = ByteBuffer.allocate(SECTOR).order(ByteOrder.LITTLE_ENDIAN)
        for (v in h) buf.putLong(v)
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(bytes)
            f.seek(0)
            f.write(buf.array())
        }
        keyFile.writeText("KEY=" + key.joinToString("") { hexLong(it) } +
            "\nNON=" + nonce.joinToString("") { hexLong(it) } + "\n")
        return true
    }

    fun check(file: File): Boolean {
        if (!file.exists() || file.length() < SECTOR) return false
        val b = ByteArray(SECTOR)
        try { RandomAccessFile(file, "r").use { it.readFully(b) } } catch (e: Exception) { return false }
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val h = LongArray(16) { bb.getLong() }
        if (h[0] != MAGIC) return false
        val ver = (h[1] ushr 16).toInt()
        val suite = (h[1] and 0xffffL).toInt()
        if (ver != 1 || (suite != 1 && suite != 2) || h[2] != SECTOR.toLong()) return false
        val cap = h[4]
        val dataLba = h[3]
        if (cap < 1L || cap > 0xFFFFFFFFL || dataLba < 1L) return false
        return dataLba + cap <= file.length() / SECTOR
    }

    private fun hexLong(v: Long): String = "%016X".format(v)
}
