; NaN16 overbootstrap point translator and route-system bridge.
; Point numbering is one-based: #1 runner CPU ring, #2 Android test universe.
; Each point owns one 8 MiB page in the 2 GiB overbootstrap address space.
; Logical route addresses use the existing 16-bit NaNNET route table: 0xA000 + point.
; API:
;   ob_point_to_page: r1 point -> r1 page(0..255), r2 role(1 runner/2 Android/0 reserved), r3 status
;   ob_point_to_addr: r1 point, r2 offset, r3 length -> r1 absolute byte address, r2 page, r3 status
;   ob_point_to_route: r1 point -> r1 gateway, r2 interface, r3 status, r4 logical address

ob_point_to_page:
MOVI r4,1
CMP r1,r4
JMP[LT] @ob_page_bad
MOVI r4,256
CMP r4,r1
JMP[LT] @ob_page_bad
MOVI r4,1
SUB r1,r1,r4
MOVI r2,0
CMP r1,r0
JMP[EQ] @ob_page_runner
MOVI r4,1
CMP r1,r4
JMP[EQ] @ob_page_android
JMP @ob_page_ok
ob_page_runner:
MOVI r2,1
JMP @ob_page_ok
ob_page_android:
MOVI r2,2
ob_page_ok:
MOVI r3,0
RET
ob_page_bad:
MOVI r1,0
MOVI r2,0
MOVI r3,1
RET

ob_point_to_addr:
ST r15,r0,@ob_addr_ret
ST r1,r0,@ob_addr_point
ST r2,r0,@ob_addr_offset
ST r3,r0,@ob_addr_length
LD r1,r0,@ob_addr_point
CALL @ob_point_to_page
CMP r3,r0
JMP[NE] @ob_addr_bad
ST r1,r0,@ob_addr_page
LD r5,r0,@ob_addr_offset
MOVI r6,0
MOVHI r6,128
CMP r5,r6
JMP[GE] @ob_addr_bad
LD r7,r0,@ob_addr_length
CMP r7,r6
JMP[GT] @ob_addr_bad
ADD r8,r5,r7
CMP r8,r6
JMP[GT] @ob_addr_bad
LD r9,r0,@ob_addr_page
SHL r1,r9,23
ADD r1,r1,r5
BOOL.14 r2,r9,r0
MOVI r3,0
LD r15,r0,@ob_addr_ret
RET
ob_addr_bad:
MOVI r1,0
MOVI r2,0
MOVI r3,1
LD r15,r0,@ob_addr_ret
RET
ob_addr_ret:
.word 0
ob_addr_point:
.word 0
ob_addr_offset:
.word 0
ob_addr_length:
.word 0
ob_addr_page:
.word 0

ob_point_to_route:
ST r15,r0,@ob_route_ret
ST r1,r0,@ob_route_point
CALL @ob_point_to_page
CMP r3,r0
JMP[NE] @ob_route_bad
LD r1,r0,@ob_route_point
MOVI r2,0xA000
ADD r1,r1,r2
ST r1,r0,@ob_route_addr
CALL @nn_route_lookup
ST r1,r0,@ob_route_gateway
ST r2,r0,@ob_route_ifid
ST r3,r0,@ob_route_status
LD r4,r0,@ob_route_addr
LD r1,r0,@ob_route_gateway
LD r2,r0,@ob_route_ifid
LD r3,r0,@ob_route_status
LD r15,r0,@ob_route_ret
RET
ob_route_bad:
MOVI r1,0
MOVI r2,0
MOVI r3,1
MOVI r4,0
LD r15,r0,@ob_route_ret
RET
ob_route_ret:
.word 0
ob_route_point:
.word 0
ob_route_addr:
.word 0
ob_route_gateway:
.word 0
ob_route_ifid:
.word 0
ob_route_status:
.word 0
