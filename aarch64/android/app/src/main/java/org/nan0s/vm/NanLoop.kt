package org.nan0s.vm

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** The run thread: slices the VM, sleeps when the guest waits (key, vsync, delay loop), reports frames and serial output. */
class NanLoop(private val vm: NanVm, private val listener: Listener, private val slice: Int = 200_000) : Thread("nan-vm") {
    interface Listener {
        fun onFrame()
        fun onSerial(bytes: ByteArray)
        fun onHalt(trap: String)
    }
    @Volatile var running = true
    @Volatile var paused = false
    private val sem = Semaphore(0)
    private var haltReported = false

    /** Wake the loop (key pressed, resumed, reset). */
    fun poke() { sem.release() }
    fun shutdown() { running = false; sem.release() }
    /** Call after reset/load so a halted machine is reported again when it halts again. */
    fun rearm() { haltReported = false; sem.release() }

    private fun nap(ms: Long) { sem.tryAcquire(ms, TimeUnit.MILLISECONDS); sem.drainPermits() }

    override fun run() {
        val t0 = System.nanoTime()
        var lastDirty = vm.dirty() - 1
        var lastFrame = 0L
        while (running && vm.alive) {
            if (paused) { nap(50); continue }
            vm.setTime(((System.nanoTime() - t0) / 1_000_000L).toInt())
            val rc = vm.run(slice)
            val now = System.nanoTime()
            val d = vm.dirty()
            if (d != lastDirty && now - lastFrame >= 16_000_000L) { lastDirty = d; lastFrame = now; listener.onFrame() }
            val s = vm.serial(); if (s.isNotEmpty()) listener.onSerial(s)
            when (rc) {
                NanVm.WAIT_KEY -> { if (d != lastDirty) { lastDirty = d; listener.onFrame() }; nap(250) }
                NanVm.VSYNC -> nap(16)
                NanVm.TIME_WAIT -> nap(1)
                NanVm.HALTED -> {
                    if (d != lastDirty) { lastDirty = d; listener.onFrame() }
                    if (!haltReported) { haltReported = true; listener.onHalt(vm.trap()) }
                    nap(100)
                }
                else -> {}
            }
        }
    }
}
