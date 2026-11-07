#!/bin/sh
# Assemble a NaN16 guest and run it on the native NaN16 core.
set -eu
cd "$(dirname "$0")/.."
export LC_ALL=C
PROGRAM="$1"
STEPS="\${2:-200000000}"
if [ -z "$PROGRAM" ]; then
  echo "usage: tools/nrun.sh PROGRAM.nas [steps]" >&2
  exit 2
fi
CLI=aarch64/android/nan_cli
if [ ! -x "$CLI" ]; then
  make -s -C aarch64/android nan_cli
fi
case "$PROGRAM" in
  *.nas)
    IMAGE="\${TMPDIR:-/tmp}/nan16-nrun-$$.nan"
    awk -f tools/nmk.awk -v CMD=emit "$PROGRAM" > "$IMAGE" 2>/dev/null
    trap 'rm -f "$IMAGE"' EXIT
    ;;
  *)
    IMAGE="$PROGRAM"
    ;;
esac
"$CLI" --img "$IMAGE" --steps "$STEPS" --serial
