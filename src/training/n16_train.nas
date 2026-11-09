; NaN16 corpus trainer v1.
; Input: N16TXT/1 LF header followed by newline-delimited UTF-8 records.
; Host boundary writes bytes at byte address 327680 (word 81920) and appends NUL.
; This starts AFTER the dense bigram model (words 16384..81919); the
; earlier 49152-byte address overlapped its cells on corpora over 16 KiB.
; All parsing, bounds, record accounting, frequency updates and digest work run here.
; Candidate model: unigram[256] at 8192; bigram[256x256] at 16384.
; Output statistics: words 8448..8450 = content bytes, records, rolling digest.
main:
MOVI r13,1
LA r4,0x00050000
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
; Preserve the END byte address before schedule_index overwrites r4 with
; the permutation result. The prior scrub compared against a sample index
; (0..record_count) and silently skipped clearing the corpus region.
; r14 is free after strict UTF-8 completion and untouched by the scheduler.
ADDI r14,r4,0
; The count-based byte model is order-independent. Record the exact guest
; epoch-1 sample permutation as candidate metadata so runner and Android
; can verify the same schedule before a future order-sensitive trainer uses it.
MOVI r9,0
MOVI r8,5381
schedule_audit_loop:
CMP r9,r6
JMP[GE] @schedule_audit_done
ADDI r1,r6,0
MOVI r2,1
ADDI r3,r9,0
CALL @schedule_index
CMP r5,r0
JMP[NE] @reject
MOVI r10,5
SHL r10,r8,5
ADD r8,r10,r8
ADD r8,r8,r4
ADDI r9,r9,1
JMP @schedule_audit_loop
schedule_audit_done:
MOVI r12,8456
ST r8,r12,0
; Restore the original scan terminator byte pointer, not the final draw index.
ADDI r4,r14,0
; Clear the original corpus bytes before any candidate checkpoint is exported.
LA r2,0x00050000
clear_corpus:
CMP r2,r4
JMP[GT] @corpus_cleared
STB r0,r2,0
ADDI r2,r2,1
JMP @clear_corpus
corpus_cleared:
; Exercise the same callable NaN16 inference ABI that a later guest
; completion interface can use. This stores evidence, not a generated text.
MOVI r1,65
CALL @predict_next
MOVI r12,8457
ST r4,r12,0
ST r5,r12,1
; Byte 255 is unseen in this bounded ASCII sample: require real backoff.
MOVI r1,255
CALL @predict_next
MOVI r12,8459
ST r4,r12,0
ST r5,r12,1
; An out-of-range context must refuse inference instead of indexing RAM.
MOVI r1,256
CALL @predict_next
MOVI r12,8461
ST r4,r12,0
ST r5,r12,1
; Bounded native multi-byte completion probe. The guest, not the host,
; follows its predicted byte back into the next prediction. Four steps fit
; the dedicated guest-owned diagnostic words; no corpus bytes are retained.
MOVI r1,65
MOVI r2,4
CALL @predict_chain
MOVI r12,8463
ST r4,r12,0
ST r5,r12,1
; Refuse unbounded requests without writing the completion buffer.
MOVI r1,65
MOVI r2,9
CALL @predict_chain
MOVI r12,8465
ST r4,r12,0
ST r5,r12,1
; Signed negative contexts must also fail closed, not wrap into tables.
MOVI r1,1
SUB r1,r0,r1
CALL @predict_next
MOVI r12,8467
ST r4,r12,0
ST r5,r12,1
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

