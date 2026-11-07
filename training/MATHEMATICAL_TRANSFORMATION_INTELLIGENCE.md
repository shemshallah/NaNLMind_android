# Mathematical Transformation Intelligence (MTI)

**Status:** research and implementation specification; not a claim that the full system is implemented.  
**Integration target:** the single canonical `GM0:SC0:MC0:HW0:BS0` universe, with NaNLMROUTER as the only control/data-plane path and six internal BS0 runner slots `R0–R5`.

## 1. Objective

Build a system that learns executable mathematical transformations and, equally importantly, learns when each transformation is valid, how it composes with other transformations, how it can be proved equivalent to alternatives, how it fails, and what it costs on available hardware.

The learned unit is not merely a formula or token sequence. It is a typed, versioned transformation contract plus its implementations, preconditions, postconditions, proof/test evidence, counterexamples, provenance, and measured cost profile.

The system must distinguish:
- mathematical semantics from one particular implementation;
- a theorem from a finite test result;
- a proven equivalence from an observed agreement;
- an undefined operation from a valid operation returning zero;
- a hardware measurement from a portable cost estimate;
- a candidate from an independently accepted canonical object.

## 2. Transformation record

Every transformation is registered as an immutable, versioned object with at least:

1. **Identity:** stable operator ID, semantic version, canonical encoding version, content digest, provenance.
2. **Signature:** ordered input types, output types, arity, output count, scalar/vector/tensor shape variables, bit widths, signedness, field/domain parameters.
3. **Domain:** integer, rational, Boolean, finite field, modular ring, vector space, matrix space, tensor space, permutation group, or explicitly declared abstract carrier.
4. **Preconditions:** shape compatibility, nonzero divisor, invertibility, rank, field characteristic, modulus constraints, bijectivity, bounds, and other applicability predicates.
5. **Semantics:** formal definition, total/partial status, exactness model, overflow behavior, canonical output representation, and exceptional outcomes.
6. **Implementation:** NaN16 program identity, reference evaluator identity, optional optimized implementations, required capabilities, and determinism declaration.
7. **Postconditions:** output type/shape, invariants, range constraints, conservation laws, and relational guarantees.
8. **Evidence:** truth tables, exhaustive enumeration bounds, proofs/certificates, property tests, metamorphic tests, counterexamples, independent evaluator results, and replay traces.
9. **Cost:** operation counts, depth, fan-in/fan-out, temporary storage, peak scratch, memory traffic, communication, latency distribution, energy estimate where available, and hardware/profile provenance.
10. **Lineage:** input object digests, parent transformations, derivation operation ID, candidate ID, reviewer/evaluator evidence, and promotion state.

Preconditions are executable predicates. The dispatcher checks them before execution; a failed precondition returns a typed rejection with the failed predicate and evidence, never a guessed result.

## 3. Mathematical curriculum

### M0 — Boolean functions and predicates

Enumerate all 16 two-input Boolean functions and exhaustively test all four input assignments. Extend to:
- NOT, AND, OR, XOR, NAND, NOR, implication, equivalence, constants;
- arbitrary truth-table-defined functions for increasing input counts;
- predicate composition, conditional selection, masking, comparison and bit extraction;
- truth-table minimization and canonical representations.

Record input arity, truth table, canonical expression, gate count, depth, fan-in, fan-out, and equivalent implementations. For n inputs, exhaustive truth tables contain 2^n rows and 2^(2^n) possible Boolean functions; the curriculum must expose this combinatorial growth and use bounded enumeration rather than silently pretending exhaustive coverage at large n.

### M1 — Circuits and multi-output transformations

Represent circuits as typed directed acyclic graphs with explicit input/output ports. Vary depth, fan-in, fan-out, gate library, and output count.

Learn:
- serial composition and parallel composition;
- shared-subexpression extraction;
- multi-output circuits that reuse intermediate values;
- circuit equivalence and output-wise equivalence;
- depth/size trade-offs, critical paths, fan-out costs and scheduling;
- constant propagation, dead-cone elimination, factoring and decomposition.

