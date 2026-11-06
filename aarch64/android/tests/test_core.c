/* test_core.c - Gate C: unit tests for the portable NaN16 core. `make test`. Semantics source: core/OPS.md. */
#include "../core/nan16_int.h"
#include <stdio.h>
#include <unistd.h>

static int fails = 0, checks = 0;
#define CHECK(c) do { checks++; if (!(c)) { fails++; printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #c); } } while (0)
#define EQ(a, b) do { checks++; uint64_t _a = (uint64_t)(a), _b = (uint64_t)(b); if (_a != _b) { fails++; printf("FAIL %s:%d  %s == %s  (got 0x%llX, want 0x%llX)\n", __FILE__, __LINE__, #a, #b, (unsigned long long)_a, (unsigned long long)_b); } } while (0)

static nan_vm *V;
static uint32_t pw;                                   /* program write index */
static void begin(void) { nan_reset(V); pw = 0; nan_set_pc(V, 0); }
static void emit(int op, int d, int a, uint32_t imm, int n) { nan_set_word(V, pw++, nan_mkword(((uint32_t)op << 24) | ((uint32_t)d << 20) | ((uint32_t)a << 16) | (imm & 0xFFFF), n)); }
static void I(int op, int d, int a, uint32_t imm) { emit(op, d, a, imm, 15); }       /* always executes */
static void halt(void) { emit(0xFF, 0, 0, 0, 15); }
static int go(uint32_t budget) { return nan_run(V, budget); }
static void ser(char *b, size_t n) { size_t k = nan_serial_read(V, b, n - 1); b[k] = 0; }
#define R(i) nan_reg(V, i)

/* run `op d,a,b` with r1=A r2=B, return r3 and k */
static uint32_t bin(int op, uint32_t A, uint32_t B, int *k) {
  begin(); nan_set_reg(V, 1, A); nan_set_reg(V, 2, B); I(op, 3, 1, 2); halt(); go(100); if (k) *k = nan_flags(V); return R(3);
}

static void t_words(void) {
  EQ(nan_hcls(0x7FF1), 0); EQ(nan_hcls(0xFFF1), 1); EQ(nan_hcls(0x7FF8), 2); EQ(nan_hcls(0xFFF8), 3);
  CHECK(nan_hvalid(0x7FF1) && nan_hvalid(0xFFF8) && !nan_hvalid(0x7FF2) && !nan_hvalid(0x7EF1) && !nan_hvalid(0));
  uint64_t w = nan_mkword(0x0C000000u, 10);                 /* n=10: both heads 0x7FF8 */
  EQ(nan_near(w), 0x7FF8); EQ(nan_far(w), 0x7FF8); EQ(w >> 16 & 0xFFFFFFFFu, 0x0C000000u);
  uint8_t b[8]; for (int i = 0; i < 8; i++) b[i] = (uint8_t)(w >> (8 * i));
  EQ(b[0], 0x7F); EQ(b[1], 0xF8); EQ(b[6], 0xF8); EQ(b[7], 0x7F);         /* FAR big-endian, NEAR little-endian */
}

static void t_loaders(void) {
  const char *txt = "; comment\nFFF8_0A000200_F8FF\n\nFFF1_0C000000_F17F ; RET[EQ]\n.org 8\nFFF8_FF000000_F8FF\n";
  CHECK(nan_load_text(V, txt, strlen(txt)) == 0);
  EQ(nan_word(V, 0), ((uint64_t)0xFFF8 << 48) | ((uint64_t)0x0A000200u << 16) | 0xF8FFu);   /* field 3 is stored as written */
  EQ(nan_far(nan_word(V, 0)), 0xFFF8);
  EQ(nan_far(nan_word(V, 1)), 0x7FF1);                        /* F17F is the byte-swapped 7FF1 */
  EQ(nan_near(nan_word(V, 1)), 0xFFF1);
  CHECK(nan_hvalid(nan_far(nan_word(V, 8))) && nan_word(V, 7) == 0);
  const char *bad = "FFF8_0A000200_F8FF\nnot a word\n"; CHECK(nan_load_text(V, bad, strlen(bad)) == -1); EQ(nan_load_error(V), 2);
  /* auto-detect: floppy / words / text / state / junk */
  uint8_t *img = (uint8_t *)calloc(1474560, 1); uint64_t w = nan_mkword(0x01100007u, 10);
  for (int i = 0; i < 8; i++) img[0x2200 + i] = (uint8_t)(w >> (8 * i));
  const char *kind = ""; CHECK(nan_load_auto(V, img, 1474560, &kind) == 0 && !strcmp(kind, "floppy")); EQ(nan_word(V, 0), w);
  uint8_t raw[16]; for (int i = 0; i < 8; i++) { raw[i] = (uint8_t)(w >> (8 * i)); raw[8 + i] = (uint8_t)(w >> (8 * i)); }
  CHECK(nan_load_auto(V, raw, 16, &kind) == 0 && !strcmp(kind, "words")); EQ(nan_word(V, 1), w);
  CHECK(nan_load_auto(V, (const uint8_t *)txt, strlen(txt), &kind) == 0 && !strcmp(kind, "nan-text"));
  uint8_t junk[16] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}; CHECK(nan_load_auto(V, junk, 16, &kind) == -1);
  CHECK(nan_load_image(V, img, 100) == -1);
  free(img);
}

