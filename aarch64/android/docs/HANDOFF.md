# HANDOFF (r5, 1 Nov 2026) - work in progress, for the next Claude

## Goal
Run nan0s on Android: (a) APK built by GitHub Actions, (b) Termux with nan_cli (no APK needed).
The bundled image app/src/main/assets/nan0s.img is the r28d build, so nothing else has to be downloaded for the app itself.

## Verified in the sandbox this round (no Android SDK available there)
- Kotlin: ALL files under app/src/main/java type-check with kotlinc 2.0.21 against android.jar (API 33): zero errors.
- C core + JNI compile warning-free with clang (zig cc, -Wall -Wextra) for x86_64, aarch64, armv7.
- `make test`: 866 checks, 0 failures; `make asan`: same under ASan/UBSan.
- Host JNI test (host-test/HostTest.kt over the real .so): HOST TEST PASS.

## NOT verified (do these first)
1. Gradle / AGP 8.5.2 / CMake / AAPT / manifest never ran. Push the tree to GitHub, open the `android` Actions run, fix what it reports. Artifact: app-debug.apk.
2. No gradle wrapper in the tree. Workflow uses setup-gradle 8.9 and plain `gradle`. For Android Studio add one: `gradle wrapper --gradle-version 8.9`.
3. Nothing has run on a real phone: AudioTrack beeper, soft keyboard (TYPE_NULL), text glyphs (box/block chars use the device font; draw 0xB0-0xDF procedurally if they look wrong), key row layout.
4. DONE: merged as aarch64/android/; the workflow is at the repo root (.github/workflows/android.yml) and runs with working-directory aarch64/android.

## Remaining work (priority order)
- Fix whatever the first Actions run reports.
- DONE (r29j): Disk fd leak - MainActivity keeps the ParcelFileDescriptor, passes pfd.fd, closes it when a new disk is attached and in onDestroy. It also auto-attaches the bundled NaNFS volume: assets/disk.nanfs is seeded to filesDir once and attached RDWR (guest writes persist); the SAF "Disk" button opens any volume from Android storage; Reset re-seeds the pristine asset. tools/build.sh and `make volume` regenerate the asset from src/tests/nfix_test.nas via tools/nmkfs.sh.
- DONE: **Fs** prompts for a size (GiB, default 1) and creates a sparse NSPV image at `filesDir/nan0s_fs.img` plus `nan0s_fs.key`, attaches it immediately, and the next boot prefers it when the header validates; Reset deletes it. Host equivalent: `tools/nan0s_fs.sh` (default `~/nan0s_fs`), boot gate `crypt/storage_boot_gate.sh`, guest header cart `crypt/guest/nan0s_fs_check.nas`.
- DONE (r29j): portrait text no longer stretched (aspect-locked 8:16 cells, letterboxed); pinch zoom 1x..8x with pan/double-tap-fit in NanView; "Fit" button added. See TODO.md B2/B3/B4.
- tools/nan_cli.c is missing --tty, --save/--load, --trace, --ports, --selftest (APIs exist; see TODO.md item 5). --tty matters: it is the no-APK way to test on a phone in Termux.
- Procedural CP437 box/block glyphs in NanView (0xB0-0xDF).
- Fuzz widening and qemu gates (TODO.md items 2-4) need gawk + qemu-system-i386, which the sandbox lacked.
- Update docs/DESIGN.md "Files (planned)" to match reality.

## What the user needs to download
- To get the APK: nothing locally. Create a GitHub repo, push this folder, download the apk artifact from the Actions run, install on the phone (allow "install unknown apps").
- To build locally: Android Studio Koala (2024.1.1) or newer (brings the SDK, NDK, CMake 3.22.1, JDK 17).
- Termux path (no APK): `pkg install clang make`, then `make` and `./nan_cli --img app/src/main/assets/nan0s.img --dump-text --keys "1" --steps 5000000`. For an interactive screen, nan_cli needs --tty (not written yet).
- Optional for the oracle tests on a PC: gawk, xxd, qemu-system-i386.
