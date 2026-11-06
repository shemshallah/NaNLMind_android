; android_desktop_input.inc
; NaN16DE desktop pointer + F1..F12 strip.
; Android writes four little-endian words at byte offset 0x168000:
; [0]=sequence [1]=guest-x [2]=guest-y [3]=action (1 down, 2 move, 3 up, 4 cancel).
; The Android layer transports coordinates only. Window hit-testing, focus, drag,
; close, maximize, and function-key dispatch are guest-owned NaN16 code.

wm_fkeybar:
ST r15,r14,0
ADD r14,r14,r13
MOVI r1,0
MOVI r2,18
LD r3,r0,@fb_w
MOVI r4,26
MOVI r5,#C_BAR
CALL @wm_rect
ST r0,r0,@wp_i
wp_fb_loop:
LD r7,r0,@wp_i
MOVI r8,12
CMP r7,r8
JMP[GE] @wp_fb_done
LD r9,r0,@fb_w
MOVI r8,12
DIVU r9,r9,r8
MUL r1,r7,r9
MOVI r8,3
ADD r1,r1,r8
ST r1,r0,@wp_fb_x
MOVI r2,21
SUB r3,r9,r8
MOVI r4,20
MOVI r5,#C_BLUR
CALL @wm_rect
LD r1,r0,@wp_i
SHL r1,r1,2
MOVI r2,@wp_labels
ADD r1,r1,r2
LD r3,r1,0
LD r1,r0,@wp_fb_x
MOVI r2,24
MOVI r4,#C_YEL
CALL @fb_text7
LD r1,r0,@wp_i
ADD r1,r1,r13
ST r1,r0,@wp_i
JMP @wp_fb_loop
wp_fb_done:
SUB r14,r14,r13
LD r15,r14,0
RET

; Poll one host pointer event and apply it to the NaN16 window manager.
wm_pointer_poll:
ST r15,r14,0
ADD r14,r14,r13
MOVI r10,0xA000
MOVHI r10,5
LD r1,r10,0
LD r2,r0,@wp_seen
CMP r1,r2
JMP[EQ] @wp_ret
ST r1,r0,@wp_seen
LD r1,r10,1
ST r1,r0,@wp_x
LD r1,r10,2
ST r1,r0,@wp_y
LD r1,r10,3
ST r1,r0,@wp_action
MOVI r2,1
CMP r1,r2
JMP[EQ] @wp_down
MOVI r2,2
CMP r1,r2
JMP[EQ] @wp_move
; Up/cancel clears any active drag.
MOVI r1,0xFFFF
ST r1,r0,@wp_drag
JMP @wp_ret

wp_down:
; F-key strip: 12 equal-width hit targets in y=[18,44).
LD r1,r0,@wp_y
MOVI r2,18
CMP r1,r2
JMP[LT] @wp_scan_start
MOVI r2,44
CMP r1,r2
JMP[GE] @wp_scan_start
LD r1,r0,@wp_x
LD r2,r0,@fb_w
MOVI r3,12
DIVU r2,r2,r3
DIVU r1,r1,r2
MOVI r2,12
CMP r1,r2
JMP[GE] @wp_ret
MOVI r2,0x101
ADD r1,r1,r2
ST r1,r0,@g_key
CALL @g_onkey
JMP @wp_ret

wp_scan_start:
LD r8,r0,@wm_zn
CMP r8,r0
JMP[EQ] @wp_ret
SUB r8,r8,r13
wp_scan:
CMP r8,r0
JMP[LT] @wp_ret
MOVI r9,#ZOL
MOVHI r9,#ZOH
ADD r9,r9,r8
LD r7,r9,0
ST r7,r0,@wp_id
SHL r9,r7,5
MOVI r10,#WTL
MOVHI r10,#WTH
ADD r9,r9,r10
ST r9,r0,@wp_base
LD r1,r9,#W_ALIVE
CMP r1,r0
JMP[EQ] @wp_scan_next
LD r1,r9,#W_X
LD r2,r0,@wp_x
CMP r2,r1
JMP[LT] @wp_scan_next
LD r3,r9,#W_W
ADD r3,r3,r1
CMP r2,r3
JMP[GE] @wp_scan_next
LD r1,r9,#W_Y
LD r2,r0,@wp_y
CMP r2,r1
JMP[LT] @wp_scan_next
LD r3,r9,#W_H
ADD r3,r3,r1
CMP r2,r3
JMP[GE] @wp_scan_next

