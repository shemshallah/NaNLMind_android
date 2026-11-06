package org.nan0s.vm

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ScrollView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File
import java.net.URI
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Thin shell: one NanVm, one run thread, the display view, a key row and a few buttons. Everything NaN16 lives in the C core. */
class MainActivity : Activity(), NanLoop.Listener {
    private lateinit var vm: NanVm
    private lateinit var view: NanView
    private lateinit var loop: NanLoop
    private lateinit var beeper: Beeper
    private lateinit var desktopSurface: FrameLayout
    private var nshWindow: View? = null
    private var nshMaximized = false
    private var wifiStatusView: TextView? = null
    private var wifiResultsView: LinearLayout? = null
    private var wifiWindow: View? = null
    private var wifiScanAfterPermission = false
    private val log = StringBuilder()
    private val reqImage = 1
    private val reqDisk = 2
    private val reqWifi = 3
    private val reqTrain = 4
    private val reqCandidate = 5
    @Volatile private var trainingActive = false
    @Volatile private var remoteSession: N16RemoteSession? = null
    @Volatile private var remoteConnectGeneration = 0
    private var trainingState: ByteArray? = null
    private val remotePrefs get() = getSharedPreferences("n16_remote", MODE_PRIVATE)
    private val autosave get() = File(filesDir, "autosave.nan1")
    private val candidateFile get() = File(filesDir, "n16_candidate.nan1")
    private val diskFile get() = File(filesDir, "disk.nanfs")
    private val fsFile get() = File(filesDir, "nan0s_fs.img")
    private val fsKey get() = File(filesDir, "nan0s_fs.key")
    private val nshHome get() = File(filesDir, "nsh").apply { mkdirs() }
    private var diskPfd: ParcelFileDescriptor? = null

    private fun asset(): ByteArray = assets.open("nan0s.img").use { it.readBytes() }

    /** Asset -> filesDir (once). The guest needs a real, writable file behind the ATA fd. */
    private fun seedDisk() {
        try { assets.open("disk.nanfs").use { i -> diskFile.outputStream().use { o -> i.copyTo(o) } } }
        catch (e: Exception) { Log.w("nan0s", "no bundled disk.nanfs", e) }
    }

    /**
     * Attach the bundled NaNFS volume so the guest can use it over ATA PIO (the C core exposes
     * it as a file descriptor; guest writes land in app storage). SAF "Disk" replaces it with
     * any volume the user opens from Android storage. We keep the PFD and pass its borrowed fd:
     * the C core never closes descriptors, so Java owns the lifetime.
     */
    private fun attachDisk(pfd: ParcelFileDescriptor, size: Long) {
        try { diskPfd?.close() } catch (e: Exception) { }
        diskPfd = pfd
        vm.attachDiskFd(pfd.fd, size)
    }

