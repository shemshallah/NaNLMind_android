# BS0 / MC0 / HW0 runner-ring mathematical bootstrap

Status: implementation contract and first integration target. This document does not claim that the ring, database, or ten mathematical layers are already implemented.

## Objective

Train BS0, MC0, and HW0 as one logically linked system through NaNLMROUTER. Bootstrap from Boolean logic, learn the NaN16 instruction language and memory-address model, map bounded scratch/register space, and progressively construct a content-addressed mathematical object graph. MC0 is the persistent integration, evaluation, and meta-linkage layer; BS0 supplies Boolean bootstrap and verified routing predicates; HW0 contributes hardware/runner observations and executable runner/ring capabilities.

All training policy, semantic interpretation, mathematical operations, scoring, and candidate evolution remain NaN16 guest programs. Host code is restricted to bounded I/O, transport, clocks, and byte-buffer translation.

## Non-negotiable invariants

1. No corpus, remote observation, or runner proposal writes directly to the active canonical model or database.
2. Every experiment is bounded by an explicit scratch allocation, instruction budget, time budget, and rollback checkpoint.
3. Every discovered register mapping is marked observed, inferred, or verified; never silently promote inference to fact.
4. A mathematical object is immutable once content-addressed. Changes create a new object and a provenance edge.
5. Every operation records operator ID/version, ordered inputs, parameters, preconditions, output digest, test results, runner identity, and parent checkpoint.
6. The double-colon operator is an operation boundary only when the router resolves a registered, typed operator. Otherwise it is a path separator/reference and must not execute arbitrary content.
7. Unknown opcodes, malformed addresses, unverified endpoints, budget overruns, and failed invariants trap or reject closed.
8. Candidate evolution is append-only until an independently passing evaluator authorizes transactional promotion. Pruning is tombstoning, never destructive deletion of provenance.
9. Hashes establish identity and integrity, not truth. Mathematical validity comes from executable checks and proof/test evidence.
10. All runner communication and state transitions go through NaNLMROUTER; no private runner-to-runner bypass.

## Runner responsibilities and ring contract

- **BS0**: Boolean bootstrap, predicate evaluation, truth-table fixtures, route-selection logic, and verified low-level control predicates.
- **MC0**: canonical object registry, relation index, mathematical dependency graph, evaluator orchestration, provenance, candidate scoring, and promotion/rollback journal.
- **HW0**: hardware and runner discovery, physical capability observations, scratch-window probes, ring timing/capability reports, and bounded execution of approved NaN16 programs.
- **NaNLMROUTER**: only control/data plane between these roles. It authenticates runner identity, validates route grammar and message schema, sequences work, enforces budgets, and returns typed results.

A ring task contains task ID, epoch, source checkpoint, runner ID, target logical point, operation ID, input object hashes, scratch reservation, step/time limits, expected output schema, and expiry. The current NaN16 task-ledger prototype stores these fields and enforces unique task IDs, point 1–255, runner/epoch ownership, one-time claim, and one-time completion. Expiry-clock enforcement, authenticated runner identity, output-schema validation, durable persistence, and actual BS0/MC0/HW0 routing remain follow-on work. A response contains task ID, runner ID, observed capabilities, status, output hash, trace hash, resource counters, and evidence references. A result is not trusted merely because a runner returned it.

## Bootstrap curriculum

### B0 — Boolean foundations (BS0-led)

Exhaustively generate all 16 two-input Boolean truth tables from the NaN word class nibble. Use canonical Boolean words (`false = 0`, `true = 0xFFFFFFFF`) for both inputs and outputs; integer `1` is not the canonical true mask for bitwise Boolean operations. Verify each of four input combinations, composition, mirror/byte-swap behavior, and the 32-lane constants. Train NOT, AND, OR, XOR, NAND, NOR, implication, equivalence, constants, and conditional predicates. Truth-table fixtures are the first hard acceptance gate. A NaN16 fixture now enumerates all 16 two-input truth-table opcodes and checks all four input combinations (64 assertions); CI status is the completion criterion. The NaN0S branch also contains a bounded task-ledger prototype for enqueue → owner/epoch claim → completion, with duplicate-ID, reserved-point, wrong-runner, wrong-epoch, and replay rejection checks. This ledger is a scratch prototype, not yet authenticated network dispatch or durable MC0DB storage.

### B1 — Raw NaN16 language

