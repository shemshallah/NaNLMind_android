# Byte-exact NaN16 word transport parity with NaN0S

This Android VM boundary follows upstream NaN0S C ABI as inspected November 8, 2026:

- `aarch64/android/core/nan16.c` Git blob `b3511c026e8d20f8585a2a1f835bb64ca1df104b`
- `aarch64/android/core/nan16.h` Git blob `d34cfa2247c9193d74dfd60145481f5ed5cf6156`

These files have been synchronized *byte for byte* without overwriting the
Android-specific N16T, NMP, Kotlin GUI, or canonical leaf bindings. The
cross-repository native parity CI checks the two upstream C/header blobs.

## Address and class correctness

`nan_write_words_raw(vm, first_word, bytes, word_count)` and
`nan_read_words_raw(vm, first_word, bytes, word_count)` preserve each complete
8-byte NaN16 word (both NaN faces, their class-sign/quiet bits and 32-bit
payload). `first_word` is a guest WORD index; `word_count` is **not** bytes.
Both operations validate their complete spans before modifying output.
No wraparound; null/nonzero spans and unsupported big-endian CPUs fail closed.
The older `nan_write_bytes` intentionally rewrites only 4-byte payloads and
resets classes: it is never a substitute for moving native head state.

Native regression tests copy all 4 head classes, a full **67,838-word**
(542,704-byte) HERMES-sized bank, and reject overrun/null/overflow inputs
without changing the last valid word. Host-side copying is not mathematical
validation or learning. Guest nh_open/nh_get/nh_put and NMP/MTH remain
responsible for validity, authorization and generation checks.

## Android boundary

`NanVm.readWordsRaw` is an internal, synchronized, bounded read-only JNI
inspection method; `NanNative.readWordsRaw` converts word counts to raw bytes.
It cannot write a native head. Deliberately **no unchecked JNI raw-write
entry point** is exposed to Android callers. A future head-bank import must
use authenticated leasing and native guest admission rather than accepting
arbitrary opaque bytes. The 8 MiB Android guest memory allocation is
distinct from the native 542,704-byte HERMES head-bank span.

Cross-repo drift of this native C ABI is tested separately from the
Android boot image's older pinned NaN0S source. One may advance while the
other remains pinned for APK reproducibility.
