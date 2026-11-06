#!/bin/sh
# gdiff.sh IMG KEY STEPS WAIT -> compares the graphics screen (qemu `screendump` PPM) with nan_cli --dump-ppm. Uses a deterministic step clock for the C side.
IMG=$1; KEY=$2; STEPS=${3:-80000000}; WAIT=${4:-5}; D=$(mktemp -d); cd "$(dirname "$0")/.."
{ sleep $WAIT; for k in $(echo "$KEY" | tr ',' ' '); do case $k in ESC) q=esc;; ENTER) q=ret;; '~') q=shift-grave_accent;; *) q=$k;; esac; echo "sendkey $q"; sleep 3; done
  echo "screendump $D/q.ppm"; sleep 1; echo quit; } | qemu-system-i386 -m 32 -fda "$IMG" -display none -serial null -monitor stdio >/dev/null 2>&1
./nan_cli --img "$IMG" --steps "$STEPS" --keys "$KEY" --dump-ppm $D/c.ppm 2>&1 | tail -1
xxd -p -c 256 "$D/q.ppm" 2>/dev/null > "$D/q.hex"; xxd -p -c 256 "$D/c.ppm" 2>/dev/null > "$D/c.hex"
gawk -v Qp="$D/q.ppm" -v Qhex="$D/q.hex" -v Cp="$D/c.ppm" -v Chex="$D/c.hex" -v Dv="$D" -v K="${KEEP-}" '
function isws(b) { return b == "20" || b == "09" || b == "0a" || b == "0b" || b == "0c" || b == "0d" }
function fail(m) { print "read fail " m; exit 1 }
function readhex(src, hx,   s, line) {
  if (system("test -f \047" src "\047")) {
    if (!system("test -d \047" src "\047")) fail("[Errno 21] Is a directory: \047" src "\047")
    fail("[Errno 2] No such file or directory: \047" src "\047")
  }
  if (system("test -r \047" src "\047")) fail("[Errno 13] Permission denied: \047" src "\047")
  s = ""; while ((getline line < hx) > 0) { gsub(/[ \t\r\n]/, "", line); s = s line }
  close(hx); return s
}
function skipws(L) { while (P <= L && isws(substr(S, P, 2))) P += 2 }
function tok(L,   t) { t = ""; while (P <= L && !isws(substr(S, P, 2))) { t = t substr(S, P, 2); P += 2 } return t }
function parity(p,   hi, lo) {                   # value 0..255 of a lowercase hex pair
  hi = index("0123456789abcdef", substr(p, 1, 1)); lo = index("0123456789abcdef", substr(p, 2, 1))
  if (hi == 0 || lo == 0) fail("malformed hex stream")
  return (hi - 1) * 16 + lo - 1
}
function tonum(t,   i, r, v) {                   # token = hex pairs of ASCII digit chars
  if (t == "") fail("index out of range")
  r = 0
  for (i = 1; i <= length(t); i += 2) {
    v = parity(substr(t, i, 2))
    if (v < 48 || v > 57) fail("invalid literal for int() with base 10: 0x" t)
    r = r * 10 + v - 48
  }
  return r
}
function hdr(w, h,   s, r, i, c, k) {           # "P6\n<w> <h>\n255\n" as hex pairs
  r = "50360a"; s = w " " h
  for (i = 1; i <= length(s); i++) {
    c = substr(s, i, 1)
    if (c == " ") r = r "20"
    else { k = index("0123456789", c); r = r sprintf("%02x", 47 + k) }
  }
  return r "0a3235350a"
}
BEGIN {
  QS = readhex(Qp, Qhex); QL = length(QS)
  CS = readhex(Cp, Chex); CL = length(CS)
  # P6 header: tokens split on ASCII whitespace bytes like python bytes.split(None,4); hex pair i = byte i
  S = QS; L = QL; P = 1
  skipws(L); tok(L)                              # "P6" (5036); not re-checked, like python (int(t[1]))
  skipws(L); qw = tonum(tok(L))
  skipws(L); qh = tonum(tok(L))
  skipws(L); tok(L)                              # maxval token ("255")
  skipws(L); qoff = int((P - 1) / 2)             # pixels start right after the maxval terminator
  S = CS; L = CL; P = 1
  skipws(L); tok(L)
  skipws(L); cw = tonum(tok(L))
  skipws(L); ch = tonum(tok(L))
  skipws(L); tok(L)
  skipws(L); coff = int((P - 1) / 2)
  printf "qemu %dx%d  core %dx%d\n", qw, qh, cw, ch
  QP = substr(QS, 2 * qoff + 1)                  # pixel hex, 6 chars per pixel, from byte qoff
  CP = substr(CS, 2 * coff + 1)
  if (qw == 2 * cw && qh == 2 * ch) {            # downsample: sample pixel (2y*qw + 2x) like python
    DS = ""
    for (y = 0; y < ch; y++)
      for (x = 0; x < cw; x++)
        DS = DS substr(QP, 6 * ((y * 2) * qw + x * 2) + 1, 6)
    QP = DS; qw = cw; qh = ch
  }
  if (qw != cw || qh != ch) { print "SIZE DIFFER"; exit 1 }
  if (K != "") {                                 # KEEP: write normalized P6 via xxd -r -p
    if (system("test -d \047" K "\047")) {
      print "gdiff: cannot write KEEP " K ": dir missing" > "/dev/stderr"; exit 1
    }
    HH = hdr(qw, qh)
    print HH QP > (Dv "/qn.hex"); close(Dv "/qn.hex")
    print HH CP > (Dv "/cn.hex"); close(Dv "/cn.hex")
    if (system("xxd -r -p \047" Dv "/qn.hex\047 > \047" K "/q.ppm\047")) {
      print "gdiff: cannot write KEEP " K "/q.ppm" > "/dev/stderr"; exit 1
    }
    if (system("xxd -r -p \047" Dv "/cn.hex\047 > \047" K "/c.ppm\047")) {
      print "gdiff: cannot write KEEP " K "/c.ppm" > "/dev/stderr"; exit 1
    }
  }
  NP = qw * qh; NF = 0
  for (i = 0; i < NP; i++) if (substr(QP, 6 * i + 1, 6) != substr(CP, 6 * i + 1, 6)) NF++
  printf "pixels differing: %d of %d\n", NF, NP
  exit 0
}'
rm -rf $D
