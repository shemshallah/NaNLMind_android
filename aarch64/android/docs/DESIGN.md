# nan0s_android - design of the portable C core and the app (plan r2, 31 Oct 2026)
Status (r4): core verified vs qemu; JNI, Kotlin app, Gradle, Actions are WRITTEN but never built by gradle - see TODO.md (read its top section first).
core/OPS.md was corrected against it; the toolchain was probed; the x86 gold image was booted on qemu as a reference (see TODO.md).

## IP separation / merge plan
- This tree (core/, tools/, app/) holds the Android port only. The image is an INPUT: `nan0s.img` produced by the main tree's `sh x86/tools/mkall.sh` (x86/build/nan0s.img).
- MERGED: this tree now lives at `<repo>/aarch64/android/`; Gradle's `syncImage` copies `<repo>/x86/build/nan0s.img`. Nothing in core/ may include
  anything from the main tree; the only shared contract is the image format (docs: OPS.md) and the op semantics.
- The core never hard-codes the museum: any image/words/.nan program that follows OPS.md runs.

## Files (planned)
core/nan16.h        public API (existing, extended below)           core/nan16_int.h  struct nan_vm + inline word helpers
core/nan16.c        fetch/gate/predication/op switch, FP rules       core/nan_dev.c    ports: DAC, PIT/speaker, PCI, ATA PIO, COM1, 0x3DA, keyboard IRQ1 path
core/nan_load.c     loaders + .nan text parser + save/load state     core/nan_render.c gfx->ARGB, text->UTF-8/ANSI, PPM, half-block
core/ktab.inc, cp437_utf8.inc (existing)
tools/nan_cli.c     runner / tty front-end / test driver              tools/qdiff.sh    differential test against qemu-system-i386 (the real kan16.xa)
tools/fuzzgen.c     random-program generator (.nan)                    tests/            golden logs (COM1 text, text-screen dumps, PPM hashes)
jni/nan_jni.c       JNI bridge                                        app/              Gradle + Kotlin shell, assets/nan0s.img
.github/workflows/android.yml

## API additions on top of the existing nan16.h
- `NAN_TIME_WAIT = 4` return from nan_run: the guest polled TICK >=256 times with an unchanged value (a delay loop) - host should sleep ~1 ms (battery).
- `int nan_load_auto(nan_vm*, const uint8_t*, size_t, const char **kind)` : detects 1,474,560-byte floppy image (guest at 0x2200), `.nan` text
  (lines like FFF8_0A000200_F8FF, `;` comments), raw 8-byte words (word 0 heads valid), saved state ("NAN1" magic). Returns 0/-1, kind = "floppy"|"nan-text"|"words"|"state".
  Floppy images smaller than 0x2200+8 bytes are rejected; larger-than-65,536-word programs are only accepted as raw words/.nan (up to 4M words).
- `nan_load_text(vm, text, len)` : words packed from index 0 (honour optional `.org N` lines if present); error line number via `nan_load_error()`.
- `size_t nan_state_size(vm) / nan_save_state(vm, buf, cap) / nan_restore_state(vm, buf, len)` : sparse snapshot (4 KB pages that are not all zero) of
  regs, pc, k, memory, VRAM, text cells, DAC, keyboard ring, PIT, ATA regs. Used for pause/resume on Android and for golden tests.
- Disk: `nan_attach_disk_mem(vm, uint8_t*, size_t)`, `nan_attach_disk_fd(vm, int fd)` (pread/pwrite, for a 1 GB+ allocation image on Android), `nan_detach_disk`.
  Primary master ATA PIO LBA28 (IDENTIFY, READ, WRITE, FLUSH). No disk => floating bus (0xFF).
- Keyboard: `nan_key_char()` pushes cooked values directly, but in RAW mode (kbd.nas) it synthesises set-1 make/break (with 0x2A shift and E0 prefixes)
  using a reverse lookup of ktab.inc, so the on-screen keys and the soft keyboard work for raw-mode exhibits too. `nan_key_event(vm, sc, down)` for hardware keys.
- Palette/text: `uint32_t nan_text_color_vm(vm, idx)` (through the DAC + attribute map) and `nan_dac_rgb(vm, idx)`; `nan_dirty(vm)` generation counter that bumps on ANY display write
  (cell, pixel, FBFILL/FBCOPY, DAC, mode) so the UI redraws only when needed.
