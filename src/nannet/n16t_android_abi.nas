; Android-callable NaN16 N16T guest ABI.
; The host sets r1..r6 and PC to one of the public entry labels below.
; Each public entry returns through HALT; the same VM instance is retained.
; All framing, CRC, sequence assignment, session and transition semantics stay here.
;
; n16_android_build: r1=out,r2=type,r4=payload,r5=len,r6=sid; guest assigns sequence.
; n16_android_route: r1=frame,r2=frame_bytes -> r1..r4 route,status
; n16_android_advance: commits pending outbound message and increments guest sequence.
; n16_android_abort: abandons unsent pending message without state/sequence advancement.
; n16_android_reset: reset validator replay/session, transition state and send sequence.

n16_android_build:
CALL @n16_android_build_impl
HALT

n16_android_route:
CALL @n16_android_route_impl
HALT

n16_android_advance:
CALL @n16_android_advance_impl
HALT

n16_android_abort:
CALL @n16_android_abort_impl
HALT

n16_android_reset:
CALL @n16_android_reset_impl
HALT

; Build is transactional: reserve the current sequence, but don't advance it
; until the host reports that the HTTPS exchange completed successfully.
n16_android_build_impl:
ST r15,r0,@n16a_call_ret
LD r7,r0,@n16a_pending
CMP r7,r0
JMP[NE] @n16a_build_busy
ST r2,r0,@n16a_type
ST r5,r0,@n16a_len
LD r3,r0,@n16a_send_seq
ST r3,r0,@n16a_seq
CALL @n16_frame_build
CMP r3,r0
JMP[NE] @n16a_build_done
MOVI r7,1
ST r7,r0,@n16a_pending
LD r7,r0,@n16a_type
ST r7,r0,@n16a_pending_type
LD r7,r0,@n16a_len
ST r7,r0,@n16a_pending_len
LD r7,r0,@n16a_seq
ST r7,r0,@n16a_pending_seq
JMP @n16a_build_done
n16a_build_busy:
MOVI r1,0
MOVI r2,4
MOVI r3,1
MOVI r4,0
n16a_build_done:
LD r15,r0,@n16a_call_ret
RET

n16_android_route_impl:
ST r15,r0,@n16a_call_ret
CALL @n16_frame_route
LD r15,r0,@n16a_call_ret
RET

n16_android_advance_impl:
ST r15,r0,@n16a_call_ret
LD r7,r0,@n16a_pending
CMP r7,r0
JMP[EQ] @n16a_advance_missing
LD r1,r0,@n16a_pending_type
LD r2,r0,@n16a_pending_len
CALL @n16_session_transition
CMP r3,r0
JMP[NE] @n16a_advance_done
; CLOSE resets the guest session; a fresh HELLO begins again at sequence one.
LD r7,r0,@n16a_pending_type
MOVI r6,13
CMP r7,r6
JMP[EQ] @n16a_advance_reset_seq
LD r7,r0,@n16a_send_seq
ADDI r7,r7,1
CMP r7,r0
JMP[NE] @n16a_advance_store_seq
MOVI r7,1
JMP @n16a_advance_store_seq
n16a_advance_reset_seq:
MOVI r7,1
n16a_advance_store_seq:
ST r7,r0,@n16a_send_seq
MOVI r7,0
ST r7,r0,@n16a_pending
JMP @n16a_advance_done
n16a_advance_missing:
MOVI r1,0
MOVI r2,4
MOVI r3,1
MOVI r4,0
n16a_advance_done:
LD r15,r0,@n16a_call_ret
RET

n16_android_abort_impl:
ST r15,r0,@n16a_call_ret
MOVI r7,0
ST r7,r0,@n16a_pending
MOVI r1,0
MOVI r2,0
MOVI r3,0
MOVI r4,0
LD r15,r0,@n16a_call_ret
RET

n16_android_reset_impl:
ST r15,r0,@n16a_reset_ret
CALL @n16_session_reset
MOVI r4,0
ST r4,r0,@n16s_state
ST r4,r0,@n16a_pending
MOVI r4,1
ST r4,r0,@n16a_send_seq
LD r15,r0,@n16a_reset_ret
RET

n16a_call_ret:
.word 0
n16a_reset_ret:
.word 0
n16a_type:
.word 0
n16a_len:
.word 0
n16a_seq:
.word 0
n16a_pending_type:
.word 0
n16a_pending_len:
.word 0
n16a_pending_seq:
.word 0
n16a_pending:
.word 0
n16a_send_seq:
.word 1
