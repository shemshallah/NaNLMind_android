# NaNLMind Remote Training — implementation queue

## Hard invariants

- Training policy, corpus codec, sample scheduler, trainer, evaluation, and merge state machine are NaN16 guest code.
- C is limited to host I/O/network drivers and byte-buffer translation.
- Runner point 1/256; Android point 2/256.
- Android runtime universe: 8 MiB, exactly one.
- One shared pipeline and shared test vectors.
- Intake is staged and transactional; no untrusted corpus directly mutates active weights.
- No GitHub PAT or deployment secret on Android.
- No Ed25519 dependency until the stack can implement and test it.

## Build order

1. [x] Add NaN16 guest corpus scanner, byte-frequency update table, record counters, and rolling digest.
2. [x] Add runner gates for host-injected N16TXT, bundled sample, valid multibyte UTF-8, and malformed UTF-8 rejection.
3. [x] Add strict UTF-8 sequence validation in NaN16 guest code.
4. [ ] Add Unicode normalization/noncharacter policy, deterministic sample scheduling, and fixed initialization/checkpoint ABI.
5. [x] Add NaN16 frame validation, CRC-32, session ownership, replay rejection, message-specific payload bounds, state transitions, CLOSE/reset, and overbootstrap route dispatch.
6. [x] Add bounded C stream framing and NaN16 byte-buffer bridge; keep protocol policy in the guest.
7. [x] Add Android GUI entry and CLI --nannet-train launch path to the shared guest.
8. [x] Add manual GitHub Runner CPU Ring workflow using the same guest and corpus format.
9. [ ] Add candidate evaluator, contribution accounting, and transactional merge.
10. [x] Add explicit Android runtime asset allowlist and enforce it at build time. [ ] Add packaged APK artifact scan.
11. [x] Add bounded Android HTTPS opaque-frame transport. [x] Expose persistent NaN16 frame-build/route/advance/abort/reset calls through the Android VM ABI; keep outbound sequencing and pending-send state in the guest. [x] Wire the UI to a configured HTTPS endpoint for HELLO/CAPABILITIES handshake. [ ] Implement remote corpus framing/upload, server-side peer/runner response handling, and deploy the authenticated TLS gateway.
12. [x] Build APK and run the shared corpus pipeline on the GitHub runner. [ ] Install APK on a device and run identical corpus vectors on Android.

## Acceptance gate

No claim of training, networking, Android packaging, or security completion until a reproducible CI run proves it. A UI that merely displays progress is not a trainer. A C/Python implementation of the training algorithm is not an acceptable substitute for the NaN16 guest pipeline.
