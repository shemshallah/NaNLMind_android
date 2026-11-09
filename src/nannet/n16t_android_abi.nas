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


; Server-side N16T handshake peer. The router host stages opaque bytes and
; invokes this entry; framing, CRC, replay, session and transition decisions
; remain in this NaN16 guest. Current advertised capabilities are deliberately
; zero: the transport handshake is implemented, but training/merge services are
; not advertised until their NMP worker bridge is active.
;
; n16_server_exchange ABI:
;   r1=request byte base, r2=request byte length
;   r4=response byte base, r5=response payload base, r6=session-id byte base
;   returns r1=response bytes, r2=detail, r3=status (0 success)
n16_server_exchange:
CALL @n16_server_exchange_impl
HALT

n16_server_exchange_impl:
ST r15,r0,@n16srv_ret
ST r1,r0,@n16srv_req_base
ST r2,r0,@n16srv_req_len
ST r4,r0,@n16srv_out_base
ST r5,r0,@n16srv_payload_base
ST r6,r0,@n16srv_sid_base

; Select only the two control messages this handshake peer implements.
; Full validation and all state changes still occur in n16_frame_route.
LD r4,r0,@n16srv_req_base
LDB r7,r4,6
LDB r8,r4,7
SHL r8,r8,8
BOOL.14 r7,r7,r8
ST r7,r0,@n16srv_type
MOVI r8,1
CMP r7,r8
JMP[EQ] @n16srv_route
MOVI r8,13
CMP r7,r8
JMP[EQ] @n16srv_route
JMP @n16srv_unsupported

n16srv_route:
LD r1,r0,@n16srv_req_base
LD r2,r0,@n16srv_req_len
CALL @n16_frame_route
CMP r3,r0
JMP[EQ] @n16srv_copy_session

; The HELLO selects logical point 1 (the runner portal). The current local
; router may report status 2 for that physical host route; accept only the
; exact portal logical point in that case.
LD r7,r0,@n16srv_type
MOVI r8,1
CMP r7,r8
JMP[NE] @n16srv_route_fail
MOVI r8,2
CMP r3,r8
JMP[NE] @n16srv_route_fail
MOVI r8,0xA001
CMP r4,r8
JMP[NE] @n16srv_route_fail

n16srv_copy_session:
; Copy the validated peer session ID to the server outbound builder buffer.
LD r4,r0,@n16srv_req_base
ADDI r4,r4,8
LD r6,r0,@n16srv_sid_base
MOVI r7,0
MOVI r8,16
n16srv_copy_session_loop:
LDB r9,r4,0
STB r9,r6,0
ADDI r4,r4,1
ADDI r6,r6,1
ADDI r7,r7,1
CMP r7,r8
JMP[LT] @n16srv_copy_session_loop

LD r7,r0,@n16srv_type
MOVI r8,13
CMP r7,r8
JMP[EQ] @n16srv_close_reply

; CAPABILITIES payload: logical runner portal point (u16 LE), then six
; reserved zero bytes. No training or merge capability is claimed here.
LD r5,r0,@n16srv_payload_base
MOVI r7,1
STB r7,r5,0
MOVI r7,0
STB r7,r5,1
STB r7,r5,2
STB r7,r5,3
STB r7,r5,4
STB r7,r5,5
STB r7,r5,6
STB r7,r5,7
LD r1,r0,@n16srv_out_base
MOVI r2,2
LD r4,r0,@n16srv_payload_base
MOVI r5,8
LD r6,r0,@n16srv_sid_base
CALL @n16_android_build_impl
CMP r3,r0
JMP[NE] @n16srv_build_fail
ST r1,r0,@n16srv_response_len
CALL @n16_android_advance_impl
CMP r3,r0
JMP[NE] @n16srv_advance_fail
LD r1,r0,@n16srv_response_len
MOVI r2,0
MOVI r3,0
MOVI r4,0
JMP @n16srv_done

n16srv_close_reply:
; CLOSE already reset the guest state/replay cursor in n16_frame_route.
; Build a terminal peer CLOSE directly; no second state transition is needed.
LD r1,r0,@n16srv_out_base
MOVI r2,13
MOVI r3,1
LD r4,r0,@n16srv_payload_base
MOVI r5,0
LD r6,r0,@n16srv_sid_base
CALL @n16_frame_build
CMP r3,r0
JMP[NE] @n16srv_build_fail
MOVI r2,0
MOVI r3,0
MOVI r4,2
JMP @n16srv_done

n16srv_route_fail:
MOVI r1,0
MOVI r2,0
JMP @n16srv_done
n16srv_unsupported:
MOVI r1,0
MOVI r2,0
MOVI r3,7
JMP @n16srv_done
n16srv_build_fail:
MOVI r1,0
MOVI r2,1
JMP @n16srv_done
n16srv_advance_fail:
MOVI r1,0
MOVI r2,2
n16srv_done:
LD r15,r0,@n16srv_ret
RET

n16srv_ret:
.word 0
n16srv_req_base:
.word 0
n16srv_req_len:
.word 0
n16srv_out_base:
.word 0
n16srv_payload_base:
.word 0
n16srv_sid_base:
.word 0
n16srv_type:
.word 0
n16srv_response_len:
.word 0
