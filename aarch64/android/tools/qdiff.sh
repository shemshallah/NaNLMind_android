#!/bin/sh
# qdiff.sh IMG "KEYS(comma list for nan_cli)" "STEPS" -> compares COM1 + VGA text cells of the real x86 core (qemu) and nan_cli.
# Keys are sent to qemu with sendkey; supported tokens: single chars 0-9a-z, ESC, ENTER, and ~ (shift+grave).
IMG=$1; KEYS=$2; STEPS=${3:-60000000}; WAIT=${4:-4}
D=$(mktemp -d); cd "$(dirname "$0")/.."
{ sleep $WAIT
  for k in $(echo "$KEYS" | tr ',' ' '); do
    case $k in ESC) q=esc;; ENTER) q=ret;; '~') q=shift-grave_accent;; @*) sleep 1; continue;; *) q=$k;; esac
    echo "sendkey $q"; sleep 1.5
  done
  echo "pmemsave 0xB8000 4000 \"$D/q_vga.bin\""; sleep 1; echo quit; } | \
  qemu-system-i386 -m 32 -fda "$IMG" -display none -serial file:$D/q_com1.txt -monitor stdio >/dev/null 2>&1
./nan_cli --img "$IMG" --steps "$STEPS" ${KEYS:+--keys "$KEYS"} --dump-vga $D/c_vga.bin --serial > $D/c_com1.txt 2>/dev/null
echo "--- COM1 qemu:"; od -c $D/q_com1.txt | head -3
echo "--- COM1 C   :"; od -c $D/c_com1.txt | head -3
if cmp -s $D/q_vga.bin $D/c_vga.bin; then echo "VGA TEXT: IDENTICAL (4000 bytes)"; else echo "VGA TEXT: DIFFER"; cmp -l $D/q_vga.bin $D/c_vga.bin | head -20; fi
cp $D/q_vga.bin /tmp/q/q_vga.bin; cp $D/c_vga.bin /tmp/q/c_vga.bin; rm -rf $D