static void t_alu(void) {
  int k; EQ(bin(0x03, 0xFFFFFFFFu, 1, &k), 0); EQ(k, 3);                 /* ADD carry + zero */
  EQ(bin(0x03, 2, 3, &k), 5); EQ(k, 0);
  EQ(bin(0x04, 3, 5, &k), 0xFFFFFFFEu); EQ(k, 1);                        /* SUB borrow */
  EQ(bin(0x04, 5, 5, &k), 0); EQ(k, 2);
  bin(0x07, 5, 5, &k); EQ(k, 2);                                          /* CMP eq: flags only, Z */
  bin(0x07, 1, 2, &k); EQ(k, 1); bin(0x07, 2, 1, &k); EQ(k, 0);
  bin(0x28, 0xFFFFFFFFu, 1, &k); EQ(k, 1);                               /* CMPS: -1 < 1 */
  bin(0x07, 0xFFFFFFFFu, 1, &k); EQ(k, 0);                               /* CMP unsigned: max > 1 */
  EQ(bin(0x20, 0x10000, 0x10000, &k), 0); EQ(k, 2);                      /* MUL low32 */
  EQ(bin(0x22, 0xFFFFFFFFu, 0xFFFFFFFFu, &k), 0xFFFFFFFEu);              /* MULHU */
  EQ(bin(0x21, 0xFFFFFFFFu, 0xFFFFFFFFu, &k), 0);                        /* MULH signed (-1*-1)>>32 */
  EQ(bin(0x21, 0x80000000u, 2, &k), 0xFFFFFFFFu);
  /* shifts use the payload low 5 bits as count (imm), not B */
  begin(); nan_set_reg(V, 1, 0x80000001u); I(0x05, 3, 1, 4); I(0x06, 4, 1, 4); I(0x27, 5, 1, 4); I(0x05, 6, 1, 36); halt(); go(100);
  EQ(R(3), 0x00000010u); EQ(R(4), 0x08000000u); EQ(R(5), 0xF8000000u); EQ(R(6), 0x00000010u);   /* 36 & 31 = 4 */
  begin(); nan_set_reg(V, 1, 0xFFFFFFF0u); I(0x29, 3, 1, 0x20); I(0x29, 4, 1, 0xFFF0); halt(); go(100);   /* ADDI sign-extends imm16 */
  EQ(R(3), 0x10); EQ(R(4), 0xFFFFFFE0u);
  /* BOOL: n is the truth table */
  uint32_t A = 0xF0F0F0F0u, Bv = 0xFF00FF00u; int n; uint32_t exp[16];
  for (n = 1; n < 16; n++) { uint32_t e = 0; if (n & 1) e |= ~(A | Bv); if (n & 2) e |= ~A & Bv; if (n & 4) e |= A & ~Bv; if (n & 8) e |= A & Bv; exp[n] = e; }
  for (n = 1; n < 16; n++) { begin(); nan_set_reg(V, 1, A); nan_set_reg(V, 2, Bv); emit(0x00, 3, 1, 2, n); halt(); go(100); EQ(R(3), exp[n]); }
  begin(); I(0x01, 3, 0, 0x1234); I(0x02, 3, 0, 0xABCD); halt(); go(100); EQ(R(3), 0xABCD1234u);        /* MOVI/MOVHI */
}

static void t_div(void) {
  int k;
  EQ(bin(0x23, 7, 0, &k), 0); EQ(k, 3);        /* DIV by 0 -> 0, k=3 */
  EQ(bin(0x25, 7, 0, &k), 0); EQ(k, 3);        /* DIVU by 0 -> 0 */
  EQ(bin(0x24, 7, 0, &k), 7); EQ(k, 3);        /* MOD by 0 -> A */
  EQ(bin(0x26, 7, 0, &k), 7); EQ(k, 3);
  EQ(bin(0x23, 7, 0xFFFFFFFFu, &k), 0xFFFFFFF9u);      /* DIV by -1 = -A */
  EQ(bin(0x23, 0x80000000u, 0xFFFFFFFFu, &k), 0x80000000u);   /* INT_MIN / -1 wraps (no host trap) */
  EQ(bin(0x24, 0x80000000u, 0xFFFFFFFFu, &k), 0); EQ(k, 2);
  EQ(bin(0x23, 0xFFFFFFF9u, 2, &k), 0xFFFFFFFDu);      /* -7 / 2 = -3 (trunc) */
  EQ(bin(0x24, 0xFFFFFFF9u, 2, &k), 0xFFFFFFFFu);      /* -7 % 2 = -1 */
  EQ(bin(0x25, 0xFFFFFFF9u, 2, &k), 0x7FFFFFFCu); EQ(bin(0x26, 0xFFFFFFF9u, 2, &k), 1);
}

