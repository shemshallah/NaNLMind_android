; NaN16 corpus trainer v1.
; Input: N16TXT/1 LF header followed by newline-delimited UTF-8 records.
; Host boundary writes bytes at byte address 49152 and appends NUL.
; All parsing, bounds, record accounting, frequency updates and digest work run here.
; Candidate model: unigram[256] at 8192; bigram[256x256] at 16384.
; Output statistics: words 8448..8450 = content bytes, records, rolling digest.
main:
MOVI r13,1
MOVI r4,49152
; exact 9-byte header: N16TXT/1 + LF
LDB r1,r4,0
MOVI r2,78
CMP r1,r2
JMP[NE] @reject
LDB r1,r4,1
MOVI r2,49
CMP r1,r2
JMP[NE] @reject
LDB r1,r4,2
MOVI r2,54
CMP r1,r2
JMP[NE] @reject
LDB r1,r4,3
MOVI r2,84
CMP r1,r2
JMP[NE] @reject
LDB r1,r4,4
MOVI r2,88
CMP r1,r2
JMP[NE] @reject
LDB r1,r4,5
MOVI r2,84
CMP r1,r2
JMP[NE] @reject
LDB r1,r4,6
MOVI r2,47
CMP r1,r2
JMP[NE] @reject
LDB r1,r4,7
MOVI r2,49
CMP r1,r2
JMP[NE] @reject
LDB r1,r4,8
MOVI r2,10
CMP r1,r2
JMP[NE] @reject
ADDI r4,r4,9
MOVI r5,0
MOVI r6,0
MOVI r7,0
MOVI r8,5381
MOVI r11,8192
MOVI r14,0
ST r0,r0,@prev_valid
MOVI r3,0
scan:
LDB r9,r4,0
CMP r9,r0
JMP[EQ] @finish
CMP r14,r0
JMP[NE] @utf8_cont
MOVI r10,13
CMP r9,r10
JMP[EQ] @cr
MOVI r10,10
CMP r9,r10
JMP[EQ] @newline
MOVI r10,32
CMP r9,r10
JMP[LT] @control
MOVI r10,127
CMP r9,r10
JMP[LT] @ascii_accept
JMP @utf8_start
control:
MOVI r10,9
CMP r9,r10
JMP[EQ] @accept
JMP @reject
ascii_accept:
MOVI r3,0
JMP @accept

; Strict UTF-8 lead-byte ranges. r14 = continuation bytes remaining.
; r3 carries a first-continuation restriction: 1 >=A0, 2 <=9F,
; 3 >=90, 4 <=8F. This rejects overlong forms, surrogates and >U+10FFFF.
utf8_start:
MOVI r10,193
CMP r9,r10
JMP[LE] @reject
MOVI r10,223
CMP r9,r10
JMP[LE] @utf8_two
MOVI r10,239
CMP r9,r10
JMP[LE] @utf8_three
MOVI r10,244
CMP r9,r10
JMP[LE] @utf8_four
JMP @reject
utf8_two:
MOVI r14,1
MOVI r3,0
MOVI r10,194
CMP r9,r10
JMP[EQ] @utf8_c2
JMP @accept
utf8_c2:
MOVI r3,5
JMP @accept
utf8_three:
MOVI r14,2
MOVI r3,0
MOVI r10,224
CMP r9,r10
JMP[EQ] @utf8_e0
MOVI r10,237
CMP r9,r10
JMP[EQ] @utf8_ed
JMP @accept
utf8_e0:
MOVI r3,1
JMP @accept
utf8_ed:
MOVI r3,2
JMP @accept
utf8_four:
MOVI r14,3
MOVI r3,0
MOVI r10,240
CMP r9,r10
JMP[EQ] @utf8_f0
MOVI r10,244
CMP r9,r10
JMP[EQ] @utf8_f4
JMP @accept
utf8_f0:
MOVI r3,3
JMP @accept
utf8_f4:
MOVI r3,4
JMP @accept

