; NaN16 mathematical object/operator registry.
; Direct-call ABI:
;   nmr_obj_put: r1=id,r2=kind,r3=owner_point,r4=payload_digest -> r1=slot,r3=status
;   nmr_obj_bind_data: r1=id,r2=guest_word_pointer -> r1=slot,r3=status
;   nmr_obj_get: r1=id -> r1=kind,r2=owner,r3=digest,r4=slot,r5=status,r6=data_pointer
;   nmr_op_put: r1=op_id,r2=left_kind,r3=right_kind,r4=out_kind,r5=program_id
;   nmr_contract_put: r1=op_id,r2=left_rank,r3=right_rank,r4=out_rank,r5=domain,
;       r6=shape_rule,r7=required_capabilities,r8=preconditions -> r1=slot,r3=status
;   nmr_contract_get: r1=op_id -> r1=10-word contract pointer,r2=status
;   nmr_resolve: r1=left_object_id,r2=right_object_id,r3=op_id
;       -> r1=out_kind,r2=program_id,r3=status,r4=left_data_pointer,
;          r5=right_data_pointer,r6=contract_pointer (0 if not registered)
; Status: 0 success, 1 invalid/unknown, 3 full, 5 immutable-key conflict, 6 signature/type mismatch.
; Object records are id,kind,owner,digest,data_pointer (5 words).
; Operator identity records remain op_id,left_kind,right_kind,out_kind,program_id.
; A separate keyed contract extension stores the complete 10-word preflight ABI.
; Fixed-size tables are bootstrap scratch. MC0DB persistence/checkpointing is a later layer.

nmr_obj_put:
ST r15,r0,@nmr_obj_put_ret
ST r1,r0,@nmr_tmp_id
ST r2,r0,@nmr_tmp_kind
ST r3,r0,@nmr_tmp_owner
ST r4,r0,@nmr_tmp_digest
MOVI r4,@nmr_obj_table
MOVI r5,32
MOVI r6,0
nmr_obj_put_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_obj_put_empty
LD r8,r0,@nmr_tmp_id
CMP r7,r8
JMP[EQ] @nmr_obj_put_found
ADDI r4,r4,5
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_obj_put_scan
MOVI r1,0
MOVI r3,3
LD r15,r0,@nmr_obj_put_ret
RET
nmr_obj_put_found:
LD r8,r0,@nmr_tmp_kind
LD r9,r4,1
CMP r8,r9
JMP[NE] @nmr_obj_put_conflict
LD r8,r0,@nmr_tmp_owner
LD r9,r4,2
CMP r8,r9
JMP[NE] @nmr_obj_put_conflict
LD r8,r0,@nmr_tmp_digest
LD r9,r4,3
CMP r8,r9
JMP[NE] @nmr_obj_put_conflict
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_obj_put_ret
RET
nmr_obj_put_empty:
LD r8,r0,@nmr_tmp_id
ST r8,r4,0
LD r8,r0,@nmr_tmp_kind
ST r8,r4,1
LD r8,r0,@nmr_tmp_owner
ST r8,r4,2
LD r8,r0,@nmr_tmp_digest
ST r8,r4,3
ST r0,r4,4
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_obj_put_ret
RET
nmr_obj_put_conflict:
MOVI r1,0
MOVI r3,5
LD r15,r0,@nmr_obj_put_ret
RET
nmr_obj_put_ret:
.word 0

