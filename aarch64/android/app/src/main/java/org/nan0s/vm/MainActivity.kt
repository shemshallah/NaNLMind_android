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
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction

/** Android host boundary: one persistent NaN16 guest, one desktop surface, and native OS services. Guest semantics stay in NaN16 cartridges. */
class MainActivity : Activity(), NanLoop.Listener {
    private lateinit var vm: NanVm
    private lateinit var view: NanView
    private lateinit var loop: NanLoop
    private lateinit var beeper: Beeper
    private lateinit var desktopSurface: FrameLayout
    private lateinit var windowDock: LinearLayout
    private val desktopWindows = linkedMapOf<View, String>()
    private var wifiStatusView: TextView? = null
    private var wifiResultsView: LinearLayout? = null
    private var wifiWindow: View? = null
    private var wifiScanAfterPermission = false
    private var defaultNetworkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val log = StringBuilder()
    private val reqImage = 1
    private val reqDisk = 2
    private val reqWifi = 3
    private val reqTrain = 4
    private val reqCandidate = 5
    @Volatile private var trainingActive = false
    @Volatile private var remoteSession: N16RemoteSession? = null
    private var gatewayStatusView: TextView? = null
    @Volatile private var remoteConnectGeneration = 0
    private var trainingState: ByteArray? = null
    private val remotePrefs get() = getSharedPreferences("n16_remote", MODE_PRIVATE)
    private val mathAdmission by lazy { N16MathAdmission(this) }

