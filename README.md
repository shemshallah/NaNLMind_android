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

The Android boot path enters the NaN16 GUI desktop directly. NSH opens as a guest child window on the desktop at startup and prints the live `--help` reference. `exit`, `quit`, or `q` closes only NSH; it does not leave the desktop or return to the museum catalog. Minimized host child windows can be restored from the desktop task strip.

Implemented NSH commands: `--help`/`h`, `c`, `v`, `w`, `l`, `m`, `x`, `t`, `u`, `s`, `n`, `b`, `f`, `files`, `write FILENAME`, `nannet-training`, `n16t`, `exit`, `quit`, and `q`. `s`, `n`, and `b` open the unified Settings, NaNNet manager, and Browser Launcher. `files` opens the internal app-private `/nsh` file manager; `write FILENAME` opens its text editor. The `nannet-training` command opens the same training interface as the desktop button. The full live reference is printed at boot and by `--help`.

NSH, Settings, NaNNet, Training, N16T Gateway, Browser launcher, COM1 Log, filesystem utility, and applications menu use a shared internal desktop child-window layer with drag, minimize/restore, maximize/restore, and close. Android document pickers and the external browser remain Android-owned system surfaces.

A 12-key F1-F12 strip is rendered by NaN16 and mirrored in the Android soft-key row. F5/F7 route to the one Settings window; F6 opens NaNNet. F2/F3 are guest move/enlarge controls, while F8-F10 open guest Lattice/Monitor/Hex windows. Guest window hit-testing and z-order remain NaN16-owned.

See [Android desktop architecture](aarch64/android/docs/ANDROID_DESKTOP.md).

## NaNNet host networking

Android owns the phone's Wi-Fi and cellular radios. NaNNet uses Android's active default network through ConnectivityManager, so the guest does not need a PCI NIC. HTTPS gateway traffic uses Wi-Fi or carrier mobile data (including 5G when Android selects it). The NaNNet manager displays the active route and renders scan results inside its own child window. Android may throttle scans or mask SSIDs; cached scan results and the current association are displayed where available.

## N16T gateway

The human-facing portal is `GET /portal`. Binary N16T frames use `POST /n16t` with `application/x-n16t`. If a user enters the portal URL into the gateway window, the client normalizes it to the binary `/n16t` route rather than POSTing frames to the HTML page. A successful HELLO/CAPABILITIES exchange proves protocol reachability only. The Android client does not yet upload corpus chunks, run remote training jobs, evaluate candidates, or merge model updates.

## NaN16-first implementation

The Android-specific boot image is patched from the pinned NaN0S source revision by `tools/android_guest_patch.pl`, then assembled with the NaN0S AWK/NaN16 build tools. The Android APK workflow does not use Python for guest-image patching. Existing C/JNI code remains the NaN16 interpreter and narrow Android host boundary; no C training/model semantics are added. Corpus validation, protocol state, and training guest behavior are NaN16 sources.

## Shared NANNET intake

The endpoint publisher is compatible with the NANNET universal intake service
in NaNLMind. Configure NANLM_INTAKE_URL to the HTTPS service origin and
NANLM_DEVICE_TOKEN to a scoped token issued for endpoint_id=my_android with
intake and observation scopes. The server derives endpoint identity from that
token; the JSON observation cannot override it.

The publisher sends only the evidence envelope to POST /v1/observations.
It never includes raw prompt/response content, correctness claims, head deltas,
or a GitHub token. A successful HTTP 202 means the observation is queued as
unverified evidence, not that it has trained or changed the canonical model.
The six-runner ring must retrieve, validate, fold, and acknowledge it.


## NaNROUTER NMP control-plane client

The Android app now includes a bounded HTTPS client for the NMP-aware NaNROUTER
routes: POST /v1/nmp/packets, POST /v1/nmp/lease, and POST /v1/nmp/complete.
The N16T binary gateway remains a separate protocol and is not treated as an
NMP submission path.

The NMP Packet Submission desktop window accepts a canonical packet JSON record
produced by the NaN16 guest, including its native admission receipt, and sends it
with a submit-role bearer token. The bearer token is not persisted in
SharedPreferences. The client requires an HTTPS service origin, disables
redirect following, applies request/response size limits, and does not decode
or reinterpret packet admission semantics.

The Android transport also exposes worker lease and completion methods, but
this change does not claim that Android has a complete local NMP execution
worker or that the guest currently generates the canonical NMP packet/receipt
for this screen. Server-side NMP admission context must be configured in
NaNROUTER; otherwise the routes return HTTP 503.
