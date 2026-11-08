# NaN16 NMP worker kernel

The Android worker now has a dedicated NaN16 execution guest. It is separate
from the admission guest: admission decides whether a packet may enter the
queue; the worker executes only a leased packet whose native receipt binds the
selected function to the server-authorized program ID.

## Current allowlist

| NMP function | NaN16 program | Output |
|---|---:|---|
| \`math.vector.add.v1\` | 78 | rank-1 vector, 1–16 words |
| \`math.vector.dot.v1\` | 79 | scalar, 1 word |

The guest owns the selector-to-program mapping and calls the registry's
\`nmr_resolve\` before \`nmr_execute\`. Unknown operation selectors, unregistered
objects, mismatched object kinds, mismatched operator signatures, and unknown
program IDs fail closed. The numerical addition and dot product execute in
NaN16; Kotlin only stages bounded words and hashes the returned result words.

## Input boundary

\`N16NmpKernelWorker\` accepts a \`NmpResolvedVectorPair\` projection, not
arbitrary numeric arrays extracted from packet JSON. The caller must resolve
both object IDs, owner points, dimensions, and payload words from canonical
NaN0S guest state. Before guest execution, the worker checks:

- packet state is \`EXECUTING\`;
- durable job ID and packet job ID agree;
- lease owner, token, and epoch are present and packet lease epoch agrees;
- the native receipt records ALLOW and the program ID allowed for the packet's
  exact function ID;
- both ordered leaf IDs, versions, and SHA-256 digests match the packet inputs;
- object IDs and owner points are valid, and payload lengths equal the bounded
  vector dimension;
- the NaN16 guest's own shape registry confirms both vectors have the requested
  rank, dimension, unit stride, and sufficient capacity.

The worker returns output words, their SHA-256 digest, the resolved program
ID, guest step count, and the worker guest image/source digests. The upstream
registry stores a 32-bit digest marker rather than a full cryptographic hash;
the full 256-bit input digest is compared at the Android resolver boundary and
bound into the native admission receipt. The marker is not treated as a
standalone integrity proof. The
\`executeAndComplete\` path submits the output digest against the exact lease
returned by the router.

## Provenance

The NaN16 registry and vector dispatcher source files are vendored unchanged
from \`shemshallah/NaN0S\` main at commit
\`634b7a2d1116519b024c8efcce86b7a049c43b12\`:

- \`src/nannet/math_registry.nas\` (upstream blob
  \`819335a2f2ec33fde79a1153dba24482c271bc79\`)
- \`src/nannet/vector_ops.nas\` (upstream blob
  \`c0444a7ab2eedd34a1b2286a3a3e46d463a07fb2\`)

Gradle compiles these sources with
\`src/training/n16_nmp_kernel_guest.nas\` and \`tools/nmk.awk\` into a
separate image, label map, and source digest. The generated runtime assets are
included in the strict asset allowlist. CI also runs the registry-backed
`math_execute_test.nas` through the native NaN16 CLI; it checks vector add,
vector dot, immutable object/operation records, derivation provenance, invalid
dimensions, and unknown-program rejection. The guest-level regression prints
`PASS` only after both numerical kernels and the fail-closed cases succeed.

## Remaining integration boundary

This worker is an executable NaN16 kernel path, but the production
canonical-state resolver is still a separate integration task. The current
manual packet window must not be described as automatically resolving guest
objects. Until the resolver is wired to the running NaN0S object store, only
trusted internal callers that can provide a verified \`NmpResolvedVectorPair\`
may invoke this worker. Server-side lease and completion checks remain
authoritative; a worker result digest is execution evidence, not an independent
server recomputation of the numeric output.

## Live registry snapshot resolver (follow-up implementation)

`NmpLiveGuestResolver` can now obtain the two registered vector
objects directly from the memory of an **already-running** `NanVm`. It reads
the existing `nmr_obj_table` and `nmr_shape_table` through addresses in the
loaded guest's matching label map, then follows the registry's bound data
pointers. All reads take place in one `NanVm.inspectGuestMemory` critical
section, excluding concurrent execution and writes through that VM instance.

The resolver fails closed on absent or duplicate registry IDs, missing shapes,
non-vector kinds, owner points outside 1–256, non-rank-1 shapes, dimensions
outside 1–16, non-unit stride, insufficient capacity, mismatched dimensions,
malformed digest records, and changed payload bytes. For payload integrity
it recomputes the SHA-256 of the exact little-endian guest words against the
digest from the trusted object-store index, not a packet-provided digest.
The registry's 32-bit marker is additionally checked against the trusted
canonical content digest but is not itself sufficient proof of identity.

`executeAndCompleteFromGuest` now offers the worker a typed path from the
snapshot resolver into the existing packet, lease, native receipt and completion
gates. A deterministic memory-image regression exercises successful resolution
and failure cases without relying on an Android native VM in JVM unit tests.

**Integration still required:** the active desktop guest must actually contain
these exact registry tables, and their label map must be associated with the
same loaded image. The canonical object-store ingestion path must publish the
`NmpGuestLeafBinding` index (leaf ID, version, full content digest,
registry object ID, and full payload digest), and must serialize updates
to that index with the VM snapshot operation. Bindings may not be read from
packet JSON or manually authored projections. The current GUI/manual packet
workflow has not been wired to this trusted index, so production end-to-end
automatic resolution is **not yet certified**.

## Committed guest-binding index (next integration stage)

`NmpGuestBindingIndex` now provides a synchronized, bounded lookup boundary
between ordered packet leaf identities and the live NaN16 registry snapshot.
Canonical ingestion code can publish immutable `NmpGuestLeafBinding` records
after its own store/guest transaction commits, invalidate removed versions,
and clear the index when the guest image is reset or reloaded. The index
rejects duplicate object ownership, conflicting versions, stale versions,
missing bindings, malformed SHA-256 identities, and contradictory packet digest
aliases. A packet selects a leaf ID and version but cannot dictate a guest
object ID or payload digest.

The worker has a new `executeAndCompleteFromIndex` entry point that obtains
the pair through this index and `NmpLiveGuestResolver` before applying the
existing lease, admission-receipt, native execution, and completion gates.
JVM tests cover accepted and rejected bindings and index invalidation.

**Trust boundary still open:** this index is an in-memory mirror, not a new
canonical object store or proof of ingestion provenance. The real NaN0S
object-store ingestion owner still must publish verified committed entries,
coordinate guest mutations with the index monitor, associate registry labels
with the running guest image, and call `clearForGuestReload` on every reset
or reload. The manual packet workflow does not establish that provenance.
No production automatic resolution or end-to-end execution is claimed.

### Atomic canonical binding publication

Canonical ingestion may call `publishBatchCommitted(bindings)` to publish
1–32 verified bindings as one index transaction. The method validates the
entire batch against a detached copy of the current index, including
intra-batch object ownership conflicts, immutable versions, invalidation
tombstones and version high-watermarks. A rejected entry leaves every
preexisting index mapping unchanged and publishes none of the batch.
Single-record `publishCommitted` remains available for committed individual
records.

**This is index atomicity, not object-store or VM transaction atomicity.**
The trusted ingestion owner must first verify source provenance and coordinate
guest installation and index publication under the same external transaction
lock. Guest reloads must clear the mirror, and a live resolve must not race
with canonical writes. No NMP lease packet can authorize publication.

### Mixed canonical binding transactions

`applyCommittedTransaction(publications, invalidations)` now applies
a bounded (1–32 records) mixed transaction atomically to the binding mirror.
It rejects duplicated publication keys, duplicated invalidations, and
contradictory publication/invalidation of the same leaf version. Invalidations
are evaluated first so that a newly committed leaf can reuse a registry object
ID released by a revoked leaf *within the same verified transaction*.

All operations execute on a detached index copy before any live mapping,
tombstone or high-watermark is changed. Rejected transactions preserve the
old index unchanged. Regression tests cover ownership transfer, malformed
publication rollback, duplicated keys and contradictory operations.

This does **not** make VM memory and canonical storage transactional: the
trusted ingestion owner remains responsible for authenticating source
transactions, serializing VM writes with live resolution, and maintaining
crash/reload recovery from its own durable canonical journal.
