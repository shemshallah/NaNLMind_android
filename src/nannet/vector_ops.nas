; NaN16 vector operation execution ABI.
; Program 78 = vector.add.i32.v1
; Input/output pointers are word addresses into the guest memory space.
; nmr_vector_add_v1: r1=left,r2=right,r3=dimension(1..16),r4=output
; Returns r1=output,r2=dimension,r3=status (0 success, 1 invalid).
; This is bounded exact signed-32-bit-word addition modulo 2^32.
nmr_vector_add_v1:
ST r15,r0,@nmr_vec_add_ret
ST r1,r0,@nmr_vec_left
ST r2,r0,@nmr_vec_right
ST r3,r0,@nmr_vec_dim
ST r4,r0,@nmr_vec_out
ST r4,r0,@nmr_vec_out_base
CMP r1,r0
JMP[EQ] @nmr_vec_add_bad
CMP r2,r0
JMP[EQ] @nmr_vec_add_bad
CMP r4,r0
JMP[EQ] @nmr_vec_add_bad
CMP r3,r0
JMP[EQ] @nmr_vec_add_bad
MOVI r5,16
CMP r3,r5
JMP[GT] @nmr_vec_add_bad
MOVI r6,0
ST r6,r0,@nmr_vec_index
nmr_vec_add_loop:
LD r8,r0,@nmr_vec_left
LD r9,r0,@nmr_vec_right
LD r10,r0,@nmr_vec_out
LD r11,r8,0
LD r12,r9,0
ADD r13,r11,r12
ST r13,r10,0
ADDI r8,r8,1
ADDI r9,r9,1
ADDI r10,r10,1
ST r8,r0,@nmr_vec_left
ST r9,r0,@nmr_vec_right
ST r10,r0,@nmr_vec_out
LD r6,r0,@nmr_vec_index
ADDI r6,r6,1
ST r6,r0,@nmr_vec_index
LD r7,r0,@nmr_vec_dim
CMP r6,r7
JMP[LT] @nmr_vec_add_loop
LD r1,r0,@nmr_vec_out_base
LD r2,r0,@nmr_vec_dim
MOVI r3,0
LD r15,r0,@nmr_vec_add_ret
RET
nmr_vec_add_bad:
MOVI r1,0
MOVI r2,0
MOVI r3,1
LD r15,r0,@nmr_vec_add_ret
RET
nmr_vec_add_ret:
.word 0
nmr_vec_left:
.word 0
nmr_vec_right:
.word 0
nmr_vec_dim:
.word 0
nmr_vec_out:
.word 0
nmr_vec_out_base:
.word 0
nmr_vec_index:
.word 0

; Registry program-ID dispatcher. Unknown IDs fail closed.
; nmr_execute: r1=program_id,r2=left,r3=right,r4=dimension,r5=output
; Returns r1=output,r2=dimension,r3=status.
nmr_execute:
ST r15,r0,@nmr_execute_ret
ST r1,r0,@nmr_exec_program
ST r2,r0,@nmr_exec_left
ST r3,r0,@nmr_exec_right
ST r4,r0,@nmr_exec_dim
ST r5,r0,@nmr_exec_out
MOVI r6,78
CMP r1,r6
JMP[EQ] @nmr_exec_vector_add
MOVI r6,79
CMP r1,r6
JMP[EQ] @nmr_exec_vector_dot
JMP @nmr_exec_unknown
nmr_exec_vector_add:
LD r1,r0,@nmr_exec_left
LD r2,r0,@nmr_exec_right
LD r3,r0,@nmr_exec_dim
LD r4,r0,@nmr_exec_out
CALL @nmr_vector_add_v1
LD r15,r0,@nmr_execute_ret
RET
nmr_exec_vector_dot:
LD r1,r0,@nmr_exec_left
LD r2,r0,@nmr_exec_right
LD r3,r0,@nmr_exec_dim
LD r4,r0,@nmr_exec_out
CALL @nmr_vector_dot_v1
LD r15,r0,@nmr_execute_ret
RET
nmr_exec_unknown:
MOVI r1,0
MOVI r2,0
MOVI r3,1
LD r15,r0,@nmr_execute_ret
RET
nmr_execute_ret:
.word 0
nmr_exec_program:
.word 0
nmr_exec_left:
.word 0
nmr_exec_right:
.word 0
nmr_exec_dim:
.word 0
nmr_exec_out:
.word 0
 