- Debug: `nan_set_trace(vm, cb, ctx)` (pc, payload, n, k before each op), `nan_set_port_log(vm, cb, ctx)` (every OUT/IN incl. unknown ports), `nan_strerror`.

## Determinism rules (so the differential fuzz is meaningful)
- Memory is calloc'd, zero = invalid heads (gate trap), exactly like the kernel's REPSTOSD.
- No host-FPU NaN behaviour (software rules in OPS.md). 32-bit wraparound everywhere. All shifts masked to 5 bits. BCOPY/BFILL/FBCOPY counts are uint32 as on x86.
- RDTSC = executed instructions. TICK = host ms; tools/nan_cli `--clock steps:N` makes TICK = steps/N so runs are reproducible.
- KEY with an empty ring rewinds pc and returns NAN_WAIT_KEY (the real HLT loop).

## Termux front-end (nan_cli)
`cc -O2 -std=c99 core/*.c tools/nan_cli.c -o nan_cli`. Modes: `--img f` (auto-detect), `--steps N`, `--keys "1,ESC,~"` scripted keys with `@T` delays,
`--dump-text` (UTF-8 via cp437_utf8.inc), `--dump-ppm out.ppm`, `--serial`, `--tty` (raw termios; arrows->NANK_*; ANSI truecolor text; half-block graphics scaled to the terminal),
`--disk f`, `--save/--load state`, `--trace`, `--ports`, `--selftest`.

## Android app (thin shell)
- Kotlin: NanVm (externals), NanView (SurfaceView; text cells drawn per cell with Typeface.MONOSPACE; CP437 0xB0-0xDF block/box glyphs drawn procedurally so no font
  dependency; no font is bundled or copied - same "homebrew only" rule as the main tree), gfx Bitmap nearest-neighbour with aspect/integer scaling options.
- Input: on-screen key row (Esc Tab arrows ~ Enter Ctrl-less), soft keyboard via nan_key_char, hardware keyboard KeyEvent->set-1 scancodes (raw mode correct), volume keys optional.
- Run thread: nan_set_time_ms(uptimeMillis - t0); nan_run(200k) per slice; sleep 16 ms on VSYNC, park on WAIT_KEY, 1 ms on TIME_WAIT; pause on onPause, autosave state.
- Image management: bundled assets/nan0s.img (current build); "Open image..." via Storage Access Framework (floppy/.nan/words/state); recent list; "Reset"; serial log viewer + copy;
  screenshot export; optional disk image (SAF fd -> nan_attach_disk_fd). Audio: AudioTrack square wave from nan_speaker_hz().
- Build: AGP 8.5, Kotlin, CMake (NDK, -O2, arm64-v8a armeabi-v7a x86_64), minSdk 24, landscape default (portrait allowed), GitHub Actions android.yml builds app-debug.apk.
- Cannot be run in the authoring sandbox: the JNI file is compile-checked on the host against the JDK jni.h; Kotlin/Gradle/AAPT are NOT checked until you push.

## Verification strategy (strongest first)
1. qemu-system-i386 boots build/nan0s.img = the REAL kan16.xa. Same image in nan_cli: compare COM1 text, text-screen dumps (qemu monitor `pmemsave 0xB8000 4000`),
   and VBE/13h screens (`screendump`) after identical scripted keys (monitor `sendkey`). Baseline already captured: COM1 = "NaN/0S R26J READY MENU=S\r".
2. Differential fuzz against the real core: generate random programs (.nan) with tools/fuzzgen, append a register-dump epilogue (PUTC of r0..r15, k, memory checksums),
   build with mkimg.awk -v GUEST=prog.nan, run in qemu (serial file) and in nan_cli, diff the serial. Replaces the stale nan16sim.py as oracle.
3. src/tests/a64_test.nas (18 checks, "NaN/0S core PASS") and the n16i/selftest logs in logs/.
4. Unit tests in C: FP NaN table, gate/trap text, BCOPY overlap, STB/LDB, DIV edge cases, keyboard translation vs ktab, DAC/text palette mapping.
