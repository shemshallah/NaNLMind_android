# NaNLMind Remote Training (NaN16-first)

Status: local Android and hosted-runner corpus-profile paths are implemented. NaN16 validates N16T frames, binds one 128-bit session identity, rejects replay/cross-session frames, enforces message-specific payload bounds and legal session transitions, and routes accepted frames. The Android client now has a persistent NaN16 protocol guest ABI for frame construction, guest-owned outbound sequencing, pending-send commit/abort, inbound validation/routing and reset. `N16RemoteSession` composes that guest with the bounded HTTPS byte transport. The Android UI now exposes an N16T Gateway control that accepts an HTTPS endpoint and performs HELLO/CAPABILITIES; remote corpus upload is not yet wired. Candidate evaluation/transactional merge, the server-side response path and a deployed live gateway are not yet implemented.

**Prototype boundary:** the current guest validates and profiles corpora, updates byte-frequency and byte-bigram count tables, and emits an isolated candidate checkpoint. This is not yet a neural language-model optimizer or a quality-evaluated model update. Candidate evaluation, fair contribution accounting, transactional merge, and a live authenticated runner gateway remain unfinished.

## Fixed topology

- Point 1/256: overbootstrap control/routing portal for the GitHub Runner CPU Ring.
- Point 2/256: Android edge/test universe.
- Android instantiates exactly one 8 MiB NaN0S universe. It does not download or instantiate the 2 GiB bootstrap map.
- Runner and Android execute the same NaN16 corpus decode, normalization, sample construction, training-step, and checkpoint-validation program. Hardware-specific execution is below that shared program.

The runner is currently a reproducible batch executor of the shared NaN16 corpus-profile guest, not yet a live remote compute peer. Android and runner use the same guest scanner/count-table prototype; deterministic scheduling, neural training steps, evaluation, and proposal/merge semantics are not implemented yet.

## Execution boundary

~~~text
GUI: NaNLMind Remote Training ─┐
CLI: --nannet-train ───────────┤
                              v
                     NaN16 training suite
                     - corpus codec
                     - validation
                     - sample scheduler
                     - trainer
                     - candidate evaluator
                              |
                     NaN16 intake protocol
                              |
                    overbootstrap point 1
                              |
                C boundary drivers (transport only)
                              |
                   runner / Android link
                              |
                  NaN16 route + peer dispatch
~~~

The intended training suite, codec rules, sample scheduler, update policy, merge logic, and protocol state transitions belong in NaN16 guest programs. Implemented today: the corpus scanner/count tables; N16T framing and CRC validation; session ownership; per-session monotonic replay checks; message-specific payload bounds; state transitions; and point routing. The Android guest maintains separate outbound-send and inbound-replay sequence cursors. The persistent guest ABI and `N16RemoteSession` compile and package in CI. The Android UI can now configure an HTTPS endpoint and perform the HELLO/CAPABILITIES handshake, but it does not yet send corpus frames or start a remote training job. The HTTPS adapter accepts only complete opaque N16T frames, enforces HTTPS, finite timeouts, no redirects, content-type checks, and bounded request/response sizes. There is still no deployed gateway or server-side response implementation. C is restricted to hardware-facing drivers: socket/file descriptor operations, bounded byte transfer, and conversion between host byte buffers and NaN16 memory/port conventions. C must not parse corpus semantics, choose examples, calculate training weights, or mutate model state.

The current Android VM already exposes a JNI-backed NaN16 interpreter. The GUI shell may present controls and SAF file selection, but it must not implement training policy. The guest owns those decisions.

## Corpus path

1. User selects a UTF-8 .txt file in the GUI, or supplies a path to --nannet-train.
2. The boundary driver reads bounded bytes and places them in a NaN16 intake buffer.
3. NaN16 validates the .n16txt header, line framing, lengths, character restrictions, and corpus limits.
4. NaN16 canonicalizes line endings and blank-line handling, then builds immutable sample records.
5. The same NaN16 corpus-profile prototype runs on the Android guest or GitHub runner; remote session dispatch is not connected yet.
6. A candidate checkpoint is evaluated against a fixed local holdout plus a rotating anti-regression suite.
7. Only an accepted candidate becomes a merge proposal. The active model is never updated directly by untrusted intake.

## Contribution fairness and merge safety

A corpus is data, never executable code. No corpus record may contain opcodes, routing commands, model deltas, checkpoint bytes, or privileged metadata.

Initial policy constants (tunable only by a versioned NaN16 policy update):