; Bind a vector's guest-memory payload after immutable object registration.
; A pointer may be set once or re-bound idempotently to the same value.
nmr_obj_bind_data:
ST r15,r0,@nmr_obj_bind_ret
ST r1,r0,@nmr_bind_id
ST r2,r0,@nmr_bind_ptr
CMP r1,r0
JMP[EQ] @nmr_obj_bind_bad
CMP r2,r0
JMP[EQ] @nmr_obj_bind_bad
MOVI r4,@nmr_obj_table
MOVI r5,32
MOVI r6,0
nmr_obj_bind_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_obj_bind_next
LD r8,r0,@nmr_bind_id
CMP r7,r8
JMP[EQ] @nmr_obj_bind_found
nmr_obj_bind_next:
ADDI r4,r4,5
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_obj_bind_scan
MOVI r1,0
MOVI r3,1
LD r15,r0,@nmr_obj_bind_ret
RET
nmr_obj_bind_found:
LD r8,r4,1
MOVI r9,4
CMP r8,r9
JMP[EQ] @nmr_obj_bind_kind_ok
MOVI r9,3
CMP r8,r9
JMP[NE] @nmr_obj_bind_type
nmr_obj_bind_kind_ok:
LD r8,r4,4
CMP r8,r0
JMP[EQ] @nmr_obj_bind_write
LD r9,r0,@nmr_bind_ptr
CMP r8,r9
JMP[NE] @nmr_obj_bind_conflict
JMP @nmr_obj_bind_ok
nmr_obj_bind_write:
LD r9,r0,@nmr_bind_ptr
ST r9,r4,4
nmr_obj_bind_ok:
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_obj_bind_ret
RET
nmr_obj_bind_type:
MOVI r1,0
MOVI r3,6
LD r15,r0,@nmr_obj_bind_ret
RET
nmr_obj_bind_conflict:
MOVI r1,0
MOVI r3,5
LD r15,r0,@nmr_obj_bind_ret
RET
nmr_obj_bind_bad:
MOVI r1,0
MOVI r3,1
LD r15,r0,@nmr_obj_bind_ret
RET
nmr_obj_bind_ret:
.word 0

nmr_obj_get:
ST r15,r0,@nmr_obj_get_ret
ST r1,r0,@nmr_tmp_id
MOVI r4,@nmr_obj_table
MOVI r5,32
MOVI r6,0
nmr_obj_get_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_obj_get_next
LD r8,r0,@nmr_tmp_id
CMP r7,r8
JMP[EQ] @nmr_obj_get_found
nmr_obj_get_next:
ADDI r4,r4,5
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_obj_get_scan
MOVI r1,0
MOVI r2,0
MOVI r3,0
MOVI r4,0
MOVI r5,1
MOVI r6,0
LD r15,r0,@nmr_obj_get_ret
RET
nmr_obj_get_found:
LD r1,r4,1
LD r2,r4,2
LD r3,r4,3
LD r7,r4,4
BOOL.14 r4,r6,r0
ADD r6,r7,r0
MOVI r5,0
LD r15,r0,@nmr_obj_get_ret
RET
nmr_obj_get_ret:
.word 0

nmr_op_put:
ST r15,r0,@nmr_op_put_ret
ST r1,r0,@nmr_tmp_op
ST r2,r0,@nmr_tmp_left
ST r3,r0,@nmr_tmp_right
ST r4,r0,@nmr_tmp_out
ST r5,r0,@nmr_tmp_program
MOVI r4,@nmr_op_table
MOVI r5,16
MOVI r6,0
nmr_op_put_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_op_put_empty
LD r8,r0,@nmr_tmp_op
CMP r7,r8
JMP[EQ] @nmr_op_put_found
ADDI r4,r4,5
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_op_put_scan
MOVI r1,0
MOVI r3,3
LD r15,r0,@nmr_op_put_ret
RET
nmr_op_put_found:
LD r8,r0,@nmr_tmp_left
LD r9,r4,1
CMP r8,r9
JMP[NE] @nmr_op_put_conflict
LD r8,r0,@nmr_tmp_right
LD r9,r4,2
CMP r8,r9
JMP[NE] @nmr_op_put_conflict
LD r8,r0,@nmr_tmp_out
LD r9,r4,3
CMP r8,r9
JMP[NE] @nmr_op_put_conflict
LD r8,r0,@nmr_tmp_program
LD r9,r4,4
CMP r8,r9
JMP[NE] @nmr_op_put_conflict
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_op_put_ret
RET
nmr_op_put_empty:
LD r8,r0,@nmr_tmp_op
ST r8,r4,0
LD r8,r0,@nmr_tmp_left
ST r8,r4,1
LD r8,r0,@nmr_tmp_right
ST r8,r4,2
LD r8,r0,@nmr_tmp_out
ST r8,r4,3
LD r8,r0,@nmr_tmp_program
ST r8,r4,4
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_op_put_ret
RET
nmr_op_put_conflict:
MOVI r1,0
MOVI r3,5
LD r15,r0,@nmr_op_put_ret
RET
nmr_op_put_ret:
.word 0