static void t_predication_and_traps(void) {
  char b[64];
  /* predicated ops: n bit k must be set. k starts 0; CMP r0,r0 -> Z -> k=2 */
  begin(); I(0x01, 3, 0, 1); emit(0x01, 4, 0, 9, 1 << 2); emit(0x01, 5, 0, 9, 1 << 0); halt(); go(100);     /* k=0: bit2 off, bit0 on */
  EQ(R(4), 0); EQ(R(5), 9);
  begin(); I(0x07, 0, 0, 0); emit(0x01, 4, 0, 9, 1 << 2); emit(0x01, 5, 0, 9, 1 << 0); halt(); go(100);     /* k=2 now */
  EQ(R(4), 9); EQ(R(5), 0);
  /* BOOL ignores n for the predicate */
  begin(); I(0x07, 0, 0, 0); nan_set_reg(V, 1, 6); nan_set_reg(V, 2, 3); emit(0x00, 3, 1, 2, 8); halt(); go(100); EQ(R(3), 2);
  /* HALT is silent and unconditional */
  begin(); emit(0xFF, 0, 0, 0, 0); go(10); ser(b, sizeof b); EQ(go(10), NAN_HALTED); CHECK(b[0] == 0); CHECK(!*nan_trap_text(V));
  /* bad op >0x45: trap text !O + pc after fetch, min 4 hex digits */
  begin(); I(0x01, 3, 0, 1); I(0x46, 0, 0, 0); EQ(go(10), NAN_HALTED); ser(b, sizeof b); CHECK(!strcmp(b, "!O0002")); CHECK(!strcmp(nan_trap_text(V), "!O0002"));
  EQ(go(10), NAN_HALTED);                                                   /* stays halted */
  begin(); I(0x0E, 0, 0, 0); go(10); ser(b, sizeof b); CHECK(!strcmp(b, "!O0001"));      /* table entry 0x0E is a bad op */
  begin(); I(0x1C, 0, 0, 0); go(10); ser(b, sizeof b); CHECK(!strcmp(b, "!O0001"));
  /* predicated-off bad table op does not trap; 0x2F is a NOP */
  begin(); emit(0x0E, 0, 0, 0, 1 << 3); I(0x2F, 0, 0, 0); I(0x01, 3, 0, 5); halt(); go(10); EQ(R(3), 5); ser(b, sizeof b); CHECK(b[0] == 0);
  /* but op>0x45 traps even when predicated off */
  begin(); emit(0x50, 0, 0, 0, 1 << 3); go(10); ser(b, sizeof b); CHECK(!strcmp(b, "!O0001"));
  /* gate: zero word (invalid heads) at pc -> !G + pc+1 */
  begin(); I(0x0F, 0, 0, 0); I(0x0F, 0, 0, 0); go(10); ser(b, sizeof b); CHECK(!strcmp(b, "!G0003"));
  /* one bad head only is still a gate trap */
  begin(); nan_set_word(V, 0, (nan_mkword(0x01000000u, 15) & ~((uint64_t)0xFFFF << 48)) | ((uint64_t)0x7FF2 << 48)); go(10); ser(b, sizeof b); CHECK(!strcmp(b, "!G0001"));
  /* large pc prints more digits */
  begin(); I(0x38, 0, 0, 0); nan_set_word(V, 0, nan_mkword(0x38ABCDEFu, 15)); nan_set_word(V, 0xABCDEF, nan_mkword(0x46000000u, 15)); go(10); ser(b, sizeof b); CHECK(!strcmp(b, "!OABCDF0"));
  /* JMP/CALL/RET/JMPR/CALLR */
  begin(); I(0x0B, 0, 0, 4); I(0x01, 4, 0, 7); halt(); I(0x0F, 0, 0, 0); I(0x01, 3, 0, 5); I(0x0C, 0, 0, 0); go(100); EQ(R(3), 5); EQ(R(4), 7); EQ(R(15), 1);
  begin(); I(0x01, 1, 0, 3); I(0x2C, 0, 1, 0); halt(); I(0x01, 5, 0, 1); I(0x0D, 0, 15, 0); go(100); EQ(R(5), 1); EQ(R(15), 2);
}

static uint32_t f2u(float f) { uint32_t x; memcpy(&x, &f, 4); return x; }
static uint32_t un(int op, uint32_t A, int *k) { begin(); nan_set_reg(V, 1, A); I(op, 3, 1, 0); halt(); go(100); if (k) *k = nan_flags(V); return R(3); }
static void t_fp(void) {
  int k; const uint32_t SNAN = 0x7F800001u, QNAN = 0x7FC00000u, INF = 0x7F800000u, NINF = 0xFF800000u, DEF = 0xFFC00000u;
  EQ(bin(0x30, f2u(1.5f), f2u(2.25f), &k), f2u(3.75f)); EQ(k, 0);
  EQ(bin(0x31, f2u(1.5f), f2u(2.25f), &k), f2u(-0.75f)); EQ(bin(0x32, f2u(1.5f), f2u(4.f), &k), f2u(6.f));
  EQ(bin(0x30, SNAN, f2u(1.f), &k), SNAN | 0x400000u);                  /* A NaN wins and is quieted */
  EQ(bin(0x30, f2u(1.f), SNAN, &k), SNAN | 0x400000u);
  EQ(bin(0x30, QNAN | 5, SNAN, &k), QNAN | 5);                          /* A before B */
  EQ(bin(0x30, INF, NINF, &k), DEF);                                    /* inf-inf -> default NaN (x86 negative QNaN) */
  EQ(bin(0x32, 0, INF, &k), DEF);
  EQ(bin(0x33, f2u(1.f), 0, &k), INF); EQ(bin(0x33, f2u(-1.f), 0, &k), NINF);
  EQ(bin(0x33, f2u(1.f), 0x80000000u, &k), NINF);                       /* x/-0 */
  EQ(bin(0x33, 0, 0, &k), QNAN); EQ(bin(0x33, SNAN, 0, &k), QNAN);       /* 0/0, NaN/0 -> positive QNaN */
  EQ(bin(0x33, INF, INF, &k), DEF);
  EQ(bin(0x33, f2u(1.f), f2u(4.f), &k), f2u(.25f));
  EQ(un(0x34, f2u(-1.f), &k), QNAN); EQ(k, 0); EQ(un(0x34, 0x80000000u, &k), 0x80000000u); EQ(un(0x34, f2u(9.f), &k), f2u(3.f)); EQ(un(0x34, SNAN, &k), QNAN);
  bin(0x35, f2u(1.f), f2u(2.f), &k); EQ(k, 1); bin(0x35, f2u(2.f), f2u(2.f), &k); EQ(k, 2); bin(0x35, f2u(3.f), f2u(2.f), &k); EQ(k, 0);
  bin(0x35, QNAN, f2u(2.f), &k); EQ(k, 3); bin(0x35, f2u(2.f), SNAN, &k); EQ(k, 3);
  bin(0x35, 0x80000000u, 0, &k); EQ(k, 2);                              /* -0 == +0 */
  EQ(un(0x36, 0xFFFFFFFDu, &k), f2u(-3.f)); EQ(un(0x36, 16777217u, &k), f2u(16777216.f));    /* CVTIF: RNE ties to even */
  EQ(un(0x36, 16777219u, &k), f2u(16777220.f)); EQ(k, 0);
  EQ(un(0x37, QNAN, &k), 0); EQ(k, 2);
  EQ(un(0x37, f2u(-3.9f), &k), 0xFFFFFFFDu);                            /* trunc toward zero */
  EQ(un(0x37, f2u(3e10f), &k), 0x7FFFFFFFu); EQ(un(0x37, f2u(-3e10f), &k), 0x80000000u);
  EQ(un(0x37, f2u(-2147483648.f), &k), 0x80000000u); EQ(un(0x37, f2u(2147483648.f), &k), 0x7FFFFFFFu);
  EQ(un(0x37, f2u(0.5f), &k), 0); EQ(k, 2); EQ(un(0x37, f2u(2147483520.f), &k), 2147483520u);
}

