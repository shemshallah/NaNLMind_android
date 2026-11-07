# MC0DB / BS0DB storage, operator graph, and learning feedback

Status: design contract plus first NaN16 quota-manager prototype. This document separates implemented guest behavior from planned persistent storage and training behavior.

## 1. Database identity and ownership

MC0DB is the canonical mathematical knowledge tree and operation DAG. BS0DB is the compact Boolean/bootstrap and routing-logic database. HW0 reports observed hardware and runner capabilities but does not redefine the 256 logical points. NaNLMROUTER is the only dispatch path between BS0, MC0, HW0, and runner workers.

The 10 GiB combined budget is a hard ceiling, not a preallocation target. Use 4 KiB accounting pages:

| Region | Page quota | Capacity | Purpose |
|---|---:|---:|---|
| BS0DB | 262,144 | 1 GiB | Boolean fixtures, predicates, route logic, bootstrap scratch |
| MC0DB | 1,835,008 | 7 GiB | canonical objects, operation DAG, indexes, evaluation cache |
| Reserved headroom | 524,288 | 2 GiB | compaction, checkpoint staging, growth, recovery |
| Total | 2,621,440 | 10 GiB | hard ceiling |

The initial allocator exposes only 8 GiB of allocatable quota. It tracks page reservations, not physical RAM commitment. It has a 64-entry in-memory allocation table and reserve/release/stats calls. This is a bootstrap accounting prototype, not durable MC0DB/BS0DB storage. Never claim a file is persisted because a page reservation succeeded.

Every allocation carries owner runner, task ID, epoch, pool, page count, lifetime class, checkpoint, and state. Scratch allocations are task-scoped and revocable. Canonical objects and provenance are never stored in scratch-only regions. A task must reserve scratch before it begins; every exit path releases it or leaves an explicitly recoverable lease.

## 2. Tree + content-addressed DAG

Use a human-readable tree for discoverability and a content-addressed DAG for identity:

- Namespace tree: universe → subsystem → logical point → kind → human alias.
- Immutable object store: canonical serialized object bytes keyed by full SHA-256 digest.
- Operation store: canonical operation record keyed by a domain-separated SHA-256 digest.
- Relation index: typed adjacency and hyperedge incidence indexes pointing to immutable object digests.
- Provenance journal: append-only records for create, derive, validate, reject, supersede, tombstone, and promote.
- Checkpoint root: Merkle root over object, operation, index, and journal roots.

For example, `vector1337` is a namespace alias for an immutable vector object. Its alias record resolves to the object's digest; it is not the identity of the object's content. A changed vector creates a new digest and a new alias version. Never overwrite a content-addressed object in place.

A result of a mathematical operation is stored under the digest of its canonical output object. The derivation itself receives a separate operation digest. The operation record stores operator ID and semantic version, ordered input digests, parameters, program digest, precondition/evaluator versions, output digest, parent checkpoint, runner identity, and evidence digests. Domain separation prevents a vector payload hash from being confused with an operation-record hash. Hashes prove byte identity/integrity, not mathematical truth.

Canonical serialization must define field order, integer width and signedness, endianness, dimension ordering, sparse-index ordering, NaN/undefined encoding, and schema version. Do not hash host-native structs or unordered maps. Full SHA-256 is the target; the current registry's small integer digest markers are only prototype test values and must not be mistaken for cryptographic content addressing.

## 3. The double-colon expression contract

The existing single-object route remains valid:

`nan://GM0:SC0:MC0:BS0:HW0::HW0:1::vector1337`

The route parser resolves scope, owner, logical point, and typed object alias. It does not execute a program merely because it found a file.

A two-endpoint operation uses the same `::` boundary but includes an explicit operator selector:

`nan://GM0:SC0:MC0:BS0:HW0::HW0:1::vector1337::op:dot.v1::nan://GM0:SC0:MC0:BS0:HW0::HW0:2::vector1338`

Interpretation:
1. Parse endpoint A as a typed object reference.
2. Parse the operator selector and resolve its immutable semantic version.
3. Parse endpoint B as a typed object reference.
4. Ask NaNLMROUTER to resolve the complete typed signature.
5. Check permissions, domain preconditions, scratch budget, step budget, and runner capability.
6. Dispatch the operation as a NaN16 guest program.
7. Validate output schema and evaluator evidence.
8. Canonicalize and hash the output, append derivation provenance, and publish the alias only after commit.

An abbreviated `A::B` form may be allowed only when exactly one registered operator is applicable to the ordered pair of types and current context. If multiple operators match (for example vector addition and dot product), it is ambiguous and must fail closed. Never choose an operator by popularity or guesswork. `::` is a typed operation boundary, not shell execution or arbitrary file evaluation.

Hyperedges are first-class objects with an ordered/unordered declaration, typed incidence roles, endpoint digests, and explicit orientation semantics. A hypergraph does not collapse to pairwise edges unless a named projection operator creates a derived graph and records the information lost.

## 4. Ten mathematical layers as executable programs

Each layer is a program family with the same contract: schemas, canonicalizer, validator, operator registry, reference evaluator, property tests, negative tests, cost model, and feedback report.

