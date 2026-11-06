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
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
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
        var restored = false
        if (autosave.exists()) restored = try { vm.restoreState(autosave.readBytes()) } catch (e: Exception) { false }
        if (!restored) vm.load(asset())
        attachDefaultDisk()
        loop = NanLoop(vm, this)
        view = NanView(this, vm) { loop.poke() }
        beeper = Beeper { vm.speakerHz() }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(desktopBar(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
        root.addView(functionRow(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
        root.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(keyRow(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(toolRow(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        view.requestFocus()
        loop.start(); beeper.start()
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
                vm.keyEvent(scan, true); vm.keyEvent(scan, false); loop.poke(); view.requestFocus()
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
        for ((label, code) in keys) row.addView(btn(label) { send(code) })
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
        row.addView(btn("NaNNet") { showWifi() })
        row.addView(btn("nannet-training") { showTrainingSuite() })
        row.addView(btn("N16T Gateway") { showRemoteGateway() })
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun showRemoteGateway() {
        val input = EditText(this).apply {
            hint = "https://deeprepo-qtcl-9c8fea15.apps.mistral.run/portal"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(remotePrefs.getString("endpoint", "https://deeprepo-qtcl-9c8fea15.apps.mistral.run/portal") ?: "https://deeprepo-qtcl-9c8fea15.apps.mistral.run/portal")
        }
        AlertDialog.Builder(this)
            .setTitle("N16T HTTPS Gateway")
            .setMessage(
                "Connect the persistent NaN16 protocol guest and perform HELLO/CAPABILITIES only. " +
                    "Remote corpus upload and the server-side runner response path are not enabled yet. " +
                    "This client does not request or store a GitHub token."
            )
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                connectRemoteGateway(input.text.toString().trim())
            }
            .setNeutralButton("Disconnect") { _, _ -> disconnectRemoteGateway() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectRemoteGateway(raw: String) {
        val normalized = raw.trim().let { if (it.startsWith("https://", true)) it else "https://$it" }
        val endpoint = try { URI(normalized) } catch (e: Exception) {
            Toast.makeText(this, "Invalid gateway URL", Toast.LENGTH_LONG).show()
            return
        }
        if (endpoint.scheme != "https" || endpoint.host.isNullOrBlank() ||
            endpoint.userInfo != null || endpoint.fragment != null) {
            Toast.makeText(this, "Use an HTTPS URL without embedded credentials or fragments", Toast.LENGTH_LONG).show()
            return
        }

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
        AlertDialog.Builder(this)
             .setTitle("NaNNet Training")
            .setMessage("N16TXT corpus training interface. Training runs in an isolated VM snapshot and restores the live desktop session afterward. The current guest emits a local candidate checkpoint; remote corpus transfer and candidate merge are not enabled yet.")
            .setPositiveButton("Choose TXT") { _, _ ->
                if (prepareTraining()) {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                    }
                    startActivityForResult(i, reqTrain)
                }
            }
            .setNeutralButton("Run sample") { _, _ ->
                if (prepareTraining()) Thread { runTraining(null) }.start()
            }
            .setNegativeButton("Export candidate") { _, _ ->
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
            }
            .show()
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
            val dialog = AlertDialog.Builder(this)
                .setTitle("NaNLMind Remote Training")
                .setMessage(if (restored) message else "$message\nWarning: the previous VM snapshot could not be restored.")
                .setPositiveButton("Close", null)
            dialog.show()
        }
    }

    // ---- real WiFi: status, scan, connect request ----
    private fun showWifi() {
        if (!WifiBridge.hasPermission(this)) {
            WifiBridge.requestPermission(this, reqWifi)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("NaNNet Network Manager")
            .setMessage(WifiBridge.status(this) + "\n\nNaNNet uses Android's active network route. Wi-Fi and cellular data (including 5G) both carry HTTPS gateway traffic. Android radios are managed by the host OS; no guest PCI adapter is required.\n\nScan Wi-Fi or manage the active radio in Android settings.")
            .setPositiveButton("Scan Wi-Fi") { _, _ -> scanWifi() }
            .setNeutralButton("Network settings") { _, _ -> WifiBridge.openSettings(this) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun scanWifi() {
        if (!WifiBridge.hasPermission(this)) {
            WifiBridge.requestPermission(this, reqWifi)
            return
        }
        Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()
        WifiBridge.scan(this) { aps ->
            runOnUiThread {
                if (aps.isEmpty()) {
                    AlertDialog.Builder(this)
                         .setTitle("NaNNet Wi-Fi scan")
                        .setMessage("Android returned no visible access points. Check Wi-Fi and Location are enabled. Android may throttle scans; try again shortly. Your current Wi-Fi or cellular connection can still carry gateway traffic.")
                        .setPositiveButton("Settings") { _, _ -> WifiBridge.openSettings(this) }
                        .setNegativeButton("Close", null)
                        .show()
                } else {
                    val labels = aps.map { "${it.ssid}   ${it.level} dBm   ${it.security}" }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("WiFi networks")
                        .setItems(labels) { _, i -> joinWifi(aps[i]) }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
        }
    }

    private fun joinWifi(ap: WifiBridge.Ap) {
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
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) showWifi()
        else Toast.makeText(this, "WiFi permission denied", Toast.LENGTH_LONG).show()
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
        AlertDialog.Builder(this).setTitle("COM1 / serial log").setMessage(if (text.isEmpty()) "(empty)" else text)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("nan0s log", text))
            }.show()
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