static void t_memory_ops(void) {
  /* ST/LD keep the payload, STB/LDB little-endian bytes, LDN/STW nibble */
  begin(); nan_set_reg(V, 1, 100); nan_set_reg(V, 2, 0xA1B2C3D4u); I(0x09, 2, 1, 5); I(0x08, 3, 1, 5);
  halt(); go(100); EQ(R(3), 0xA1B2C3D4u); EQ(nan_word(V, 105) >> 16 & 0xFFFFFFFFu, 0xA1B2C3D4u); EQ(nan_near(nan_word(V, 105)), 0x7FF8);
  /* LDB/STB: byte address = 4*word + lane */
  begin(); nan_set_reg(V, 1, 8); nan_set_reg(V, 2, 0xA1B2C3D4u); I(0x09, 2, 1, 0);          /* word 8 payload = A1B2C3D4 */
  nan_set_reg(V, 10, 32); I(0x2A, 3, 10, 0); I(0x2A, 4, 10, 1); I(0x2A, 5, 10, 2); I(0x2A, 6, 10, 3);
  nan_set_reg(V, 7, 0x5A); I(0x2B, 7, 10, 2); I(0x08, 9, 1, 0); halt(); go(100);
  EQ(R(3), 0xD4); EQ(R(4), 0xC3); EQ(R(5), 0xB2); EQ(R(6), 0xA1); EQ(R(9), 0xA15AC3D4u);  /* r1=8: payload word 8 after STB lane 2 -> checked below */
  EQ(nan_near(nan_word(V, 8)), 0x7FF8); EQ(nan_far(nan_word(V, 8)), 0x7FF8);               /* STB rewrites the word with n=10 */
  /* LDN returns the nibble, 0xFFFFFFFF for a bad word */
  begin(); nan_set_reg(V, 1, 20); nan_set_reg(V, 2, 77); nan_set_reg(V, 3, 6); emit(0x16, 2, 1, 3, 15); I(0x17, 4, 1, 0); nan_set_reg(V, 5, 21); I(0x17, 6, 5, 0); halt(); go(100);
  EQ(R(4), 6); EQ(R(6), 0xFFFFFFFFu); EQ(nan_word(V, 20) >> 16 & 0xFFFFFFFFu, 77);
  /* BCOPY forward, backward (overlap), BFILL */
  begin(); for (uint32_t i = 0; i < 8; i++) nan_set_word(V, 100 + i, nan_mkword(i + 1, 10));
  nan_set_reg(V, 1, 102); nan_set_reg(V, 2, 100); nan_set_reg(V, 3, 6); I(0x2D, 1, 2, 3); halt(); go(100);       /* dst 102 <- src 100, count 6: overlap, must go backward */
  { uint32_t want[8] = {1, 2, 1, 2, 3, 4, 5, 6}; for (int i = 0; i < 8; i++) EQ(nan_word(V, 100 + i) >> 16 & 0xFFFFFFFFu, want[i]); }
  begin(); for (uint32_t i = 0; i < 8; i++) nan_set_word(V, 100 + i, nan_mkword(i + 1, 10));
  nan_set_reg(V, 1, 100); nan_set_reg(V, 2, 102); nan_set_reg(V, 3, 6); I(0x2D, 1, 2, 3); halt(); go(100);       /* dst below src: forward */
  { uint32_t want[8] = {3, 4, 5, 6, 7, 8, 7, 8}; for (int i = 0; i < 8; i++) EQ(nan_word(V, 100 + i) >> 16 & 0xFFFFFFFFu, want[i]); }
  begin(); nan_set_reg(V, 1, 50); nan_set_reg(V, 2, 0xDEAD); nan_set_reg(V, 3, 4); I(0x2E, 1, 2, 3); halt(); go(100);
  for (int i = 0; i < 4; i++) EQ(nan_word(V, 50 + i) >> 16 & 0xFFFFFFFFu, 0xDEAD); EQ(nan_word(V, 54), 0);
  begin(); nan_set_reg(V, 1, NAN_WORDS - 2u); nan_set_reg(V, 2, 7); nan_set_reg(V, 3, 4); I(0x2E, 1, 2, 3); halt(); go(100);    /* wraps with the configured word mask */
  EQ(nan_word(V, NAN_WORDS - 1u) >> 16 & 0xFFFFFFFFu, 7); EQ(nan_word(V, 1) >> 16 & 0xFFFFFFFFu, 7);
  /* BSWAPM */
  begin(); nan_set_word(V, 200, 0x0102030405060708ull); nan_set_reg(V, 1, 200); I(0x14, 0, 1, 0); halt(); go(100); EQ(nan_word(V, 200), 0x0807060504030201ull);
  /* RDTSC counts executed instructions; CPUID leaf 0 */
  begin(); I(0x0F, 0, 0, 0); I(0x0F, 0, 0, 0); I(0x45, 3, 0, 0); nan_set_reg(V, 1, 0); nan_set_reg(V, 2, 1); I(0x44, 4, 1, 2); halt(); go(100); EQ(R(3), 3); EQ(R(4), 0x314E614Eu);  /* RDTSC = executed instructions incl. itself; CPUID leaf 0 EBX */
}

