.org 0
JMP @n16_nmp_kernel_add_test_entry
.include "n16_nmp_kernel_body.nas"

n16_nmp_kernel_add_test_entry:
  MOVI r13,1
  MOVI r8,1
  ST r8,r0,@n16_nmp_operation
  MOVI r8,1401
  ST r8,r0,@n16_nmp_left_id
  MOVI r8,1402
  ST r8,r0,@n16_nmp_right_id
  MOVI r8,1
  ST r8,r0,@n16_nmp_left_owner
  MOVI r8,2
  ST r8,r0,@n16_nmp_right_owner
  MOVI r8,1101
  ST r8,r0,@n16_nmp_left_digest_marker
  MOVI r8,1102
  ST r8,r0,@n16_nmp_right_digest_marker
  MOVI r8,4
  ST r8,r0,@n16_nmp_dimension

  MOVI r7,@n16_nmp_left_payload
  MOVI r8,1
  ST r8,r7,0
  MOVI r8,2
  ST r8,r7,1
  MOVI r8,3
  ST r8,r7,2
  MOVI r8,4
  ST r8,r7,3

  MOVI r7,@n16_nmp_right_payload
  MOVI r8,10
  ST r8,r7,0
  MOVI r8,20
  ST r8,r7,1
  MOVI r8,30
  ST r8,r7,2
  MOVI r8,40
  ST r8,r7,3

  CALL @n16_nmp_kernel_entry

  MOVI r7,@n16_nmp_result
  LD r8,r7,0
  CMP r8,r0
  JMP[NE] @n16_nmp_add_fail
  LD r8,r7,1
  MOVI r9,78
  CMP r8,r9
  JMP[NE] @n16_nmp_add_fail
  LD r8,r7,2
  MOVI r9,4
  CMP r8,r9
  JMP[NE] @n16_nmp_add_fail
  LD r8,r7,3
  CMP r8,r9
  JMP[NE] @n16_nmp_add_fail
  LD r8,r7,4
  CMP r8,r9
  JMP[NE] @n16_nmp_add_fail

  MOVI r7,@n16_nmp_output_payload
  LD r8,r7,0
  MOVI r9,11
  CMP r8,r9
  JMP[NE] @n16_nmp_add_fail
  LD r8,r7,1
  MOVI r9,22
  CMP r8,r9
  JMP[NE] @n16_nmp_add_fail
  LD r8,r7,2
  MOVI r9,33
  CMP r8,r9
  JMP[NE] @n16_nmp_add_fail
  LD r8,r7,3
  MOVI r9,44
  CMP r8,r9
  JMP[NE] @n16_nmp_add_fail

  MOVI r1,78
  PUTC r1
  MOVI r1,77
  PUTC r1
  MOVI r1,80
  PUTC r1
  MOVI r1,32
  PUTC r1
  MOVI r1,65
  PUTC r1
  MOVI r1,68
  PUTC r1
  MOVI r1,68
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

n16_nmp_add_fail:
  MOVI r1,70
  PUTC r1
  MOVI r1,65
  PUTC r1
  MOVI r1,73
  PUTC r1
  MOVI r1,76
  PUTC r1
  MOVI r1,10
  PUTC r1
  HALT