utf8_cont:
MOVI r10,128
CMP r9,r10
JMP[LT] @reject
MOVI r10,191
CMP r10,r9
JMP[LT] @reject
CMP r3,r0
JMP[EQ] @utf8_cont_ok
MOVI r10,1
CMP r3,r10
JMP[EQ] @utf8_min_a0
MOVI r10,2
CMP r3,r10
JMP[EQ] @utf8_max_9f
MOVI r10,3
CMP r3,r10
JMP[EQ] @utf8_min_90
MOVI r10,5
CMP r3,r10
JMP[EQ] @utf8_min_a0
MOVI r10,143
CMP r10,r9
JMP[LT] @reject
JMP @utf8_cont_ok
utf8_min_a0:
MOVI r10,160
CMP r9,r10
JMP[LT] @reject
JMP @utf8_cont_ok
utf8_max_9f:
MOVI r10,159
CMP r10,r9
JMP[LT] @reject
JMP @utf8_cont_ok
utf8_min_90:
MOVI r10,144
CMP r9,r10
JMP[LT] @reject
utf8_cont_ok:
MOVI r3,0
MOVI r10,1
SUB r14,r14,r10
JMP @accept

accept:
ADDI r7,r7,1
MOVI r10,4096
CMP r7,r10
JMP[GT] @reject
ADDI r5,r5,1
MOVI r10,0
MOVHI r10,16
CMP r5,r10
JMP[GT] @reject
; rolling digest h = h*33 + byte
MOVI r10,5
SHL r10,r8,5
ADD r8,r10,r8
ADD r8,r8,r9
; NaN16-owned byte-frequency table
ADD r12,r11,r9
LD r10,r12,0
ADDI r10,r10,1
ST r10,r12,0
; Candidate next-byte model: bigram[previous][current]++.
LD r2,r0,@prev_valid
CMP r2,r0
JMP[EQ] @set_prev
LD r2,r0,@prev_byte
SHL r2,r2,8
ADD r2,r2,r9
MOVI r12,16384
ADD r12,r12,r2
LD r10,r12,0
ADDI r10,r10,1
ST r10,r12,0
set_prev:
ST r9,r0,@prev_byte
MOVI r2,1
ST r2,r0,@prev_valid
JMP @advance
cr:
; CR is legal only as the first byte of a CRLF line ending.
LDB r10,r4,1
MOVI r2,10
CMP r10,r2
JMP[NE] @reject
JMP @advance

advance:
ADDI r4,r4,1
JMP @scan
newline:
CMP r14,r0
JMP[NE] @reject
CMP r7,r0
JMP[EQ] @newline_advance
ADDI r6,r6,1
MOVI r7,0
ST r0,r0,@prev_valid
ADDI r4,r4,1
JMP @scan
newline_advance:
ADDI r4,r4,1
JMP @scan
finish:
CMP r14,r0
JMP[NE] @reject
CMP r7,r0
JMP[EQ] @finish_counted
ADDI r6,r6,1
finish_counted:
CMP r6,r0
JMP[EQ] @reject
MOVI r12,8448
ST r5,r12,0
ST r6,r12,1
ST r8,r12,2
; Clear the original corpus bytes before any candidate checkpoint is exported.
MOVI r2,49152
clear_corpus:
CMP r2,r4
JMP[GT] @corpus_cleared
STB r0,r2,0
ADDI r2,r2,1
JMP @clear_corpus
corpus_cleared:
; report success on COM1
MOVI r1,78
PUTC r1
MOVI r1,49
PUTC r1
MOVI r1,54
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,84
PUTC r1
MOVI r1,82
PUTC r1
MOVI r1,65
PUTC r1
MOVI r1,73
PUTC r1
MOVI r1,78
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,80
PUTC r1
MOVI r1,65
PUTC r1
MOVI r1,83
PUTC r1
MOVI r1,83
PUTC r1
MOVI r1,10
PUTC r1
HALT
reject:
MOVI r1,78
PUTC r1
MOVI r1,49
PUTC r1
MOVI r1,54
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,84
PUTC r1
MOVI r1,82
PUTC r1
MOVI r1,65
PUTC r1
MOVI r1,73
PUTC r1
MOVI r1,78
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,82
PUTC r1
MOVI r1,69
PUTC r1
MOVI r1,74
PUTC r1
MOVI r1,69
PUTC r1
MOVI r1,67
PUTC r1
MOVI r1,84
PUTC r1
MOVI r1,10
PUTC r1
HALT
.org 8192
freq:
.word 0
.org 8448
stats:
.word 0,0,0
prev_byte:
.word 0
prev_valid:
.word 0
.org 12288
corpus:
.str "N16TXT/1\nNaN16 owns the corpus codec.\nRunner and Android share one trainer.\nThe intake stages updates before merge.\n"