static void t_display(void) {
  begin(); I(0x01, 1, 0, 5); I(0x01, 2, 0, 0x4142); I(0x10, 2, 1, 0); I(0x11, 3, 1, 0); halt(); go(100);        /* text VPOKE/VPEEK */
  EQ(nan_text_cell(V, 5), 0x4142); EQ(R(3), 0x4142);
  begin(); I(0x01, 1, 0, 2000); I(0x01, 2, 0, 9); I(0x10, 2, 1, 0); I(0x01, 3, 0, 0x55); I(0x11, 3, 1, 0); halt(); go(100); EQ(R(3), 0x55);   /* out of range: poke ignored, peek leaves r[d] */
  EQ(nan_text_cell(V, 1999), 0x0720);
  begin(); nan_set_reg(V, 1, 0x13); I(0x19, 1, 0, 0); I(0x18, 2, 0, 0); halt(); go(100);                       /* mode 13h */
  EQ(nan_display_mode(V), 1); int w, h; nan_display_size(V, &w, &h); EQ(w, 320); EQ(h, 200); EQ(R(2), 0x1300C800u);
  begin(); nan_set_reg(V, 1, 0x00400030u); I(0x19, 1, 0, 0); I(0x3A, 2, 0, 0); halt(); go(100); EQ(R(2), 0x00400030u); nan_display_size(V, &w, &h); EQ(w, 64); EQ(h, 48);
  /* FBFILL(count r[d], start A, colour B) and FBCOPY(count r[d], dst A, src B), VPEEK/VPOKE on pixels */
  begin(); nan_set_reg(V, 1, 0x00400030u); I(0x19, 1, 0, 0); nan_set_reg(V, 4, 100); nan_set_reg(V, 5, 10); nan_set_reg(V, 6, 0xAB); I(0x3D, 4, 5, 6);
  nan_set_reg(V, 7, 300); nan_set_reg(V, 8, 5); I(0x3C, 4, 7, 5); I(0x11, 9, 7, 0); nan_set_reg(V, 10, 3071); I(0x11, 11, 10, 0); nan_set_reg(V, 12, 3072); nan_set_reg(V, 13, 0x77); I(0x10, 13, 12, 0);
  I(0x11, 14, 12, 0); halt(); go(100);
  EQ(R(9), 0xAB); EQ(R(14), 0);                                                    /* idx 3072 == FBTOT: poke ignored */
  uint32_t px[64 * 48]; CHECK(nan_render_gfx(V, px, 64 * 48) == 0); EQ(px[10], 0xFF000000u | 0); /* index 0xAB past the 64 EGA entries = black (BIOS DAC state) */
  EQ(nan_render_gfx(V, px, 10), (uint64_t)(int64_t)-1);                             /* cap too small */
  begin(); nan_set_reg(V, 1, 0xFFFF0001u); I(0x19, 1, 0, 0); halt(); go(100); EQ(nan_raw_keyboard(V), 1);          /* RAWKB */
  begin(); nan_set_reg(V, 1, 0x03); I(0x19, 1, 0, 0); halt(); go(100); EQ(nan_text_cell(V, 0), 0); EQ(nan_display_mode(V), 0);   /* text DISPSET clears cells to 0 */
  /* VSYNC only yields in graphics modes */
  begin(); I(0x3B, 0, 0, 0); I(0x01, 3, 0, 1); halt(); EQ(go(10), NAN_HALTED); EQ(R(3), 1);
  begin(); nan_set_reg(V, 1, 0x13); I(0x19, 1, 0, 0); I(0x3B, 0, 0, 0); I(0x01, 3, 0, 1); halt(); EQ(go(10), NAN_VSYNC); EQ(R(3), 0); EQ(go(10), NAN_HALTED); EQ(R(3), 1);
  /* dirty counter bumps on display writes only */
  begin(); uint32_t d0 = nan_dirty(V); I(0x01, 1, 0, 3); I(0x03, 2, 1, 1); halt(); go(10); EQ(nan_dirty(V), d0);
  begin(); d0 = nan_dirty(V); I(0x01, 1, 0, 3); I(0x10, 1, 1, 0); halt(); go(10); CHECK(nan_dirty(V) != d0);
  /* text -> UTF-8 */
  begin(); I(0x01, 1, 0, 0); I(0x01, 2, 0, 0x0741); I(0x10, 2, 1, 0); I(0x01, 1, 0, 1); I(0x01, 2, 0, 0x07DB); I(0x10, 2, 1, 0); halt(); go(100);
  static char tb[4096]; nan_text_utf8(V, tb, sizeof tb, 1); CHECK(!strncmp(tb, "A\xE2\x96\x88\n", 5));
}

static void t_dac(void) {
  uint8_t c[3]; nan_dac_rgb(V, 15, c); EQ(c[0], 168); EQ(c[1], 168); EQ(c[2], 255);             /* qemu c6_to_8: 42->168, 63->255 */
  nan_dac_rgb(V, 100, c); EQ(c[0] | c[1] | c[2], 0);                                              /* entries >= 64 start black */
  nan_port_out(V, 0x3C8, 200, 1); nan_port_out(V, 0x3C9, 1, 1); nan_port_out(V, 0x3C9, 21, 1); nan_port_out(V, 0x3C9, 63, 1);
  nan_dac_rgb(V, 200, c); EQ(c[0], 7); EQ(c[1], 87); EQ(c[2], 255);                               /* 1 -> 4|3 = 7 ; 21 -> 84|3 = 87 */
  nan_port_out(V, 0x3C7, 200, 1); EQ(nan_port_in(V, 0x3C9, 1), 1); EQ(nan_port_in(V, 0x3C9, 1), 21); EQ(nan_port_in(V, 0x3C9, 1), 63);
  nan_port_out(V, 0x3C8, 201, 1); nan_port_out(V, 0x3C9, 0xFF, 1); nan_port_out(V, 0x3C9, 0, 1); nan_port_out(V, 0x3C9, 0, 1);
  nan_dac_rgb(V, 201, c); EQ(c[0], 255);                                                           /* values are masked to 6 bits */
  EQ(nan_text_color_vm(V, 1), 0xFF0000A8u); EQ(nan_text_color_vm(V, 6), 0xFFA85700u); EQ(nan_text_color_vm(V, 15), 0xFFFFFFFFu); EQ(nan_text_color_vm(V, 8), 0xFF575757u);
}

