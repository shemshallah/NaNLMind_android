#!/bin/sh
set -eu
root=${NAN16_ROOT:-.}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
make -s -C "$root/aarch64/android" nan_cli
awk -f "$root/tools/nmk.awk" -v CMD=emit src/training/n16_math_guest_receipt_test.nas > "$tmp/receipt.nan"
"$root/aarch64/android/nan_cli" --img "$tmp/receipt.nan" --steps 2000000 --serial > "$tmp/out" 2>&1
cat "$tmp/out"
grep -qx 'MTH NATIVE RECEIPT PASS' "$tmp/out"
