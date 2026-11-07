#!/bin/sh
set -eu
root=${NAN16_ROOT:-.}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
make -s -C "$root/aarch64/android" nan_cli
awk -f "$root/tools/nmk.awk" -v CMD=emit src/training/n16_math_admission_test.nas > "$tmp/admission.nan"
"$root/aarch64/android/nan_cli" --img "$tmp/admission.nan" --steps 2000000 --serial --peek 40254:1 > "$tmp/out" 2>&1
cat "$tmp/out"
grep -qx 'MTH COMPOSED ADMISSION PASS' "$tmp/out"