static void t_keyboard(void) {
  begin(); I(0x12, 3, 0, 0); halt();
  EQ(go(10), NAN_WAIT_KEY); EQ(nan_pc(V), 0); EQ(nan_steps(V), 0);                                  /* blocking KEY rewinds pc, not counted */
  nan_scancode(V, 0x1E); EQ(go(10), NAN_HALTED); EQ(R(3), 'a');
  /* shift / caps / specials via ktab */
  begin(); nan_scancode(V, 0x2A); nan_scancode(V, 0x02); nan_scancode(V, 0xAA); nan_scancode(V, 0x02);
  nan_scancode(V, 0x3A); nan_scancode(V, 0x1E); nan_scancode(V, 0x02);                              /* shift+1 = !, then 1, caps a = A, caps leaves digits */
  nan_scancode(V, 0xE0); nan_scancode(V, 0x48); nan_scancode(V, 0xE0); nan_scancode(V, 0xC8);        /* Up make then break */
  I(0x1A, 1, 0, 0); I(0x1A, 2, 0, 0); I(0x1A, 3, 0, 0); I(0x1A, 4, 0, 0); I(0x1A, 5, 0, 0); I(0x1A, 6, 0, 0); halt(); go(100);
  EQ(R(1), '!'); EQ(R(2), '1'); EQ(R(3), 'A'); EQ(R(4), '1'); EQ(R(5), NANK_UP); EQ(R(6), 0);
  begin(); nan_key_char(V, 'x'); nan_key_char(V, NANK_LEFT); I(0x1A, 1, 0, 0); I(0x1A, 2, 0, 0); halt(); go(100); EQ(R(1), 'x'); EQ(R(2), NANK_LEFT);
  /* ring capacity is 15; a push into a full ring is dropped */
  begin(); for (int i = 0; i < 40; i++) nan_key_char(V, 'a' + (i % 26));
  { int n = 0, first = nan_ring_pop(V), c = first; while (c >= 0) { n++; c = nan_ring_pop(V); } EQ(first, 'a'); EQ(n, 15); }
  /* raw mode: bytes pass through, nan_key_char synthesises make/break */
  begin(); nan_set_reg(V, 1, 0xFFFF0001u); I(0x19, 1, 0, 0); halt(); go(10); nan_scancode(V, 0xE0); nan_scancode(V, 0x48); nan_scancode(V, 0xE0); nan_scancode(V, 0xC8);
  nan_set_pc(V, 1); pw = 1; I(0x1A, 1, 0, 0); I(0x1A, 2, 0, 0); I(0x1A, 3, 0, 0); I(0x1A, 4, 0, 0); halt(); nan_set_pc(V, 1); go(100);
  EQ(R(1), 0xE0); EQ(R(2), 0x48); EQ(R(3), 0xE0); EQ(R(4), 0xC8);
  nan_scancode(V, 0); /* drain */ 
  begin(); nan_set_reg(V, 1, 0xFFFF0001u); I(0x19, 1, 0, 0); halt(); go(10); nan_key_char(V, 'A');                       /* shift down, 'a' make, break, shift up */
  nan_set_pc(V, 1); pw = 1; for (int i = 1; i <= 4; i++) I(0x1A, i, 0, 0); halt(); nan_set_pc(V, 1); go(100);
  EQ(R(1), 0x2A); EQ(R(2), 0x1E); EQ(R(3), 0x9E); EQ(R(4), 0xAA);
}

static void t_time_and_speaker(void) {
  begin(); for (int i = 0; i < 300; i++) I(0x13, 3, 0, 0); halt(); nan_set_time_ms(V, 1234);
  EQ(go(1000), NAN_TIME_WAIT); EQ(R(3), 1234);                                                      /* polled 256x with an unchanged value */
  EQ(go(1000), NAN_HALTED);
  begin(); I(0x13, 3, 0, 0); halt(); nan_set_time_ms(V, 77); go(10); EQ(R(3), 77);
  nan_port_out(V, 0x43, 0xB6, 1); nan_port_out(V, 0x42, 0xA9, 1); nan_port_out(V, 0x42, 0x04, 1); EQ(nan_speaker_hz(V), 0);   /* gate off */
  nan_port_out(V, 0x61, 3, 1); EQ(nan_speaker_hz(V), 1193182u / 0x04A9); nan_port_out(V, 0x61, 1, 1); EQ(nan_speaker_hz(V), 0);
  EQ(nan_port_in(V, 0x3FD, 1), 0x60); nan_port_out(V, 0x3F8, 'Z', 1); char b[8]; ser(b, 8); CHECK(!strcmp(b, "Z"));
  uint32_t a = nan_port_in(V, 0x3DA, 1), c = nan_port_in(V, 0x3DA, 1); CHECK(((a ^ c) & 8) != 0);   /* retrace bit toggles */
  EQ(nan_port_in(V, 0x1234, 1), 0xFF); EQ(nan_port_in(V, 0x1234, 2), 0xFFFF);
}

static uint32_t cfg(uint32_t dev, uint32_t reg, int size, uint32_t off) {
  nan_port_out(V, 0xCF8, 0x80000000u | (dev << 11) | reg, 4); return nan_port_in(V, 0xCFC + off, size);
}
static void t_pci(void) {
  EQ(cfg(0, 0, 2, 0), 0x8086); EQ(cfg(0, 0, 2, 2), 0x1237); EQ(cfg(1, 0, 2, 2), 0x7000); EQ(cfg(2, 0, 2, 0), 0x1234); EQ(cfg(2, 0, 2, 2), 0x1111);
  EQ(cfg(2, 8, 2, 2), 0x0300); EQ(cfg(0, 8, 2, 2), 0x0600); EQ(cfg(1, 8, 2, 2), 0x0601);
  EQ(cfg(2, 0x10, 2, 0), 0x0008); EQ(cfg(2, 0x10, 2, 2), 0xFD00);                                    /* BAR0 0xFD000008 */
  EQ(cfg(3, 0, 2, 0), 0xFFFF); EQ(cfg(31, 0, 2, 2), 0xFFFF);
  nan_port_out(V, 0xCF8, 0x00000000u, 4); EQ(nan_port_in(V, 0xCFC, 2), 0xFFFF);                      /* enable bit clear */
}