; Immutable typed applicability contract, attached to an existing operator ID.
; The three kinds are copied from the canonical operator record and cannot
; diverge from it. The returned descriptor is exactly the 10-word MTH ABI.
nmr_contract_put:
ST r15,r0,@nmr_contract_put_ret
ST r1,r0,@nmr_contract_tmp_op
ST r2,r0,@nmr_contract_tmp_lr
ST r3,r0,@nmr_contract_tmp_rr
ST r4,r0,@nmr_contract_tmp_or
ST r5,r0,@nmr_contract_tmp_domain
ST r6,r0,@nmr_contract_tmp_shape
ST r7,r0,@nmr_contract_tmp_caps
ST r8,r0,@nmr_contract_tmp_pre
CMP r1,r0
JMP[EQ] @nmr_contract_put_bad
; Resolve the existing operator; contracts cannot invent operator identities.
MOVI r4,@nmr_op_table
MOVI r5,16
MOVI r6,0
nmr_contract_op_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_contract_op_next
LD r8,r0,@nmr_contract_tmp_op
CMP r7,r8
JMP[EQ] @nmr_contract_op_found
nmr_contract_op_next:
ADDI r4,r4,5
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_contract_op_scan
JMP @nmr_contract_put_missing
nmr_contract_op_found:
LD r7,r4,1
ST r7,r0,@nmr_contract_tmp_lk
LD r7,r4,2
ST r7,r0,@nmr_contract_tmp_rk
LD r7,r4,3
ST r7,r0,@nmr_contract_tmp_ok
; Find an existing immutable contract or the first empty slot.
MOVI r4,@nmr_contract_table
MOVI r5,16
MOVI r6,0
nmr_contract_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_contract_empty
LD r8,r0,@nmr_contract_tmp_op
CMP r7,r8
JMP[EQ] @nmr_contract_found
ADDI r4,r4,11
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_contract_scan
MOVI r1,0
MOVI r3,3
LD r15,r0,@nmr_contract_put_ret
RET
nmr_contract_found:
; Exact duplicate is idempotent; any changed field is an immutable conflict.
LD r8,r0,@nmr_contract_tmp_lk
LD r9,r4,1
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_rk
LD r9,r4,2
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_ok
LD r9,r4,3
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_lr
LD r9,r4,4
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_rr
LD r9,r4,5
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_or
LD r9,r4,6
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_domain
LD r9,r4,7
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_shape
LD r9,r4,8
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_caps
LD r9,r4,9
CMP r8,r9
JMP[NE] @nmr_contract_conflict
LD r8,r0,@nmr_contract_tmp_pre
LD r9,r4,10
CMP r8,r9
JMP[NE] @nmr_contract_conflict
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_contract_put_ret
RET
nmr_contract_empty:
LD r8,r0,@nmr_contract_tmp_op
ST r8,r4,0
LD r8,r0,@nmr_contract_tmp_lk
ST r8,r4,1
LD r8,r0,@nmr_contract_tmp_rk
ST r8,r4,2
LD r8,r0,@nmr_contract_tmp_ok
ST r8,r4,3
LD r8,r0,@nmr_contract_tmp_lr
ST r8,r4,4
LD r8,r0,@nmr_contract_tmp_rr
ST r8,r4,5
LD r8,r0,@nmr_contract_tmp_or
ST r8,r4,6
LD r8,r0,@nmr_contract_tmp_domain
ST r8,r4,7
LD r8,r0,@nmr_contract_tmp_shape
ST r8,r4,8
LD r8,r0,@nmr_contract_tmp_caps
ST r8,r4,9
LD r8,r0,@nmr_contract_tmp_pre
ST r8,r4,10
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_contract_put_ret
RET
nmr_contract_put_bad:
MOVI r1,0
MOVI r3,1
LD r15,r0,@nmr_contract_put_ret
RET
nmr_contract_put_missing:
MOVI r1,0
MOVI r3,1
LD r15,r0,@nmr_contract_put_ret
RET
nmr_contract_conflict:
MOVI r1,0
MOVI r3,5
LD r15,r0,@nmr_contract_put_ret
RET
nmr_contract_put_ret:
.word 0

