# NaN16 MTH admission source pin

Vendored from `shemshallah/NaNROUTER` main at commit
`3aea7dfc64e1ad10433fb7640a9a465477aa1c1c`.

Files mirrored byte-for-byte:
- `mth/nan16/mth_preflight_gate.nas`
- `mth/nan16/mth_preflight.nas`
- `mth/nan16/mth_admission.nas`

The Android build compiles these guest sources into `n16_math_admission.nan`. The typed preflight includes half-open payload-range no-alias checks, including partial overlaps and address-wrap rejection.
CI compares the vendored files with the pinned NaNROUTER commit before tests.
Update the pin and all three copies together; do not fork the mathematical rules.