    private fun attachDefaultDisk() {
        val image = if (fsFile.exists() && NspvImage.check(fsFile)) fsFile else diskFile
        if (image == diskFile) {
            if (!diskFile.exists()) seedDisk()
            if (!diskFile.exists()) return
        }
        try {
            attachDisk(ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_WRITE), image.length())
        } catch (e: Exception) { Log.w("nan0s", "disk attach failed", e) }
    }

    /** Create a sparse NSPV image in app storage and attach it now. */
    private fun makeFsImage(bytes: Long = NspvImage.DEFAULT_BYTES) {
        try {
            if (!NspvImage.ensure(fsFile, fsKey, bytes)) {
                Toast.makeText(this, "fs image create failed", Toast.LENGTH_SHORT).show(); return
            }
            attachDisk(ParcelFileDescriptor.open(fsFile, ParcelFileDescriptor.MODE_READ_WRITE), fsFile.length())
            val mib = bytes / (1024 * 1024)
            Toast.makeText(this, "sparse image attached (${mib} MiB)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w("nan0s", "fs image failed", e)
            Toast.makeText(this, "fs image failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun chooseFsSize() {
        val input = EditText(this).apply {
            setText("1")
            hint = "GiB"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this)
            .setTitle("nan0s_fs size (GiB)")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val gib = input.text.toString().toDoubleOrNull()?.coerceIn(0.0625, 64.0) ?: 1.0
                makeFsImage((gib * 1024.0 * 1024.0 * 1024.0).toLong())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vm = NanVm()
        val bootPrefs = getSharedPreferences("system_boot", MODE_PRIVATE)
        if (!bootPrefs.getBoolean("desktop_nsh_v1", false)) {
            autosave.delete()
            bootPrefs.edit().putBoolean("desktop_nsh_v1", true).apply()
        }
        var restored = false
        if (autosave.exists()) restored = try { vm.restoreState(autosave.readBytes()) } catch (e: Exception) { false }
        if (!restored) vm.load(asset())
        attachDefaultDisk()
        loop = NanLoop(vm, this)
        view = NanView(this, vm, { loop.poke() }, { x, y -> handleWindowChromeTap(x, y) }) { keyCode ->
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_F5 -> { showUnifiedSettings(); true }
                android.view.KeyEvent.KEYCODE_F6 -> { showWifi(); true }
                else -> false
            }
        }
        beeper = Beeper { vm.speakerHz() }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(desktopBar(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
        root.addView(functionRow(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
        desktopSurface = FrameLayout(this).apply { setBackgroundColor(Color.rgb(4, 8, 18)) }
        desktopSurface.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(desktopSurface, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(keyRow(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(toolRow(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        view.requestFocus()
        loop.start(); beeper.start()
        view.post { if (!isFinishing && !isDestroyed) showNshWindow() }
    }

    private fun showDesktopWindow(title: String, content: View, heightDp: Int = 340): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setBackgroundColor(Color.rgb(9, 13, 27))
            elevation = dp(14).toFloat()
        }
        val chrome = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(4), dp(3))
            setBackgroundColor(Color.rgb(37, 53, 91))
        }
        val caption = TextView(this).apply {
            text = title.uppercase()
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(128, 232, 218))
        }
        chrome.addView(caption, LinearLayout.LayoutParams(0, dp(32), 1f))
        val minimize = btn("−") { shell.visibility = View.GONE }
        var maximized = false
        var maximizeButton: Button? = null
        val maximize = btn("□") {
            val lp = shell.layoutParams as FrameLayout.LayoutParams
            maximized = !maximized
            if (maximized) {
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = FrameLayout.LayoutParams.MATCH_PARENT
                lp.setMargins(0, 0, 0, 0)
                lp.gravity = Gravity.TOP or Gravity.START
            } else {
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = dp(heightDp)
                lp.setMargins(dp(8), dp(8), dp(8), dp(8))
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            shell.layoutParams = lp
            maximizeButton?.text = if (maximized) "❐" else "□"
        }
        maximizeButton = maximize
        val close = btn("×") {
            desktopSurface.removeView(shell)
        }
        chrome.addView(minimize, LinearLayout.LayoutParams(dp(40), dp(32)))
        chrome.addView(maximize, LinearLayout.LayoutParams(dp(40), dp(32)))
        chrome.addView(close, LinearLayout.LayoutParams(dp(40), dp(32)))
        shell.addView(chrome)
        shell.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        desktopSurface.addView(shell, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(heightDp), Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        var rawX = 0f
        var rawY = 0f
        var originX = 0
        var originY = 0
        chrome.setOnTouchListener { _, event ->
            val lp = shell.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rawX = event.rawX; rawY = event.rawY
                    originX = lp.leftMargin; originY = lp.topMargin
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!maximized) {
                        lp.gravity = Gravity.TOP or Gravity.START
                        lp.leftMargin = (originX + event.rawX - rawX).toInt().coerceIn(0, (desktopSurface.width - shell.width).coerceAtLeast(0))
                        lp.topMargin = (originY + event.rawY - rawY).toInt().coerceIn(0, (desktopSurface.height - shell.height).coerceAtLeast(0))
                        shell.layoutParams = lp
                    }
                    true
                }
                else -> true
            }
        }
        shell.bringToFront()
        return shell
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun desktopBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(8), 0)
        setBackgroundColor(Color.rgb(22, 28, 55))
        addView(TextView(this@MainActivity).apply {
            text = "NaNLMind  /  ANDROID DESKTOP"
            setTextColor(Color.rgb(112, 231, 220)); textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(TextView(this@MainActivity).apply {
            text = "●  LOCAL"
            setTextColor(Color.rgb(238, 194, 91)); textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        })
    }

    private fun functionRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3), dp(2), dp(3), dp(2))
            setBackgroundColor(Color.rgb(31, 38, 70))
        }
        for (i in 1..12) {
            val scan = when (i) { in 1..10 -> 0x3A + i; 11 -> 0x57; else -> 0x58 }
            val key = btn("F$i") {
                when (i) {
                    2 -> { vm.keyEvent(0x102, true); vm.keyEvent(0x102, false); loop.poke(); view.requestFocus() }
                    3 -> { vm.keyEvent(0x103, true); vm.keyEvent(0x103, false); loop.poke(); view.requestFocus() }
                    5 -> showUnifiedSettings()
                    6 -> showWifi()
                    7 -> { vm.keyEvent(0x107, true); vm.keyEvent(0x107, false); loop.poke(); view.requestFocus() }
                    8 -> { vm.keyEvent(0x108, true); vm.keyEvent(0x108, false); loop.poke(); view.requestFocus() }
                    9 -> { vm.keyEvent(0x109, true); vm.keyEvent(0x109, false); loop.poke(); view.requestFocus() }
                    10 -> { vm.keyEvent(0x10A, true); vm.keyEvent(0x10A, false); loop.poke(); view.requestFocus() }
                    else -> { vm.keyEvent(scan, true); vm.keyEvent(scan, false); loop.poke(); view.requestFocus() }
                }
            }
            key.textSize = 11f
            row.addView(key, LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return row
    }

    private fun btn(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; minWidth = 0; minimumWidth = 0
        textSize = 11f
        setTextColor(Color.rgb(205, 226, 232))
        setBackgroundColor(Color.rgb(48, 56, 86))
        setPadding(dp(7), dp(4), dp(7), dp(4))
        setOnClickListener { action() }
    }
    private fun send(c: Int) { vm.keyChar(c); loop.poke(); view.requestFocus() }

    private fun keyRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val keys = listOf("Esc" to 27, "Tab" to 9, "\u2190" to NanVm.LEFT, "\u2191" to NanVm.UP, "\u2193" to NanVm.DOWN, "\u2192" to NanVm.RIGHT,
            "~" to '~'.code, "Enter" to 13, "Bksp" to 8, "Home" to NanVm.HOME, "End" to NanVm.END, "PgUp" to NanVm.PGUP, "PgDn" to NanVm.PGDN)
        for ((label, code) in keys) row.addView(btn(label) {
            send(code)
        })
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun toolRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("Kbd") {
            view.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, 0)
        })
        row.addView(btn("Open") { pick(reqImage) })
        row.addView(btn("Disk") { pick(reqDisk) })
        row.addView(btn("Fs") { chooseFsSize() })
        row.addView(btn("Reset") {
            autosave.delete(); vm.detachDisk()
            diskFile.delete(); fsFile.delete(); fsKey.delete()
            seedDisk(); vm.load(asset()); attachDefaultDisk(); loop.rearm()
        })
        row.addView(btn("Scale") { view.integerScale = !view.integerScale; view.invalidate() })
        row.addView(btn("Fit") { view.resetZoom() })
        row.addView(btn("Log") { showLog() })
        row.addView(btn("NSH") { showNshWindow() })
        row.addView(btn("NaNNet") { showWifi() })
        row.addView(btn("nannet-training") { showTrainingSuite() })
        row.addView(btn("N16T Gateway") { showRemoteGateway() })
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun hideNshWindow() {
        nshWindow?.visibility = View.GONE
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(nshWindow?.windowToken, 0)
    }

    private fun showNshWindow() {
        nshWindow?.let {
            it.visibility = View.VISIBLE
            it.bringToFront()
            it.requestFocus()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setBackgroundColor(Color.rgb(9, 13, 27))
            elevation = dp(12).toFloat()
        }
        val chrome = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(4), dp(3))
            setBackgroundColor(Color.rgb(37, 53, 91))
        }
        val caption = TextView(this).apply {
            text = "NSH  /  NaN16 COMMAND SHELL"
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(128, 232, 218))
        }
        chrome.addView(caption, LinearLayout.LayoutParams(0, dp(34), 1f))
        val minimize = btn("−") { hideNshWindow() }
        val maximize = btn("□") { toggleNshMaximize(null) }
        val close = btn("×") { hideNshWindow() }
        chrome.addView(minimize, LinearLayout.LayoutParams(dp(42), dp(34)))
        chrome.addView(maximize, LinearLayout.LayoutParams(dp(42), dp(34)))
        chrome.addView(close, LinearLayout.LayoutParams(dp(42), dp(34)))
        panel.addView(chrome)
        val output = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(180, 225, 218))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            text = "NaNLMind NSH  •  NaN16 desktop shell\nType --help for commands. Files are sandboxed in /nsh.\n\n" + nshHelp()
            setBackgroundColor(Color.rgb(3, 7, 14))
        }
        val scroll = ScrollView(this).apply { addView(output) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val input = EditText(this).apply {
            isSingleLine = true
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            hint = "nsh> command"
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(30, 39, 61))
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        desktopSurface.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(360), Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        nshWindow = panel
        nshMaximized = false

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        chrome.setOnTouchListener { _, event ->
            val lp = panel.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.leftMargin
                    startY = lp.topMargin
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!nshMaximized) {
                        lp.gravity = Gravity.TOP or Gravity.START
                        lp.leftMargin = (startX + event.rawX - downX).toInt().coerceIn(0, (desktopSurface.width - panel.width).coerceAtLeast(0))
                        lp.topMargin = (startY + event.rawY - downY).toInt().coerceIn(0, (desktopSurface.height - panel.height).coerceAtLeast(0))
                        panel.layoutParams = lp
                    }
                    true
                }
                else -> true
            }
        }

        fun append(line: String) {
            output.append(line + "\n")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        fun runCommand() {
            val command = input.text.toString().trim()
            if (command.isEmpty()) return
            input.text.clear()
            append("nsh> " + command)
            val parts = command.split(Regex("\\s+"), limit = 3)
            val verb = parts[0].lowercase()
            val arg = parts.getOrNull(1).orEmpty()
            val rest = parts.getOrNull(2).orEmpty()
            when (verb) {
                "--help", "-h", "help" -> append(nshHelp())
                "pwd" -> append("/nsh")
                "ls", "dir", "files" -> append(nshHome.listFiles()?.sortedBy { it.name }?.joinToString("\n") {
                    if (it.isDirectory) it.name + "/" else it.name + "  (" + it.length() + " bytes)"
                }?.ifBlank { "(empty /nsh)" } ?: "(empty /nsh)")
                "clear", "cls" -> output.text = "NaNLMind NSH  •  /nsh\n"
                "write", "touch" -> {
                    if (arg.isBlank()) append("usage: write <filename> [text]")
                    else if (rest.isNotEmpty()) append(nshWrite(arg, rest))
                    else {
                        val editor = EditText(this).apply { minLines = 5; gravity = Gravity.TOP; hint = "Enter file contents" }
                        AlertDialog.Builder(this).setTitle("Write /nsh/" + arg).setView(editor)
                            .setPositiveButton("Save") { _, _ -> append(nshWrite(arg, editor.text.toString())) }
                            .setNegativeButton("Cancel", null).show()
                    }
                }
                "cat", "type", "read" -> append(if (arg.isBlank()) "usage: cat <filename>" else try { nshFile(arg).readText(Charsets.UTF_8) } catch (e: Exception) { "READ NO: " + e.message })
                "mkdir" -> append(if (arg.isBlank()) "usage: mkdir <directory>" else try { val f = nshFile(arg); if (f.mkdirs() || f.isDirectory) "OK: " + arg else "MKDIR NO" } catch (e: Exception) { "MKDIR NO: " + e.message })
                "rm", "del" -> append(if (arg.isBlank()) "usage: rm <filename>" else try { if (nshFile(arg).delete()) "DELETED: " + arg else "DELETE NO" } catch (e: Exception) { "DELETE NO: " + e.message })
                "nannet-training", "training" -> showTrainingSuite()
                "wifi", "nannet" -> showWifi()
                "n16t", "gateway" -> showRemoteGateway()
                "browser", "b" -> {
                    val url = arg.ifBlank { "https://deeprepo-qtcl-9c8fea15.apps.mistral.run/portal" }
                    val target = if (url.startsWith("https://") || url.startsWith("http://")) url else "https://" + url
                    append("Opening isolated Android browser: " + target)
                    showBrowserWindow(target)
                }
                "settings" -> showUnifiedSettings()
                "open", "load" -> {
                    if (arg.isBlank()) { hideNshWindow(); pick(reqImage) }
                    else try {
                        val kind = vm.load(nshFile(arg).readBytes())
                        loop.rearm()
                        append(if (kind != null) "Loaded NaN16 program: " + kind else "OPEN NO: not a NaN16 program")
                    } catch (e: Exception) { append("OPEN NO: " + e.message) }
                }
                "disk" -> pick(reqDisk)
                "fs" -> chooseFsSize()
                "log" -> showLog()
                "fit" -> { view.resetZoom(); append("Display fitted to desktop.") }
                "scale" -> { view.integerScale = !view.integerScale; view.invalidate(); append("Integer scaling: " + view.integerScale) }
                "system" -> { vm.keyEvent(0x107, true); vm.keyEvent(0x107, false); loop.poke() }
                "run" -> when (arg.lowercase()) {
                    "lattice" -> { vm.keyEvent(0x108, true); vm.keyEvent(0x108, false); loop.poke(); append("Opened Lattice.") }
                    "monitor" -> { vm.keyEvent(0x109, true); vm.keyEvent(0x109, false); loop.poke(); append("Opened Monitor.") }
                    "hex" -> { vm.keyEvent(0x10A, true); vm.keyEvent(0x10A, false); loop.poke(); append("Opened Hex.") }
                    "settings" -> showUnifiedSettings()
                    "nannet", "wifi" -> showWifi()
                    "training", "nannet-training" -> showTrainingSuite()
                    "gateway", "n16t" -> showRemoteGateway()
                    "browser" -> showBrowserWindow("https://deeprepo-qtcl-9c8fea15.apps.mistral.run/portal")
                    "system" -> { vm.keyEvent(0x107, true); vm.keyEvent(0x107, false); loop.poke() }
                    else -> append("usage: run lattice|monitor|hex|settings|nannet|system|training|gateway|browser")
                }
                "apps" -> {
                    val apps = arrayOf("Lattice", "Monitor", "Hex viewer", "Settings", "NaNNet", "System utilities", "NaNNet training", "N16T gateway", "Browser")
                    AlertDialog.Builder(this).setTitle("Desktop applications").setItems(apps) { _, which ->
                        when (which) {
                            0 -> { vm.keyEvent(0x108, true); vm.keyEvent(0x108, false); loop.poke() }
                            1 -> { vm.keyEvent(0x109, true); vm.keyEvent(0x109, false); loop.poke() }
                            2 -> { vm.keyEvent(0x10A, true); vm.keyEvent(0x10A, false); loop.poke() }
                            3 -> showUnifiedSettings()
                            4 -> showWifi()
                            5 -> { vm.keyEvent(0x107, true); vm.keyEvent(0x107, false); loop.poke() }
                            6 -> showTrainingSuite()
                            7 -> showRemoteGateway()
                            8 -> showBrowserWindow("https://deeprepo-qtcl-9c8fea15.apps.mistral.run/portal")
                        }
                    }.setNegativeButton("Close", null).show()
                }
                "windows", "wm" -> append("Guest windows: Lattice, Monitor, Hex. Host windows: NSH, Settings, NaNNet, Training, N16T.")
                "version", "ver" -> append("NaNLMind_ANDROID • NaN16 guest • Android host services")
                "desktop" -> append("Desktop remains active behind this NSH window.")
                "reset" -> AlertDialog.Builder(this).setTitle("Reset desktop")
                    .setMessage("Restore the bundled NaN16 desktop and discard the current unsaved VM session?")
                    .setPositiveButton("Reset") { _, _ ->
                        autosave.delete(); vm.detachDisk(); diskFile.delete(); fsFile.delete(); fsKey.delete()
                        seedDisk(); vm.load(asset()); attachDefaultDisk(); loop.rearm()
                    }.setNegativeButton("Cancel", null).show()
                "exit", "quit" -> hideNshWindow()
                else -> append("command not found: " + verb + " (type --help)")
            }
        }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_NULL) { runCommand(); true } else false
        }
        input.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && event.action == android.view.KeyEvent.ACTION_UP) {
                hideNshWindow()
                true
            } else false
        }
        input.requestFocus()
        input.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun toggleNshMaximize(maximize: Boolean?) {
        val panel = nshWindow ?: return
        val lp = panel.layoutParams as? FrameLayout.LayoutParams ?: return
        nshMaximized = maximize ?: !nshMaximized
        if (nshMaximized) {
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT
            lp.setMargins(0, 0, 0, 0)
            lp.gravity = Gravity.TOP or Gravity.START
        } else {
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            lp.height = dp(360)
            lp.setMargins(dp(8), dp(8), dp(8), dp(8))
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        panel.layoutParams = lp
    }

    private fun nshHelp():String =
        "COMMANDS\n--help, help       Show commands\nls, dir            List /nsh files\npwd                Current directory\nwrite NAME [TEXT]  Create/overwrite file\ncat NAME           Read file\nmkdir NAME         Create directory\nrm NAME            Delete file\nclear              Clear output\nnannet-training    Open training interface\nwifi, nannet       Open Wi-Fi/cellular manager\nn16t, gateway      Open HTTPS gateway\nbrowser [URL], b   Open Android browser\nsettings           Unified desktop settings\nopen FILE          Load a NaN16 program\ndisk               Attach a disk image\nfs                 Create sparse filesystem image\nlog                View serial log\nfit, scale         Adjust desktop viewport\nsystem             Open system utilities\nrun APP            Launch a desktop application\napps               Open application menu\nwindows            List desktop apps\nfiles              List files in /nsh\nreset              Restore bundled desktop\nversion            Show system version\nexit               Close NSH; desktop stays running"
    private fun nshFile(name:String):File{
        require(name.isNotBlank()&&!name.startsWith("/")&&!name.contains("\\"))
        val base=nshHome.canonicalFile;val file=File(base,name).canonicalFile
        require(file.path==base.path||file.path.startsWith(base.path+File.separator)){"path escapes /nsh"};return file
    }
    private fun nshWrite(name:String,contents:String):String=try{
        val f=nshFile(name)
        val parent=f.parentFile ?: throw IllegalArgumentException("missing parent directory")
        if(!parent.exists() && !parent.mkdirs()) throw java.io.IOException("cannot create /nsh directory")
        val tmp=File(parent,f.name+".nsh-tmp")
        tmp.writeText(contents,Charsets.UTF_8)
        if(f.exists() && !f.delete()) { tmp.delete(); throw java.io.IOException("cannot replace existing file") }
        if(!tmp.renameTo(f)) { tmp.copyTo(f,overwrite=true); tmp.delete() }
        "WRITE OK: /nsh/"+name+" ("+f.length()+" bytes)"
    }catch(e:Exception){"WRITE NO: "+(e.message ?: e.javaClass.simpleName)}
    /**
     * Delegate browsing to Androids isolated browser process. The old embedded WebView
     * could stall the guest UI while rendering script-heavy pages.
     */
    private fun showBrowserWindow(initialUrl: String) {
        val uri = try { URI(initialUrl.trim()) } catch (e: Exception) {
            Toast.makeText(this, "Invalid browser URL", Toast.LENGTH_LONG).show()
            return
        }
        if ((uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.fragment != null) {
            Toast.makeText(this, "Use an HTTP(S) URL without credentials or fragments", Toast.LENGTH_LONG).show()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "ISOLATED BROWSER LAUNCHER\n\n" + uri.toString() + "\n\nThe page opens in Android's separate browser process. It cannot block the NaN16 VM or desktop rendering thread."
            setTextColor(Color.rgb(180, 224, 220))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(12))
        })
        panel.addView(btn("OPEN IN ANDROID BROWSER") {
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri.toString()))
                startActivity(Intent.createChooser(intent, "Open with browser"))
            } catch (e: Exception) {
                Toast.makeText(this, "No browser available: " + e.message, Toast.LENGTH_LONG).show()
            }
        })
        showDesktopWindow("Browser", panel, 260)
    }

    private fun showUnifiedSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "One settings surface for desktop display, NaNNet, training, and N16T."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        })
        panel.addView(btn("DISPLAY / GUEST SETTINGS") {
            vm.keyEvent(0x105, true); vm.keyEvent(0x105, false); loop.poke()
        })
        panel.addView(btn("NANNET / WI-FI / CELLULAR") { showWifi() })
        panel.addView(btn("NANNET TRAINING") { showTrainingSuite() })
        panel.addView(btn("N16T GATEWAY") { showRemoteGateway() })
        panel.addView(btn("FIT DESKTOP VIEWPORT") { view.resetZoom(); view.invalidate() })
        panel.addView(btn("TOGGLE INTEGER SCALING") {
            view.integerScale = !view.integerScale
            view.invalidate()
        })
        showDesktopWindow("NaNLMind Desktop Settings", panel, 350)
    }

    private fun showRemoteGateway() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "N16T binary frames use POST /n16t. /portal is the human-facing web page. A successful HELLO/CAPABILITIES exchange does not mean remote training or merge is deployed."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        })
        val input = EditText(this).apply {
            hint = "https://deeprepo-qtcl-9c8fea15.apps.mistral.run/n16t"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(remotePrefs.getString("endpoint", "https://deeprepo-qtcl-9c8fea15.apps.mistral.run/n16t") ?: "https://deeprepo-qtcl-9c8fea15.apps.mistral.run/n16t")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, -2))
        panel.addView(btn("CONNECT / HELLO + CAPABILITIES") {
            connectRemoteGateway(input.text.toString().trim())
        })
        panel.addView(btn("DISCONNECT SESSION") { disconnectRemoteGateway() })
        panel.addView(btn("OPEN HUMAN PORTAL") {
            showBrowserWindow("https://deeprepo-qtcl-9c8fea15.apps.mistral.run/portal")
        })
        showDesktopWindow("N16T Gateway", panel, 330)
    }

    private fun connectRemoteGateway(raw: String) {
        val normalized = raw.trim().let { if (it.startsWith("https://", true)) it else "https://$it" }
        val entered = try { URI(normalized) } catch (e: Exception) {
            Toast.makeText(this, "Invalid gateway URL", Toast.LENGTH_LONG).show()
            return
        }
        if (entered.scheme != "https" || entered.host.isNullOrBlank() ||
            entered.userInfo != null || entered.fragment != null) {
            Toast.makeText(this, "Use an HTTPS URL without embedded credentials or fragments", Toast.LENGTH_LONG).show()
            return
        }
        // The Koyeb service accepts the N16T binary handshake on /portal and /n16t.
        // Keep the requested portal URL visible and send it unchanged.
        val endpoint = if (entered.path.trimEnd('/') == "/portal") {
            URI("https", entered.userInfo, entered.host, entered.port, "/n16t", entered.query, null)
        } else entered

        val generation = remoteConnectGeneration + 1
        remoteConnectGeneration = generation
        remoteSession?.close()
        remoteSession = null
        val session = try { N16RemoteSession(this, endpoint) } catch (e: Exception) {
            Toast.makeText(this, "Could not initialize NaN16 protocol guest: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        remotePrefs.edit().putString("endpoint", endpoint.toString()).apply()
        Toast.makeText(this, "Connecting to N16T gateway…", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                // HELLO's two-byte route prefix selects the Runner portal (point 1).
                val result = session.exchange(1, byteArrayOf(1, 0))
                runOnUiThread {
                    if (generation != remoteConnectGeneration || isFinishing || isDestroyed) {
                        session.close()
                    } else {
                        remoteSession = session
                        Toast.makeText(
                            this,
                            "N16T HELLO/CAPABILITIES accepted; route ${result.route.gateway}/${result.route.interfaceId}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                session.close()
                runOnUiThread {
                    if (generation == remoteConnectGeneration) {
                        Toast.makeText(this, "N16T gateway handshake failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private fun disconnectRemoteGateway() {
        remoteConnectGeneration++
        val active = remoteSession
        remoteSession = null
        if (active == null) {
            Toast.makeText(this, "No active N16T session", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try { active.closeRemote() } catch (_: Exception) { }
            active.close()
            runOnUiThread {
                Toast.makeText(this, "N16T session disconnected", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun showTrainingSuite() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "N16TXT corpus training. The NaN16 guest validates UTF-8 records and builds an isolated candidate. The active desktop universe is restored after the run. Candidate merge and remote corpus transfer are not yet enabled."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        })
        panel.addView(btn("CHOOSE UTF-8 TXT CORPUS") {
            if (prepareTraining()) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                }
                startActivityForResult(intent, reqTrain)
            }
        })
        panel.addView(btn("RUN BUILT-IN SAMPLE CORPUS") {
            if (prepareTraining()) Thread { runTraining(null) }.start()
        })
        panel.addView(btn("EXPORT LOCAL CANDIDATE") {
            if (!candidateFile.isFile) {
                Toast.makeText(this, "Run a successful corpus first", Toast.LENGTH_SHORT).show()
            } else {
                val export = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, "n16_candidate.nan1")
                }
                startActivityForResult(export, reqCandidate)
            }
        })
        panel.addView(btn("TRAINING STATUS / LIMITS") {
            AlertDialog.Builder(this).setTitle("Training boundary")
                .setMessage("The current guest builds a temporary byte-frequency/byte-bigram candidate. It is not yet a neural optimizer, remote runner job, or automatic merge. Corpus data never executes as instructions.")
                .setPositiveButton("OK", null).show()
        })
        showDesktopWindow("NaNNet Training", panel, 360)
    }

    private fun prepareTraining(): Boolean {
        if (trainingActive) return false
        loop.paused = true
        val saved = vm.saveState()
        if (saved == null) {
            loop.paused = false
            loop.rearm()
            Toast.makeText(this, "Could not snapshot the active universe", Toast.LENGTH_LONG).show()
            return false
        }
        trainingState = saved
        // Never leave an older candidate exportable after a new run is started.
        candidateFile.delete()
        trainingActive = true
        return true
    }

    private fun readCorpus(uri: android.net.Uri): ByteArray {
        val out = ByteArrayOutputStream()
        contentResolver.openInputStream(uri)!!.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > 1_048_576) throw IllegalArgumentException("TXT corpus exceeds 1 MiB")
                out.write(buffer, 0, n)
            }
        }
        val raw = out.toByteArray()
        if (raw.any { it.toInt() == 0 }) throw IllegalArgumentException("TXT corpus contains a NUL byte")
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(raw))
        return raw
    }

    private fun runTraining(raw: ByteArray?) {
        var result = "Training failed"
        try {
            val guest = assets.open("n16_train.nan").use { it.readBytes() }
            if (vm.load(guest) == null) throw IllegalStateException("NaN16 training guest did not load")
            val sample = "NaN16 owns the corpus codec.\nRunner and Android share one trainer.\nA temporary candidate is built and the active universe is restored afterward.\n".toByteArray(Charsets.UTF_8)
            val corpus = raw ?: sample
            val header = "N16TXT/1\n".toByteArray(Charsets.US_ASCII)
            val crlfHeader = "N16TXT/1\r\n".toByteArray(Charsets.US_ASCII)
            val hasLfHeader = corpus.size >= header.size && corpus.copyOfRange(0, header.size).contentEquals(header)
            val hasCrlfHeader = corpus.size >= crlfHeader.size && corpus.copyOfRange(0, crlfHeader.size).contentEquals(crlfHeader)
            val bodyOffset = if (hasCrlfHeader) crlfHeader.size else if (hasLfHeader) header.size else 0
            val framed = header + corpus.copyOfRange(bodyOffset, corpus.size)
            if (!vm.writeBytes(49152, framed + byteArrayOf(0))) {
                throw IllegalStateException("Could not transfer corpus into NaN16 memory")
            }
            var rc = NanVm.BUDGET
            var rounds = 0
            while (rc == NanVm.BUDGET && rounds < 5000) {
                rc = vm.run(100_000)
                rounds++
            }
            val transcript = String(vm.serial(), Charsets.UTF_8).trim()
            if (rc == NanVm.HALTED && transcript.contains("N16 TRAIN PASS")) {
                val candidate = vm.saveState()
                    ?: throw IllegalStateException("Could not snapshot NaN16 candidate")
                candidateFile.writeBytes(candidate)
            }
            result = when {
                rc != NanVm.HALTED -> "Training stopped before completion (VM result $rc)"
                transcript.contains("N16 TRAIN PASS") -> "$transcript\nTemporary byte-bigram candidate saved locally; active universe restored."
                transcript.contains("N16 TRAIN REJECT") -> "$transcript\nCorpus rejected; active universe restored unchanged."
                else -> "Training guest halted without a success marker: $transcript"
            }
        } catch (e: Exception) {
            result = "Training failed: ${e.message}"
        }
        finishTraining(result)
    }

    private fun finishTraining(message: String) {
        val saved = trainingState
        trainingState = null
        trainingActive = false
        val restored = saved != null && vm.restoreState(saved)
        loop.paused = false
        loop.rearm()
        runOnUiThread {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.rgb(17, 22, 42))
            }
            panel.addView(TextView(this).apply {
                text = if (restored) message else "$message\nWarning: the previous VM snapshot could not be restored."
                setTextColor(Color.rgb(180, 224, 220))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
            })
            showDesktopWindow("NaNNet Training Result", panel, 300)
        }
    }

    // ---- real WiFi: status, scan, connect request ----
    private fun showWifi() {
        wifiWindow?.let { desktopSurface.removeView(it) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        val status = TextView(this).apply {
            text = WifiBridge.status(this@MainActivity)
            setTextColor(Color.rgb(180, 224, 220))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(7, 12, 24))
        }
        wifiStatusView = status
        panel.addView(TextView(this).apply {
            text = "NANNET  /  ANDROID NETWORK CONTROL"
            setTextColor(Color.rgb(115, 229, 214))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
        })
        panel.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        panel.addView(TextView(this).apply {
            text = "Host-managed Wi-Fi + LTE/5G. The NaN16 guest does not need a PCI radio. Scan results appear below."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(0, dp(8), 0, dp(8))
        })
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        wifiResultsView = results
        results.addView(TextView(this).apply {
            text = "No scan performed. Select SCAN NETWORKS."
            setTextColor(Color.rgb(180, 190, 205))
            textSize = 12f
        })
        val scroll = ScrollView(this).apply { addView(results) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(btn("SCAN NETWORKS") { scanWifi() })
        panel.addView(btn("ANDROID WI-FI SETTINGS") { WifiBridge.openSettings(this) })
        panel.addView(btn("MOBILE DATA / 5G SETTINGS") { WifiBridge.openMobileSettings(this) })
        panel.addView(btn("REFRESH ROUTE STATUS") { status.text = WifiBridge.status(this) })
        wifiWindow = showDesktopWindow("NaNNet Network Manager", panel, 430)

    }

    /** The guest window chrome is rendered by NaN16; translate taps on its close/enlarge
     *  titlebar buttons into the same keyboard actions used by ESC/F3. */
    private fun handleWindowChromeTap(x: Int, y: Int): Boolean {
        if (!vm.graphics() || x < 0 || y < 0) return false
        val width = vm.width()
        val height = vm.height()
        if (width <= 0 || height <= 0 || x >= width || y >= height) return false
        val pixels = IntArray(width * height)
        if (!vm.snapshotGfx(pixels)) return false
        var red = 0
        var green = 0
        for (yy in (y - 6).coerceAtLeast(0)..(y + 6).coerceAtMost(height - 1)) {
            for (xx in (x - 6).coerceAtLeast(0)..(x + 6).coerceAtMost(width - 1)) {
                val c = pixels[yy * width + xx]
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                if (r > 100 && r > g * 1.35 && r > b * 1.35) red++
                if (g > 90 && g > r * 1.25 && g > b * 1.15) green++
            }
        }
        when {
            red >= 5 -> { vm.keyChar(27); loop.poke(); return true }
            green >= 5 -> { vm.keyEvent(0x103, true); vm.keyEvent(0x103, false); loop.poke(); return true }
        }
        return false
    }

    private fun scanWifi() {
        if (!WifiBridge.hasPermission(this)) {
            wifiScanAfterPermission = true
            wifiStatusView?.text = WifiBridge.status(this) + "\n\nRequesting Android Wi-Fi scan permission…"
            WifiBridge.requestPermission(this, reqWifi)
            return
        }
        wifiResultsView?.removeAllViews()
        wifiResultsView?.addView(TextView(this).apply {
            text = "Scanning Android Wi-Fi cache/radio…"
            setTextColor(Color.rgb(180, 190, 205))
            textSize = 12f
        })
        WifiBridge.scan(this) { aps ->
            runOnUiThread {
                wifiStatusView?.text = WifiBridge.status(this)
                val target = wifiResultsView ?: return@runOnUiThread
                target.removeAllViews()
                if (aps.isEmpty()) {
                    target.addView(TextView(this).apply {
                        text = "No visible scan results. Android may throttle active scans or hide SSIDs. Check Location and Wi-Fi settings; the active Wi-Fi/cellular route remains usable."
                        setTextColor(Color.rgb(235, 190, 115))
                        textSize = 12f
                        setPadding(dp(6), dp(8), dp(6), dp(8))
                    })
                    return@runOnUiThread
                }
                target.addView(TextView(this).apply {
                    text = "FOUND \${aps.size} NETWORK(S)  •  TAP TO JOIN"
                    setTextColor(Color.rgb(115, 229, 214))
                    textSize = 12f
                })
                aps.forEach { ap ->
                    target.addView(btn("\${ap.ssid}   \${ap.level} dBm   \${ap.security}") {
                        joinWifi(ap)
                    }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(3) })
                }
            }
        }
    }

    private fun joinWifi(ap: WifiBridge.Ap) {
        if (ap.security == "connected") {
            Toast.makeText(this, "Already connected to " + ap.ssid, Toast.LENGTH_SHORT).show()
            return
        }
        if (ap.security == "open") {
            Toast.makeText(this, WifiBridge.join(this, ap.ssid, ""), Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "password"
        }
        AlertDialog.Builder(this)
            .setTitle(ap.ssid)
            .setMessage("WPA password")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                Toast.makeText(this, WifiBridge.join(this, ap.ssid, input.text.toString()), Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != reqWifi) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "NaNNet scan permission granted.", Toast.LENGTH_SHORT).show()
            if (wifiScanAfterPermission) {
                wifiScanAfterPermission = false
                scanWifi()
            }
        } else {
            wifiScanAfterPermission = false
            Toast.makeText(this, "Wi-Fi scan permission denied; active route status remains available.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pick(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        startActivityForResult(i, req)
    }

    @Deprecated("kept to avoid an androidx dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqTrain) {
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) {
                finishTraining("Training cancelled")
                return
            }
            Thread {
                try { runTraining(readCorpus(uri)) }
                catch (e: Exception) { finishTraining("Training failed: ${e.message}") }
            }.start()
            return
        }
        if (requestCode == reqCandidate) {
            val uri = data?.data ?: return
            if (resultCode != RESULT_OK) return
            Thread {
                val copied = try {
                    val output = contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Could not open export destination")
                    output.use { candidateFile.inputStream().use { input -> input.copyTo(it) } }
                    true
                } catch (_: Exception) { false }
                runOnUiThread {
                    Toast.makeText(this, if (copied) "Candidate exported" else "Candidate export failed", Toast.LENGTH_LONG).show()
                }
            }.start()
            return
        }
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        Thread {
            try {
                if (requestCode == reqImage) {
                    val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val kind = vm.load(bytes)
                    runOnUiThread { Toast.makeText(this, if (kind != null) "loaded: $kind" else "not a NaN16 program", Toast.LENGTH_SHORT).show() }
                    loop.rearm()
                } else {
                    val pfd = contentResolver.openFileDescriptor(uri, "rw")!!
                    val size = pfd.statSize
                    attachDisk(pfd, size)
                    runOnUiThread { Toast.makeText(this, "disk attached ($size bytes)", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showLog() {
        val text = synchronized(log) { log.toString() }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(3, 7, 14))
        }
        val body = TextView(this).apply {
            text = if (text.isEmpty()) "(empty)" else text
            setTextColor(Color.rgb(180, 225, 218))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
        }
        panel.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(btn("COPY LOG") {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("nan0s log", text))
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
        })
        showDesktopWindow("COM1 / Serial Log", panel, 360)
    }

    // ---- NanLoop.Listener (called on the run thread) ----
    override fun onFrame() { view.postInvalidate() }
    override fun onSerial(bytes: ByteArray) {
        synchronized(log) { log.append(String(bytes, Charsets.ISO_8859_1)); if (log.length > 65536) log.delete(0, log.length - 49152) }
    }
    override fun onHalt(trap: String) {
        runOnUiThread { Toast.makeText(this, if (trap.isEmpty()) "guest halted" else "guest halted: $trap", Toast.LENGTH_SHORT).show() }
    }

    override fun onPause() {
        super.onPause()
        loop.paused = true; beeper.muted = true
        if (!trainingActive) vm.saveState()?.let { try { autosave.writeBytes(it) } catch (e: Exception) { } }
    }
    override fun onResume() {
        super.onResume()
        loop.paused = trainingActive; beeper.muted = false; loop.poke(); view.invalidate()
    }
    override fun onDestroy() {
        super.onDestroy()
        remoteConnectGeneration++
        remoteSession?.close()
        remoteSession = null
        loop.shutdown(); beeper.shutdown()
        try { loop.join(1000) } catch (e: InterruptedException) { }
        vm.detachDisk()
        try { diskPfd?.close() } catch (e: Exception) { }
        vm.close()
    }
}
