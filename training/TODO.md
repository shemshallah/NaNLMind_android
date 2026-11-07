# NaNLMind Remote Training — implementation queue

## Hard invariants

- Training policy, corpus codec, sample scheduler, trainer, evaluation, and merge state machine are NaN16 guest code.
- C is limited to host I/O/network drivers and byte-buffer translation.
- Runner point 1/256; Android point 2/256. Every universe has 256 canonical palindromic points (0–255); point 0 is reserved for the NaNNet interface, and HW0 binds physical capabilities without redefining this topology.
- Android runtime universe: 8 MiB, exactly one.
- One shared pipeline and shared test vectors.
- Intake is staged and transactional; no untrusted corpus directly mutates active weights.
- No GitHub PAT or deployment secret on Android.
- No Ed25519 dependency until the stack can implement and test it.

## Build order

1. [x] Add NaN16 guest corpus scanner, byte-frequency update table, record counters, and rolling digest.
2. [x] Add runner gates for host-injected N16TXT, bundled sample, valid multibyte UTF-8, and malformed UTF-8 rejection.
3. [x] Add strict UTF-8 sequence validation in NaN16 guest code.
4. [~] Add Unicode normalization/noncharacter policy and fixed initialization/checkpoint ABI. [x] Add deterministic NaN16 epoch-rotation scheduler guest, shared CI regression, Android asset packaging, and trainer-side schedule audit digest. The current count-based trainer records the deterministic permutation fingerprint; it does not yet reorder records during learning or deduplicate exact duplicates.
5. [x] Add NaN16 frame validation, CRC-32, session ownership, replay rejection, message-specific payload bounds, state transitions, CLOSE/reset, and overbootstrap route dispatch.
6. [x] Add bounded C stream framing and NaN16 byte-buffer bridge; keep protocol policy in the guest.
7. [x] Add Android GUI entry and CLI --nannet-train launch path to the shared guest.
8. [x] Add manual GitHub Runner CPU Ring workflow using the same guest and corpus format.
9. [~] Add first NaN16 candidate lifecycle kernel in NaN0S: immutable candidate IDs, parent checkpoint, required pass count, pass/fail evidence, staged/rejected/promoted states, insufficient-evidence rejection, and one-way promotion. Its guest regression now passes the NaN0S CI gate. Independent mathematical evaluation, contribution accounting, durable transaction journal, and checkpoint-root promotion remain open.
10. [x] Add canonical NaN16 `nan://...::HW0:<point>::<object-key>` parser; enforce point 1–255 and reject reserved point 0/malformed routes. [x] Decode initial `vector<ID>` and `hypergraph<ID>` object-key families from the parser's original byte pointer/length (IDs 1–9999); exercise route→typed-key decoding in the guest regression and reject unknown, zero, and oversized keys. [x] Add NaN16 guest integration test: task ledger claim → typed mathematical operator resolution → task completion. [ ] Extend the NaN0S NaN16 task-ledger prototype into full NaNLMROUTER task/result envelopes for BS0/MC0/HW0: authenticated runner identity, signed envelope, sequence/replay protection, wall-clock/timebase binding for expiry epochs, budget accounting, checkpoint binding, evidence references, and routed dispatch.
11. [ ] Extend the NaN0S NaN16 object/operator registry prototype with canonical serialization, full digests, MC0DB persistence, generalized typed endpoint/operator resolution, wider object-ID support, and vector/matrix/tensor/graph/hypergraph schemas.
12. [ ] Add canonical operation records, domain-separated content hashes, dependency lineage, and deterministic replay checks.
13. [ ] Validate the new BS0 16-function × 4-input NaN16 Boolean fixture in CI; then add composition/mirror/property tests and HW0 bounded physical-capability binding to logical points with observed/inferred/verified states.
14. [~] First NaN16 guest scratch quota manager added in NaN0S and its CI regression now passes: 4 KiB accounting pages, BS0DB 1 GiB cap, MC0DB 7 GiB cap, 8 GiB allocatable ceiling, 2 GiB reserved headroom, 64 allocation records, reserve/release/stats ABI, and tested quota rejection plus release accounting. This is bounded in-memory accounting only; durable database files, append-only journal, crash recovery, scratch snapshots, owner/task/epoch-bound leases, and staged candidate promotion remain open.
15. [ ] Add ten-layer mathematical evaluator suite and MC0 feedback/evolution audit loop; candidates may only be promoted after independent regression gates.
16. [x] Add explicit Android runtime asset allowlist and enforce it at build time. [ ] Add packaged APK artifact scan.
17. [x] Add bounded Android HTTPS opaque-frame transport. [x] Expose persistent NaN16 frame-build/route/advance/abort/reset calls through the Android VM ABI; keep outbound sequencing and pending-send state in the guest. [x] Wire the UI to a configured HTTPS endpoint for HELLO/CAPABILITIES handshake. [ ] Implement remote corpus framing/upload, server-side peer/runner response handling, and deploy the authenticated TLS gateway.
18. [x] Build APK and run the shared corpus pipeline on the GitHub runner. [ ] Install APK on a device and run identical corpus vectors on Android.

## Acceptance gate

No claim of training, networking, Android packaging, or security completion until a reproducible CI run proves it. A UI that merely displays progress is not a trainer. A C/Python implementation of the training algorithm is not an acceptable substitute for the NaN16 guest pipeline.