- Maximum one submitted corpus: 1 MiB decoded text.
- Maximum record: 4096 UTF-8 bytes.
- Maximum source contribution: 1/16 of effective update mass in one merge epoch.
- Aggregate external-corpus contribution: at most 1/4 of effective update mass in one epoch.
- Exact duplicate records are deduplicated before sampling.
- Sampling is deterministic for a given policy version, corpus digest, and epoch.
- A source cannot increase its weight by splitting one corpus across sessions or runner identities; intake groups contributions by the stable device/source identity supplied by the authenticated deployment layer.
- Every candidate is evaluated against the same holdout. Reject if the candidate regresses beyond configured tolerance, collapses diversity, or overfits a contributor's corpus.
- Accepted contributions are staged, scored, and merged transactionally. Failed validation leaves the active checkpoint byte-for-byte unchanged.

The first transport iteration may use a scoped revocable bearer credential over HTTPS terminated by the deployment gateway. This is not end-to-end authentication and is not a substitute for Ed25519. Do not send credentials over plaintext HTTP. Ed25519 remains deferred until the NaN0S network stack can implement and test it.

## Network protocol v1

The NaN16 protocol is framed independently of TCP packet boundaries:

- magic: 4 bytes N16T
- protocol version: u16 (1)
- message type: u16
- session id: 128-bit opaque value
- sequence: u32, strictly increasing per direction
- payload length: u32, hard bounded before allocation
- payload: exactly payload-length bytes
- checksum: CRC-32 over header and payload

Message types: HELLO, CAPABILITIES, CORPUS_BEGIN, CORPUS_CHUNK, CORPUS_END, ACCEPTED, REJECTED, TRAIN_STATUS, CANDIDATE_DIGEST, MERGE_PROPOSAL, MERGE_RESULT, HEARTBEAT, CLOSE.

CRC-32 detects accidental corruption; it is not authentication. The gateway must authenticate the device credential, enforce quotas, rate limits, TLS, sequence/replay checks, and session ownership before forwarding frames to NaN16 intake. NaN16 independently checks framing, lengths, state transitions, and sequence numbers.

Runner and Android use the same NaN16 frame and state-machine definitions. The Android guest validates inbound frame structure, CRC, session ownership, replay sequence, message-specific payload bounds, legal transitions, and route selection; it separately assigns outbound sequence numbers and commits them only after successful transport. The server-side peer must implement the matching independent directional cursors. Only the C transport driver should differ by platform.

## Launch surfaces

- GUI: one menu entry, NaNLMind Remote Training, opens the training suite.
- CLI: --nannet-train [corpus.txt] opens the same suite and accepts an optional file path.
- NaNNET continues to open the browser/service surface.
- Keep Settings, About Shemshallah, and About NaN0S. Remove museum/exhibit launch entries from the production menu; do not delete underlying museum source or exhibits from the source repository as part of this training task.

## Android packaging

The Android build contains the required NaN0S runtime and one 8 MiB universe image. Keep the packaging inputs explicit and reproducible; the build should package only runtime assets intended for distribution.

## Deployment

Start with GitHub Actions as the #1 runner implementation because it is reproducible and has a free-tier path. Keep the runner stateless between jobs except for explicitly versioned model artifacts. A free VPS is a later always-on gateway option, not a dependency for the first corpus test.

Do not put PATs, repository write tokens, signing keys, or runner secrets in APK assets, source files, logs, or corpus data. The Android device must never receive a GitHub PAT.

## Milestones

1. [x] Inspect current Android wrapper, NaN0S Android VM, NaNNET guest program, and existing intake boundary.
2. [x] Define a shared NaN16-first pipeline, corpus format, protocol framing, and contribution policy.
3. [x] Implement initial NaN16 corpus scanner and local test against a known corpus; full UTF-8 canonicalization remains pending.
4. [x] Implement bounded C stream/frame byte transport and NaN16 protocol validator/route bridge; TLS gateway remains pending.
5. [x] Add runner invocation using the shared NaN16 guest program.
6. [x] Add Android GUI menu entry and CLI --nannet-train launch path.
7. [x] Add N16T session ownership, replay rejection, message-specific payload bounds, state transitions, CLOSE/reset handling, and independent guest-owned outbound sequence. [x] Add persistent Android guest-call ABI, HTTPS session coordinator, and UI HELLO/CAPABILITIES handshake. [ ] Add corpus-frame transfer, server-side peer response, transactional intake, candidate evaluation, and fair merge.
8. [x] Add an explicit APK runtime-asset allowlist enforced during the Android build. [ ] Inspect the packaged APK against that allowlist.
9. [x] Build and package the debug APK in CI. [ ] Install it on a device; run identical corpus vectors on runner and Android and compare pipeline outputs.

A milestone is complete only with an artifact and a reproducible test result. Architecture documents are not implementation evidence.
