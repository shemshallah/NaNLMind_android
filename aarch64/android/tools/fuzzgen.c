/* fuzzgen.c - random NaN16 program generator for differential testing (C core vs qemu running the real kan16.xa).
 *   fuzzgen SEED [NOPS] > prog.nan
 * The program seeds registers and a 128-word memory window (0x1000..0x107F), runs NOPS random deterministic ops
 * (no KEY/TICK/RDTSC/CPUID/port/display/branch ops), then dumps the flag, r1..r14 and the window (payload + nibble via LDN) to COM1 with PUTC and HALTs.
 * About 1 in 8 programs instead ends in a trap (bad op or running into a zero word) so the trap text is compared too. */
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

static uint64_t S;
static uint32_t rnd(void) { S ^= S << 13; S ^= S >> 7; S ^= S << 17; return (uint32_t)(S >> 16); }
static uint32_t below(uint32_t n) { return rnd() % n; }
static const char *NEAR[4] = {"7FF1", "FFF1", "7FF8", "FFF8"}, *FAR[4] = {"F17F", "F1FF", "F87F", "F8FF"};
static void emit(int op, int d, int a, uint32_t imm, int n) {
  printf("%s_%08X_%s\n", NEAR[(n >> 2) & 3], ((uint32_t)op << 24) | ((uint32_t)d << 20) | ((uint32_t)a << 16) | (imm & 0xFFFF), FAR[n & 3]);
}
static const uint32_t SP[] = {0, 1, 2, 3, 0x7FFFFFFF, 0x80000000, 0x80000001, 0xFFFFFFFF, 0xFFFFFFFE, 0x12345678, 0xDEADBEEF, 0xFFFF, 0x10000, 0x7F800000,
  0xFF800000, 0x7FC00000, 0xFFC00000, 0x7F800001, 0xFF800001, 0x3F800000, 0xBF800000, 0x4F000000, 0xCF000000, 0x4EFFFFFF, 0x7FC00123, 0x00000001, 0x007FFFFF,
  0x00800000, 0x80000001, 0x4B800000, 0x4B7FFFFF, 0xCB800000, 0x3F000000, 0x40490FDB, 0x7F7FFFFF, 0xFF7FFFFF};
static uint32_t val(void) {
  switch (below(5)) { case 0: return SP[below(sizeof SP / sizeof SP[0])]; case 1: return rnd(); case 2: return below(256); case 3: return below(65536); default: return rnd() & 0xFFFF0000u | below(4); }
}
static int dreg(void) { static const int d[] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 14}; return d[below(10)]; }
static void load(int r, uint32_t v) { emit(0x01, r, 0, v & 0xFFFF, 15); emit(0x02, r, 0, v >> 16, 15); }
static int pred(void) { return below(2) ? 15 : 1 + (int)below(15); }