; Hit: raise this exact window before applying title-bar actions.
LD r1,r0,@wp_id
CALL @wm_raise
LD r1,r0,@wp_id
CALL @wm_wbase
ST r1,r0,@wp_base
LD r2,r1,#W_Y
MOVI r3,1
ADD r2,r2,r3
LD r3,r0,@wp_y
CMP r3,r2
JMP[LT] @wp_body_hit
LD r2,r1,#W_Y
MOVI r3,16
ADD r2,r2,r3
LD r3,r0,@wp_y
CMP r3,r2
JMP[GE] @wp_body_hit

; Close box: x in [right-16,right-3).
LD r2,r1,#W_X
LD r3,r1,#W_W
ADD r3,r3,r2
MOVI r4,16
SUB r4,r3,r4
LD r5,r0,@wp_x
CMP r5,r4
JMP[LT] @wp_try_enlarge
MOVI r4,3
SUB r4,r3,r4
CMP r5,r4
JMP[GE] @wp_try_enlarge
LD r1,r0,@wp_id
CALL @wm_close
MOVI r1,0xFFFF
ST r1,r0,@wp_drag
JMP @wp_ret

wp_try_enlarge:
; Enlarge box: x in [right-31,right-18).
LD r1,r0,@wp_base
LD r2,r1,#W_X
LD r3,r1,#W_W
ADD r3,r3,r2
MOVI r4,31
SUB r4,r3,r4
LD r5,r0,@wp_x
CMP r5,r4
JMP[LT] @wp_try_drag
MOVI r4,18
SUB r4,r3,r4
CMP r5,r4
JMP[GE] @wp_try_drag
LD r1,r0,@wp_id
MOVI r2,0
MOVI r3,0
CALL @wm_enlarge
MOVI r1,0xFFFF
ST r1,r0,@wp_drag
JMP @wp_ret

wp_try_drag:
; Drag only from title bar, and only while not maximized.
LD r1,r0,@wp_base
LD r2,r1,#W_FLAGS
CMP r2,r0
JMP[NE] @wp_ret
LD r2,r1,#W_X
LD r3,r0,@wp_x
SUB r3,r3,r2
ST r3,r0,@wp_dx
LD r2,r1,#W_Y
LD r3,r0,@wp_y
SUB r3,r3,r2
ST r3,r0,@wp_dy
LD r1,r0,@wp_id
ST r1,r0,@wp_drag
JMP @wp_ret

wp_body_hit:
MOVI r1,0xFFFF
ST r1,r0,@wp_drag
JMP @wp_ret

wp_scan_next:
SUB r8,r8,r13
JMP @wp_scan

wp_move:
LD r1,r0,@wp_drag
MOVI r2,0xFFFF
CMP r1,r2
JMP[EQ] @wp_ret
CALL @wm_wbase
ST r1,r0,@wp_base
LD r2,r0,@wp_x
LD r3,r0,@wp_dx
SUB r2,r2,r3
CMP r2,r0
JMP[GE] @wp_x_nonneg
MOVI r2,0
wp_x_nonneg:
LD r3,r1,#W_W
LD r4,r0,@fb_w
SUB r4,r4,r3
CMP r2,r4
JMP[LE] @wp_x_bound
ADD r2,r4,r0
wp_x_bound:
ST r2,r1,#W_X
LD r2,r0,@wp_y
LD r3,r0,@wp_dy
SUB r2,r2,r3
MOVI r3,#TOPH
CMP r2,r3
JMP[GE] @wp_y_top
ADD r2,r3,r0
wp_y_top:
LD r3,r1,#W_H
LD r4,r0,@fb_h
SUB r4,r4,r3
CMP r2,r4
JMP[LE] @wp_y_bound
ADD r2,r4,r0
wp_y_bound:
ST r2,r1,#W_Y
MOVI r1,1
ST r1,r0,@wm_redraw
wp_ret:
SUB r14,r14,r13
LD r15,r14,0
RET

wp_seen:
.word 0
wp_x:
.word 0
wp_y:
.word 0
wp_action:
.word 0
wp_drag:
.word 0xFFFF
wp_id:
.word 0
wp_base:
.word 0
wp_dx:
.word 0
wp_dy:
.word 0
wp_i:
.word 0
wp_fb_x:
.word 0
wp_labels:
.word @wp_f1,@wp_f2,@wp_f3,@wp_f4,@wp_f5,@wp_f6
.word @wp_f7,@wp_f8,@wp_f9,@wp_f10,@wp_f11,@wp_f12
wp_f1:
.str "F1"
wp_f2:
.str "F2"
wp_f3:
.str "F3"
wp_f4:
.str "F4"
wp_f5:
.str "F5"
wp_f6:
.str "F6"
wp_f7:
.str "F7"
wp_f8:
.str "F8"
wp_f9:
.str "F9"
wp_f10:
.str "F10"
wp_f11:
.str "F11"
wp_f12:
.str "F12"
