# Android NaN16 mathematical admission integration

**Status:** Android guest-call adapter implemented alongside the canonical
NaNROUTER NaN16 admission routine. The Android host only stages bounded
32-bit guest words and reads the returned status/output descriptor.

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
