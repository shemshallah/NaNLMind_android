#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
run_and_check() {
  program="$1"
  expected="$2"
  out="$(sh tools/nrun.sh "$program" 200000000)"
  printf '%s\n' "$out"
  printf '%s\n' "$out" | grep -qx "$expected"
}
run_and_check src/nannet/math_execute_test.nas PASS
# Emit a temporary label map for the worker guest if its first direct test fails.
awk -f tools/nmk.awk -v CMD=emit -v LABELS=/tmp/nmp-worker-add.labels \
  src/training/n16_nmp_kernel_add_test.nas > /tmp/nmp-worker-add.nan
grep -E 'n16_nmp_|nmr_(obj_put|obj_bind_data|shape_put|op_put|resolve|execute|vector_add)' \
  /tmp/nmp-worker-add.labels || true
run_and_check src/training/n16_nmp_kernel_add_test.nas "NMP ADD PASS"
run_and_check src/training/n16_nmp_kernel_dot_test.nas "NMP DOT PASS"
echo "NaN16 NMP vector kernel execution gate: PASS"
