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
