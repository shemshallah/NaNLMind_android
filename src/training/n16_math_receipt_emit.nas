; Emit the fixed 14-word raw receipt only after mth_admit returns ALLOW.
; The host adapter supplies program_id; this guest routine echoes it alongside
; the exact candidate/rule epochs and authoritative eight-word rule digest.
; ABI labels are provided by the including wrapper or native regression fixture.
n16_math_emit_receipt:
  CMP r1,r0
  JMP[NE] @n16_math_emit_receipt_return

  MOVI r8,@n16_math_candidate
  LD r9,r8,9
  MOVI r10,@n16_math_rule
  LD r11,r10,1
  MOVI r12,@n16_math_receipt

  MOVI r7,1312895286
  ST r7,r12,0
  MOVI r7,1
  ST r7,r12,1
  MOVI r7,0
  ST r7,r12,2
  MOVI r7,@n16_math_program_id
  LD r7,r7,0
  ST r7,r12,3
  ST r9,r12,4
  ST r11,r12,5

  LD r7,r10,9
  ST r7,r12,6
  LD r7,r10,10
  ST r7,r12,7
  LD r7,r10,11
  ST r7,r12,8
  LD r7,r10,12
  ST r7,r12,9
  LD r7,r10,13
  ST r7,r12,10
  LD r7,r10,14
  ST r7,r12,11
  LD r7,r10,15
  ST r7,r12,12
  LD r7,r10,16
  ST r7,r12,13

n16_math_emit_receipt_return:
  RET