; Return the canonical 10-word contract descriptor (not a copied summary).
nmr_contract_get:
ST r15,r0,@nmr_contract_get_ret
ST r1,r0,@nmr_contract_get_op
MOVI r4,@nmr_contract_table
MOVI r5,16
MOVI r6,0
nmr_contract_get_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_contract_get_next
LD r8,r0,@nmr_contract_get_op
CMP r7,r8
JMP[EQ] @nmr_contract_get_found
nmr_contract_get_next:
ADDI r4,r4,11
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_contract_get_scan
MOVI r1,0
MOVI r2,1
LD r15,r0,@nmr_contract_get_ret
RET
nmr_contract_get_found:
ADDI r1,r4,1
MOVI r2,0
LD r15,r0,@nmr_contract_get_ret
RET
nmr_contract_get_ret:
.word 0
nmr_contract_get_op:
.word 0

nmr_resolve:
ST r15,r0,@nmr_resolve_ret
ST r1,r0,@nmr_tmp_left_id
ST r2,r0,@nmr_tmp_right_id
ST r3,r0,@nmr_tmp_op
LD r1,r0,@nmr_tmp_left_id
CALL @nmr_obj_get
CMP r5,r0
JMP[NE] @nmr_resolve_missing
ST r1,r0,@nmr_tmp_left_kind
ST r6,r0,@nmr_tmp_left_data
LD r1,r0,@nmr_tmp_right_id
CALL @nmr_obj_get
CMP r5,r0
JMP[NE] @nmr_resolve_missing
ST r1,r0,@nmr_tmp_right_kind
ST r6,r0,@nmr_tmp_right_data
MOVI r4,@nmr_op_table
MOVI r5,16
MOVI r6,0
nmr_resolve_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_resolve_next
LD r8,r0,@nmr_tmp_op
CMP r7,r8
JMP[EQ] @nmr_resolve_found
nmr_resolve_next:
ADDI r4,r4,5
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_resolve_scan
JMP @nmr_resolve_missing
nmr_resolve_found:
LD r8,r0,@nmr_tmp_left_kind
LD r9,r4,1
CMP r8,r9
JMP[NE] @nmr_resolve_signature
LD r8,r0,@nmr_tmp_right_kind
LD r9,r4,2
CMP r8,r9
JMP[NE] @nmr_resolve_signature
LD r1,r4,3
ST r1,r0,@nmr_resolve_out_kind
LD r1,r4,4
ST r1,r0,@nmr_resolve_program
LD r1,r0,@nmr_tmp_left_data
ST r1,r0,@nmr_resolve_left_data
LD r1,r0,@nmr_tmp_right_data
ST r1,r0,@nmr_resolve_right_data
LD r1,r0,@nmr_tmp_op
CALL @nmr_contract_get
ST r1,r0,@nmr_resolve_contract
LD r1,r0,@nmr_resolve_out_kind
LD r2,r0,@nmr_resolve_program
LD r4,r0,@nmr_resolve_left_data
LD r5,r0,@nmr_resolve_right_data
LD r6,r0,@nmr_resolve_contract
MOVI r3,0
LD r15,r0,@nmr_resolve_ret
RET
nmr_resolve_signature:
MOVI r1,0
MOVI r2,0
MOVI r3,6
MOVI r4,0
MOVI r5,0
MOVI r6,0
LD r15,r0,@nmr_resolve_ret
RET
nmr_resolve_missing:
MOVI r1,0
MOVI r2,0
MOVI r3,1
MOVI r4,0
MOVI r5,0
MOVI r6,0
LD r15,r0,@nmr_resolve_ret
RET
nmr_resolve_ret:
.word 0

