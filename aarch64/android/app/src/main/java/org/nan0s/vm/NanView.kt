package org.nan0s.vm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the guest display. Text (80x25) and graphics both render into a content bitmap at
 * "fit" size, then the bitmap is blitted through a live zoom/pan transform:
 *  - text cells keep the VGA 8:16 aspect (uniform scale + letterbox; fixes the portrait
 *    stretching where letters were tall and unreadably narrow),
 *  - pinch zooms 1x..8x around the focal point, one-finger drag pans when zoomed,
 *    double-tap or the "Fit" button returns to the whole screen,
 *  - "resolution" adjust on the fly = the zoom over the current fit; integerScale snaps the
 *    base stage to whole pixels for a crisp blit (guest VGA mode is untouched).
 */
class NanView(context: Context, private val vm: NanVm, private val onInput: () -> Unit, private val onGuestPointer: (Int, Int, Int) -> Unit = { _, _, _ -> }, private val onHostKey: (Int) -> Boolean = { false }) : View(context) {
    companion object {
        private const val MAX_ZOOM = 8f
        private const val CELL_ASPECT = 0.5f   // VGA text cell width/height = 8/16
    }
    private val cells = IntArray(2000)
    private val pal = IntArray(16)
    private val bg = Paint()
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val blit = Paint().apply { isFilterBitmap = true }
    private var px = IntArray(0)
    private var gfxBmp: Bitmap? = null
    private var txtBmp: Bitmap? = null
    private val dst = RectF()
    private var baseW = 0f
    private var baseH = 0f
    var integerScale = false
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    // content cache: rebuild only when the guest drew, the view/mode changed, or the
    // integer-scale toggle flipped. During a pinch we KEEP the last bitmap and only
    // transform it, so the gesture never pays for text rasterisation or a VM snapshot.
    private var contentBmp: Bitmap? = null
    private var cacheVW = -1
    private var cacheVH = -1
    private var cacheGfx = false
    private var cacheDims = -1
    private var cacheInt = false
    private var cacheDirty = -1

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val old = zoom
            zoom = (zoom * d.scaleFactor).coerceIn(1f, MAX_ZOOM)
            if (old > 0f && zoom != old) {          // keep the point under the fingers pinned
                panX = d.focusX - (d.focusX - panX) * (zoom / old)
                panY = d.focusY - (d.focusY - panY) * (zoom / old)
                clampPan()
            }
            invalidate()
            return true
        }
    }
    private val scaleDetector = ScaleGestureDetector(context, scaleListener)

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (zoom <= 1.01f) return false
            panX -= distanceX; panY -= distanceY; clampPan(); invalidate(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { resetZoom(); return true }
    }
    private val gestures = GestureDetector(context, gestureListener)
    private val hostKeysDown = HashSet<Int>()

    init { isFocusable = true; isFocusableInTouchMode = true }

    /** Back to the whole-screen fit (double-tap / "Fit" button). */
    fun resetZoom() { zoom = 1f; panX = 0f; panY = 0f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val vw = width; val vh = height
        if (vw <= 0 || vh <= 0) return
        val gfx = vm.graphics()
        val dims = if (gfx) (vm.width() shl 16) or (vm.height() and 0xFFFF) else 0
        val dirty = vm.dirty()
        val geometry = vw != cacheVW || vh != cacheVH || gfx != cacheGfx || dims != cacheDims || integerScale != cacheInt
        val pinching = scaleDetector.isInProgress
        var b = contentBmp
        if (b == null || geometry || (dirty != cacheDirty && !pinching)) {
            b = (if (gfx) buildGfx(vw, vh) else buildText(vw, vh)) ?: return
            contentBmp = b
            cacheVW = vw; cacheVH = vh; cacheGfx = gfx; cacheDims = dims; cacheInt = integerScale
            cacheDirty = dirty
        }
        val content = b ?: return
        val zw = baseW * zoom; val zh = baseH * zoom
        clampPan()                       // also recenters when the content fits (zoom == 1)
        dst.set(panX, panY, panX + zw, panY + zh)
        canvas.drawBitmap(content, null, dst, blit)
    }

    private fun clampPan() {
        val zw = baseW * zoom; val zh = baseH * zoom
        panX = if (zw <= width) (width - zw) / 2f else panX.coerceIn(width - zw, 0f)
        panY = if (zh <= height) (height - zh) / 2f else panY.coerceIn(height - zh, 0f)
    }

    /** Text: uniform cell scale, 8:16 cells, centered/letterboxed - never stretched per axis. */
    private fun buildText(vw: Int, vh: Int): Bitmap? {
        if (!vm.snapshotText(cells, pal)) return null
        var s = min(vw / (80f * CELL_ASPECT), vh / 25f)
        if (s <= 0f) return null
        if (integerScale && s >= 1f) s = floor(s)
        val cw = s * CELL_ASPECT
        val bw = max(1, (80 * cw).roundToInt())
        val bh = max(1, (25 * s).roundToInt())
        var b = txtBmp
        if (b == null || b.width != bw || b.height != bh) {
            b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); txtBmp = b
        }
        val c = Canvas(b)
        c.drawColor(Color.BLACK)
        fg.textSize = s * 0.82f
        val m = fg.measureText("M")
        fg.textScaleX = if (m > 0f) cw / m else 1f
        val base = s * 0.78f
        for (y in 0 until 25) {
            for (x in 0 until 80) {
                val cell = cells[y * 80 + x]
                val attr = cell shr 8 and 255
                val bi = attr shr 4 and 7
                if (bi != 0) { bg.color = pal[bi]; c.drawRect(x * cw, y * s, (x + 1) * cw, (y + 1) * s, bg) }
                val code = cell and 255
                if (code == 0 || code == 32) continue
                fg.color = pal[attr and 15]
                c.drawText(Cp437.map[code], x * cw, y * s + base, fg)
            }
        }
        baseW = bw.toFloat(); baseH = bh.toFloat()
        return b
    }

    /** Graphics: fit the guest framebuffer uniformly, integer-snapped on request. */
    private fun buildGfx(vw: Int, vh: Int): Bitmap? {
        val gw = vm.width(); val gh = vm.height()
        if (gw <= 0 || gh <= 0 || gw * gh > 4_000_000) return null
        if (px.size < gw * gh) px = IntArray(gw * gh)
        if (!vm.snapshotGfx(px)) return null
        var b = gfxBmp
        if (b == null || b.width != gw || b.height != gh) {
            b = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888); gfxBmp = b
        }
        b.setPixels(px, 0, gw, 0, 0, gw, gh)
        var s = min(vw.toFloat() / gw, vh.toFloat() / gh)
        if (integerScale && s >= 1f) s = floor(s)
        baseW = gw * s; baseH = gh * s
        return b
    }

    // ---- touch: pinch zoom / pan / double-tap fit ----
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) requestFocus()
        var guestHandled = false
        if (vm.graphics() && baseW > 0f && baseH > 0f) {
            val action = when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> 1
                MotionEvent.ACTION_MOVE -> if (e.pointerCount == 1 && zoom <= 1.01f) 2 else 0
                MotionEvent.ACTION_UP -> 3
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> 4
                else -> 0
            }
            if (action != 0) {
                val gx = (((e.x - panX) / (baseW * zoom)) * vm.width()).toInt().coerceIn(0, vm.width() - 1)
                val gy = (((e.y - panY) / (baseH * zoom)) * vm.height()).toInt().coerceIn(0, vm.height() - 1)
                onGuestPointer(gx, gy, action)
                guestHandled = true
            }
        }
        val a = scaleDetector.onTouchEvent(e)
        val b = gestures.onTouchEvent(e)
        return guestHandled || a || b || super.onTouchEvent(e)
    }

    // ---- input: hardware and soft keyboards arrive as KeyEvents (soft keyboard with TYPE_NULL) ----
    override fun onCheckIsTextEditor(): Boolean = true
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return BaseInputConnection(this, false)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = key(keyCode, event, true) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = key(keyCode, event, false) || super.onKeyUp(keyCode, event)

    private fun key(code: Int, e: KeyEvent, down: Boolean): Boolean {
        if (down && onHostKey(code)) {
            hostKeysDown.add(code)
            return true
        }
        if (!down && hostKeysDown.remove(code)) return true
        if (vm.rawKeyboard()) {
            val sc = KeyMap.scancode(code)
            if (sc < 0) return false
            vm.keyEvent(sc, down); onInput(); return true
        }
        if (!down) return KeyMap.special(code) >= 0 || e.unicodeChar in 1..127
        val sp = KeyMap.special(code)
        if (sp >= 0) { vm.keyChar(sp); onInput(); return true }
        val u = e.unicodeChar
        if (u in 1..127) { vm.keyChar(u); onInput(); return true }
        return false
    }
}