Use the NaN0S corpus as source material, preserving raw machine words and source provenance. Separate legal instruction words and opcode/operand structure; data words and literal bit patterns; malformed words and trap cases; NaN16 source programs and assembled instruction streams. The trainer parses instruction structure, executes only inside the NaN16 guest, compares results against reference fixtures, and retains byte/word-level lineage. Corpus text is data unless an explicitly selected NaN16 compilation task says otherwise.

### B2 — Logical-point and physical-capability mapping (HW0-led)

The logical topology has exactly 256 palindromic points, numbered 0–255, in every universe. Point 0 is reserved for the NaNNet interface; points 1–255 are the remaining canonical logical points. BS0 owns this invariant and it is never redefined by hardware discovery. HW0 maps physical hardware/runner capabilities onto these canonical points and reports each binding as verified, unavailable, reserved, or unknown. A logical point exists even when no physical register backs it. For each physical binding, record width, access mode, reset value, read/write behavior, alias candidates, mirror partner, runner/architecture, and confidence. Probe one variable at a time, with canary regions and before/after snapshots. Never probe reserved or unmapped hardware by speculative writes.

### B3 — Memory and address semantics

Teach byte/word addressing, bounds, alignment, endianness, file offsets, tree nodes, endpoint identity, and object lifetime. Point 0 remains the NaNNet interface reservation; its presence in the 256-point topology does not make it a general-purpose hardware register. Address resolution must be deterministic and typed. An address resolving to a file does not itself authorize execution.

### B4 — Mathematical object construction

Create canonical objects for booleans, sets, relations, functions, scalars, vectors, matrices, tensors, graphs, hypergraphs, transformations, proofs, and definitions. Each object has a schema, canonical encoding, validator, evaluator, and fixture set.

### B5 — Ten mathematical layers

1. Boolean predicates and truth tables.
2. Discrete sets, relations, functions, and finite maps.
3. Numeric domains and exact arithmetic.
4. Vectors, bases, norms, and defined dot/cross products.
5. Matrices, linear maps, rank, composition, and inverse where defined.
6. Tensors, products, contraction, reshape, and index transforms.
7. Graphs, adjacency, paths, connectedness, and graph transforms.
8. Hypergraphs, multi-endpoint incidence, hyperpaths, and projections.
9. General transformations, embeddings, lifting, projection, normalization, and composition.
10. Meta-mathematics: equivalence, invariants, proof objects, dependency analysis, optimization, and program synthesis.

This is a curriculum decomposition, not an assertion that every operation applies to every object. Every operator declares input/output types and domain preconditions. Undefined operations return typed failures, not fabricated values.

### B6 — Feedback and evolution

MC0 schedules audits over every registered object and operator:
1. verify canonical encoding and stored digest;
2. verify all referenced dependencies exist;
3. validate types, dimensions, domains, and preconditions;
4. replay deterministic operations when feasible;
5. run unit, property, metamorphic, and mirror tests;
6. measure usage, coverage, confidence, contradiction, redundancy, and cost;
7. propose additions, rewrites, alternate definitions, or tombstones;
8. run candidates in isolated scratch snapshots;
9. compare against frozen regression fixtures and an independent evaluator;
10. append accepted candidates and provenance; otherwise retain rejection evidence.

No self-modification of the evaluator, router policy, trust rules, or canonical registry is permitted from an unverified candidate.

## Canonical address and object model

Example object endpoint:

    nan://GM0:SC0:MC0:BS0:HW0::HW0:1::vector1337

The address parses into a typed route: global/space/runner scope GM0:SC0:MC0:BS0:HW0; owner and logical point HW0:1; object key vector1337.

A two-endpoint expression:

    nan://GM0:SC0:MC0:BS0:HW0::HW0:1::vector1337::hypergraph42

does not blindly execute. NaNLMROUTER resolves the pair to an operator declaration, checks declared signatures and policy, then dispatches a typed operation. If no unique operator is registered, resolution fails without side effects.

### Object record

Each tree endpoint stores:
- magic/version and object kind;
- canonical NaN16 payload and encoding version;
- type signature, dimensions, coordinate/index conventions;
- formal definition and domain constraints;
- executable validator/evaluator program IDs;
- dependency and incidence references;
- mirror/logical-point/ring ownership metadata where relevant;
- content digest, parent operation digest, and provenance;
- fixture IDs, evaluation summary, and trust status.

### Operation record

