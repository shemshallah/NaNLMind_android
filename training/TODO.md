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

## Current prototype boundary

The current NaN16 guest validates UTF-8/record limits, computes byte-frequency and byte-bigram counts, scrubs the submitted corpus from guest memory, and saves an isolated candidate checkpoint. This is a corpus-profile/count-table prototype, not yet a neural optimizer or quality-evaluated language model. The runner workflow is intended to exercise this exact guest; candidate evaluation, contribution accounting, transactional merge, and a live authenticated gateway remain open.

## Build order

1. [x] Add NaN16 guest corpus scanner, byte-frequency update table, record counters, and rolling digest.
2. [x] Add runner gates for host-injected N16TXT, bundled sample, valid multibyte UTF-8, and malformed UTF-8 rejection.
3. [x] Add strict UTF-8 sequence validation in NaN16 guest code.
4. [ ] Add Unicode normalization/noncharacter policy, deterministic sample scheduling, and fixed initialization/checkpoint ABI.
5. [x] Add NaN16 frame validation, CRC-32, replay-sequence checks, and overbootstrap route dispatch.
6. [x] Add bounded C stream framing and NaN16 byte-buffer bridge; keep protocol policy in the guest.
7. [x] Add Android GUI entry and CLI --nannet-train launch path to the shared guest.
8. [x] Add manual GitHub Runner CPU Ring workflow using the same guest and corpus format.
9. [ ] Add candidate evaluator and transactional merge.
10. [x] Add explicit Android runtime asset allowlist. [ ] Add packaged APK artifact scan.
11. [ ] Add TLS gateway deployment and connect Android transport to the runner/VPS.
12. [ ] Build APK and run identical corpus vectors on Android and runner.

## Acceptance gate

No claim of training, networking, Android packaging, or security completion until a reproducible CI run proves it. A UI that merely displays progress is not a trainer. A C/Python implementation of the training algorithm is not an acceptable substitute for the NaN16 guest pipeline.