; Program 79 = vector.dot.u32.v1. Each product and the accumulated sum
; are defined modulo 2^32. Multiplication is implemented in NaN16 using
; BOOL-AND plus shifts/adds; no host arithmetic callback is involved.
; ABI: r1=left,r2=right,r3=dimension(1..16),r4=one-word scalar output.
; Returns r1=output pointer,r2=dimension,r3=status.
nmr_vector_dot_v1:
ST r15,r0,@nmr_vec_dot_ret
ST r1,r0,@nmr_dot_left
ST r2,r0,@nmr_dot_right
ST r3,r0,@nmr_dot_dim
ST r4,r0,@nmr_dot_out
CMP r1,r0
JMP[EQ] @nmr_vec_dot_bad
CMP r2,r0
JMP[EQ] @nmr_vec_dot_bad
CMP r4,r0
JMP[EQ] @nmr_vec_dot_bad
CMP r3,r0
JMP[EQ] @nmr_vec_dot_bad
MOVI r5,16
CMP r3,r5
JMP[GT] @nmr_vec_dot_bad
MOVI r6,0
ST r6,r0,@nmr_dot_index
ST r6,r0,@nmr_dot_acc
nmr_vec_dot_loop:
LD r7,r0,@nmr_dot_left
LD r8,r0,@nmr_dot_right
LD r1,r7,0
LD r2,r8,0
CALL @nmr_mul_u32
LD r9,r0,@nmr_dot_acc
ADD r9,r9,r1
ST r9,r0,@nmr_dot_acc
; The multiplier uses r8 internally, so reload both endpoint cursors.
LD r7,r0,@nmr_dot_left
LD r8,r0,@nmr_dot_right
ADDI r7,r7,1
ADDI r8,r8,1
ST r7,r0,@nmr_dot_left
ST r8,r0,@nmr_dot_right
LD r6,r0,@nmr_dot_index
ADDI r6,r6,1
ST r6,r0,@nmr_dot_index
LD r5,r0,@nmr_dot_dim
CMP r6,r5
JMP[LT] @nmr_vec_dot_loop
LD r7,r0,@nmr_dot_out
LD r8,r0,@nmr_dot_acc
ST r8,r7,0
LD r1,r0,@nmr_dot_out
LD r2,r0,@nmr_dot_dim
MOVI r3,0
LD r15,r0,@nmr_vec_dot_ret
RET
nmr_vec_dot_bad:
MOVI r1,0
MOVI r2,0
MOVI r3,1
LD r15,r0,@nmr_vec_dot_ret
RET
nmr_vec_dot_ret:
.word 0
nmr_dot_left:
.word 0
nmr_dot_right:
.word 0
nmr_dot_dim:
.word 0
nmr_dot_out:
.word 0
nmr_dot_index:
.word 0
nmr_dot_acc:
.word 0

; Exact 32-round unsigned shift/add multiplication, modulo 2^32.
; ABI: r1=a,r2=b -> r1=(a*b) mod 2^32.
nmr_mul_u32:
ST r15,r0,@nmr_mul_ret
ST r1,r0,@nmr_mul_multiplicand
ST r2,r0,@nmr_mul_multiplier
MOVI r3,0
ST r3,r0,@nmr_mul_acc
ST r3,r0,@nmr_mul_count
MOVI r3,1
ST r3,r0,@nmr_mul_one
nmr_mul_loop:
LD r8,r0,@nmr_mul_multiplier
CMP r8,r0
JMP[EQ] @nmr_mul_done
LD r9,r0,@nmr_mul_one
BOOL.8 r10,r8,r9
CMP r10,r0
JMP[EQ] @nmr_mul_skip_add
LD r11,r0,@nmr_mul_acc
LD r12,r0,@nmr_mul_multiplicand
ADD r11,r11,r12
ST r11,r0,@nmr_mul_acc
nmr_mul_skip_add:
LD r11,r0,@nmr_mul_multiplicand
SHL r11,r11,1
ST r11,r0,@nmr_mul_multiplicand
LD r11,r0,@nmr_mul_multiplier
SHR r11,r11,1
ST r11,r0,@nmr_mul_multiplier
LD r11,r0,@nmr_mul_count
ADDI r11,r11,1
ST r11,r0,@nmr_mul_count
MOVI r12,32
CMP r11,r12
JMP[LT] @nmr_mul_loop
nmr_mul_done:
LD r1,r0,@nmr_mul_acc
LD r15,r0,@nmr_mul_ret
RET
nmr_mul_ret:
.word 0
nmr_mul_multiplicand:
.word 0
nmr_mul_multiplier:
.word 0
nmr_mul_acc:
.word 0
nmr_mul_count:
.word 0
nmr_mul_one:
.word 1
