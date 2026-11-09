#!/bin/sh
# Android-vendored native NaN16 trainer parity; no host predictor.
set -eu
cd "$(dirname "$0")/.."
tmp="/tmp/android-n16-predict-$$"
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
mkdir -p "$tmp"
make -s -C aarch64/android nan_cli
# Keep the actual Android GUI and CLI byte transports on the same safe
# guest corpus address. Static entrypoint check complements native VM tests.
grep -Fq 'vm.writeBytes(327680, framed + byteArrayOf(0))' \
    aarch64/android/app/src/main/java/org/nan0s/vm/MainActivity.kt || {
  echo "Android GUI writes N16TXT to an obsolete guest address"; exit 1;
}
test "$(grep -Fc 'nan_write_bytes(v, 327680u,' aarch64/android/tools/nan_cli.c)" -eq 2 || {
  echo "Android CLI corpus transport addresses diverged"; exit 1;
}

# Three AB records create A->B, but no cross-record B->A edge.
printf 'N16TXT/1\nAB\nAB\nAB\n' > "$tmp/tiny.txt"
if ! aarch64/android/nan_cli --nannet-train "$tmp/tiny.txt" --serial \
    --peek 8449:1,33090:1,8463:6,8470:4,8478:4,81920:3 > "$tmp/tiny.out"; then
  cat "$tmp/tiny.out"
  echo "Android native NaN16 completion invocation failed"
  exit 1
fi
if ! awk '
  BEGIN {
    expected[1]="N16 TRAIN PASS";
    expected[2]="00000003"; expected[3]="00000003";
    expected[4]="00000004"; expected[5]="00000000";
    expected[6]="00000000"; expected[7]="00000001";
    expected[8]="00000000"; expected[9]="00000003";
    expected[10]="00000042"; expected[11]="00000041";
    expected[12]="00000042"; expected[13]="00000041";
    expected[14]="00000000"; expected[15]="00000001";
    expected[16]="00000000"; expected[17]="00000001";
    expected[18]="00000000"; expected[19]="00000000";
    expected[20]="00000000";
  }
  { if ($0 != expected[NR]) bad=1 }
  END { if (bad || NR != 20) exit 1 }
' "$tmp/tiny.out"; then
  echo "Android NaN16 native BABA/fallback/bounds/scrub mismatch"
  cat "$tmp/tiny.out"
  exit 1
fi

# Over 16 KiB ensures old placement would corrupt the bigram table.
printf 'N16TXT/1\n' > "$tmp/long.txt"
i=0
while [ "$i" -lt 700 ]; do
  printf 'AB stable native model region 0123456789.\n' >> "$tmp/long.txt"
  i=$((i + 1))
done
if ! aarch64/android/nan_cli --nannet-train "$tmp/long.txt" --serial \
    --peek 8449:1,33090:1,81920:3 > "$tmp/long.out"; then
  cat "$tmp/long.out"
  echo "Android NaN16 corpus separation invocation failed"
  exit 1
fi
if ! awk '
  NR==1 && $0!="N16 TRAIN PASS" { bad=1 }
  (NR==2 || NR==3) && $0!="000002BC" { bad=1 }
  NR>=4 && NR<=6 && $0!="00000000" { bad=1 }
  END { if (bad || NR != 6) exit 1 }
' "$tmp/long.out"; then
  echo "Android NaN16 corpus/model separation or zeroization failed"
  cat "$tmp/long.out"
  exit 1
fi
echo "Android native NaN16 completion parity: PASS"
