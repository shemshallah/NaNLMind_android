# Android NaN16 mathematical admission integration

**Status:** Android guest-call adapter implemented alongside the canonical
NaNROUTER NaN16 admission routine. The Android host stages bounded 32-bit descriptors and payload words into an
isolated NaN16 VM and reads the returned status/output descriptor. Payload
pointers are remapped only inside that disposable VM; the caller's original
descriptor addresses are restored in the returned output metadata.

## Guest ABI

The Android asset `n16_math_admission.nan` includes the canonical composed
routine `mth_admit`. Its fixed mailboxes are exposed in
`n16_math_admission.labels`:

- candidate descriptor: 22 words
- authoritative rule descriptor: 17 words
- left and right leaf descriptors: 8 words each
- resolved operator contract: 10 words
- available capability bitset: 1 word
- reserved output descriptor: 8 words
- caller scratch: 48 words
- result status: 1 word

The guest executes rule/candidate freshness and digest checks first, then
typed leaf applicability, shape, capacity and precondition checks. Return
status 0 is the only allow result. 16–25 identify rule/candidate rejection;
32–41 identify typed applicability rejection. Numerical execution remains a
separate guest call and must not run on a nonzero status.

## Android boundary

`N16MathAdmission.evaluate` accepts descriptor arrays, writes them into the
guest VM's fixed word-addressed mailboxes, starts the NaN16 entry point, and
returns the status plus the guest-updated output descriptor. It does not
compute shapes, validate math, rewrite denials, or execute the selected
operator. Descriptor lengths are checked before guest invocation.

The guest source is vendored from the pinned NaNROUTER commit recorded in
`src/mth/PREFLIGHT_SOURCE_REVISION.md`; CI checks the three source files
byte-for-byte against that upstream commit and runs a native guest regression.
NaNLMind may propose a candidate, but only a zero guest status may authorize
the next NMP admission stage. The caller must still bind the successful
decision to candidate identity, snapshot, epoch, and full rule digest before
queue/lease/execute.

## Guest-issued rule snapshot receipt

On an allow result, the wrapper writes a fixed 14-word raw receipt in NaN16 memory: magic, ABI version, guest status, program ID, candidate epoch, authoritative rule epoch, and all eight 32-bit slices of the 256-bit rule digest. The Android adapter reads this mailbox only after the guest halts and verifies every field against the returned decision and submitted descriptors. A mismatch is an integrity error, never an allow. Denials carry no reusable receipt.

This receipt binds the successful guest decision to the exact rule snapshot used for that call. It is correlation evidence, not a cryptographic signature and not by itself an ACL grant or NMP lease. Candidate ID, math snapshot ID, ruleset snapshot ID, and downstream packet/lease binding remain separate fields owned by the NMP integration.

## Raw guest receipt and NMP serialization

The guest now emits a fixed 14-word receipt only when `mth_admit` returns ALLOW:

- magic `NA16`, receipt ABI version, guest status;
- selected program ID echoed from its fixed mailbox;
- candidate rule epoch and authoritative rule epoch;
- all eight words of the authoritative 256-bit rule digest.

The same NaN16 receipt-emission subroutine is included by the Android guest and
its native regression fixture. The regression checks the complete allow record,
then clears the mailbox and verifies that a stale-epoch denial emits no receipt.

The Android adapter checks every raw field against the submitted candidate and
rule descriptors. Its `Decision.toNmpAdmissionReceipt` serializes the validated
raw receipt into the canonical `nanrouter.native-admission-receipt.v1` schema,
including candidate/input/capability/preflight digests, generation and output
descriptor digest, program ID, guest source/image digests, and the receipt digest.
These host-side hashes bind data; they are not signatures or execution
attestation. The NMP service must still validate the schema and lease binding.