An operation is itself an addressable NaN16 program. Its immutable record includes operator ID and semantic version; ordered input object digests and type signatures; parameter object digest; preconditions and resource bounds; program digest; output object digest; deterministic/replayability declaration; evaluator and fixture digests.

Use domain-separated hashing over canonical serialization, e.g. SHA-256 of the prefix NAN-MATH-OP, schema version, operator ID/version, ordered input digests, parameter digest, and program digest. The operation key identifies the derivation. The result object's own digest identifies the canonical output. A hash is never a substitute for semantic correctness.

## Mirror pairs and 256 palindromic points

Every universe has exactly 256 canonical palindromic points, numbered 0–255. Point 0 is reserved for the NaNNet interface; points 1–255 remain available as logical runner/register addresses. This is a topology invariant defined by BS0, not a count of detected physical hardware devices or registers. HW0 only binds physical capabilities to the existing logical points.

The mirror-pair fixture enumerates unordered pairs of **distinct** points:

- 32,640 mirror pairs: C(256, 2) = 256 × 255 / 2.
- Zero self-pairs in this pair set; self-relations, if needed, are a separate relation class and are not counted as mirror pairs.
- Point 0 participates in the 256-point topology as the reserved NaNNet interface point; the reservation prohibits treating it as an ordinary HW0 register.

Each pair is stored once in canonical endpoint order. Where an operation is directional, its two ordered directions are represented explicitly as separate directed relations. Record the mirror transform, endpoint logical-point identities, physical binding/capability state, compatibility, and test result. Do not assume a mirrored pair is symmetric.

## Scratch database and memory budget

The stated 10 GiB is a combined ceiling for BS0DB and MC0DB, not a target to allocate eagerly. Initial policy:
- at most 1 GiB for BS0DB bootstrap fixtures, route tables, and bounded scratch;
- at most 7 GiB for MC0DB canonical objects, graph indexes, operation records, and evaluation caches;
- at least 2 GiB unallocated headroom for compaction, checkpoints, and growth;
- quota changes only through measured capacity reports and an explicit versioned policy.

Every allocation is tagged by owner, task, epoch, lifetime, and quota class. Scratch is disposable and never confused with canonical objects. Checkpoints and provenance are durable. Large blobs are content-addressed; databases store metadata and indexes, not duplicate copies. Use append-only journals and periodic verified compaction.

## Minimum acceptance gates

1. Boolean: all 16 functions × 4 input pairs pass; mirror identities match reference.
2. Raw language: valid/invalid instruction classifications match fixtures; malformed instructions trap.
3. Logical-point map: all 256 canonical points are represented; point 0 is reserved for NaNNet, and HW0 separately reports physical bindings for points 1–255 as verified, unavailable, reserved, or unknown. No unsafe write probes.
4. Pair map: all 32,640 unordered distinct-point pairs across 256 palindromic points are represented exactly once; self-relations are separate and directional tests are explicit.
5. Address parser: valid route resolves; malformed scope/point/object and ambiguous operation reject closed.
6. Content addressing: identical canonical operation inputs reproduce operation/result hashes; changed input or version changes the digest.
7. Hypergraph: incidence, degree, projection, and round-trip fixtures pass.
8. Ten layers: every registered operator has typed signatures, domain preconditions, reference fixtures, and negative tests.
9. Evolution: candidate additions/revisions/tombstones are staged, replayable, and rollback-safe.
10. Ring: BS0/MC0/HW0 exchange sequenced tasks through NaNLMROUTER; invalid, stale, duplicate, or over-budget tasks are rejected.
11. Persistence: crash/restart recovers the last committed checkpoint and journal without partial promotion.
12. Capacity: quota enforcement and high-water reporting stay within the 10 GiB combined limit.

## First implementation slice

Implement the address parser and operation registry first, then the Boolean bootstrap fixture set and ring task/result envelope. Initial executable slices now exist in NaN0S: `src/nannet/boolean_bootstrap_test.nas`, `src/nannet/math_registry.nas`, and `src/nannet/runner_task.nas`, each with guest test coverage and CI gates. They are bounded prototypes; the complete router, persistent registries, and mathematical training curriculum are not yet complete. These create the safety and determinism foundation for later mathematical programs. Add a runnable NaN16 guest test for every operator as it is introduced. The existing N16TXT corpus scanner is an intake primitive, not yet a semantic trainer or mathematical memory engine.
