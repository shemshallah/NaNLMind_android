; Deterministic NaN16 sample scheduler kernel.
; ABI: r1=sample_count (1..65535), r2=epoch, r3=draw (0..count-1).
; Return: r4=sample index, r5=0; invalid input returns r5=1.
; Each epoch rotates the complete sample order by epoch modulo sample_count.
; This is a permutation: every draw in [0,count) appears exactly once.
main:
MOVI r1,7
MOVI r2,9
MOVI r3,4
CALL @schedule_index
MOVI r6,6
CMP r4,r6
JMP[NE] @reject
CMP r5,r0
JMP[NE] @reject
; Boundary: a 256-sample corpus, epoch 257, final draw 255.
MOVI r1,256
MOVI r2,257
MOVI r3,255
CALL @schedule_index
CMP r4,r0
JMP[NE] @reject
CMP r5,r0
JMP[NE] @reject
; Reject a draw equal to the sample count.
MOVI r1,7
MOVI r2,0
MOVI r3,7
CALL @schedule_index
MOVI r6,1
CMP r5,r6
JMP[NE] @reject
; Reject an empty corpus.
MOVI r1,0
MOVI r2,0
MOVI r3,0
CALL @schedule_index
CMP r5,r6
JMP[NE] @reject
; PASS
MOVI r1,78
PUTC r1
MOVI r1,49
PUTC r1
MOVI r1,54
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,83
PUTC r1
MOVI r1,67
PUTC r1
MOVI r1,72
PUTC r1
MOVI r1,69
PUTC r1
MOVI r1,68
PUTC r1
MOVI r1,32
PUTC r1
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
reject:
MOVI r1,78
PUTC r1
MOVI r1,49
PUTC r1
MOVI r1,54
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,83
PUTC r1
MOVI r1,67
PUTC r1
MOVI r1,72
PUTC r1
MOVI r1,69
PUTC r1
MOVI r1,68
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,82
PUTC r1
MOVI r1,69
PUTC r1
MOVI r1,74
PUTC r1
MOVI r1,69
PUTC r1
MOVI r1,67
PUTC r1
MOVI r1,84
PUTC r1
MOVI r1,10
PUTC r1
HALT
schedule_index:
MOVI r5,0
CMP r1,r0
JMP[EQ] @schedule_invalid
CMP r3,r1
JMP[GE] @schedule_invalid
MODU r4,r2,r1
ADD r4,r4,r3
MODU r4,r4,r1
RET
schedule_invalid:
MOVI r5,1
MOVI r4,0
RET