static void t_ata(void) {
  EQ(nan_port_in(V, 0x1F7, 1), 0xFF); EQ(nan_port_in(V, 0x1F2, 1), 0xFF);                            /* no drive: floating bus */
  static uint8_t disk[512 * 8]; for (size_t i = 0; i < sizeof disk; i++) disk[i] = (uint8_t)(i * 7 + (i >> 8));
  nan_attach_disk_mem(V, disk, sizeof disk);
  EQ(nan_port_in(V, 0x1F7, 1), 0x50);
  nan_port_out(V, 0x1F6, 0xE0, 1); nan_port_out(V, 0x1F7, 0xEC, 1); EQ(nan_port_in(V, 0x1F7, 1), 0x58);
  uint16_t id[256]; for (int i = 0; i < 256; i++) id[i] = (uint16_t)nan_port_in(V, 0x1F0, 2);
  EQ(id[60], 8); EQ(id[61], 0); EQ(id[0], 0x0040); EQ(nan_port_in(V, 0x1F7, 1), 0x50);
  nan_port_out(V, 0x1F2, 2, 1); nan_port_out(V, 0x1F3, 3, 1); nan_port_out(V, 0x1F4, 0, 1); nan_port_out(V, 0x1F5, 0, 1); nan_port_out(V, 0x1F6, 0xE0, 1); nan_port_out(V, 0x1F7, 0x20, 1);
  for (int i = 0; i < 256; i++) EQ(nan_port_in(V, 0x1F0, 2), (uint32_t)(disk[3 * 512 + 2 * i] | (disk[3 * 512 + 2 * i + 1] << 8)));
  EQ(nan_port_in(V, 0x1F7, 1), 0x58);                                                                 /* second sector is still pending */
  for (int i = 0; i < 256; i++) EQ(nan_port_in(V, 0x1F0, 2), (uint32_t)(disk[4 * 512 + 2 * i] | (disk[4 * 512 + 2 * i + 1] << 8)));
  EQ(nan_port_in(V, 0x1F7, 1), 0x50);
  nan_port_out(V, 0x1F2, 1, 1); nan_port_out(V, 0x1F3, 6, 1); nan_port_out(V, 0x1F7, 0x30, 1);
  for (int i = 0; i < 256; i++) nan_port_out(V, 0x1F0, 0xA000 + i, 2);
  EQ(nan_port_in(V, 0x1F7, 1), 0x50); EQ(disk[6 * 512], 0x00); EQ(disk[6 * 512 + 1], 0xA0); EQ(disk[6 * 512 + 2 * 255 + 0], 0xFF);
  nan_port_out(V, 0x1F7, 0xE7, 1); EQ(nan_port_in(V, 0x1F7, 1), 0x50);
  nan_port_out(V, 0x1F7, 0x99, 1); EQ(nan_port_in(V, 0x1F7, 1), 0x51); EQ(nan_port_in(V, 0x1F1, 1), 4);   /* unknown command: ERR + ABRT */
  nan_detach_disk(V); EQ(nan_port_in(V, 0x1F7, 1), 0xFF);
  /* fd backend (TMPDIR honored: /tmp is read-only on Android) */
  const char *atad = getenv("TMPDIR"); if (!atad || !*atad) atad = "/tmp";
  char path[512]; snprintf(path, sizeof path, "%s/nan_ata_XXXXXX", atad); int fd = mkstemp(path); CHECK(fd >= 0);
  if (fd >= 0) {
    CHECK(write(fd, disk, sizeof disk) == (ssize_t)sizeof disk); nan_attach_disk_fd(V, fd, sizeof disk);
    nan_port_out(V, 0x1F2, 1, 1); nan_port_out(V, 0x1F3, 1, 1); nan_port_out(V, 0x1F4, 0, 1); nan_port_out(V, 0x1F5, 0, 1); nan_port_out(V, 0x1F7, 0x20, 1);
    EQ(nan_port_in(V, 0x1F0, 2), (uint32_t)(disk[512] | (disk[513] << 8)));
    nan_detach_disk(V); close(fd); unlink(path);
  }
}

static int hook_n; static uint32_t hook_last_port; static int hook_dir;
static void port_hook(void *c, int dir, uint32_t port, uint32_t val, int size) { (void)c; (void)val; (void)size; hook_n++; hook_last_port = port; hook_dir = dir; }
static int trace_n;
static void trace_hook(void *c, uint32_t pc, uint32_t p, int n, int k) { (void)c; (void)pc; (void)p; (void)n; (void)k; trace_n++; }
static void t_hooks(void) {
  begin(); nan_set_reg(V, 1, 0x777); nan_set_reg(V, 2, 5); I(0x3E, 2, 1, 0); I(0x3F, 3, 1, 0); halt();
  nan_set_port_log(V, port_hook, NULL); nan_set_trace(V, trace_hook, NULL); go(100);
  EQ(hook_n, 2); EQ(hook_last_port, 0x777); EQ(hook_dir, 0); EQ(trace_n, 2);                         /* unknown ports are logged too; HALT is not traced */
  nan_set_port_log(V, NULL, NULL); nan_set_trace(V, NULL, NULL);
  CHECK(!strcmp(nan_strerror(NAN_VSYNC), "vsync")); CHECK(!strcmp(nan_strerror(NAN_TIME_WAIT), "time-wait"));
}

