
; NaN16 worker guest: registry resolution and allowlisted kernel execution.
; Host stages only object identities, owner points, digest markers, and bounded
; payload words obtained from the canonical guest-state resolver.
.include "../nannet/math_registry.nas"
.include "../nannet/vector_ops.nas"

n16_nmp_kernel_entry:
  MOVI r13,1

  ; Select one of the two compiled guest kernels. Program IDs are guest-owned:
  ; 1 = vector.add.i32.v1 (78), 2 = vector.dot.u32.v1 (79).
  MOVI r8,@n16_nmp_operation
  LD r8,r8,0
  MOVI r9,1
  CMP r8,r9
  JMP[EQ] @n16_nmp_select_add
  MOVI r9,2
  CMP r8,r9
  JMP[EQ] @n16_nmp_select_dot
  MOVI r8,1
  JMP @n16_nmp_kernel_fail
n16_nmp_select_add:
  MOVI r8,701
  ST r8,r0,@n16_nmp_operator_id
  MOVI r8,78
  ST r8,r0,@n16_nmp_expected_program
  MOVI r8,4
  ST r8,r0,@n16_nmp_expected_output_kind
  JMP @n16_nmp_register_objects
n16_nmp_select_dot:
  MOVI r8,702
  ST r8,r0,@n16_nmp_operator_id
  MOVI r8,79
  ST r8,r0,@n16_nmp_expected_program
  MOVI r8,3
  ST r8,r0,@n16_nmp_expected_output_kind

n16_nmp_register_objects:
  ; Register the left vector using its canonical immutable identity.
  MOVI r1,@n16_nmp_left_id
  LD r1,r1,0
  MOVI r2,4
  MOVI r3,@n16_nmp_left_owner
  LD r3,r3,0
  MOVI r4,@n16_nmp_left_digest_marker
  LD r4,r4,0
  CALL @nmr_obj_put
  CMP r3,r0
  JMP[NE] @n16_nmp_registry_fail

  MOVI r1,@n16_nmp_left_id
  LD r1,r1,0
  MOVI r2,@n16_nmp_left_payload
  CALL @nmr_obj_bind_data
  CMP r3,r0
  JMP[NE] @n16_nmp_bind_fail

  ; Register the right vector.
  MOVI r1,@n16_nmp_right_id
  LD r1,r1,0
  MOVI r2,4
  MOVI r3,@n16_nmp_right_owner
  LD r3,r3,0
  MOVI r4,@n16_nmp_right_digest_marker
  LD r4,r4,0
  CALL @nmr_obj_put
  CMP r3,r0
  JMP[NE] @n16_nmp_registry_fail

  MOVI r1,@n16_nmp_right_id
  LD r1,r1,0
  MOVI r2,@n16_nmp_right_payload
  CALL @nmr_obj_bind_data
  CMP r3,r0
  JMP[NE] @n16_nmp_bind_fail

  ; Register exact rank-1 shapes for both typed vector objects.
  MOVI r1,@n16_nmp_left_id
  LD r1,r1,0
  MOVI r2,1
  MOVI r3,@n16_nmp_dimension
  LD r3,r3,0
  MOVI r4,1
  MOVI r5,@n16_nmp_dimension
  LD r5,r5,0
  CALL @nmr_shape_put
  CMP r3,r0
  JMP[NE] @n16_nmp_shape_fail

  MOVI r1,@n16_nmp_right_id
  LD r1,r1,0
  MOVI r2,1
  MOVI r3,@n16_nmp_dimension
  LD r3,r3,0
  MOVI r4,1
  MOVI r5,@n16_nmp_dimension
  LD r5,r5,0
  CALL @nmr_shape_put
  CMP r3,r0
  JMP[NE] @n16_nmp_shape_fail

  ; The guest chooses the output kind and program ID from the fixed allowlist.
  MOVI r1,@n16_nmp_operator_id
  LD r1,r1,0
  MOVI r2,4
  MOVI r3,4
  MOVI r4,@n16_nmp_expected_output_kind
  LD r4,r4,0
  MOVI r5,@n16_nmp_expected_program
  LD r5,r5,0
  CALL @nmr_op_put
  CMP r3,r0
  JMP[NE] @n16_nmp_operator_fail

  ; Resolve again through the guest registry. Caller-supplied program IDs are
  ; never used as dispatch authority.
  MOVI r1,@n16_nmp_left_id
  LD r1,r1,0
  MOVI r2,@n16_nmp_right_id
  LD r2,r2,0
  MOVI r3,@n16_nmp_operator_id
  LD r3,r3,0
  CALL @nmr_resolve
  CMP r3,r0
  JMP[NE] @n16_nmp_resolve_fail

  MOVI r8,@n16_nmp_expected_output_kind
  LD r8,r8,0
  CMP r1,r8
  JMP[NE] @n16_nmp_mapping_fail
  MOVI r8,@n16_nmp_expected_program
  LD r8,r8,0
  CMP r2,r8
  JMP[NE] @n16_nmp_mapping_fail
  CMP r4,r0
  JMP[EQ] @n16_nmp_resolve_fail
  CMP r5,r0
  JMP[EQ] @n16_nmp_resolve_fail
  ST r4,r0,@n16_nmp_resolved_left
  ST r5,r0,@n16_nmp_resolved_right

  ; Execute the resolved, allowlisted NaN16 kernel over guest-owned payloads.
  MOVI r1,@n16_nmp_expected_program
  LD r1,r1,0
  LD r2,r0,@n16_nmp_resolved_left
  LD r3,r0,@n16_nmp_resolved_right
  MOVI r4,@n16_nmp_dimension
  LD r4,r4,0
  MOVI r5,@n16_nmp_output_payload
  CALL @nmr_execute
  CMP r3,r0
  JMP[NE] @n16_nmp_execute_fail

  MOVI r11,@n16_nmp_result
  MOVI r8,0
  ST r8,r11,0
  LD r8,r0,@n16_nmp_expected_program
  ST r8,r11,1
  LD r8,r0,@n16_nmp_expected_output_kind
  ST r8,r11,2
  LD r8,r0,@n16_nmp_dimension
  ST r8,r11,3
  MOVI r8,1
  LD r9,r0,@n16_nmp_expected_program
  MOVI r10,78
  CMP r9,r10
  JMP[NE] @n16_nmp_result_ready
  LD r8,r0,@n16_nmp_dimension
