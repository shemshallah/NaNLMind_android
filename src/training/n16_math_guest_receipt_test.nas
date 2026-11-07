.org 0
JMP @n16_guest_receipt_test_main
.include "../mth/mth_preflight_gate.nas"
.include "../mth/mth_preflight.nas"
.include "../mth/mth_admission.nas"
.include "n16_math_receipt_emit.nas"

n16_guest_receipt_test_main:
  MOVI r13,1
  MOVI r12,@n16_math_scratch
  MOVI r1,@n16_math_candidate
  MOVI r2,@n16_math_rule
  MOVI r3,@n16_math_left
  MOVI r4,@n16_math_right
  MOVI r5,@n16_math_contract
  MOVI r6,1
  MOVI r7,@n16_math_output
  CALL @mth_admit
  CALL @n16_math_emit_receipt
  CMP r1,r0
  JMP[NE] @n16_receipt_fail_1

  MOVI r8,@n16_math_receipt
  LD r9,r8,0
  MOVI r10,1312895286
  CMP r9,r10
  JMP[NE] @n16_receipt_fail_2
  LD r9,r8,1
  MOVI r10,1
  CMP r9,r10
  JMP[NE] @n16_receipt_fail_3
  LD r9,r8,2
  CMP r9,r0
  JMP[NE] @n16_receipt_fail_4
  LD r9,r8,3
  MOVI r10,900
  CMP r9,r10
  JMP[NE] @n16_receipt_fail_5
  LD r9,r8,4
  MOVI r10,7
  CMP r9,r10
  JMP[NE] @n16_receipt_fail_6
  LD r9,r8,5
  MOVI r10,7
  CMP r9,r10
  JMP[NE] @n16_receipt_fail_7
  LD r9,r8,6
  MOVI r10,100
  CMP r9,r10
  JMP[NE] @n16_receipt_fail_8
  LD r9,r8,13
  MOVI r10,107
  CMP r9,r10
  JMP[NE] @n16_receipt_fail_9

  ; Clear the raw receipt, then prove a stale epoch emits no receipt at all.
  MOVI r8,@n16_math_receipt
  MOVI r9,0
  ST r9,r8,0
  ST r9,r8,1
  ST r9,r8,2
  ST r9,r8,3
  ST r9,r8,4
  ST r9,r8,5
  ST r9,r8,6
  ST r9,r8,7
  ST r9,r8,8
  ST r9,r8,9
  ST r9,r8,10
  ST r9,r8,11
  ST r9,r8,12
  ST r9,r8,13
  MOVI r8,@n16_math_candidate
  MOVI r9,6
  ST r9,r8,9
  MOVI r1,@n16_math_candidate
  MOVI r2,@n16_math_rule
  MOVI r3,@n16_math_left
  MOVI r4,@n16_math_right
  MOVI r5,@n16_math_contract
  MOVI r6,1
  MOVI r7,@n16_math_output
  MOVI r12,@n16_math_scratch
  CALL @mth_admit
  MOVI r10,18
  CMP r1,r10
  JMP[NE] @n16_receipt_fail_10
  CALL @n16_math_emit_receipt
  MOVI r8,@n16_math_receipt
  LD r9,r8,0
  CMP r9,r0
  JMP[NE] @n16_receipt_fail_11

  MOVI r1,@n16_receipt_pass
  CALL @n16_receipt_puts
  HALT

n16_receipt_fail_1:
  MOVI r1,1
  JMP @n16_receipt_fail
n16_receipt_fail_2:
  MOVI r1,2
  JMP @n16_receipt_fail
n16_receipt_fail_3:
  MOVI r1,3
  JMP @n16_receipt_fail
n16_receipt_fail_4:
  MOVI r1,4
  JMP @n16_receipt_fail
n16_receipt_fail_5:
  MOVI r1,5
  JMP @n16_receipt_fail
n16_receipt_fail_6:
  MOVI r1,6
  JMP @n16_receipt_fail
n16_receipt_fail_7:
  MOVI r1,7
  JMP @n16_receipt_fail
n16_receipt_fail_8:
  MOVI r1,8
  JMP @n16_receipt_fail
n16_receipt_fail_9:
  MOVI r1,9
  JMP @n16_receipt_fail
n16_receipt_fail_10:
  MOVI r1,10
  JMP @n16_receipt_fail
n16_receipt_fail_11:
  MOVI r1,11
n16_receipt_fail:
  MOVI r1,@n16_receipt_bad
  CALL @n16_receipt_puts
  HALT

n16_receipt_puts:
  MOVI r4,255
n16_receipt_puts_word:
  LD r2,r1,0
  MOVI r3,4
n16_receipt_puts_byte:
  BOOL.8 r5,r2,r4
  CMP r5,r0
  RET[EQ]
  PUTC r5
  SHR r2,r2,8
  SUB r3,r3,r13
  JMP[NE] @n16_receipt_puts_byte
  ADD r1,r1,r13
  JMP @n16_receipt_puts_word

n16_receipt_pass:
.str "MTH NATIVE RECEIPT PASS\n"
n16_receipt_bad:
.str "MTH NATIVE RECEIPT FAIL\n"

.org 40000
n16_math_candidate:
.word 1,7,4,4,3,1,1,3,3,7,1,1,2,1,100,101,102,103,104,105,106,107
n16_math_rule:
.word 1,7,4,4,3,1,1,1,1,100,101,102,103,104,105,106,107
n16_math_left:
.word 4,1,3,1,1,@n16_math_left_payload,3,1
n16_math_right:
.word 4,1,3,1,1,@n16_math_right_payload,3,1
n16_math_contract:
.word 4,4,3,1,1,0,1,1,1,0
n16_math_capabilities:
.word 1
n16_math_program_id:
.word 900
n16_math_output:
.word 0,0,0,0,0,@n16_math_output_payload,1,0
n16_math_scratch:
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
n16_math_receipt:
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0
.org 40200
n16_math_left_payload:
.word 1,2,3
n16_math_right_payload:
.word 4,5,6
n16_math_output_payload:
.word 0