; Append-only derivation provenance. operation_id is a local immutable record key,
; NOT a cryptographic content hash. Output digest is checked against the result object.
; ABI: r1=operation_id,r2=left_id,r3=right_id,r4=operator_id,
;      r5=output_id,r6=program_id,r7=output_digest_marker -> r1=slot,r3=status.
nmr_derivation_put:
ST r15,r0,@nmr_derivation_ret
ST r1,r0,@nmr_deriv_id
ST r2,r0,@nmr_deriv_left
ST r3,r0,@nmr_deriv_right
ST r4,r0,@nmr_deriv_op
ST r5,r0,@nmr_deriv_output
ST r6,r0,@nmr_deriv_program
ST r7,r0,@nmr_deriv_digest
CMP r1,r0
JMP[EQ] @nmr_derivation_bad
CMP r5,r0
JMP[EQ] @nmr_derivation_bad
; Re-resolve the ordered input signature; workers cannot self-assert program IDs.
LD r1,r0,@nmr_deriv_left
LD r2,r0,@nmr_deriv_right
LD r3,r0,@nmr_deriv_op
CALL @nmr_resolve
CMP r3,r0
JMP[NE] @nmr_derivation_missing
ST r1,r0,@nmr_deriv_out_kind
LD r8,r0,@nmr_deriv_program
CMP r2,r8
JMP[NE] @nmr_derivation_signature
; The result object must exist, have the registered output kind and digest,
; and have a bound guest payload address.
LD r1,r0,@nmr_deriv_output
CALL @nmr_obj_get
CMP r5,r0
JMP[NE] @nmr_derivation_missing
LD r8,r0,@nmr_deriv_out_kind
CMP r1,r8
JMP[NE] @nmr_derivation_signature
LD r8,r0,@nmr_deriv_digest
CMP r3,r8
JMP[NE] @nmr_derivation_signature
CMP r6,r0
JMP[EQ] @nmr_derivation_signature
; Immutable operation ID: exact duplicate is idempotent; any changed field conflicts.
MOVI r4,@nmr_derivation_table
MOVI r5,16
MOVI r6,0
nmr_derivation_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_derivation_empty
LD r8,r0,@nmr_deriv_id
CMP r7,r8
JMP[EQ] @nmr_derivation_found
ADDI r4,r4,7
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_derivation_scan
MOVI r1,0
MOVI r3,3
LD r15,r0,@nmr_derivation_ret
RET
nmr_derivation_found:
LD r8,r0,@nmr_deriv_left
LD r9,r4,1
CMP r8,r9
JMP[NE] @nmr_derivation_conflict
LD r8,r0,@nmr_deriv_right
LD r9,r4,2
CMP r8,r9
JMP[NE] @nmr_derivation_conflict
LD r8,r0,@nmr_deriv_op
LD r9,r4,3
CMP r8,r9
JMP[NE] @nmr_derivation_conflict
LD r8,r0,@nmr_deriv_output
LD r9,r4,4
CMP r8,r9
JMP[NE] @nmr_derivation_conflict
LD r8,r0,@nmr_deriv_program
LD r9,r4,5
CMP r8,r9
JMP[NE] @nmr_derivation_conflict
LD r8,r0,@nmr_deriv_digest
LD r9,r4,6
CMP r8,r9
JMP[NE] @nmr_derivation_conflict
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_derivation_ret
RET
nmr_derivation_empty:
LD r8,r0,@nmr_deriv_id
ST r8,r4,0
LD r8,r0,@nmr_deriv_left
ST r8,r4,1
LD r8,r0,@nmr_deriv_right
ST r8,r4,2
LD r8,r0,@nmr_deriv_op
ST r8,r4,3
LD r8,r0,@nmr_deriv_output
ST r8,r4,4
LD r8,r0,@nmr_deriv_program
ST r8,r4,5
LD r8,r0,@nmr_deriv_digest
ST r8,r4,6
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_derivation_ret
RET
nmr_derivation_bad:
MOVI r1,0
MOVI r3,1
LD r15,r0,@nmr_derivation_ret
RET
nmr_derivation_missing:
MOVI r1,0
MOVI r3,1
LD r15,r0,@nmr_derivation_ret
RET
nmr_derivation_signature:
MOVI r1,0
MOVI r3,6
LD r15,r0,@nmr_derivation_ret
RET
nmr_derivation_conflict:
MOVI r1,0
MOVI r3,5
LD r15,r0,@nmr_derivation_ret
RET
nmr_derivation_ret:
.word 0
nmr_deriv_id:
.word 0
nmr_deriv_left:
.word 0
nmr_deriv_right:
.word 0
nmr_deriv_op:
.word 0
nmr_deriv_output:
.word 0
nmr_deriv_program:
.word 0
nmr_deriv_digest:
.word 0
nmr_deriv_out_kind:
.word 0
nmr_shape_id:
.word 0
nmr_shape_rank:
.word 0
nmr_shape_dim:
.word 0
nmr_shape_stride:
.word 0
nmr_shape_capacity:
.word 0

