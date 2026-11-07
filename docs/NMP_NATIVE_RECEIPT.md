# Native NaN16 receipt to NMP submission


The Android NMP client can now be composed with the NaN16 native admission guest through `NmpNativeReceiptBridge`.



## Flow

1. The caller resolves the candidate, authoritative rule snapshot, typed leaf descriptors, registered operator contract, capability bits, and bounded operand payloads from canonical NaN0S guest state.
2. It passes those values as `NmpNativeAdmissionInput` to `NmpNativeReceiptBridge.submit`.
3. The bridge invokes `N16MathAdmission` in the NaN16 guest. A denial throws and no HTTP request is made.
4. On ALLOW, the bridge verifies the raw 14-word guest receipt against the returned decision, binds it to the packet ID, candidate, snapshot, inputs, capability digest, preflight digest and generation, and serializes the canonical NMP receipt.
5. The bridge returns the packet as `PREFLIGHTED` with `metadata.native_admission_required=true` and submits it through the bounded HTTPS client.

## Important boundary

`NmpNativeReceiptBridge` does not invent descriptors, payloads, rule digests, capability claims, or preflight digests. Its caller must obtain those values from the canonical guest state and authoritative application data. It does not make arbitrary caller-supplied arrays authoritative. The router independently validates the serialized receipt and rechecks current server-owned admission and lease evidence.

The older manual JSON submission window remains a transport/debug surface. Production UI integration must replace it with a resolver backed by the canonical NaN0S guest state; until that resolver is wired, do not describe the Android UI as automatically generating complete NMP packets.
