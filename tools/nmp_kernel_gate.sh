#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out="$(sh tools/nrun.sh src/nannet/math_execute_test.nas 200000000)"
printf '%s\n' "$out"
printf '%s\n' "$out" | grep -qx 'PASS'
echo "NaN16 NMP vector kernel execution gate: PASS"
