# NaNLMind Remote Training — implementation queue

## Hard invariants

- Training policy, corpus codec, sample scheduler, trainer, evaluation, and merge state machine are NaN16 guest code.
- C is limited to host I/O/network drivers and byte-buffer translation.
- Runner point 1/256; Android point 2/256.
- Android runtime universe: 8 MiB, exactly one.
- APK excludes gitignored nannetsuite and all pentest material.
- One shared pipeline and shared test vectors.
- Intake is staged and transactional; no untrusted corpus directly mutates active weights.
- No GitHub PAT or deployment secret on Android.
- No Ed25519 dependency until the stack can implement and test it.

## Build order

1. Write NaN16 .n16txt decoder + canonical stream encoder.
2. Write local corpus test program and deterministic sample scheduler.
3. Define trainer ABI over NaN16 state cells; use a tiny corpus and a fixed initialization image.
4. Write protocol state machine in NaN16; keep socket operations in C drivers only.
5. Add the same guest program to runner and Android build inputs.
6. Wire GUI menu and CLI --nannet-train to the same guest entry point.
7. Add candidate evaluator and transactional merge.
8. Add explicit APK allowlist and artifact scan for forbidden paths/material.
9. Build on GitHub Actions; run parity vectors on both targets.

## Acceptance gate

No claim of training, networking, Android packaging, or security completion until a reproducible CI run proves it. A UI that merely displays progress is not a trainer. A C/Python implementation of the training algorithm is not an acceptable substitute for the NaN16 guest pipeline.
