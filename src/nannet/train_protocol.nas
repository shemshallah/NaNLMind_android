; NaN16 network frame validator v1.
; Header: N16T | version:u16 | type:u16 | session:16 | seq:u32 | len:u32
; Body: payload[len] | CRC32(header+payload):u32, all integer fields LE.
; API n16_frame_validate(r1=byte base,r2=frame bytes) -> r1=type,r2=payload bytes,r3=status.
; API n16_frame_route(r1=byte base,r2=frame bytes) -> gateway,ifid,status,logical point address.
; CRC32, replay sequence, framing and route selection are guest-side operations.
n16_frame_validate:
ST r15,r0,@n16v_ret
ST r1,r0,@n16v_base
ST r2,r0,@n16v_frame_len
MOVI r13,1
MOVI r4,36
CMP r2,r4
JMP[LT] @n16v_bounds
LD r4,r0,@n16v_base
LDB r5,r4,0
MOVI r6,78
CMP r5,r6
JMP[NE] @n16v_magic
LDB r5,r4,1
MOVI r6,49
CMP r5,r6
JMP[NE] @n16v_magic
LDB r5,r4,2
MOVI r6,54
CMP r5,r6
JMP[NE] @n16v_magic
LDB r5,r4,3
MOVI r6,84
CMP r5,r6
JMP[NE] @n16v_magic
LDB r5,r4,4
CMP r5,r13
JMP[NE] @n16v_magic
LDB r5,r4,5
CMP r5,r0
JMP[NE] @n16v_magic
ADDI r4,r4,6
MOVI r5,0
LDB r6,r4,0
BOOL.14 r5,r5,r6
LDB r6,r4,1
SHL r6,r6,8
BOOL.14 r5,r5,r6
CMP r5,r0
JMP[EQ] @n16v_magic
MOVI r6,14
CMP r6,r5
JMP[LT] @n16v_magic
ST r5,r0,@n16v_type
LD r4,r0,@n16v_base
ADDI r4,r4,24
MOVI r6,0
LDB r7,r4,0
BOOL.14 r6,r6,r7
LDB r7,r4,1
SHL r7,r7,8
BOOL.14 r6,r6,r7
LDB r7,r4,2
SHL r7,r7,16
BOOL.14 r6,r6,r7
LDB r7,r4,3
SHL r7,r7,24
BOOL.14 r6,r6,r7
ST r6,r0,@n16v_seq
LD r4,r0,@n16v_base
ADDI r4,r4,28
MOVI r8,0
LDB r7,r4,0
BOOL.14 r8,r8,r7
LDB r7,r4,1
SHL r7,r7,8
BOOL.14 r8,r8,r7
LDB r7,r4,2
SHL r7,r7,16
BOOL.14 r8,r8,r7
LDB r7,r4,3
SHL r7,r7,24
BOOL.14 r8,r8,r7
ST r8,r0,@n16v_payload
MOVI r9,0
MOVHI r9,1
CMP r8,r9
JMP[GT] @n16v_bounds
MOVI r9,36
ADD r9,r9,r8
LD r10,r0,@n16v_frame_len
CMP r9,r10
JMP[NE] @n16v_bounds
LD r12,r0,@n16v_last_seq
CMP r6,r12
JMP[LE] @n16v_sequence
MOVI r11,65535
MOVHI r11,65535
MOVI r7,0
MOVI r9,32
ADD r9,r9,r8
n16v_crc_byte:
CMP r7,r9
JMP[GE] @n16v_crc_done
LD r4,r0,@n16v_base
ADD r4,r4,r7
LDB r10,r4,0
BOOL.6 r11,r11,r10
MOVI r10,8
n16v_crc_bit:
MOVI r12,1
BOOL.8 r12,r11,r12
CMP r12,r0
JMP[EQ] @n16v_crc_zero
SHR r11,r11,1
MOVI r12,0x8320
MOVHI r12,0xEDB8
BOOL.6 r11,r11,r12
JMP @n16v_crc_next
n16v_crc_zero:
SHR r11,r11,1
n16v_crc_next:
SUB r10,r10,r13
CMP r10,r0
JMP[NE] @n16v_crc_bit
ADDI r7,r7,1
JMP @n16v_crc_byte
n16v_crc_done:
MOVI r12,65535
MOVHI r12,65535
BOOL.6 r11,r11,r12
LD r4,r0,@n16v_base
ADD r4,r4,r9
MOVI r10,0
LDB r12,r4,0
BOOL.14 r10,r10,r12
LDB r12,r4,1
SHL r12,r12,8
BOOL.14 r10,r10,r12
LDB r12,r4,2
SHL r12,r12,16
BOOL.14 r10,r10,r12
LDB r12,r4,3
SHL r12,r12,24
BOOL.14 r10,r10,r12
CMP r11,r10
JMP[NE] @n16v_crc_error
; Lock the first CRC-valid 128-bit session id and compare all
; sixteen bytes on every subsequent frame.
LD r12,r0,@n16v_session_active
CMP r12,r0
JMP[NE] @n16v_session_check
LD r4,r0,@n16v_base
ADDI r4,r4,8
LDB r5,r4,0
ST r5,r0,@n16v_session_0
LDB r5,r4,1
ST r5,r0,@n16v_session_1
LDB r5,r4,2
ST r5,r0,@n16v_session_2
LDB r5,r4,3
ST r5,r0,@n16v_session_3
LDB r5,r4,4
ST r5,r0,@n16v_session_4
LDB r5,r4,5
ST r5,r0,@n16v_session_5
LDB r5,r4,6
ST r5,r0,@n16v_session_6
LDB r5,r4,7
ST r5,r0,@n16v_session_7
LDB r5,r4,8
ST r5,r0,@n16v_session_8
LDB r5,r4,9
ST r5,r0,@n16v_session_9
LDB r5,r4,10
ST r5,r0,@n16v_session_10
LDB r5,r4,11
ST r5,r0,@n16v_session_11
LDB r5,r4,12
ST r5,r0,@n16v_session_12
LDB r5,r4,13
ST r5,r0,@n16v_session_13
LDB r5,r4,14
ST r5,r0,@n16v_session_14
LDB r5,r4,15
ST r5,r0,@n16v_session_15
MOVI r12,1
ST r12,r0,@n16v_session_active
JMP @n16v_session_ok
n16v_session_check:
LD r4,r0,@n16v_base
ADDI r4,r4,8
LDB r5,r4,0
LD r12,r0,@n16v_session_0
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,1
LD r12,r0,@n16v_session_1
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,2
LD r12,r0,@n16v_session_2
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,3
LD r12,r0,@n16v_session_3
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,4
LD r12,r0,@n16v_session_4
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,5
LD r12,r0,@n16v_session_5
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,6
LD r12,r0,@n16v_session_6
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,7
LD r12,r0,@n16v_session_7
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,8
LD r12,r0,@n16v_session_8
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,9
LD r12,r0,@n16v_session_9
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,10
LD r12,r0,@n16v_session_10
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,11
LD r12,r0,@n16v_session_11
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,12
LD r12,r0,@n16v_session_12
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,13
LD r12,r0,@n16v_session_13
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,14
LD r12,r0,@n16v_session_14
CMP r5,r12
JMP[NE] @n16v_session_error
LDB r5,r4,15
LD r12,r0,@n16v_session_15
CMP r5,r12
JMP[NE] @n16v_session_error
n16v_session_ok:
LD r6,r0,@n16v_seq
ST r6,r0,@n16v_last_seq
LD r1,r0,@n16v_type
LD r2,r0,@n16v_payload
MOVI r3,0
LD r15,r0,@n16v_ret
RET
n16v_magic:
MOVI r3,1
JMP @n16v_error
n16v_bounds:
MOVI r3,2
JMP @n16v_error
n16v_sequence:
MOVI r3,3
JMP @n16v_error
n16v_session_error:
MOVI r3,5
JMP @n16v_error
n16v_crc_error:
MOVI r3,4
n16v_error:
MOVI r1,0
MOVI r2,0
LD r15,r0,@n16v_ret
RET
n16v_ret:
.word 0
n16v_base:
.word 0
n16v_frame_len:
.word 0
n16v_type:
.word 0
n16v_seq:
.word 0
n16v_payload:
.word 0
n16v_last_seq:
.word 0
n16v_session_active:
.word 0
n16v_session_0:
.word 0
n16v_session_1:
.word 0
n16v_session_2:
.word 0
n16v_session_3:
.word 0
n16v_session_4:
.word 0
n16v_session_5:
.word 0
n16v_session_6:
.word 0
n16v_session_7:
.word 0
n16v_session_8:
.word 0
n16v_session_9:
.word 0
n16v_session_10:
.word 0
n16v_session_11:
.word 0
n16v_session_12:
.word 0
n16v_session_13:
.word 0
n16v_session_14:
.word 0
n16v_session_15:
.word 0
; Reset only after the owning transport has closed/aborted its session.
n16_session_reset:
MOVI r4,0
ST r4,r0,@n16v_session_active
ST r4,r0,@n16v_last_seq
RET
n16_frame_route:
ST r15,r0,@n16r_ret
ST r1,r0,@n16r_base
ST r2,r0,@n16r_len
; Validation advances the replay cursor and may claim a new session.
; Save those fields so a later transition/routing rejection is atomic.
LD r5,r0,@n16v_last_seq
ST r5,r0,@n16r_prev_seq
LD r5,r0,@n16v_session_active
ST r5,r0,@n16r_prev_active
LD r5,r0,@n16s_state
ST r5,r0,@n16r_prev_state
CALL @n16_frame_validate
CMP r3,r0
JMP[NE] @n16r_done
ST r1,r0,@n16r_type
ST r2,r0,@n16r_len
CALL @n16_session_transition
CMP r3,r0
JMP[NE] @n16r_rollback_state
; HEARTBEAT and CLOSE are control messages, not point-addressed payloads.
; In particular, a zero-byte CLOSE must still complete the guest reset.
LD r5,r0,@n16r_type
MOVI r6,12
CMP r5,r6
JMP[EQ] @n16r_control
MOVI r6,13
CMP r5,r6
JMP[EQ] @n16r_control
LD r2,r0,@n16r_len
MOVI r5,2
CMP r2,r5
JMP[LT] @n16r_rollback_short
LD r4,r0,@n16r_base
ADDI r4,r4,32
LDB r1,r4,0
LDB r5,r4,1
SHL r5,r5,8
BOOL.14 r1,r1,r5
CALL @ob_point_to_route
JMP @n16r_done
n16r_control:
MOVI r1,0
MOVI r2,0
MOVI r3,0
MOVI r4,0
JMP @n16r_done
n16r_rollback_short:
MOVI r3,2
JMP @n16r_rollback
n16r_rollback_state:
MOVI r3,6
n16r_rollback:
; Do not consume sequence/session ownership for a rejected route.
LD r5,r0,@n16r_prev_seq
ST r5,r0,@n16v_last_seq
LD r5,r0,@n16r_prev_active
ST r5,r0,@n16v_session_active
LD r5,r0,@n16r_prev_state
ST r5,r0,@n16s_state
MOVI r1,0
MOVI r2,0
MOVI r4,0
JMP @n16r_done
n16r_done:
LD r15,r0,@n16r_ret
RET
n16r_ret:
.word 0
n16r_base:
.word 0
n16r_len:
.word 0
n16r_type:
.word 0
n16r_prev_seq:
.word 0
n16r_prev_active:
.word 0
n16r_prev_state:
.word 0