; Immutable rank-1 vector shape registry.
; Shape record: object_id,rank,dimension,stride,capacity_words.
; nmr_shape_put ABI: r1=id,r2=rank,r3=dimension,r4=stride,r5=capacity_words
; Returns r1=slot,r3=status. Only registered vector objects (kind 4) are accepted.
; Required capacity is (dimension-1)*stride+1 words.
nmr_shape_put:
ST r15,r0,@nmr_shape_put_ret
ST r1,r0,@nmr_shape_id
ST r2,r0,@nmr_shape_rank
ST r3,r0,@nmr_shape_dim
ST r4,r0,@nmr_shape_stride
ST r5,r0,@nmr_shape_capacity
CMP r1,r0
JMP[EQ] @nmr_shape_bad
MOVI r6,1
CMP r2,r6
JMP[NE] @nmr_shape_bad
CMP r3,r0
JMP[EQ] @nmr_shape_bad
; Rank-1 vector dimensions support the same 256-word kernels as MTH.
MOVI r6,256
CMP r3,r6
JMP[GT] @nmr_shape_bad
CMP r4,r0
JMP[EQ] @nmr_shape_bad
CMP r5,r0
JMP[EQ] @nmr_shape_bad
; Verify capacity for the requested stride.
LD r6,r0,@nmr_shape_dim
MOVI r9,1
SUB r6,r6,r9
LD r7,r0,@nmr_shape_stride
MUL r6,r6,r7
ADDI r6,r6,1
LD r7,r0,@nmr_shape_capacity
CMP r7,r6
JMP[LT] @nmr_shape_bad
; Shape metadata can only be attached to an existing vector object.
LD r1,r0,@nmr_shape_id
CALL @nmr_obj_get
CMP r5,r0
JMP[NE] @nmr_shape_missing
MOVI r6,4
CMP r1,r6
JMP[NE] @nmr_shape_type
MOVI r4,@nmr_shape_table
MOVI r5,32
MOVI r6,0
nmr_shape_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_shape_empty
LD r8,r0,@nmr_shape_id
CMP r7,r8
JMP[EQ] @nmr_shape_found
ADDI r4,r4,5
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_shape_scan
MOVI r1,0
MOVI r3,3
LD r15,r0,@nmr_shape_put_ret
RET
nmr_shape_found:
LD r8,r0,@nmr_shape_rank
LD r9,r4,1
CMP r8,r9
JMP[NE] @nmr_shape_conflict
LD r8,r0,@nmr_shape_dim
LD r9,r4,2
CMP r8,r9
JMP[NE] @nmr_shape_conflict
LD r8,r0,@nmr_shape_stride
LD r9,r4,3
CMP r8,r9
JMP[NE] @nmr_shape_conflict
LD r8,r0,@nmr_shape_capacity
LD r9,r4,4
CMP r8,r9
JMP[NE] @nmr_shape_conflict
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_shape_put_ret
RET
nmr_shape_empty:
LD r8,r0,@nmr_shape_id
ST r8,r4,0
LD r8,r0,@nmr_shape_rank
ST r8,r4,1
LD r8,r0,@nmr_shape_dim
ST r8,r4,2
LD r8,r0,@nmr_shape_stride
ST r8,r4,3
LD r8,r0,@nmr_shape_capacity
ST r8,r4,4
BOOL.14 r1,r6,r0
MOVI r3,0
LD r15,r0,@nmr_shape_put_ret
RET
nmr_shape_bad:
MOVI r1,0
MOVI r3,1
LD r15,r0,@nmr_shape_put_ret
RET
nmr_shape_missing:
MOVI r1,0
MOVI r3,1
LD r15,r0,@nmr_shape_put_ret
RET
nmr_shape_type:
MOVI r1,0
MOVI r3,6
LD r15,r0,@nmr_shape_put_ret
RET
nmr_shape_conflict:
MOVI r1,0
MOVI r3,5
LD r15,r0,@nmr_shape_put_ret
RET
nmr_shape_put_ret:
.word 0

