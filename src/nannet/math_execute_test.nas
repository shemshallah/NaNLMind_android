; End-to-end typed vector.add.v1 execution through the NaN16 program dispatcher.
main:
; Register two vector(4) endpoints and an immutable operator mapping to guest program 78.
MOVI r1,1401
MOVI r2,4
MOVI r3,1
MOVI r4,1101
CALL @nmr_obj_put
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r1,1401
MOVI r2,@math_vec_left
CALL @nmr_obj_bind_data
CMP r3,r0
JMP[NE] @math_exec_fail
; Rebinding the identical payload address is idempotent.
MOVI r1,1401
MOVI r2,@math_vec_left
CALL @nmr_obj_bind_data
CMP r3,r0
JMP[NE] @math_exec_fail
; A different payload cannot silently replace an immutable binding.
MOVI r1,1401
MOVI r2,@math_vec_right
CALL @nmr_obj_bind_data
MOVI r6,5
CMP r3,r6
JMP[NE] @math_exec_fail
MOVI r1,1402
MOVI r2,4
MOVI r3,2
MOVI r4,1102
CALL @nmr_obj_put
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r1,1402
MOVI r2,@math_vec_right
CALL @nmr_obj_bind_data
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r1,702
MOVI r2,4
MOVI r3,4
MOVI r4,4
MOVI r5,78
CALL @nmr_op_put
CMP r3,r0
JMP[NE] @math_exec_fail
; Resolve the operator before execution; registry is the authority for program ID.
MOVI r1,1401
MOVI r2,1402
MOVI r3,702
CALL @nmr_resolve
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r6,4
CMP r1,r6
JMP[NE] @math_exec_fail
MOVI r6,78
CMP r2,r6
JMP[NE] @math_exec_fail
; The resolved object records, not caller-supplied guesses, provide payload addresses.
MOVI r6,@math_vec_left
CMP r4,r6
JMP[NE] @math_exec_fail
MOVI r6,@math_vec_right
CMP r5,r6
JMP[NE] @math_exec_fail
ST r4,r0,@math_exec_left_ptr
ST r5,r0,@math_exec_right_ptr
; Execute the resolved program on the two registered guest payloads.
MOVI r1,78
LD r2,r0,@math_exec_left_ptr
LD r3,r0,@math_exec_right_ptr
MOVI r4,4
MOVI r5,@math_vec_output
CALL @nmr_execute
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r6,@math_vec_output
CMP r1,r6
JMP[NE] @math_exec_fail
MOVI r6,4
CMP r2,r6
JMP[NE] @math_exec_fail
; Expected [11,22,33,44].
MOVI r7,@math_vec_output
LD r8,r7,0
MOVI r9,11
CMP r8,r9
JMP[NE] @math_exec_fail
LD r8,r7,1
MOVI r9,22
CMP r8,r9
JMP[NE] @math_exec_fail
LD r8,r7,2
MOVI r9,33
CMP r8,r9
JMP[NE] @math_exec_fail
LD r8,r7,3
MOVI r9,44
CMP r8,r9
JMP[NE] @math_exec_fail
; Register dot.v1: vector(4) x vector(4) -> scalar(3), program 79.
MOVI r1,701
MOVI r2,4
MOVI r3,4
MOVI r4,3
MOVI r5,79
CALL @nmr_op_put
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r1,1401
MOVI r2,1402
MOVI r3,701
CALL @nmr_resolve
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r6,3
CMP r1,r6
JMP[NE] @math_exec_fail
MOVI r6,79
CMP r2,r6
JMP[NE] @math_exec_fail
ST r4,r0,@math_exec_left_ptr
ST r5,r0,@math_exec_right_ptr
; Execute the registered dot product using the resolved endpoint addresses.
MOVI r1,79
LD r2,r0,@math_exec_left_ptr
LD r3,r0,@math_exec_right_ptr
MOVI r4,4
MOVI r5,@math_dot_output
CALL @nmr_execute
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r6,@math_dot_output
CMP r1,r6
JMP[NE] @math_exec_fail
LD r7,r6,0
MOVI r8,300
CMP r7,r8
JMP[NE] @math_exec_fail
; Publish a scalar result object with its guest payload pointer.
MOVI r1,1501
MOVI r2,3
MOVI r3,3
MOVI r4,1201
CALL @nmr_obj_put
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r1,1501
MOVI r2,@math_dot_output
CALL @nmr_obj_bind_data
CMP r3,r0
JMP[NE] @math_exec_fail
; Append derivation only after re-resolving the typed operation and result schema.
MOVI r1,9001
MOVI r2,1401
MOVI r3,1402
MOVI r4,701
MOVI r5,1501
MOVI r6,79
MOVI r7,1201
CALL @nmr_derivation_put
CMP r3,r0
JMP[NE] @math_exec_fail
; Exact derivation replay is idempotent.
MOVI r1,9001
MOVI r2,1401
MOVI r3,1402
MOVI r4,701
MOVI r5,1501
MOVI r6,79
MOVI r7,1201
CALL @nmr_derivation_put
CMP r3,r0
JMP[NE] @math_exec_fail
; A different output cannot rewrite an existing operation ID.
MOVI r1,1502
MOVI r2,3
MOVI r3,4
MOVI r4,1202
CALL @nmr_obj_put
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r1,1502
MOVI r2,@math_dot_output
CALL @nmr_obj_bind_data
CMP r3,r0
JMP[NE] @math_exec_fail
MOVI r1,9001
MOVI r2,1401
MOVI r3,1402
MOVI r4,701
MOVI r5,1502
MOVI r6,79
MOVI r7,1202
CALL @nmr_derivation_put
MOVI r8,5
CMP r3,r8
JMP[NE] @math_exec_fail
; Invalid dimension is rejected without touching a valid result contract.
MOVI r1,78
MOVI r2,@math_vec_left
MOVI r3,@math_vec_right
MOVI r4,0
MOVI r5,@math_vec_output
CALL @nmr_execute
MOVI r6,1
CMP r3,r6
JMP[NE] @math_exec_fail
; Dimensions above the bounded v1 limit are rejected.
MOVI r1,78
MOVI r2,@math_vec_left
MOVI r3,@math_vec_right
MOVI r4,17
MOVI r5,@math_vec_output
CALL @nmr_execute
MOVI r6,1
CMP r3,r6
JMP[NE] @math_exec_fail
; Unknown program IDs never dispatch.
MOVI r1,999
MOVI r2,@math_vec_left
MOVI r3,@math_vec_right
MOVI r4,4
MOVI r5,@math_vec_output
CALL @nmr_execute
MOVI r6,1
CMP r3,r6
JMP[NE] @math_exec_fail
; PASS
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
math_exec_fail:
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
.include "math_registry.nas"
.include "vector_ops.nas"
.org 20900
math_exec_left_ptr:
.word 0
math_exec_right_ptr:
.word 0
.org 21000
math_vec_left:
.word 1,2,3,4
math_vec_right:
.word 10,20,30,40
math_vec_output:
.word 0,0,0,0
math_dot_output:
.word 0