; N16T session transition machine. r1=type,r2=payload bytes.
; Return r1=state,r2=error (0,1 transition,2 payload),r3=status.
; State: 0 idle,1 hello,2 negotiated,3 receiving,4 corpus complete,
; 5 accepted,6 candidate digest,7 merge proposal,8 terminal.
; Payload lengths: HELLO 2, CAPABILITIES 8, BEGIN 12, CHUNK 1..4096,
; END 4, ACCEPTED 4, REJECTED 4..256, STATUS 8, DIGEST 4,
; PROPOSAL 8, RESULT 4, HEARTBEAT 0, CLOSE 0.
n16_session_transition:
ST r15,r0,@n16s_ret
ST r1,r0,@n16s_type
ST r2,r0,@n16s_len
LD r4,r0,@n16s_state
LD r5,r0,@n16s_type
MOVI r6,12
CMP r5,r6
JMP[NE] @n16s_close
LD r6,r0,@n16s_len
CMP r6,r0
JMP[NE] @n16s_payload_error
CMP r4,r0
JMP[EQ] @n16s_transition_error
MOVI r6,8
CMP r4,r6
JMP[GE] @n16s_transition_error
JMP @n16s_ok
n16s_close:
MOVI r6,13
CMP r5,r6
JMP[NE] @n16s_hello
LD r6,r0,@n16s_len
CMP r6,r0
JMP[NE] @n16s_payload_error
CMP r4,r0
JMP[EQ] @n16s_transition_error
MOVI r6,0
ST r6,r0,@n16s_state
ST r6,r0,@n16v_session_active
ST r6,r0,@n16v_last_seq
JMP @n16s_ok
n16s_hello:
MOVI r6,1
CMP r5,r6
JMP[NE] @n16s_caps
CMP r4,r0
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,2
CMP r6,r7
JMP[NE] @n16s_payload_error
MOVI r4,1
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_caps:
MOVI r6,2
CMP r5,r6
JMP[NE] @n16s_begin
MOVI r6,1
CMP r4,r6
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,8
CMP r6,r7
JMP[NE] @n16s_payload_error
MOVI r4,2
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_begin:
MOVI r6,3
CMP r5,r6
JMP[NE] @n16s_chunk
MOVI r6,2
CMP r4,r6
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,12
CMP r6,r7
JMP[NE] @n16s_payload_error
MOVI r4,3
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_chunk:
MOVI r6,4
CMP r5,r6
JMP[NE] @n16s_end
MOVI r6,3
CMP r4,r6
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
CMP r6,r0
JMP[EQ] @n16s_payload_error
MOVI r7,4096
CMP r6,r7
JMP[GT] @n16s_payload_error
JMP @n16s_ok
n16s_end:
MOVI r6,5
CMP r5,r6
JMP[NE] @n16s_accept
MOVI r6,3
CMP r4,r6
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,4
CMP r6,r7
JMP[NE] @n16s_payload_error
MOVI r4,4
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_accept:
MOVI r6,6
CMP r5,r6
JMP[NE] @n16s_reject
MOVI r6,4
CMP r4,r6
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,4
CMP r6,r7
JMP[NE] @n16s_payload_error
MOVI r4,5
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_reject:
MOVI r6,7
CMP r5,r6
JMP[NE] @n16s_status
CMP r4,r0
JMP[EQ] @n16s_transition_error
MOVI r7,8
CMP r4,r7
JMP[GE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,4
CMP r6,r7
JMP[LT] @n16s_payload_error
MOVI r7,256
CMP r6,r7
JMP[GT] @n16s_payload_error
MOVI r4,8
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_status:
MOVI r6,8
CMP r5,r6
JMP[NE] @n16s_digest
MOVI r6,4
CMP r4,r6
JMP[LT] @n16s_transition_error
MOVI r7,7
CMP r4,r7
JMP[GT] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,8
CMP r6,r7
JMP[NE] @n16s_payload_error
JMP @n16s_ok
n16s_digest:
MOVI r6,9
CMP r5,r6
JMP[NE] @n16s_proposal
MOVI r6,5
CMP r4,r6
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,4
CMP r6,r7
JMP[NE] @n16s_payload_error
MOVI r4,6
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_proposal:
MOVI r6,10
CMP r5,r6
JMP[NE] @n16s_result
MOVI r6,6
CMP r4,r6
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,8
CMP r6,r7
JMP[NE] @n16s_payload_error
MOVI r4,7
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_result:
MOVI r6,11
CMP r5,r6
JMP[NE] @n16s_unknown
MOVI r6,7
CMP r4,r6
JMP[NE] @n16s_transition_error
LD r6,r0,@n16s_len
MOVI r7,4
CMP r6,r7
JMP[NE] @n16s_payload_error
MOVI r4,8
ST r4,r0,@n16s_state
JMP @n16s_ok
n16s_unknown:
JMP @n16s_transition_error
n16s_payload_error:
MOVI r2,2
JMP @n16s_error
n16s_transition_error:
MOVI r2,1
n16s_error:
LD r1,r0,@n16s_state
MOVI r3,1
LD r15,r0,@n16s_ret
RET
n16s_ok:
LD r1,r0,@n16s_state
MOVI r2,0
MOVI r3,0
LD r15,r0,@n16s_ret
RET
n16s_ret:
.word 0
n16s_type:
.word 0
n16s_len:
.word 0
n16s_state:
.word 0

; Guest-side N16T frame encoder.
; n16_frame_build(r1=output byte base,r2=type,r3=sequence,
;                 r4=payload byte base,r5=payload bytes,r6=session-id byte base)
; returns r1=frame bytes (36+payload),r2=error,r3=status.
; Session id entropy is supplied by the host; all wire framing and CRC stay guest-side.
n16_frame_build:
ST r15,r0,@n16b_ret
ST r1,r0,@n16b_base
ST r2,r0,@n16b_type
ST r3,r0,@n16b_sequence_value
ST r4,r0,@n16b_src
ST r5,r0,@n16b_len
ST r6,r0,@n16b_sid
CMP r2,r0
JMP[EQ] @n16b_error
MOVI r10,14
CMP r10,r2
JMP[LT] @n16b_error
MOVI r10,0
MOVHI r10,1
CMP r10,r5
JMP[LT] @n16b_error
LD r4,r0,@n16b_base
MOVI r10,78
STB r10,r4,0
MOVI r10,49
STB r10,r4,1
MOVI r10,54
STB r10,r4,2
MOVI r10,84
STB r10,r4,3
MOVI r10,1
STB r10,r4,4
MOVI r10,0
STB r10,r4,5
LD r5,r0,@n16b_type
MOVI r10,255
BOOL.8 r11,r5,r10
STB r11,r4,6
SHR r5,r5,8
BOOL.8 r11,r5,r10
STB r11,r4,7
; Copy all 16 opaque session-id bytes.
LD r7,r0,@n16b_sid
MOVI r8,0
n16b_sid_loop:
MOVI r10,16
CMP r8,r10
JMP[GE] @n16b_write_seq
ADD r9,r7,r8
LDB r11,r9,0
LD r4,r0,@n16b_base
ADD r9,r4,r8
ADDI r9,r9,8
STB r11,r9,0
ADDI r8,r8,1
JMP @n16b_sid_loop
; Write sequence and payload length as little-endian u32 values.
n16b_write_seq:
LD r5,r0,@n16b_sequence_value
LD r4,r0,@n16b_base
MOVI r10,255
BOOL.8 r11,r5,r10
STB r11,r4,24
SHR r5,r5,8
BOOL.8 r11,r5,r10
STB r11,r4,25
SHR r5,r5,8
BOOL.8 r11,r5,r10
STB r11,r4,26
SHR r5,r5,8
BOOL.8 r11,r5,r10
STB r11,r4,27
LD r5,r0,@n16b_len
MOVI r10,255
BOOL.8 r11,r5,r10
STB r11,r4,28
SHR r5,r5,8
BOOL.8 r11,r5,r10
STB r11,r4,29
SHR r5,r5,8
BOOL.8 r11,r5,r10
STB r11,r4,30
SHR r5,r5,8
BOOL.8 r11,r5,r10
STB r11,r4,31
; Copy the opaque payload after the fixed 32-byte header.
LD r7,r0,@n16b_src
MOVI r8,0
n16b_payload_loop:
LD r5,r0,@n16b_len
CMP r8,r5
JMP[GE] @n16b_crc_start
ADD r9,r7,r8
LDB r11,r9,0
LD r4,r0,@n16b_base
ADD r9,r4,r8
ADDI r9,r9,32
STB r11,r9,0
ADDI r8,r8,1
JMP @n16b_payload_loop
; CRC-32/IEEE over header + payload, matching n16_frame_validate.
n16b_crc_start:
MOVI r11,65535
MOVHI r11,65535
MOVI r7,0
LD r9,r0,@n16b_len
ADDI r9,r9,32
n16b_crc_byte:
CMP r7,r9
JMP[GE] @n16b_crc_done
LD r4,r0,@n16b_base
ADD r4,r4,r7
LDB r10,r4,0
BOOL.6 r11,r11,r10
MOVI r13,8
n16b_crc_bit:
MOVI r12,1
BOOL.8 r12,r11,r12
CMP r12,r0
JMP[EQ] @n16b_crc_zero
SHR r11,r11,1
MOVI r12,0x8320
MOVHI r12,0xEDB8
BOOL.6 r11,r11,r12
JMP @n16b_crc_next
n16b_crc_zero:
SHR r11,r11,1
n16b_crc_next:
MOVI r12,1
SUB r13,r13,r12
CMP r13,r0
JMP[NE] @n16b_crc_bit
ADDI r7,r7,1
JMP @n16b_crc_byte
n16b_crc_done:
MOVI r10,65535
MOVHI r10,65535
BOOL.6 r11,r11,r10
LD r4,r0,@n16b_base
LD r5,r0,@n16b_len
ADDI r5,r5,32
ADD r4,r4,r5
MOVI r10,255
BOOL.8 r12,r11,r10
STB r12,r4,0
SHR r11,r11,8
BOOL.8 r12,r11,r10
STB r12,r4,1
SHR r11,r11,8
BOOL.8 r12,r11,r10
STB r12,r4,2
SHR r11,r11,8
BOOL.8 r12,r11,r10
STB r12,r4,3
LD r5,r0,@n16b_len
ADDI r1,r5,36
MOVI r2,0
MOVI r3,0
LD r15,r0,@n16b_ret
RET
n16b_error:
MOVI r1,0
MOVI r2,1
MOVI r3,1
LD r15,r0,@n16b_ret
RET
n16b_ret:
.word 0
n16b_base:
.word 0
n16b_type:
.word 0
n16b_sequence_value:
.word 0
n16b_src:
.word 0
n16b_len:
.word 0
n16b_sid:
.word 0