; Deterministic epoch rotation: a complete permutation of [0,count).
schedule_index:
MOVI r5,0
CMP r1,r0
JMP[EQ] @schedule_invalid
CMP r3,r1
JMP[GE] @schedule_invalid
MODU r4,r2,r1
ADD r4,r4,r3
MODU r4,r4,r1
RET
schedule_invalid:
MOVI r5,1
MOVI r4,0
RET
; Native count-based next-byte completion, callable from a persisted trainer
; image without creating another model or relying on a host opcode handler.
; ABI: r1=previous byte (0..255).
;      r4=best next byte; r5=0 bigram,1 unigram fallback,
;         2 no learned counts,3 invalid previous byte.
; Deterministic lowest-byte tie break, no record-crossing bigrams.
; All counts and choices remain inside the NaN16 guest.
predict_next:
CMP r1,r0
JMP[LT] @predict_invalid
MOVI r6,256
CMP r1,r6
JMP[GE] @predict_invalid
SHL r6,r1,8
MOVI r7,16384
ADD r6,r6,r7
MOVI r7,0
MOVI r8,0
MOVI r4,0
predict_bigram:
LD r9,r6,0
CMP r9,r8
JMP[GT] @predict_bigram_best
predict_bigram_advance:
ADDI r6,r6,1
ADDI r7,r7,1
MOVI r10,256
CMP r7,r10
JMP[LT] @predict_bigram
CMP r8,r0
JMP[EQ] @predict_fallback
MOVI r5,0
RET
predict_bigram_best:
ADDI r8,r9,0
ADDI r4,r7,0
JMP @predict_bigram_advance
predict_fallback:
MOVI r6,8192
MOVI r7,0
MOVI r8,0
MOVI r4,0
predict_unigram:
LD r9,r6,0
CMP r9,r8
JMP[GT] @predict_unigram_best
predict_unigram_advance:
ADDI r6,r6,1
ADDI r7,r7,1
MOVI r10,256
CMP r7,r10
JMP[LT] @predict_unigram
CMP r8,r0
JMP[EQ] @predict_empty
MOVI r5,1
RET
predict_unigram_best:
ADDI r8,r9,0
ADDI r4,r7,0
JMP @predict_unigram_advance
predict_empty:
MOVI r5,2
RET
predict_invalid:
MOVI r4,0
MOVI r5,3
RET

; Bounded greedy generation composed entirely of native NaN16 CALLs.
; ABI: r1=start byte (0..255); r2=length (1..8).
;      On success r4=number generated and r5=0; r5=1 invalid length,
;      r5=2 untrained model, r5=3 invalid context. Failed validation never
;      writes results. Guest words 8470..8477 hold bytes and 8478..8485
;      hold per-step backoff codes (0 bigram, 1 unigram).
; IMPORTANT: NaN16 CALL stores its return PC in a SINGLE r15 link register.
; The nested CALL to predict_next must save/restore r15 explicitly.
predict_chain:
MOVI r4,0
MOVI r5,1
CMP r2,r0
JMP[LE] @predict_chain_return
MOVI r10,8
CMP r2,r10
JMP[GT] @predict_chain_return
CMP r1,r0
JMP[LT] @predict_chain_invalid
MOVI r10,256
CMP r1,r10
JMP[GE] @predict_chain_invalid
ADDI r14,r15,0
MOVI r11,0
MOVI r12,8470
predict_chain_step:
CALL @predict_next
MOVI r10,2
CMP r5,r10
JMP[GE] @predict_chain_failure
ST r4,r12,0
ST r5,r12,8
ADDI r1,r4,0
ADDI r12,r12,1
ADDI r11,r11,1
CMP r11,r2
JMP[LT] @predict_chain_step
MOVI r5,0
predict_chain_failure:
ADDI r4,r11,0
ADDI r15,r14,0
RET
predict_chain_invalid:
MOVI r5,3
predict_chain_return:
MOVI r4,0
RET

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
.org 8456
; Schedule digest, next byte for 'A', prediction status, unseen-context
; backoff byte and status. These are observable candidate diagnostics.
prediction_probe:
.word 0,0,0,0,0,0,0
.org 8463
; chain length/status, invalid-length length/status, negative-context result/status
chain_metadata:
.word 0,0,0,0,0,0
.org 8470
chain_bytes:
.word 0,0,0,0,0,0,0,0
chain_statuses:
.word 0,0,0,0,0,0,0,0
.org 81920
corpus:
.str "N16TXT/1\nNaN16 owns the corpus codec.\nRunner and Android share one trainer.\nThe intake stages updates before merge.\n"
