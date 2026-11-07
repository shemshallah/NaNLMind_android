.org 0
JMP @n16_math_admission_entry
.include "../mth/mth_preflight_gate.nas"
.include "../mth/mth_preflight.nas"
.include "../mth/mth_admission.nas"

; Host writes the descriptor arrays into the fixed word-addressed mailboxes.
; This wrapper only invokes the canonical composed NaN16 admission routine.
n16_math_admission_entry:
  MOVI r13,1
  MOVI r12,@n16_math_scratch
  MOVI r1,@n16_math_candidate
  MOVI r2,@n16_math_rule
  MOVI r3,@n16_math_left
  MOVI r4,@n16_math_right
  MOVI r5,@n16_math_contract
  MOVI r6,@n16_math_capabilities
  LD r6,r6,0
  MOVI r7,@n16_math_output
  CALL @mth_admit
  ST r1,r0,@n16_math_result
  HALT

.org 40000
n16_math_candidate:
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
n16_math_rule:
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
n16_math_left:
.word 0,0,0,0,0,0,0,0
n16_math_right:
.word 0,0,0,0,0,0,0,0
n16_math_contract:
.word 0,0,0,0,0,0,0,0,0,0
n16_math_capabilities:
.word 0
n16_math_output:
.word 0,0,0,0,0,0,0,0
n16_math_scratch:
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
n16_math_result:
.word 0

; Isolated bounded payload windows used by the Android adapter.
; Each buffer reserves 65,536 NaN16 words; pointers are rewritten only inside
; this disposable admission VM, never in the canonical object descriptor.
.org 50000
n16_math_left_payload:
.org 116000
n16_math_right_payload:
.org 182000
n16_math_output_payload:
