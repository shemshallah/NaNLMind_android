#!/bin/sh
# fuzz.sh MAINTREE START COUNT [NOPS]  - differential fuzz: C core (nan_cli) vs qemu-system-i386 running the real kan16.xa kernel.
# MAINTREE = the nan0s main tree (needs tools/mkimg.awk and x86/native/nan16_x86.nan). Needs gawk, qemu, a C compiler. Runs 6 qemus in parallel.
MT=$1; S0=${2:-1}; N=${3:-60}; NOPS=${4:-80}; ROOT=$(cd "$(dirname "$0")/.." && pwd); export LC_ALL=C
W=$(mktemp -d); cc -O2 -o "$W/fuzzgen" "$ROOT/tools/fuzzgen.c" || exit 2; [ -x "$ROOT/nan_cli" ] || (cd "$ROOT" && make nan_cli) >/dev/null
one() { s=$1
  "$W/fuzzgen" $s $NOPS > "$W/p$s.nan"
  gawk -f "$MT/tools/mkimg.awk" -v GOLD="$MT/x86/native/nan16_x86.nan" -v GUEST="$W/p$s.nan" -v CARTS=/dev/null -v OUTB="$W/b$s" -v OUTG="$W/g$s" 2>/dev/null || { echo "BUILD-FAIL $s"; return; }
  cat "$W/b$s" "$W/g$s" > "$W/i$s.img"; truncate -s 1474560 "$W/i$s.img"
  timeout 20 qemu-system-i386 -m 32 -fda "$W/i$s.img" -display none -serial file:"$W/q$s.txt" -monitor none -no-reboot >/dev/null 2>&1 &
  qp=$!; i=0; while [ $i -lt 40 ]; do sleep 0.25; i=$((i+1)); [ -s "$W/q$s.txt" ] && [ $i -gt 8 ] && break; done; kill $qp 2>/dev/null; wait $qp 2>/dev/null
  "$ROOT/nan_cli" --img "$W/p$s.nan" --steps 2000000 --serial > "$W/c$s.txt" 2>/dev/null
  [ -f "$W/q$s.txt" ] || : > "$W/q$s.txt"
  if cmp -s "$W/q$s.txt" "$W/c$s.txt"; then echo "ok $s $(wc -c < "$W/q$s.txt")"; else echo "DIFF $s"; cp "$W/p$s.nan" "/tmp/fuzz_fail_$s.nan"; cp "$W/q$s.txt" "/tmp/fuzz_q_$s.txt"; cp "$W/c$s.txt" "/tmp/fuzz_c_$s.txt"; fi
}
s=$S0; end=$((S0+N)); running=0
while [ $s -lt $end ]; do one $s & running=$((running+1)); s=$((s+1)); [ $running -ge 6 ] && { wait; running=0; }; done; wait
rm -rf "$W"
