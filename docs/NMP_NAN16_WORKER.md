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
  vector dimension.

The worker returns output words, their SHA-256 digest, the resolved program
ID, guest step count, and the worker guest image/source digests. The
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
included in the strict asset allowlist.

## Remaining integration boundary

This worker is an executable NaN16 kernel path, but the production
canonical-state resolver is still a separate integration task. The current
manual packet window must not be described as automatically resolving guest
objects. Until the resolver is wired to the running NaN0S object store, only
trusted internal callers that can provide a verified \`NmpResolvedVectorPair\`
may invoke this worker. Server-side lease and completion checks remain
authoritative; a worker result digest is execution evidence, not an independent
server recomputation of the numeric output.
