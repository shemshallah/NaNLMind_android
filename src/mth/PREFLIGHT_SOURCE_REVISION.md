# NaN16 MTH admission source pin

Vendored from `shemshallah/NaNROUTER` main at commit
`f4549595a49a6c5dc0a42fa3715eac6944acbe40`.

Files mirrored byte-for-byte:
- `mth/nan16/mth_preflight_gate.nas`
- `mth/nan16/mth_preflight.nas`
- `mth/nan16/mth_admission.nas`

The Android build compiles these guest sources into `n16_math_admission.nan`.
CI compares the vendored files with the pinned NaNROUTER commit before tests.
Update the pin and all three copies together; do not fork the mathematical rules.