A multi-output operator is one transformation with an ordered output signature, not a bag of unrelated scalar functions. Evidence must include output ordering and cross-output invariants.

### M2 — Reversible Boolean functions and permutations

Model an n-bit reversible Boolean transformation as a bijection on the 2^n-element state space. Store its permutation representation when tractable, inverse permutation, cycle decomposition, and composition order.

Train on:
- bijection detection and collision counterexamples;
- inverse construction and round-trip proofs;
- permutation composition, identity and order;
- reversible gate decompositions (including controlled and swap-style gates);
- ancilla/work-bit requirements, garbage outputs and uncomputation;
- reversible embeddings of functions that are not themselves bijections.

Never label a many-to-one function reversible merely because a tested subset happened to be collision-free. Distinguish a permutation on a fixed-width state space from an injective embedding with retained auxiliary information.

### M3 — Exact scalar arithmetic

Separate integers, rationals, finite fields and modular arithmetic as different typed domains.

Integer operations: addition, subtraction, multiplication, comparison, Euclidean division with explicit quotient/remainder convention, gcd, powers, bit operations and overflow policies.

Rational operations: normalized numerator/denominator pairs, denominator nonzero, gcd reduction, exact equality, sign normalization and growth costs.

Finite-field operations: field identity, characteristic, extension degree, irreducible polynomial where applicable, modular reduction, inverses for nonzero elements, and proof that the selected modulus defines the intended field.

Modular arithmetic must not be conflated with finite-field arithmetic: Z/nZ is a field only when n is prime. Every result carries its domain parameters.

### M4 — Vectors and linear transformations

Typed vectors carry field/domain, dimension, coordinate convention, and storage layout. Learn vector addition, scaling, dot products where defined, norms only when the required structure exists, projections, basis changes and linear maps.

Preconditions include matching dimensions and compatible scalar fields. Costs include arithmetic count, sparsity, memory traffic and communication. Distinguish exact algebraic operations from floating-point approximations.

### M5 — Matrices and matrix transformations

Learn matrix addition/multiplication, transpose, block decomposition, rank, determinant, solve, inverse where it exists, factorization, basis transformation and composition.

Conditions include shape compatibility, square/invertible requirements, pivot conditions, field/domain constraints and singularity. Counterexamples should include dimension mismatch and singular matrices. Multi-output decompositions (for example factorization outputs) must preserve their joint reconstruction invariant.

### M6 — Tensors and higher-order transformations

Represent rank, dimensions, strides, index labels, layout and scalar domain explicitly. Learn products, contraction, permutation, reshape, broadcasting only under declared rules, slicing, reductions, sparse transforms and tensor-network composition.

A reshape is valid only when element-count and layout semantics permit it. A contraction records contracted axes and resulting index order. The system must not infer shape semantics from payload length alone.

### M7 — Relations, graphs and hypergraphs

Learn finite relations, functions, adjacency structures, paths, reachability, connectedness, graph homomorphisms, graph products and hypergraph incidence/projection. Preserve directionality and multiplicity where the mathematical object requires them.

### M8 — Abstract transformations and algebraic structure

Generalize beyond concrete arrays to typed carriers and structure-preserving maps: monoids, groups, rings, fields, vector spaces, linear maps, homomorphisms, isomorphisms, embeddings, quotients and functors where formalized.

The system must record which axioms are assumed and which have been verified. It must not infer a group merely from a few observed operations.

### M9 — Meta-mathematics and synthesis

Learn equivalence classes, invariants, proof composition, counterexample-guided synthesis, rewrite systems, dependency graphs, operator discovery, complexity bounds and proof-carrying optimization.

This layer proposes candidates. It cannot alter trust policy, evaluators, router authorization or canonical state without independent acceptance.

## 4. Conditions of use are first-class training targets

For every operator, train and evaluate four separate questions:

