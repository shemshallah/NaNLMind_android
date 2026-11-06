package org.nan0s.vm

/** Android KeyEvent.KEYCODE_* -> set-1 scancode (0x100 = E0-prefixed) for raw-keyboard guests, and -> cooked NaN codes. Numbers are the android.view.KeyEvent constants. */
object KeyMap {
    private val letters = intArrayOf(0x1E, 0x30, 0x2E, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32, 0x31, 0x18, 0x19, 0x10, 0x13, 0x1F, 0x14, 0x16, 0x2F, 0x11, 0x2D, 0x15, 0x2C) // a..z
    private val digits = intArrayOf(0x0B, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A) // 0..9

    /** returns -1 for keys the guest has no scancode for */
    fun scancode(keyCode: Int): Int = when (keyCode) {
        in 29..54 -> letters[keyCode - 29]            // KEYCODE_A..Z
        in 7..16 -> digits[keyCode - 7]               // KEYCODE_0..9
        111 -> 0x01; 61 -> 0x0F; 66 -> 0x1C; 62 -> 0x39; 67 -> 0x0E   // ESC TAB ENTER SPACE DEL(backspace)
        56 -> 0x34; 55 -> 0x33; 69 -> 0x0C; 70 -> 0x0D; 68 -> 0x29     // . , - = `
        71 -> 0x1A; 72 -> 0x1B; 73 -> 0x2B; 74 -> 0x27; 75 -> 0x28; 76 -> 0x35  // [ ] \ ; ' /
        19 -> 0x148; 20 -> 0x150; 21 -> 0x14B; 22 -> 0x14D             // DPAD up down left right
        122 -> 0x147; 123 -> 0x14F; 92 -> 0x149; 93 -> 0x151; 124 -> 0x152; 112 -> 0x153  // HOME END PGUP PGDN INS FWDDEL
        59, 60 -> 0x2A; 113, 114 -> 0x1D; 57, 58 -> 0x38; 115 -> 0x3A  // shift ctrl alt caps
        else -> -1
    }
    /** cooked guests get special codes for navigation keys; -1 = use the unicode character instead */
    fun special(keyCode: Int): Int = when (keyCode) {
        19 -> NanVm.UP; 20 -> NanVm.DOWN; 21 -> NanVm.LEFT; 22 -> NanVm.RIGHT
        122 -> NanVm.HOME; 123 -> NanVm.END; 92 -> NanVm.PGUP; 93 -> NanVm.PGDN; 124 -> NanVm.INS; 112 -> NanVm.DEL
        111 -> 27; 66 -> 13; 67 -> 8; 61 -> 9
        else -> -1
    }
}
