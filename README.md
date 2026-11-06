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

The Android boot path enters the NaN16 GUI desktop directly. The Android host opens one NSH desktop child window at startup and prints `--help`. Closing NSH leaves the desktop running; it does not return to the exhibit catalog. The legacy hidden storage-shell handoff is disabled on this path.

NSH commands include `ls`, `pwd`, `write`, `cat`, `mkdir`, `rm`, `open`, `disk`, `fs`, `log`, `fit`, `scale`, `settings`, `apps`, and `run APP`. NSH files live in the app-private `/nsh` directory; path traversal outside that directory is rejected. `write filename.txt` opens an editor and `write filename.txt text` writes inline.

`run lattice`, `run monitor`, and `run hex` open guest desktop windows. `run nannet`, `run training`, `run gateway`, and `run browser` open the corresponding Android-host subwindows. The browser uses an isolated Android WebView with JavaScript and file access disabled; a renderer crash does not stop the NaN16 VM.

## NaNNet host networking

Android owns the phone's Wi-Fi and cellular radios. NaNNet uses Android's active default network, so the guest does not need a PCI NIC. HTTPS gateway traffic therefore works over Wi-Fi or carrier mobile data (including 5G when Android selects it). The NaNNet manager shows the active transport and signal details, waits for Android's scan-results broadcast, and falls back to the platform scan cache when scans are throttled.

## N16T gateway

The Android client defaults to `https://deeprepo-qtcl-9c8fea15.apps.mistral.run/portal`. The training-merge authority now implements the binary N16T HELLO/CAPABILITIES handshake at `POST /portal` and `POST /n16t`; `GET /portal` is a small browser landing page. The separate `/v1/training/merge` API remains distinct. Corpus transfer and merge are not enabled in the Android client yet.