    /** Run candidate/rule and typed-leaf admission in the NaN16 guest before NMP submission. */
    internal fun runMathAdmission(
        candidate: IntArray,
        rule: IntArray,
        left: IntArray,
        right: IntArray,
        contract: IntArray,
        availableCapabilities: Int,
        leftPayload: IntArray,
        rightPayload: IntArray,
        output: IntArray
    ): N16MathAdmission.Decision = mathAdmission.evaluate(
        candidate, rule, left, right, contract, availableCapabilities,
        leftPayload, rightPayload, output
    )
    private val autosave get() = File(filesDir, "autosave.nan1")
    private val candidateFile get() = File(filesDir, "n16_candidate.nan1")
    private val diskFile get() = File(filesDir, "disk.nanfs")
    private val fsFile get() = File(filesDir, "nan0s_fs.img")
    private val fsKey get() = File(filesDir, "nan0s_fs.key")
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
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "Create a sparse NaNFS image in app-private storage and attach it to the guest."
            setTextColor(Color.LTGRAY)
            textSize = 12f
        })
        val input = EditText(this).apply {
            setText("1")
            hint = "GiB"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        panel.addView(btn("CREATE AND ATTACH") {
            val gib = input.text.toString().toDoubleOrNull()?.coerceIn(0.0625, 64.0) ?: 1.0
            makeFsImage((gib * 1024.0 * 1024.0 * 1024.0).toLong())
        })
        showDesktopWindow("NaNFS Image Utility", panel, 220)
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
        view = NanView(this, vm, { loop.poke() }, onGuestPointer = { x, y, action ->
            vm.pointerEvent(x, y, action)
            loop.poke()
        }, onHostKey = { keyCode ->
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_F6 -> { showWifi(); true }
                else -> false
            }
        })
        beeper = Beeper { vm.speakerHz() }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        desktopSurface = FrameLayout(this).apply { setBackgroundColor(Color.rgb(4, 8, 18)) }
        desktopSurface.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        windowDock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(3), dp(5), dp(3))
            setBackgroundColor(Color.rgb(17, 22, 42))
            visibility = View.GONE
        }
        desktopSurface.addView(windowDock, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(42), Gravity.BOTTOM
        ))
        root.addView(desktopSurface, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(keyRow(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        watchDefaultNetwork()
        view.requestFocus()
        loop.start(); beeper.start()
    }

    private fun showDesktopWindow(title: String, content: View, heightDp: Int = 340): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setBackgroundColor(Color.rgb(192, 192, 192))
            elevation = dp(14).toFloat()
        }
        val chrome = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(4), dp(3))
            setBackgroundColor(Color.rgb(0, 0, 128))
        }
        val caption = TextView(this).apply {
            text = title.uppercase()
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.WHITE)
        }
        chrome.addView(caption, LinearLayout.LayoutParams(0, dp(32), 1f))
        val initialWidth = minOf(dp(760), (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(280)))
        val minimize = btn("−") {
            shell.visibility = View.GONE
            updateWindowDock()
        }
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
                lp.width = initialWidth
                lp.height = dp(heightDp)
                lp.setMargins(dp(8), dp(8), dp(8), dp(8))
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            shell.layoutParams = lp
            maximizeButton?.text = if (maximized) "❐" else "□"
        }
        maximizeButton = maximize
        val close = btn("×") {
            desktopWindows.remove(shell)
            if (wifiWindow === shell) {
                wifiWindow = null
                wifiStatusView = null
                wifiResultsView = null
            }
            desktopSurface.removeView(shell)
            updateWindowDock()
        }
        chrome.addView(minimize, LinearLayout.LayoutParams(dp(40), dp(32)))
        chrome.addView(maximize, LinearLayout.LayoutParams(dp(40), dp(32)))
        chrome.addView(close, LinearLayout.LayoutParams(dp(40), dp(32)))
        shell.addView(chrome)
        shell.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        desktopSurface.addView(shell, FrameLayout.LayoutParams(
            initialWidth, dp(heightDp), Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        desktopWindows[shell] = title
        var rawX = 0f
        var rawY = 0f
        var originX = 0
        var originY = 0
        chrome.setOnTouchListener { _, event ->
            val lp = shell.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rawX = event.rawX; rawY = event.rawY
                    originX = shell.left; originY = shell.top
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
        if (windowDock.visibility == View.VISIBLE) windowDock.bringToFront()
        updateWindowDock()
        return shell
    }

    private fun updateWindowDock() {
        if (!::windowDock.isInitialized) return
        windowDock.removeAllViews()
        val hidden = desktopWindows.filter { it.key.visibility != View.VISIBLE }
        hidden.forEach { (window, title) ->
            windowDock.addView(btn(title.take(14)) {
                window.visibility = View.VISIBLE
                window.bringToFront()
                updateWindowDock()
            }, LinearLayout.LayoutParams(-2, dp(34)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        windowDock.visibility = if (hidden.isEmpty()) View.GONE else View.VISIBLE
        if (windowDock.visibility == View.VISIBLE) windowDock.bringToFront()
    }

    private fun watchDefaultNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            private fun refresh() {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) wifiStatusView?.text = WifiBridge.status(this@MainActivity)
                }
            }
            override fun onAvailable(network: android.net.Network) = refresh()
            override fun onLost(network: android.net.Network) = refresh()
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: android.net.NetworkCapabilities
            ) = refresh()
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            defaultNetworkCallback = callback
        } catch (e: Exception) {
            Log.w("nan0s", "default network callback unavailable", e)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun btn(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; minWidth = 0; minimumWidth = 0
        textSize = 11f
        setTextColor(Color.rgb(205, 226, 232))
        setBackgroundColor(Color.rgb(48, 56, 86))
        setPadding(dp(7), dp(4), dp(7), dp(4))
        setOnClickListener { action() }
    }
    private fun send(c: Int) { vm.keyChar(c); loop.poke(); view.requestFocus() }

    private fun dispatchFunctionKey(index: Int) {
        when (index) {
            4, 6 -> showUnifiedSettings()
            5 -> showWifi()
            else -> send(0x101 + index)
        }
    }

    private fun keyRow(): View {
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 13, 27))
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val functionKeys = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (index in 0 until 12) {
            val label = "F" + (index + 1)
            functionKeys.addView(btn(label) { dispatchFunctionKey(index) },
                LinearLayout.LayoutParams(dp(40), dp(38)).apply { setMargins(dp(1), 0, dp(1), 0) })
        }
        stack.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(functionKeys)
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("Kbd") {
            view.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, 0)
        })
        val keys = listOf("Esc" to 27, "Tab" to 9, "\u2190" to NanVm.LEFT, "\u2191" to NanVm.UP,
            "\u2193" to NanVm.DOWN, "\u2192" to NanVm.RIGHT, "~" to '~'.code, "Enter" to 13,
            "Bksp" to 8, "Home" to NanVm.HOME, "End" to NanVm.END, "PgUp" to NanVm.PGUP,
            "PgDn" to NanVm.PGDN)
        for ((label, code) in keys) row.addView(btn(label) { send(code) })
        stack.addView(HorizontalScrollView(this).apply { addView(row) })
        return stack
    }

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

    private fun showBrowserPrompt() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "ISOLATED BROWSER LAUNCHER  •  external browser runs outside the NaN16 VM"
            setTextColor(Color.rgb(128, 232, 218))
            textSize = 12f
        })
        val address = EditText(this).apply {
            setText("https://")
            hint = "https://example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        panel.addView(address, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        panel.addView(btn("OPEN IN ANDROID BROWSER") {
            showBrowserWindow(address.text.toString())
        })
        showDesktopWindow("Browser Launcher", panel, 190)
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
        panel.addView(btn("FIT DESKTOP VIEWPORT") { view.resetZoom(); view.invalidate() })
        panel.addView(btn("NANNET / WI-FI / CELLULAR") { showWifi() })
        panel.addView(btn("NANNET TRAINING") { showTrainingSuite() })
        panel.addView(btn("N16T GATEWAY") { showRemoteGateway() })
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
        val sessionStatus = TextView(this).apply {
            text = remoteSession?.connectionSummary() ?: "STATUS: DISCONNECTED\nNo negotiated session yet."
            setTextColor(Color.rgb(142, 224, 205))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setBackgroundColor(Color.rgb(5, 10, 20))
        }
        gatewayStatusView = sessionStatus
        panel.addView(sessionStatus, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
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
        // /portal is a browser page, not the binary POST handler. Normalize it to
        // the N16T route so a portal-page 404 cannot be mistaken for a protocol failure.
        val endpoint = if (entered.path.trimEnd('/') == "/portal" || entered.path.isBlank() || entered.path == "/") {
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
                        gatewayStatusView?.text = session.connectionSummary()
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
                        gatewayStatusView?.text = "STATUS: FAILED\n${e.message}"
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
            gatewayStatusView?.text = "STATUS: DISCONNECTED"
            Toast.makeText(this, "No active N16T session", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try { active.closeRemote() } catch (_: Exception) { }
            active.close()
            runOnUiThread {
                gatewayStatusView?.text = "STATUS: DISCONNECTED\nSession closed."
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
        panel.addView(btn("OPEN /nsh TXT FILE") {
            val files = File(filesDir, "nsh").apply { mkdirs() }
                .listFiles()?.filter { it.isFile && it.extension.equals("txt", true) }.orEmpty()
            if (files.isEmpty()) {
                Toast.makeText(this, "No .txt files in /nsh. Use NSH: write test.txt", Toast.LENGTH_LONG).show()
            } else {
                val choosePanel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    setBackgroundColor(Color.rgb(17, 22, 42))
                }
                files.sortedBy { it.name.lowercase() }.forEach { file ->
                    choosePanel.addView(btn(file.name + "  •  " + file.length() + " bytes") {
                        if (prepareTraining()) Thread {
                            try { runTraining(file.readBytes()) }
                            catch (e: Exception) { finishTraining("Could not read /nsh/" + file.name + ": " + e.message) }
                        }.start()
                    })
                }
                showDesktopWindow("Select NSH Training Corpus", choosePanel, 300)
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
            val statusPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.rgb(17, 22, 42))
            }
            statusPanel.addView(TextView(this).apply {
                text = "Current guest: temporary byte-frequency and byte-bigram candidate.\n\nNot yet: neural optimizer, remote runner job, candidate quality evaluation, or automatic merge.\n\nCorpus data is validated as data and never executed as instructions."
                setTextColor(Color.rgb(180, 224, 220))
                textSize = 12f
            })
            showDesktopWindow("Training Boundary", statusPanel, 250)
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
        wifiWindow?.let { desktopWindows.remove(it); desktopSurface.removeView(it) }
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
        panel.addView(btn("LOCATION SERVICES FOR WI-FI SCAN") { WifiBridge.openLocationSettings(this) })
        panel.addView(btn("MOBILE DATA / 5G SETTINGS") { WifiBridge.openMobileSettings(this) })
        panel.addView(btn("REFRESH ROUTE STATUS") { status.text = WifiBridge.status(this) })
        wifiWindow = showDesktopWindow("NaNNet Network Manager", panel, 430)

    }

    /**
     * Resolve touch directly against the NaN16 WM tables (WT=0x58000, ZO=0x58100).
     * This avoids colour heuristics and focuses the exact tapped window before
     * dispatching close/enlarge. The guest remains the authority for WM actions.
     */
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
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "Join " + ap.ssid + "  •  " + ap.security
            setTextColor(Color.rgb(180, 224, 220))
            textSize = 13f
        })
        val password = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Wi-Fi password"
            setTextColor(Color.WHITE)
        }
        panel.addView(password, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        panel.addView(btn("REQUEST CONNECTION") {
            Toast.makeText(this, WifiBridge.join(this, ap.ssid, password.text.toString()), Toast.LENGTH_LONG).show()
        })
        showDesktopWindow("Join Wi-Fi / " + ap.ssid, panel, 220)
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

    private fun nshFile(name: String): File? {
        val safe = name.trim()
        if (safe.isEmpty() || safe == "." || safe == ".." ||
            !safe.matches(Regex("[A-Za-z0-9._-]{1,64}"))) return null
        val root = File(filesDir, "nsh").apply { mkdirs() }.canonicalFile
        val file = File(root, safe).canonicalFile
        return if (file.parentFile == root) file else null
    }

    private fun showNshEditor(name: String) {
        val file = nshFile(name)
        if (file == null) {
            Toast.makeText(this, "NSH filename must use letters, numbers, dot, dash or underscore (max 64)", Toast.LENGTH_LONG).show()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "APP-PRIVATE NSH WORKSPACE  /  " + file.name
            setTextColor(Color.rgb(128, 232, 218))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        })
        val editor = EditText(this).apply {
            minLines = 8
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "Write file contents…"
            if (file.isFile) setText(try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" })
        }
        panel.addView(ScrollView(this).apply { addView(editor) }, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(btn("SAVE FILE") {
            try {
                file.parentFile?.mkdirs()
                file.writeText(editor.text.toString(), Charsets.UTF_8)
                Toast.makeText(this, "Saved /nsh/" + file.name, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "WRITE NO: " + e.message, Toast.LENGTH_LONG).show()
            }
        })
        showDesktopWindow("NSH Editor / " + file.name, panel, 430)
    }

    private fun showNshFiles() {
        val root = File(filesDir, "nsh").apply { mkdirs() }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(17, 22, 42))
        }
        panel.addView(TextView(this).apply {
            text = "NSH WORKSPACE  /nsh  •  APP-PRIVATE TEXT FILES"
            setTextColor(Color.rgb(128, 232, 218))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        })
        val newName = EditText(this).apply {
            hint = "example.txt"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        panel.addView(newName, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        panel.addView(btn("CREATE / OPEN TEXT FILE") {
            showNshEditor(newName.text.toString())
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { file ->
            if (file.isFile) list.addView(btn(file.name + "   •   " + file.length() + " bytes") {
                showNshEditor(file.name)
            })
        }
        if (list.childCount == 0) list.addView(TextView(this).apply {
            text = "No files yet. Use NEW TEXT FILE or type: write test.txt"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        })
        panel.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        showDesktopWindow("NSH Files /nsh", panel, 390)
    }

    private fun showLog() {
        val logText = synchronized(log) { log.toString() }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(3, 7, 14))
        }
        val body = TextView(this).apply {
            text = if (logText.isEmpty()) "(empty)" else logText
            setTextColor(Color.rgb(180, 225, 218))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
        }
        panel.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(btn("COPY LOG") {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("nan0s log", logText))
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
        })
        showDesktopWindow("COM1 / Serial Log", panel, 360)
    }

    // ---- NanLoop.Listener (called on the run thread) ----
    override fun onFrame() { view.postInvalidate() }
    override fun onSerial(bytes: ByteArray) {
        val received = String(bytes, Charsets.ISO_8859_1)
        val openTraining = received.contains("N16HOST:TRAIN\n")
        val openFiles = received.contains("N16HOST:FILES\n")
        val openSettings = received.contains("N16HOST:SETTINGS\n")
        val openWifi = received.contains("N16HOST:WIFI\n")
        val openBrowser = received.contains("N16HOST:BROWSER\n")
        val openGateway = received.contains("N16HOST:GATEWAY\n")
        val writeRequest = Regex("N16HOST:WRITE:([^\\r\\n]*)\\n").find(received)
        var visible = received
            .replace("N16HOST:TRAIN\n", "")
            .replace("N16HOST:FILES\n", "")
            .replace("N16HOST:SETTINGS\n", "")
            .replace("N16HOST:WIFI\n", "")
            .replace("N16HOST:BROWSER\n", "")
            .replace("N16HOST:GATEWAY\n", "")
        if (writeRequest != null) visible = visible.replace(writeRequest.value, "")
        if (visible.isNotEmpty()) synchronized(log) {
            log.append(visible)
            if (log.length > 65536) log.delete(0, log.length - 49152)
        }
        if (openTraining || openFiles || openSettings || openWifi || openBrowser || openGateway || writeRequest != null) runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (openTraining) showTrainingSuite()
            if (openFiles) showNshFiles()
            if (openSettings) showUnifiedSettings()
            if (openWifi) showWifi()
            if (openBrowser) showBrowserPrompt()
            if (openGateway) showRemoteGateway()
            writeRequest?.groupValues?.getOrNull(1)?.let { name ->
                if (name.isBlank()) Toast.makeText(this, "Usage: write FILENAME", Toast.LENGTH_LONG).show()
                else showNshEditor(name)
            }
        }
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
        wifiStatusView?.text = WifiBridge.status(this)
    }
    override fun onDestroy() {
        defaultNetworkCallback?.let { callback ->
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
                    .unregisterNetworkCallback(callback)
            } catch (_: Exception) { }
        }
        defaultNetworkCallback = null
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