1. **Boolean logic:** truth tables, predicates, masks, implication, equivalence, composition, and route guards. This is the first bootstrap because all later control flow depends on reliable predicates.
2. **Sets and relations:** membership, subset, Cartesian product, equivalence relations, partial orders, functions, and finite maps.
3. **Exact numeric domains:** integers, rationals, modular arithmetic, intervals, units, overflow and undefined-domain behavior.
4. **Vectors:** basis, coordinates, dimension, norm, dot/cross where defined, projection, basis change, sparse/dense representation.
5. **Matrices and linear maps:** multiplication, composition, rank, nullspace, inverse only when valid, determinant, factorization, and linear-system solving.
6. **Tensors:** typed axes, products, contraction, permutation, reshape, broadcasting only under declared rules, and sparse tensor support.
7. **Graphs:** adjacency, degree, path, reachability, connected components, cycle detection, and graph transforms.
8. **Hypergraphs:** incidence, degree, hyperpaths, cuts, projections, duals, and typed multi-endpoint relations.
9. **Transformations and lifting:** embeddings, projections, normalization, coordinate transforms, lifting/lowering between representations, composition, and inverse conditions.
10. **Meta-mathematics:** invariants, equivalence, proof/evidence objects, dependency analysis, optimization proposals, program synthesis, and cross-layer consistency.

These are not ten arbitrary neural-network layers. They are ten typed mathematical execution/evaluation layers. A transformation may span multiple layers, but every step remains explicit, typed, bounded, and replayable.

## 5. Bootstrap curriculum and runner-ring sequence

Training is staged. A later stage cannot silently bypass an earlier acceptance gate.

### Stage A — raw NaN bit and instruction grounding
- Ingest raw NaN0S corpus words with byte/word offsets, source digest, and provenance.
- Separate opcode, operand, literal-data, metadata, malformed, and unknown classes.
- Train bit masks, shifts, comparisons, arithmetic, branches, loads/stores, calls, and returns from executable fixtures.
- Execute candidates only in isolated NaN16 scratch; compare output and trace against frozen fixtures.
- Keep corpus text as data unless a specific compilation task authorizes parsing it as source.

### Stage B — Boolean bootstrap in BS0
- Exhaust all 16 two-input Boolean functions and four input combinations.
- Test composition, complements, masks, mirror transforms, and 32-lane behavior.
- Build verified predicates for route parsing, bounds, ownership, replay, type checks, and budget checks.
- Freeze a versioned Boolean fixture root before advancing.

### Stage C — memory and address grounding in HW0
- Start with virtual scratch registers, never speculative physical writes.
- Map address width, word/byte units, alignment, bounds, endianness, aliasing, and file offsets.
- Enumerate all 256 logical points and 32,640 unordered distinct-point pairs. Point 0 remains reserved for NaNNet. Mirror relations are explicit; directional transforms store both directions.
- Each binding is observed, inferred, or verified, with evidence. A discovered physical register never changes the logical topology.
- Use canary values and one-variable-at-a-time probes only where the platform explicitly permits safe reads/writes.

### Stage D — mathematical object and operator learning in MC0
- Build schemas and small exact fixtures before large objects.
- Register vector and hypergraph objects as typed endpoints.
- Train each operator from input schema + formal definition + reference examples + counterexamples.
- Generate candidate NaN16 implementations and compare them with the reference evaluator.
- Store each accepted definition, program, test result, and dependency edge under immutable digests.

### Stage E — cross-layer integration
- NaNLMROUTER issues tasks to BS0, MC0, or HW0 with runner identity, epoch, checkpoint, endpoint digests, operator version, scratch reservation, step/time limits, expected output schema, and expiry.
- Runner responses contain status, output digest, trace digest, resource counters, capability observations, and evidence references.
- MC0 verifies the response independently; it does not accept a worker's self-reported success as proof.
- The same task corpus and deterministic seed produce comparable results across local, Android, and GitHub CPU runners.

## 6. MC0 feedback and evolution loop

Every object, operator, and relation has a lifecycle: proposed → staged → validated → active → superseded/tombstoned. Rejected candidates remain in the audit journal but never enter active indexes.

The evaluator loop:
1. Walk every namespace alias and verify it resolves to a canonical digest.
2. Recompute canonical digests and check schema versions.
3. Check that every dependency exists and every relation endpoint has a valid type.
4. Re-run deterministic operations where affordable.
5. Run unit, property, metamorphic, mirror-pair, and cross-runner reproducibility tests.
6. Score coverage, use frequency, confidence, contradiction count, redundancy, resource cost, and unresolved dependencies separately.
7. Propose additions, alternate definitions, optimizations, or tombstones. Do not silently rewrite definitions.
8. Run proposals in isolated scratch snapshots with bounded CPU, memory, and wall-clock budgets.
9. Compare proposals against frozen fixtures and an independent evaluator.
10. Append evidence and either promote atomically or reject. Promotion changes a checkpoint root; it never rewrites history.

