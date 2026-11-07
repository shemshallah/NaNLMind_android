.org 0
JMP @receipt_test_main
.include "../mth/mth_preflight_gate.nas"
.include "../mth/mth_preflight.nas"
.include "../mth/mth_admission.nas"
.include "n16_math_receipt_emit.nas"

receipt_test_main:
  MOVI r13,1
  MOVI r12,@receipt_test_scratch
  MOVI r1,@receipt_test_candidate
  MOVI r2,@receipt_test_rule
  MOVI r3,@receipt_test_left
  MOVI r4,@receipt_test_right
  MOVI r5,@receipt_test_contract
  MOVI r6,1
  MOVI r7,@receipt_test_output
  CALL @mth_admit
  CMP r1,r0
  JMP[NE] @receipt_test_fail_allow
  CALL @n16_math_emit_receipt

  ; Verify the complete raw receipt header and eight-word rule digest.
  MOVI r8,@receipt_test_receipt
  LD r9,r8,0
  MOVI r10,1312895286
  CMP r9,r10
  JMP[NE] @receipt_test_fail_header
  LD r9,r8,1
  MOVI r10,1
  CMP r9,r10
  JMP[NE] @receipt_test_fail_header
  LD r9,r8,2
  CMP r9,r0
  JMP[NE] @receipt_test_fail_header
  LD r9,r8,3
  MOVI r10,900
  CMP r9,r10
  JMP[NE] @receipt_test_fail_header
  LD r9,r8,4
  MOVI r10,7
  CMP r9,r10
  JMP[NE] @receipt_test_fail_epoch
  LD r9,r8,5
  CMP r9,r10
  JMP[NE] @receipt_test_fail_epoch
  LD r9,r8,6
  MOVI r10,100
  CMP r9,r10
  JMP[NE] @receipt_test_fail_digest
  LD r9,r8,7
  MOVI r10,101
  CMP r9,r10
  JMP[NE] @receipt_test_fail_digest
  LD r9,r8,8
  MOVI r10,102
  CMP r9,r10
  JMP[NE] @receipt_test_fail_digest
  LD r9,r8,9
  MOVI r10,103
  CMP r9,r10
  JMP[NE] @receipt_test_fail_digest
  LD r9,r8,10
  MOVI r10,104
  CMP r9,r10
  JMP[NE] @receipt_test_fail_digest
  LD r9,r8,11
  MOVI r10,105
  CMP r9,r10
  JMP[NE] @receipt_test_fail_digest
  LD r9,r8,12
  MOVI r10,106
  CMP r9,r10
  JMP[NE] @receipt_test_fail_digest
  LD r9,r8,13
  MOVI r10,107
  CMP r9,r10
  JMP[NE] @receipt_test_fail_digest

  ; A stale candidate epoch must deny and leave every receipt word zero.
  MOVI r8,@receipt_test_candidate
  MOVI r9,6
  ST r9,r8,9
  MOVI r8,@receipt_test_receipt
  MOVI r9,14
  MOVI r10,0
receipt_test_clear:
  ST r10,r8,0
  ADD r8,r8,r13
  SUB r9,r9,r13
  JMP[NE] @receipt_test_clear
  MOVI r1,@receipt_test_candidate
  MOVI r2,@receipt_test_rule
  MOVI r3,@receipt_test_left
  MOVI r4,@receipt_test_right
  MOVI r5,@receipt_test_contract
  MOVI r6,1
  MOVI r7,@receipt_test_output
  CALL @mth_admit
  MOVI r10,18
  CMP r1,r10
  JMP[NE] @receipt_test_fail_deny
  CALL @n16_math_emit_receipt
  MOVI r8,@receipt_test_receipt
  MOVI r9,14
receipt_test_deny_zero:
  LD r10,r8,0
  CMP r10,r0
  JMP[NE] @receipt_test_fail_deny
  ADD r8,r8,r13
  SUB r9,r9,r13
  JMP[NE] @receipt_test_deny_zero

  MOVI r1,@receipt_test_pass
  CALL @receipt_test_puts
  HALT

receipt_test_fail_allow:
  MOVI r1,@receipt_test_fail_allow_text
  JMP @receipt_test_fail
receipt_test_fail_header:
  MOVI r1,@receipt_test_fail_header_text
  JMP @receipt_test_fail
receipt_test_fail_epoch:
  MOVI r1,@receipt_test_fail_epoch_text
  JMP @receipt_test_fail
receipt_test_fail_digest:
  MOVI r1,@receipt_test_fail_digest_text
  JMP @receipt_test_fail
receipt_test_fail_deny:
  MOVI r1,@receipt_test_fail_deny_text
receipt_test_fail:
  CALL @receipt_test_puts
  HALT

receipt_test_puts:
  MOVI r4,255
receipt_test_puts_word:
  LD r2,r1,0
  MOVI r3,4
receipt_test_puts_byte:
  BOOL.8 r5,r2,r4
  CMP r5,r0
  RET[EQ]
  PUTC r5
  SHR r2,r2,8
  SUB r3,r3,r13
  JMP[NE] @receipt_test_puts_byte
  ADD r1,r1,r13
  JMP @receipt_test_puts_word

.org 40000
receipt_test_candidate:
.word 1,7,4,4,3,1,1,3,3,7,1,1,2,1,100,101,102,103,104,105,106,107
receipt_test_rule:
.word 1,7,4,4,3,1,1,1,1,100,101,102,103,104,105,106,107
receipt_test_left:
.word 4,1,3,1,1,@receipt_test_left_payload,3,1
receipt_test_right:
.word 4,1,3,1,1,@receipt_test_right_payload,3,1
receipt_test_contract:
.word 4,4,3,1,1,0,1,1,1,0
receipt_test_output:
.word 0,0,0,0,0,@receipt_test_output_payload,1,0
receipt_test_program_id:
.word 900
receipt_test_receipt:
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0
receipt_test_pass:
.str "NAN16 RAW RECEIPT PASS\n"
receipt_test_fail_allow_text:
.str "NAN16 RAW RECEIPT FAIL: allow\n"
receipt_test_fail_header_text:
.str "NAN16 RAW RECEIPT FAIL: header\n"
receipt_test_fail_epoch_text:
.str "NAN16 RAW RECEIPT FAIL: epoch\n"
receipt_test_fail_digest_text:
.str "NAN16 RAW RECEIPT FAIL: digest\n"
receipt_test_fail_deny_text:
.str "NAN16 RAW RECEIPT FAIL: deny emitted receipt\n"
.org 40200
receipt_test_left_payload:
.word 1,2,3
receipt_test_right_payload:
.word 4,5,6
receipt_test_output_payload:
.word 0
receipt_test_scratch:
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