1. **Recognition:** does the input have the required type and shape?
2. **Applicability:** are all mathematical preconditions satisfied?
3. **Execution:** does the implementation produce the specified output?
4. **Justification:** what evidence supports correctness, equivalence, or rejection?

The training set must contain positive and negative examples. Negative examples are not noise: they teach domain boundaries. Examples include division by zero, mismatched vector dimensions, singular inverse requests, non-prime moduli presented as fields, non-bijective reversible candidates, invalid tensor contractions and integer overflow under a checked policy.

A condition record includes predicate ID, variables, domain, evaluation result, failure reason, and evidence source. A model prediction alone cannot certify a precondition.

## 5. Evidence and learning corpus

Store linked, typed records rather than flattening everything into prose:

- **Truth-table record:** input width, row ordering, output vector, canonical digest.
- **Execution record:** operator/version, typed inputs, parameters, outputs, trace, resource counters.
- **Proof record:** proposition, assumptions, proof method, dependency IDs, certificate/checker version, verified status.
- **Equivalence record:** two operator IDs, domain and assumptions, proof/certificate or bounded test scope, status (proven, refuted, observed-only, unknown).
- **Counterexample record:** exact input object, violated claim/precondition, expected versus actual result, minimized witness and reproduction trace.
- **Type/shape record:** scalar domain, rank, dimensions, strides, capacity, units/index conventions and validator result.
- **Cost record:** operation counts and measured resource vector, hardware identity/capability snapshot, sample count, variance and measurement method.
- **Composition record:** ordered operator DAG, intermediate types, shared nodes, outputs and whole-graph evidence.
- **Curriculum record:** prerequisite concepts, difficulty, coverage, held-out partitions and mastery evidence.

Proofs and tests must be distinguishable in storage and in every UI/report. Exhaustive enumeration proves a finite-domain statement only over the enumerated domain. Randomized/property testing is empirical evidence unless paired with a proof.

## 6. Equivalence, counterexamples and canonicalization

Maintain an equivalence graph over typed operators, with edges carrying proof status, domain restrictions and evidence. Do not merge nodes merely because outputs matched on a finite sample.

Use counterexample-guided refinement:
1. propose a rewrite or candidate implementation;
2. compare against the reference semantics on bounded domains;
3. search for a counterexample;
4. minimize and persist any witness;
5. if no witness is found, record only bounded/observed status;
6. seek a proof or independent certificate;
7. promote only after the required evaluator policy passes.

Canonicalization is domain-sensitive. Algebraic rewrites valid over rationals may fail over fixed-width integers or finite fields. Every rewrite carries assumptions such as commutativity, associativity, field laws, nonzero divisors, exact arithmetic, and shape constraints.

## 7. Cost is a vector, not one scalar

Store a portable structural cost and a measured hardware cost separately.

Structural cost:
- circuit size, depth, fan-in/fan-out;
- arithmetic operation counts by type;
- asymptotic class where justified;
- intermediate object sizes and peak live values;
- serial dependency depth and available parallelism.

Measured cost:
- latency distribution, throughput and variance;
- scratch and persistent memory;
- bytes read/written and network transfer;
- energy/power only when measured with a declared method;
- runner/hardware capability, runtime version, clock state and measurement provenance.

A dispatcher may optimize a weighted objective, but the raw cost vector must remain available. A cheap implementation that violates a precondition or lacks required evidence is not eligible.

## 8. Representation and execution pipeline

Use one canonical typed transformation DAG with multiple faithful views:
- mathematical definition and assumptions;
- typed operator graph;
- Boolean/circuit or arithmetic intermediate representation;
- NaN16 program and operand representation;
- execution trace and evidence graph.

The views are projections of the same canonical object, not independently editable competing truths. Compilation and optimization produce new versioned candidates with lineage.

Dispatch flow:
1. resolve owner-checked canonical endpoints through NaNLMROUTER;
2. load immutable type/shape metadata;
3. resolve exactly one operator signature;
4. evaluate preconditions and authorization;
5. reserve task-scoped scratch and enforce budgets;
6. execute a pinned NaN16 program;
7. validate output type, shape, invariants and digest;
8. append derivation/evidence/cost records;
9. return a typed result and trace reference.

