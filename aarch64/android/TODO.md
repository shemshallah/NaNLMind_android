# nan0s_android TODO (status r4, 1 Nov 2026) - core verified vs qemu, Android layer WRITTEN BUT NEVER BUILT
**Read docs/HANDOFF.md first (r5 status: Kotlin type-checked vs android.jar, core clang-clean on 3 ABIs, tests pass; gradle build still never run).**
Design: docs/DESIGN.md (still says "plan"; app now exists, see below). Semantics: core/OPS.md. Build: `make` -> ./nan_cli; `make test`; `make asan`.

## REMAINING TASKS FOR THE NEXT CLAUDE (in order)
1. [ ] **Type-check / build the Android layer.** Nothing under app/ has been compiled by gradle. Pure-Kotlin parts (Cp437, NanNative, NanVm, NanLoop, KeyMap) DO compile and pass host-test/HostTest.kt over the real JNI .so (see "Host JNI test" below). UNCHECKED: NanView.kt, Beeper.kt, MainActivity.kt (android.* APIs), app/build.gradle.kts, CMakeLists.txt, AndroidManifest.xml, res/. Push to GitHub and read the `android` Actions run (.github/workflows/android.yml: core-tests job, then apk job -> app-debug.apk). Fix whatever AGP/Kotlin reports. Also compile core/*.c with clang (the NDK compiler; gcc only was used): `clang -std=gnu99 -O2 -Wall -ffp-contract=off core/*.c`.
2. [ ] **Seed-diff triage is done, widen the fuzz.** `sh tools/fuzz.sh <main-tree> 1 300` (needs gawk, qemu-system-i386; main tree = r28d). First batch: 11/12 identical, seed 10 = qemu x87-style NaN pick (not a core bug, see OPS.md). Make fuzzgen avoid two-NaN operands (or mask them) so the run is a clean oracle, then run 300+ seeds; investigate every other DIFF (copies land in /tmp/fuzz_*). Bisect trick used: prefix N lines + epilogue (see tools/fuzz.sh `one()`).
3. [ ] Gate Q2 leftovers: ESC and `~` keys vs qemu (qdiff.sh supports them), exhibit 3 and 9 animation phases are clock driven (compare static parts only; gdiff.sh; exhibit 3 matched exactly at 3,264,000 steps with the default 1000 steps/ms clock).
4. [ ] Gate A: build src/tests/a64_test.nas etc. with gawk (tools/mkimg.awk -v GUEST=...) and run on nan_cli; match logs/*.txt.
5. [ ] nan_cli: `--tty` (raw termios, ANSI truecolor text, half-block gfx scaled to the terminal, arrows->NANK_*, Ctrl-] quits), `--save F` / `--load F` (API exists: nan_save_state/nan_restore_state; nan_load_auto already detects "state"), `--trace`, `--ports` (hooks exist: nan_set_trace, nan_set_port_log), `--selftest`. Not written.
6. [ ] Android polish: screenshot export, recent-image list, serial log is memory only, SAF disk uses detachFd (never closed on detach), landscape/portrait layout of the key row, AudioTrack beeper untested, NanView text uses Typeface.MONOSPACE with Unicode CP437 (box/block glyphs depend on the device font; draw 0xB0-0xDF procedurally if they look wrong).
7. [ ] Huge BCOPY/BFILL counts (32-bit) can stall a slice; consider a work-based budget.
8. [ ] Update docs/DESIGN.md "Files (planned)" to match reality (below) and merge into the main tree under android/ (copy this tree; `./gradlew syncImage`-style task `syncImage` in app/build.gradle.kts refreshes the bundled image from ../../build/nan0s.img).
9. [ ] Memory/ATA unknowns: ATA slave bit ignored, IDENTIFY words minimal, 0x61 reads return the stored value, CPUID returns a fixed table.

## Done in r4
- [x] Gate Q1 PASS: museum menu 4000 VGA text bytes identical to qemu; COM1 banner identical.
- [x] Gate Q2: text exhibits 1,2,4,5,6,7 identical to qemu; 8 differs only in a clock-driven cell; 9 (VBE 1024x768) pixel-identical outside the animated windows; 3 (13h) pixel-identical at the matching animation frame.
- [x] Core fixes found by the compare: DAC 6->8 uses qemu c6_to_8; initial DAC = EGA table + black (NO default VGA palette); `.nan` loader accepted only >=19 char lines (now 18).
- [x] New core API: nan_save_state/nan_restore_state/nan_state_size (core/nan_state.c, "NAN1" sparse snapshot, validated before apply), "state" kind in nan_load_auto, nan_set_port_log, nan_strerror.
- [x] tests/test_core.c: 866 checks (words, loaders, ALU, DIV edges, predication+traps, FP table, memory ops, display, DAC, keyboard cooked/raw, time/speaker, PCI, ATA mem+fd, hooks, state round trip + corruption rejection); passes plain and under ASan/UBSan (`make asan`).
- [x] tools/gdiff.sh (graphics compare vs qemu screendump), tools/fuzzgen.c + tools/fuzz.sh (differential fuzz), tools/qdiff.sh verified.
- [x] jni/nan_jni.c (compiled against jdk jni.h), Kotlin app under app/src/main/java/org/nan0s/vm, Gradle (AGP 8.5.2, Kotlin 2.0.21, minSdk 24, CMake 3.22.1, abis arm64-v8a armeabi-v7a x86_64), AndroidManifest, res, bundled app/src/main/assets/nan0s.img (r28d build), .github/workflows/android.yml.

## Host JNI test (how the pure-Kotlin layer was verified)
```
J=/usr/lib/jvm/java-21-openjdk-amd64/include   # needs openjdk-21-jdk-headless
gcc -std=gnu99 -O2 -fPIC -shared -I$J -I$J/linux jni/nan_jni.c core/nan16.c core/nan_dev.c core/nan_load.c core/nan_render.c core/nan_state.c -lm -o /tmp/libnanjni.so
P=app/src/main/java/org/nan0s/vm; kotlinc $P/Cp437.kt $P/NanNative.kt $P/NanVm.kt $P/NanLoop.kt $P/KeyMap.kt host-test/HostTest.kt -include-runtime -d host.jar
java -Djava.library.path=/tmp -jar host.jar app/src/main/assets/nan0s.img      # -> HOST TEST PASS (kotlinc 2.0.21 from the GitHub release zip)
```

## Known facts / risks
- qemu is the oracle (real kan16.xa). Clock-driven exhibits (3, 8, 9 animations, tick counters) cannot be byte-compared except at a matching phase.
- nan_vga_default() in nan_dev.c is now unused (kept; may delete). `dac_loaded` is still written but no longer read.
- The sandbox had no Android SDK; Kotlin Android code, Gradle and AAPT were not run anywhere.