n16_nmp_result_ready:
  ST r8,r11,4
  RET

n16_nmp_registry_fail:
  MOVI r8,2
  JMP @n16_nmp_kernel_fail
n16_nmp_bind_fail:
  MOVI r8,3
  JMP @n16_nmp_kernel_fail
n16_nmp_shape_fail:
  MOVI r8,4
  JMP @n16_nmp_kernel_fail
n16_nmp_operator_fail:
  MOVI r8,5
  JMP @n16_nmp_kernel_fail
n16_nmp_resolve_fail:
  MOVI r8,6
  JMP @n16_nmp_kernel_fail
n16_nmp_mapping_fail:
  MOVI r8,7
  JMP @n16_nmp_kernel_fail
n16_nmp_execute_fail:
  MOVI r8,8
n16_nmp_kernel_fail:
  ST r8,r0,@n16_nmp_result
  RET

.org 12000
n16_nmp_operation:
.word 0
n16_nmp_left_id:
.word 0
n16_nmp_right_id:
.word 0
n16_nmp_left_owner:
.word 0
n16_nmp_right_owner:
.word 0
n16_nmp_left_digest_marker:
.word 0
n16_nmp_right_digest_marker:
.word 0
n16_nmp_dimension:
.word 0
n16_nmp_operator_id:
.word 0
n16_nmp_expected_program:
.word 0
n16_nmp_expected_output_kind:
.word 0
n16_nmp_resolved_left:
.word 0
n16_nmp_resolved_right:
.word 0
n16_nmp_result:
.word 0,0,0,0,0
.org 13000
n16_nmp_left_payload:
.org 14000
n16_nmp_right_payload:
.org 15000
n16_nmp_output_payload:

.org 16000
n16_nmp_kernel_halt:
  HALT