int main(int argc, char **argv) {
  S = (argc > 1 ? strtoull(argv[1], 0, 0) : 1) * 0x9E3779B97F4A7C15ull + 0x1234567; for (int i = 0; i < 8; i++) rnd();
  int nops = argc > 2 ? atoi(argv[2]) : 80;
  printf("; fuzzgen seed %s\n", argc > 1 ? argv[1] : "1");
  /* r10 dst base, r11 src base, r12 small count, r13 byte base, r14 scratch; data regs r1..r9 */
  load(10, 0x1000 + below(16)); load(11, 0x1030 + below(16)); load(12, below(25)); load(13, 0x4000 + below(16));
  for (int r = 1; r <= 9; r++) load(r, val());
  load(14, val());
  for (int i = 0; i < 24; i++) { emit(0x09, dreg(), 10, below(48) + (i & 1 ? 0 : 0), 15); }       /* prefill window with data-register payloads */
  for (int i = 0; i < nops; i++) {
    int d = dreg(), a = below(16), b = below(16), n = pred(); uint32_t imm = (rnd() & 0xFFF0) | (uint32_t)b;
    switch (below(24)) {
    case 0: emit(0x00, d, a, imm, 1 + below(15)); break;                                         /* BOOL, n = truth table */
    case 1: emit(0x01, d, 0, rnd(), n); break;
    case 2: emit(0x02, d, 0, rnd(), n); break;
    case 3: { static const int o[] = {0x03, 0x04, 0x07, 0x28, 0x20, 0x21, 0x22}; emit(o[below(7)], d, a, imm, n); break; }
    case 4: { static const int o[] = {0x23, 0x24, 0x25, 0x26}; emit(o[below(4)], d, a, imm, n); break; }
    case 5: { static const int o[] = {0x05, 0x06, 0x27}; emit(o[below(3)], d, a, rnd() & 0x3F, n); break; }   /* shift count = low 5 bits of payload */
    case 6: emit(0x29, d, a, rnd(), n); break;                                                    /* ADDI */
    case 7: case 8: case 9: { static const int o[] = {0x30, 0x31, 0x32, 0x33, 0x35}; emit(o[below(5)], d, a, imm, n); break; }
    case 10: { static const int o[] = {0x34, 0x36, 0x37}; emit(o[below(3)], d, a, imm, n); break; }
    case 11: emit(0x08, d, 10 + below(2), below(32), n); break;                                   /* LD */
    case 12: emit(0x09, d, 10 + below(2), below(32), n); break;                                   /* ST */
    case 13: emit(0x2A, d, 13, below(200), n); break;                                             /* LDB */
    case 14: emit(0x2B, d, 13, below(200), n); break;                                             /* STB */
    case 15: emit(0x16, d, 10 + below(2), (rnd() & 0xFFF0) | (uint32_t)dreg(), n); break;        /* STW nibble = r[b]&15 */
    case 16: emit(0x17, d, 10 + below(2), below(32), n); break;                                   /* LDN */
    case 17: emit(0x14, 0, 10 + below(2), below(32), n); break;                                   /* BSWAPM */
    case 18: emit(0x2D, 10, 11, 12, n); break;                                                    /* BCOPY dst r10 src r11 count r12 */
    case 19: emit(0x2D, 11, 10, 12, n); break;
    case 20: emit(0x2E, 10 + below(2), dreg(), 12, n); break;                                     /* BFILL */
    case 21: load(12, below(25)); break;
    case 22: { static const int o[] = {0x0F, 0x1B, 0x2F}; emit(o[below(3)], 0, 0, 0, n); break; }
    default: emit(0x04, d, a, imm, n); break;
    }
  }
  if (below(8) == 0) {                                                                            /* trap ending */
    switch (below(4)) {
    case 0: emit(0x46 + below(0xB9), 0, 0, 0, 15); break;                                         /* op > 0x45 (not 0xFF) */
    case 1: { static const int o[] = {0x0E, 0x1C, 0x1D, 0x1E, 0x1F}; emit(o[below(5)], 0, 0, 0, 15); break; }
    case 2: emit(0xFF, 0, 0, 0, 0); break;                                                         /* silent HALT */
    default: break;                                                                                 /* fall into a zero word: !G trap */
    }
    return 0;
  }
  /* epilogue: k probe, registers, memory window */
  emit(0x01, 15, 0, 0, 15); emit(0x01, 15, 0, 1, 1); emit(0x01, 15, 0, 2, 2); emit(0x01, 15, 0, 3, 4); emit(0x01, 15, 0, 4, 8); emit(0x15, 15, 0, 0, 15);
  for (int r = 1; r <= 14; r++) for (int k = 0; k < 4; k++) { emit(0x06, 15, r, 8 * k, 15); emit(0x15, 15, 0, 0, 15); }
  load(14, 0x1000);
  for (int i = 0; i < 128; i++) {
    emit(0x08, 15, 14, i, 15); for (int k = 0; k < 4; k++) { emit(0x06, 12, 15, 8 * k, 15); emit(0x15, 12, 0, 0, 15); }
    emit(0x17, 15, 14, i, 15); emit(0x15, 15, 0, 0, 15);
  }
  emit(0xFF, 0, 0, 0, 15);
  return 0;
}
