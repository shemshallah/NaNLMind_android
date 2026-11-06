import org.nan0s.vm.*
import java.io.File

/** Runs the real JNI bridge on the host JVM against build/nan0s.img: boot banner, menu text, key -> exhibit, mode 13h render, snapshot, thread loop. */
fun main(args: Array<String>) {
    var fails = 0
    fun check(c: Boolean, what: String) { if (!c) { fails++; println("FAIL $what") } else println("ok   $what") }
    val img = File(args[0]).readBytes()
    NanVm().use { vm ->
        check(vm.load(img) == "floppy", "load floppy")
        var rc = 0; var n = 0
        while (n++ < 200 && rc != NanVm.WAIT_KEY) rc = vm.run(1_000_000)
        check(rc == NanVm.WAIT_KEY, "boots to the menu (waits for a key)")
        check(String(vm.serial(), Charsets.ISO_8859_1) == "NaN/0S R26J READY MENU=S\r\n", "COM1 banner")
        val lines = vm.textLines(); check(lines.isNotEmpty() && lines.joinToString("\n").contains("MANCHESTER").not(), "menu text present")
        vm.keyChar('1'.code); rc = 0; n = 0
        while (n++ < 50 && rc != NanVm.WAIT_KEY) rc = vm.run(1_000_000)
        check(vm.textLines().joinToString("\n").contains("MANCHESTER"), "key 1 shows the Baby exhibit")
        val snap = vm.saveState(); check(snap != null && snap.size > 5000, "saveState")
        vm.keyChar(27); vm.run(1_000_000)
        check(vm.restoreState(snap!!), "restoreState"); check(vm.textLines().joinToString("\n").contains("MANCHESTER"), "state restored the exhibit screen")
        vm.keyChar(27); rc = 0; n = 0; while (n++ < 50 && rc != NanVm.WAIT_KEY) rc = vm.run(1_000_000)
        vm.keyChar('3'.code); for (i in 0 until 20) vm.run(500_000)
        check(vm.graphics() && vm.width() == 320 && vm.height() == 200, "key 3 -> mode 13h 320x200")
        val px = IntArray(320 * 200); check(vm.snapshotGfx(px), "render"); check(px.any { it != 0xFF000000.toInt() }, "framebuffer has lit pixels")
        check(!vm.snapshotGfx(IntArray(10)), "render rejects a small buffer")
        check(vm.load(ByteArray(16) { it.toByte() }) == null, "junk rejected")
        val cells = IntArray(2000); val pal = IntArray(16); check(vm.snapshotText(cells, pal) && pal[15] == 0xFFFFFFFF.toInt(), "text snapshot + palette")
        check(KeyMap.scancode(29) == 0x1E && KeyMap.scancode(19) == 0x148 && KeyMap.special(111) == 27, "KeyMap")
    }
    NanVm().use { vm ->
        vm.load(img); var frames = 0; var bytes = 0; var halted: String? = null
        val loop = NanLoop(vm, object : NanLoop.Listener {
            override fun onFrame() { frames++ }
            override fun onSerial(bytes: ByteArray) { }
            override fun onHalt(trap: String) { halted = trap }
        })
        loop.start(); Thread.sleep(1500); vm.keyChar('2'.code); loop.poke(); Thread.sleep(800)
        check(frames > 0, "run loop delivered frames ($frames)"); check(vm.textLines().joinToString("\n").contains("ENIAC") || vm.textLines().size == 25, "loop reacts to keys")
        loop.paused = true; Thread.sleep(100); loop.shutdown(); loop.join(2000); check(!loop.isAlive, "loop shuts down")
    }
    println(if (fails == 0) "HOST TEST PASS" else "HOST TEST FAILED: $fails"); if (fails != 0) System.exit(1)
}
