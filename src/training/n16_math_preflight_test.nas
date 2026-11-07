.org 0
JMP @n16_math_preflight_test_main
.include "../mth/mth_preflight.nas"

n16_math_preflight_test_main:
  MOVI r13,1
  MOVI r12,@n16_pf_scratch

  ; Non-overlapping payloads are admitted.
  MOVI r1,@n16_pf_left
  MOVI r2,@n16_pf_right
  MOVI r3,@n16_pf_contract
  MOVI r4,1
  MOVI r5,@n16_pf_output
  CALL @mth_preflight
  CMP r1,r0
  JMP[NE] @n16_pf_fail_1

  ; Output begins one word inside the left payload allocation.
  MOVI r6,@n16_pf_output
  MOVI r7,55
  ST r7,r6,0
  MOVI r7,@n16_pf_left_payload
  ADDI r7,r7,1
  ST r7,r6,5
  MOVI r7,3
  ST r7,r6,6
  MOVI r1,@n16_pf_left
  MOVI r2,@n16_pf_right
  MOVI r3,@n16_pf_contract
  MOVI r4,1
  MOVI r5,@n16_pf_output
  CALL @mth_preflight
  MOVI r7,8
  CMP r1,r7
  JMP[NE] @n16_pf_fail_2
  MOVI r6,@n16_pf_output
  LD r7,r6,0
  MOVI r8,55
  CMP r7,r8
  JMP[NE] @n16_pf_fail_3

  ; Output begins one word inside the right payload allocation.
  MOVI r6,@n16_pf_output
  MOVI r7,55
  ST r7,r6,0
  MOVI r7,@n16_pf_right_payload
  ADDI r7,r7,1
  ST r7,r6,5
  MOVI r7,3
  ST r7,r6,6
  MOVI r1,@n16_pf_left
  MOVI r2,@n16_pf_right
  MOVI r3,@n16_pf_contract
  MOVI r4,1
  MOVI r5,@n16_pf_output
  CALL @mth_preflight
  MOVI r7,8
  CMP r1,r7
  JMP[NE] @n16_pf_fail_4
  MOVI r6,@n16_pf_output
  LD r7,r6,0
  MOVI r8,55
  CMP r7,r8
  JMP[NE] @n16_pf_fail_5

  MOVI r1,@n16_pf_pass
  CALL @n16_pf_puts
  HALT

n16_pf_fail_1:
  MOVI r1,1
  JMP @n16_pf_fail
n16_pf_fail_2:
  MOVI r1,2
  JMP @n16_pf_fail
n16_pf_fail_3:
  MOVI r1,3
  JMP @n16_pf_fail
n16_pf_fail_4:
  MOVI r1,4
  JMP @n16_pf_fail
n16_pf_fail_5:
  MOVI r1,5
n16_pf_fail:
  MOVI r1,@n16_pf_bad
  CALL @n16_pf_puts
  HALT

n16_pf_puts:
  MOVI r4,255
n16_pf_puts_word:
  LD r2,r1,0
  MOVI r3,4
n16_pf_puts_byte:
  BOOL.8 r5,r2,r4
  CMP r5,r0
  RET[EQ]
  PUTC r5
  SHR r2,r2,8
  SUB r3,r3,r13
  JMP[NE] @n16_pf_puts_byte
  ADD r1,r1,r13
  JMP @n16_pf_puts_word

.org 40000
n16_pf_left:
.word 4,1,3,1,1,@n16_pf_left_payload,3,1
n16_pf_right:
.word 4,1,3,1,1,@n16_pf_right_payload,3,1
n16_pf_output:
.word 0,0,0,0,0,@n16_pf_output_payload,3,0
n16_pf_contract:
.word 4,4,3,1,1,0,1,1,1,2
n16_pf_scratch:
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.org 40200
n16_pf_left_payload:
.word 1,2,3
n16_pf_right_payload:
.word 4,5,6
n16_pf_output_payload:
.word 0,0,0
n16_pf_pass:
.str "MTH PAYLOAD RANGE PREFLIGHT PASS\n"
n16_pf_bad:
.str "MTH PAYLOAD RANGE PREFLIGHT FAIL\n"
