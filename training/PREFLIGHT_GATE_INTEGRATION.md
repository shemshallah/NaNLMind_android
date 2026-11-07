# NaN16 Preflight Gate Integration Contract

**Status:** native gate ABI landed in NaNROUTER; Android integration contract.
**Authority:** NaNROUTER owns routing; the mathematical validity engine is the only execution authorization path. NaNLMind may generate candidates but cannot authorize them.

## Call sequence

1. NaNLMind resolves candidate and rule identities through the canonical NaNNet mapping.
2. It serializes the candidate descriptor and authoritative rule descriptor into guest words.
3. The NaN16 guest invokes `mth_preflight_gate` from the NaNROUTER native MTH image.
4. Only status `0` may proceed to capability selection and NMP submission.
5. The NMP packet and resulting lease bind the exact rule epoch and eight-word (256-bit) rule digest used by preflight.
6. Any rule update, candidate mutation, or digest mismatch invalidates the authorization and requires a fresh preflight.
7. NaNLMind records the decision and evidence; it never rewrites a denial into acceptance.

## Descriptor ABI

The authoritative ABI and status values are defined in
`NaNROUTER/mth/nan16/mth_preflight_gate.nas`. Candidate and rule descriptors
are fixed-width word arrays. The gate checks ABI version, epoch freshness,
input/output signature, domain, optional equal-dimension shape policy,
capability subset, minimum proof level, enabled policy, and eight digest words.

The gate is intentionally conservative. It does not infer types, prove a
theorem, grant ACL permissions, or claim a runner is healthy. Those are separate
authorities. An unsupported shape policy is denied rather than guessed.

## Integration invariants

- No direct executor call from a learning, scratch, or candidate-generation head.
- No NMP submission on a nonzero preflight status.
- No reuse of authorization after rule epoch/digest change.
- No conflation of undefined, invalid, unauthorized, unsupported, unavailable,
  stale, and execution-failed outcomes.
- Mathematical arithmetic stays in NaN16 guest code; host code transports
  descriptors and displays evidence only.