; nmr_shape_get ABI: r1=object_id -> r1=rank,r2=dimension,r3=stride,
; r4=capacity_words,r5=status.
nmr_shape_get:
ST r15,r0,@nmr_shape_get_ret
ST r1,r0,@nmr_shape_get_id
MOVI r4,@nmr_shape_table
MOVI r5,32
MOVI r6,0
nmr_shape_get_scan:
LD r7,r4,0
CMP r7,r0
JMP[EQ] @nmr_shape_get_next
LD r8,r0,@nmr_shape_get_id
CMP r7,r8
JMP[EQ] @nmr_shape_get_found
nmr_shape_get_next:
ADDI r4,r4,5
ADDI r6,r6,1
CMP r6,r5
JMP[LT] @nmr_shape_get_scan
MOVI r1,0
MOVI r2,0
MOVI r3,0
MOVI r4,0
MOVI r5,1
LD r15,r0,@nmr_shape_get_ret
RET
nmr_shape_get_found:
LD r1,r4,1
LD r2,r4,2
LD r3,r4,3
LD r8,r4,4
ADD r4,r8,r0
MOVI r5,0
LD r15,r0,@nmr_shape_get_ret
RET
nmr_shape_get_ret:
.word 0
nmr_shape_get_id:
.word 0

nmr_tmp_id:
.word 0
nmr_tmp_kind:
.word 0
nmr_tmp_owner:
.word 0
nmr_tmp_digest:
.word 0
nmr_tmp_op:
.word 0
nmr_tmp_left:
.word 0
nmr_tmp_right:
.word 0
nmr_tmp_out:
.word 0
nmr_tmp_program:
.word 0
nmr_tmp_left_id:
.word 0
nmr_tmp_right_id:
.word 0
nmr_tmp_left_kind:
.word 0
nmr_tmp_right_kind:
.word 0
nmr_tmp_left_data:
.word 0
nmr_tmp_right_data:
.word 0
nmr_bind_id:
.word 0
nmr_bind_ptr:
.word 0

nmr_contract_tmp_op:
.word 0
nmr_contract_tmp_lk:
.word 0
nmr_contract_tmp_rk:
.word 0
nmr_contract_tmp_ok:
.word 0
nmr_contract_tmp_lr:
.word 0
nmr_contract_tmp_rr:
.word 0
nmr_contract_tmp_or:
.word 0
nmr_contract_tmp_domain:
.word 0
nmr_contract_tmp_shape:
.word 0
nmr_contract_tmp_caps:
.word 0
nmr_contract_tmp_pre:
.word 0
nmr_resolve_out_kind:
.word 0
nmr_resolve_program:
.word 0
nmr_resolve_left_data:
.word 0
nmr_resolve_right_data:
.word 0
nmr_resolve_contract:
.word 0

.org 20000
nmr_obj_table:
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
nmr_op_table:
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
nmr_contract_table:
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0,0,0,0,0

nmr_derivation_table:
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
.word 0,0,0,0,0,0,0
nmr_shape_table:
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
.word 0,0,0,0,0