Unknown, ambiguous, stale, unauthorized, over-budget or ill-typed requests fail closed.

## 9. Fit with BS0 / MC0 / HW0 and Hermes

- **BS0:** Boolean semantics, predicates, truth-table fixtures, circuit primitives and low-level control correctness.
- **MC0:** canonical mathematical object/operator registry, type and shape metadata, dependency/equivalence graph, proof/evidence index, curriculum, candidate evaluation and promotion journal.
- **HW0:** physical capability observation and cost measurement for approved programs; it does not redefine logical topology.
- **NaNLMROUTER:** sole task/result route, identity/lease checks, sequencing, replay protection, budgets and typed dispatch.
- **Hermes:** a thin orchestration and user-facing tool surface for requesting mathematical tasks, inspecting evidence, comparing implementations and initiating approved evaluations. Hermes is not a second registry, evaluator authority, runner universe or credential store.

The six internal runner slots R0–R5 are schedulable execution capacity inside the one BS0 universe. Task specialization is metadata and scheduling policy, not a permanent identity or separate universe. No runner-to-runner private bypass is introduced.

The Hermes adapter should expose narrow operations such as:
- inspect a registered operator and its conditions;
- execute a typed transformation against authorized object references;
- request equivalence analysis with an explicit evidence level;
- retrieve counterexamples and proof/evaluation artifacts;
- compare implementations and cost profiles;
- submit a candidate for isolated evaluation.

It must not expose arbitrary NaN16 execution, raw credential access, unrestricted object mutation, or evaluator/trust-policy overrides. Every tool maps to a documented router operation and declares its actual capability and approval behavior.

## 10. Incremental acceptance plan

**Gate A — Boolean correctness:** all 16 two-input functions × four assignments; composition, truth-table canonicalization and negative fixtures.

**Gate B — circuit growth:** bounded exhaustive generation by input count, depth and fan-in; multi-output shared-cone tests; structural cost accounting.

**Gate C — reversibility:** exhaustive bijection checks for small widths, inverse and composition round trips, collision witnesses for non-bijections.

**Gate D — exact arithmetic:** integer/rational/finite-field separation, domain parameter checks, exact reference comparisons and invalid-domain counterexamples.

**Gate E — vectors/matrices:** shape-derived dimensions, mismatch rejection, singularity witnesses and multi-output reconstruction invariants.

**Gate F — tensors/abstract structures:** explicit index/shape contracts, axiom metadata and structure-preserving map checks.

**Gate G — evidence and persistence:** immutable proof/test/counterexample/cost records, content addressing, append-only journal and crash recovery.

**Gate H — dispatch and learning:** authenticated owner/task/epoch-bound route, independent evaluator, held-out challenge set, resource budget and candidate promotion.

Do not report a gate as passed because its design is documented. Report implementation, CI, runtime, and independent-evaluation status separately.

## 11. Immediate implementation priorities

1. Preserve the current 16-function Boolean fixture and connect its results to typed evidence records.
2. Extend operator signatures to ordered multi-output signatures and shape variables.
3. Add small-width exhaustive permutation/bijection fixtures and inverse generation.
4. Add domain-tagged exact integer, rational and finite-field scalar objects.
5. Add equivalence status taxonomy and minimized counterexample records.
6. Add structural cost counters before attempting hardware-specific optimization.
7. Bind all new records to the existing owner/task/epoch and candidate lifecycle; do not create a parallel ledger.
8. Keep MC0DB/BS0DB durability, authenticated dispatch, trusted timebase, independent evaluation and actual BS0/MC0/HW0 network routing as explicit blockers until implemented and tested.

## 12. Core principle

The system should learn not just **what transformation computes**, but **what it means, when it is legal, why it is correct, when it is equivalent, how it can fail, and what resources it consumes**. Mathematical capability grows by adding verified transformations and evidence—not by promoting plausible outputs into facts.
