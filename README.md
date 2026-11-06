# NaNLMind_ANDROID

Android application project for the NaNLMind / NaN0S system.

## Android project

The native application lives in `aarch64/android`. It contains the Kotlin UI and guest protocol, JNI bridge, NaN16 C runtime, native regression tests, and Gradle application module.

The Android app is built from the NaN0S implementation at pinned source revision `ad2225f2a61a888287a0f5631aca63fe091296f1`. The source files required to compile the NaN16 training and NANNET guests are vendored under `src/` and `tools/nmk.awk`.

The large boot image is staged from that exact upstream revision by CI rather than stored as an empty or truncated Git blob. CI verifies its exact 1,474,560-byte length before running tests or packaging.

## Build

From the repository root:

```sh
cd aarch64/android
make test
make asan
make seals
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The CI workflow runs the native regression suite, address/undefined-behavior sanitizer build, seal-pair checks, Android unit tests, and debug APK packaging. It publishes the APK and SHA-256 file as a GitHub Actions artifact.

## Runtime assets

The Gradle build compiles the training guest and persistent Android N16 protocol guest from NaN16 sources. The runtime asset allowlist is enforced by `verifyRuntimeAssetAllowlist`.

The current upstream boot image is 1,474,560 bytes. It is not the full 8 MiB runtime universe; the native runtime's memory configuration and the boot image's byte size are distinct things.

## Android desktop session

The Android boot path enters the NaN16 GUI desktop directly. NSH opens as a child window on the desktop surface at startup and prints the live `--help` reference. It can be moved, minimized, maximized/restored, closed, and restored from the NSH desktop button. Closing NSH leaves the desktop running; it does not return to the museum catalog.

NSH supports file operations under the app-private `/nsh` sandbox (`pwd`, `ls`, `write`, `touch`, `cat`, `mkdir`, `rm`), guest application launch (`run lattice|monitor|hex|system`), and host desktop tools (`settings`, `nannet`, `nannet-training`, `n16t`, `browser`, `disk`, `fs`, `log`, `fit`, `scale`, `apps`, `windows`, `reset`). The complete command reference is printed by `--help`.

NSH, Settings, NaNNet, Training, N16T Gateway, Browser launcher, COM1 Log, filesystem utility, and applications menu use a shared internal desktop child-window layer with drag, minimize, maximize/restore, and close. Android document pickers and the external browser remain Android-owned system surfaces.

Guest windows are NaN16-managed. The Android shell injects actual PC keyboard scan codes for F2/F3/F7-F10 and translates touches on guest close/enlarge controls into the corresponding guest Escape/F3 actions. See [Android desktop architecture](aarch64/android/docs/ANDROID_DESKTOP.md).

## NaNNet host networking

Android owns the phone's Wi-Fi and cellular radios. NaNNet uses Android's active default network through ConnectivityManager, so the guest does not need a PCI NIC. HTTPS gateway traffic uses Wi-Fi or carrier mobile data (including 5G when Android selects it). The NaNNet manager displays the active route and renders scan results inside its own child window. Android may throttle scans or mask SSIDs; cached scan results and the current association are displayed where available.

## N16T gateway

The human-facing portal is `GET /portal`. Binary N16T frames use `POST /n16t` with `application/x-n16t`. If a user enters the portal URL into the gateway window, the client normalizes it to the binary `/n16t` route rather than POSTing frames to the HTML page. A successful HELLO/CAPABILITIES exchange proves protocol reachability only. The Android client does not yet upload corpus chunks, run remote training jobs, evaluate candidates, or merge model updates.

## NaN16-first implementation

The Android-specific boot image is patched from the pinned NaN0S source revision by `tools/android_guest_patch.pl`, then assembled with the NaN0S AWK/NaN16 build tools. The Android APK workflow does not use Python for guest-image patching. Existing C/JNI code remains the NaN16 interpreter and narrow Android host boundary; no C training/model semantics are added. Corpus validation, protocol state, and training guest behavior are NaN16 sources.