Pruning is a tombstone plus a reason and replacement edge, not deletion. A low-use object may still be foundational. Usage frequency is not a correctness metric. The evaluator, router trust policy, hash canonicalizer, and promotion rules are protected roots and cannot be modified by an ordinary candidate.

## 7. Scratch/register sandbox model

Virtual scratch registers are named by owner, task, epoch, logical point, and offset. A task sees only its reservation. Reads outside the reservation return a typed bounds failure. Writes are copy-on-write into the task snapshot. Commit creates a candidate snapshot; abort discards it. Scratch may contain temporary vectors, matrix blocks, hyperedge work queues, test traces, and compiler intermediates.

The allocator's first prototype accounts in 4 KiB pages:
- BS0DB: 1 GiB quota.
- MC0DB: 7 GiB quota.
- 2 GiB reserved headroom.
- 64 in-memory allocation records.

This is not yet a 10 GiB physical allocation, not durable storage, and not crash-safe. Next storage milestones are a NaN16 storage ABI, append-only journal records, checksummed page files, recovery scan, checkpoint manifest, lease expiry, and a power-failure test.

## 8. Immediate implementation sequence

1. Keep the scratch quota regression in CI and correct any guest assembler/runtime failures before extending the allocator.
2. Add allocation owner/task/epoch/lifetime fields and reject cross-task release.
3. Add a bounded append-only journal API and replay/recovery test.
4. Extend object records from small integer digest markers to canonical byte serialization and full SHA-256.
5. Add explicit operator selector parsing to the two-endpoint route grammar.
6. Add an operation execution ABI: resolve → reserve → dispatch → validate → hash → journal → publish.
7. Implement the two-endpoint `::op:<id>::` expression parser and operation execution ABI; then add exact vector operations (copy, add, scale, dot) with dimension/domain negative tests.
8. Add hypergraph incidence and projection fixtures.
9. Add MC0 full-tree audit and staged tombstone/promotion records.
10. Expand the ten-layer suite only when the previous layer's regression root is frozen.

## 9. What is implemented versus planned

Implemented prototypes in the current branch family include the canonical single-object route parser, initial vector/hypergraph key decoding, small object/operator registry, bounded task ledger, an in-memory BS0DB/MC0DB scratch quota manager with reserve/release/stats regression coverage, an MC0 candidate lifecycle kernel, and typed two-endpoint operator resolution. The candidate kernel records parent checkpoint, required passing-evidence count, pass/fail evidence, staged/rejected/promoted state, blocks promotion when evidence is insufficient or any evaluation fails, and rejects repeated promotion. Scratch-quota, candidate-lifecycle, and typed mathematical-dispatch guest regressions now pass the NaN0S core CI gate. They are not yet the complete persistent databases, full SHA-256 object store, ten-layer evaluator, or authenticated network-dispatched runner ring. CI is the authority for whether the new quota regression actually passes.


## 8. Implemented NaN16 execution slice (2026-11-06)

The companion NaN0S branch now has a tested, bounded first operation execution path beyond type resolution:

- Object records have a fifth field for a guest-memory payload pointer. The existing immutable object identity remains keyed by object ID, kind, owner point and digest marker; binding the memory pointer is a separate operation.
- `nmr_obj_bind_data` currently accepts vector objects only. It binds a nonzero guest word address once, accepts an identical rebind idempotently, and rejects a different address rather than silently redirecting an object.
- `nmr_obj_get` returns the bound data pointer in an additional result register. `nmr_resolve` returns both endpoint data pointers together with the output kind and program ID after validating the registered ordered type signature.
- Program ID 78 dispatches to NaN16 `vector.add.i32.v1`. Its direct-call ABI is left pointer, right pointer, dimension, output pointer; it accepts dimensions 1–16 and performs word-wise addition modulo (2^{32}). The implementation is a guest NaN16 routine, not a host-side math callback.
- The end-to-end test registers two vector endpoints and an immutable operator, binds the payload addresses, resolves the operator, takes the two addresses from the resolver (not caller-supplied guesses), dispatches program 78, and checks the result `[11,22,33,44]`. It also checks idempotent binding, conflicting rebinding, zero/oversized dimensions, and unknown program IDs.
- `tools/math_execute_gate.sh` is part of the NaN0S Android workflow. GitHub Actions run `42332779973` passed the core-tests job, including all NaN toolchain gates with this execution test.

**Scope limits:** the object and operator tables are still bounded in-memory bootstrap structures. The payload pointer is a guest-memory address, not a durable content-addressed object location. Digest values are test markers, not SHA-256. There is no persistent MC0DB journal, crash recovery, general bounds-aware object allocator, or arbitrary indirect program execution. The dispatcher is an explicit allowlist and rejects unknown program IDs.

### Next mathematical execution increments

1. Add a second exact vector operator (dot product) using only documented NaN16 integer/Boolean operations, with overflow semantics stated explicitly and an independent reference fixture.
2. Add typed scalar result-object publication and derivation provenance; do not overwrite either input object.
3. Add vector shape/bounds metadata and scratch reservations to the execution contract before allowing non-fixture payloads.
4. Extend the same pattern to matrices and graph/hypergraph operations only after independent evaluator tests exist.