static void t_state(void) {
  nan_vm *B = nan_create(); CHECK(B != NULL);
  begin(); nan_set_reg(V, 1, 0x13); I(0x19, 1, 0, 0); nan_set_reg(V, 7, 33); nan_set_reg(V, 2, 0x99); I(0x10, 2, 7, 0); nan_set_reg(V, 3, 0xDEADBEEFu); nan_set_reg(V, 4, 9000); I(0x09, 3, 4, 3);
  nan_set_reg(V, 5, 0x3FFFFF); I(0x09, 3, 5, 0); I(0x01, 6, 0, 'Q'); I(0x15, 6, 0, 0); nan_set_word(V, 70000, 0x1122334455667788ull);
  nan_port_out(V, 0x3C8, 9, 1); nan_port_out(V, 0x3C9, 1, 1); nan_port_out(V, 0x3C9, 2, 1); nan_key_char(V, 'k'); nan_key_char(V, 'j'); halt(); go(100);
  size_t sz = nan_state_size(V); CHECK(sz > 5000 && sz < 100000); uint8_t *buf = (uint8_t *)malloc(sz);
  EQ(nan_save_state(V, buf, sz), sz); EQ(nan_save_state(V, buf, sz - 1), 0);
  CHECK(nan_restore_state(B, buf, sz) == 0);
  EQ(nan_word(B, 9003), nan_word(V, 9003)); EQ(nan_word(B, 0x3FFFFF), nan_word(V, 0x3FFFFF)); EQ(nan_word(B, 70000), 0x1122334455667788ull); EQ(nan_pc(B), nan_pc(V));
  for (int i = 0; i < 16; i++) EQ(nan_reg(B, i), nan_reg(V, i));
  EQ(nan_display_mode(B), 1); EQ(nan_steps(B), nan_steps(V)); EQ(nan_flags(B), nan_flags(V));
  uint8_t c1[3], c2[3]; nan_dac_rgb(B, 9, c1); nan_dac_rgb(V, 9, c2); CHECK(!memcmp(c1, c2, 3));
  static uint32_t pa[320 * 200], pb[320 * 200]; nan_render_gfx(V, pa, 64000); nan_render_gfx(B, pb, 64000); CHECK(!memcmp(pa, pb, sizeof pa));
  CHECK(nan_restore_state(B, buf, sz) == 0);
  nan_vm *S = V; V = B; pw = 100; nan_set_pc(V, 100); I(0x1A, 1, 0, 0); I(0x1A, 2, 0, 0); I(0x1A, 3, 0, 0); halt(); go(10); V = S;
  EQ(nan_reg(B, 1), 'k'); EQ(nan_reg(B, 2), 'j'); EQ(nan_reg(B, 3), 0);
  /* auto-detect + determinism: run both for the same steps with the same time source */
  const char *kind = ""; CHECK(nan_load_auto(B, buf, sz, &kind) == 0 && !strcmp(kind, "state"));
  /* corruption must be rejected without touching the VM */
  uint8_t *bad = (uint8_t *)malloc(sz); memcpy(bad, buf, sz); bad[0] = 'X'; CHECK(nan_restore_state(B, bad, sz) == -1);
  CHECK(nan_restore_state(B, buf, sz - 1) == -1); CHECK(nan_restore_state(B, buf, 8) == -1); CHECK(nan_restore_state(B, NULL, 100) == -1);
  memcpy(bad, buf, sz); bad[8] ^= 1; CHECK(nan_restore_state(B, bad, sz) == -1);                     /* scalar length field */
  memcpy(bad, buf, sz); bad[4] = 9; CHECK(nan_restore_state(B, bad, sz) == -1);                      /* version */
  { size_t pages = 12 + 6144; memcpy(bad, buf, sz); bad[pages] = 0xFF; bad[pages + 1] = 0xFF; bad[pages + 2] = 0xFF; bad[pages + 3] = 0x7F; CHECK(nan_restore_state(B, bad, sz) == -1); }   /* absurd page count */
  { /* pc/regs in a scalar block that violate invariants: ring head 99 */
    size_t off = 12 + 64 + 12 + 8 + 4 + 16 + 4000 + 768 + 20 + 16;          /* offset of rhead */
    memcpy(bad, buf, sz); bad[off] = 99; CHECK(nan_restore_state(B, bad, sz) == -1);
    memcpy(bad, buf, sz); bad[12 + 64 + 4] = 7; CHECK(nan_restore_state(B, bad, sz) == -1); }          /* k = 7 */
  CHECK(nan_pc(B) == nan_pc(V));                                                                   /* a rejected restore left B untouched */
  /* a fresh deterministic replay: same program run from the same snapshot reaches the same state */
  free(bad);
  begin(); I(0x13, 1, 0, 0); I(0x03, 2, 2, 1); I(0x0A, 0, 0, 0);
  nan_set_time_ms(V, 5); go(1000); size_t s2 = nan_state_size(V); uint8_t *b2 = (uint8_t *)malloc(s2); nan_save_state(V, b2, s2);
  nan_restore_state(B, b2, s2); nan_set_time_ms(B, 5);
  nan_set_time_ms(V, 9); nan_set_time_ms(B, 9); nan_run(V, 777); nan_run(B, 777); EQ(nan_reg(B, 2), nan_reg(V, 2)); EQ(nan_pc(B), nan_pc(V)); EQ(nan_steps(B), nan_steps(V));
  free(b2); free(buf); nan_destroy(B);
}

int main(void) {
  V = nan_create(); if (!V) { puts("nan_create failed"); return 2; }
  struct { const char *n; void (*f)(void); } t[] = {
    {"words", t_words}, {"loaders", t_loaders}, {"alu", t_alu}, {"div", t_div}, {"predication+traps", t_predication_and_traps}, {"fp", t_fp},
    {"memory ops", t_memory_ops}, {"display", t_display}, {"dac", t_dac}, {"keyboard", t_keyboard}, {"time+speaker", t_time_and_speaker},
    {"pci", t_pci}, {"ata", t_ata}, {"hooks", t_hooks}, {"state", t_state} };
  for (size_t i = 0; i < sizeof t / sizeof t[0]; i++) { int before = fails; nan_reset(V); t[i].f(); printf("%-18s %s\n", t[i].n, fails == before ? "ok" : "FAILED"); }
  printf("%d checks, %d failures\n", checks, fails);
  nan_destroy(V); return fails ? 1 : 0;
}
