; NANNET M1 control plane: links, addresses, routes, DNS, config generations.
; Direct-call ABI: r1..r5 arguments, r1/r2 results, r3 status.
; Status: 0 ok, 1 EINVAL, 2 ENOENT, 3 ENOSPC, 4 EOVERFLOW, 5 EPROTO.
; Config lines (keywords are read by first letter, rest of token skipped):
;   link <ifid> <0|1>
;   addr <ifid> <ip> <prefix>       (ip 16-bit, 0x or decimal; prefix 0..16)
;   route <dst> <prefix> <gw> <ifid> <metric>
;   dns <ip>
;   driver <drvid> <0|1>
; '#' comments and blank lines are ignored.

nn_n_lo:
.str "lo"
nn_n_eth:
.str "eth0"
nn_n_wlan:
.str "wlan0"
nn_n_seam:
.str "seam0"
nn_k_loop:
.str "loop"
nn_k_wired:
.str "wired"
nn_k_wifi:
.str "wifi"
nn_k_seam:
.str "seam"
nn_s_up:
.str "up"
nn_s_down:
.str "down"
nn_s_scan:
.str "scan"
nn_s_join:
.str "join"
nn_s_ok:
.str "ok"
nn_s_fail:
.str "fail"
nn_s_none:
.str "-"
nn_state_tab:
.word @nn_s_down, @nn_s_up, @nn_s_scan, @nn_s_join, @nn_s_ok, @nn_s_fail
nn_scan_ssid0:
.str "HomeNet"
nn_scan_ssid1:
.str "Cafe"
nn_scan_ssid2:
.str "Lab"
nn_scan_tab:
.word @nn_scan_ssid0, 60
.word @nn_scan_ssid1, 42
.word @nn_scan_ssid2, 30
nn_drv_udp:
.str "DN_UDP"
nn_drv_tcp:
.str "DN_TCP"
nn_drv_tls:
.str "DN_TLS"
nn_drv_dns:
.str "DN_DNS"
nn_drv_icmp:
.str "DN_ICMP"
nn_drv_ws:
.str "DN_WS"
nn_drv_unix:
.str "DN_UNIX"
nn_drv_ble:
.str "DN_BLE"
nn_drv_loop:
.str "DN_LOOP"
nn_drv_null:
.str "DN_NULL"
nn_drv_rec:
.str "DN_REC"
nn_drv_rep:
.str "DN_REP"
nn_drv_seam:
.str "DN_SEAM"
nn_drv_reg:
.word 1,  @nn_drv_udp,  0
.word 2,  @nn_drv_tcp,  0
.word 3,  @nn_drv_tls,  0
.word 4,  @nn_drv_dns,  0
.word 5,  @nn_drv_icmp, 0
.word 6,  @nn_drv_ws,   0
.word 7,  @nn_drv_unix, 0
.word 8,  @nn_drv_ble,  0
.word 9,  @nn_drv_loop, 0
.word 10, @nn_drv_null, 0
.word 11, @nn_drv_rec,  0
.word 12, @nn_drv_rep,  0
.word 13, @nn_drv_seam, 0
.word 0, 0, 0
nn_kind_tab:
.word @nn_k_loop, @nn_k_wired, @nn_k_wifi, @nn_k_seam

; interface record {ifid, name, kind, state, mtu, drvid, mac, flags}
nn_ifs:
.word 0, @nn_n_lo,   0, 1, 65535, 9,  0, 0
.word 1, @nn_n_eth,  1, 0, 1500,  10, 0, 0
.word 2, @nn_n_wlan, 2, 0, 1500,  10, 0, 0
.word 3, @nn_n_seam, 3, 0, 1500,  10, 0, 0

; address record {ifid, ip, prefix, flags, state}
nn_addr:
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0

; route record {dst, prefix, gw, ifid, metric, state}
nn_route:
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0

; dns record {ip, state}
nn_dns:
.word 0, 0
.word 0, 0

; config generation backup
nn_bif:
.word 0, 0, 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0, 0, 0
nn_baddr:
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0
nn_broute:
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0
nn_bdns:
.word 0, 0
.word 0, 0
nn_bdrv:
.word 0, 0, 0, 0, 0, 0, 0
.word 0, 0, 0, 0, 0, 0

nn_v1:
.word 0
nn_v2:
.word 0
nn_v3:
.word 0
nn_v4:
.word 0
nn_v5:
.word 0
nn_gen:
.word 0
nn_cfgst:
.word 0
nn_estat:
.word 0
nn_ldst:
.word 0
nn_tmp:
.word 0
nn_wifi_ssid:
.word 0
nn_wifi_rssi:
.word 0
nn_bwssid:
.word 0
nn_brssi:
.word 0

; ---- primitives ----------------------------------------------------------
nn_puts: ; r1 = word address of a zero-terminated string
        SHL r1,r1,2
np_l:
        LDB r2,r1,0
        CMP r2,r0
        JMP[EQ] @np_d
        PUTC r2
        ADDI r1,r1,1
        JMP @np_l
np_d:
        RET

nn_putsmall: ; r1 = 0..99
        MOVI r2,0
nps_t:
        MOVI r3,10
        CMP r1,r3
        JMP[LT] @nps_o
        SUB r1,r1,r3
        ADDI r2,r2,1
        JMP @nps_t
nps_o:
        CMP r2,r0
        JMP[EQ] @nps_skip
        ADDI r2,r2,48
        PUTC r2
nps_skip:
        ADDI r1,r1,48
        PUTC r1
        RET

nn_puthex4: ; r1 = 16-bit value
        ST r15,r0,@nph_ret
        MOVI r9,15
        SHR r2,r1,12
        BOOL.8 r2,r2,r9
        CALL @nn_hexdig
        MOVI r9,15
        SHR r2,r1,8
        BOOL.8 r2,r2,r9
        CALL @nn_hexdig
        MOVI r9,15
        SHR r2,r1,4
        BOOL.8 r2,r2,r9
        CALL @nn_hexdig
        MOVI r9,15
        BOOL.8 r2,r1,r9
        CALL @nn_hexdig
        LD r15,r0,@nph_ret
        RET
nph_ret:
.word 0

nn_hexdig: ; r2 = 0..15
        MOVI r3,9
        CMP r2,r3
        JMP[LE] @nhd_num
        ADDI r2,r2,55
        JMP @nhd_out
nhd_num:
        ADDI r2,r2,48
nhd_out:
        PUTC r2
        RET

nn_copy: ; r1 dst, r2 src, r3 count
        MOVI r5,0
nc_l:
        CMP r5,r3
        JMP[EQ] @nc_d
        ADD r6,r2,r5
        LD r7,r6,0
        ADD r6,r1,r5
        ST r7,r6,0
        ADDI r5,r5,1
        JMP @nc_l
nc_d:
        RET

nn_if_find: ; r1 = ifid -> r5 = record or 0
        MOVI r4,@nn_ifs
        MOVI r6,4
nf_l:
        CMP r6,r0
        JMP[EQ] @nf_no
        LD r7,r4,0
        CMP r7,r1
        JMP[EQ] @nf_yes
        ADDI r4,r4,8
        MOVI r7,1
        SUB r6,r6,r7
        JMP @nf_l
nf_yes:
        BOOL.14 r5,r4,r0
        RET
nf_no:
        MOVI r5,0
        RET

; ---- links ---------------------------------------------------------------
nn_link_set: ; r1 = ifid, r2 = 0|1
        ST r15,r0,@nls_ret
        ST r2,r0,@nn_tmp
        CALL @nn_if_find
        CMP r5,r0
        JMP[EQ] @nls_no
        LD r2,r0,@nn_tmp
        ST r2,r5,3
        MOVI r3,0
        LD r15,r0,@nls_ret
        RET
nls_no:
        MOVI r3,2
        LD r15,r0,@nls_ret
        RET
nls_ret:
.word 0

nn_link_up:
        MOVI r2,1
        JMP @nn_link_set

nn_link_down:
        MOVI r2,0
        JMP @nn_link_set

nn_link_list:
        ST r15,r0,@nll_ret
        MOVI r4,@nn_ifs
        MOVI r5,4
nll_l:
        CMP r5,r0
        JMP[EQ] @nll_d
        MOVI r1,73
        PUTC r1
        MOVI r1,70
        PUTC r1
        MOVI r1,32
        PUTC r1
        LD r6,r4,0
        ADDI r6,r6,48
        PUTC r6
        MOVI r1,32
        PUTC r1
        LD r1,r4,1
        CALL @nn_puts
        MOVI r1,32
        PUTC r1
        LD r6,r4,2
        MOVI r1,@nn_kind_tab
        ADD r7,r1,r6
        LD r1,r7,0
        CALL @nn_puts
        MOVI r1,32
        PUTC r1
        LD r6,r4,3
        MOVI r1,@nn_state_tab
        ADD r7,r1,r6
        LD r1,r7,0
        CALL @nn_puts
        MOVI r1,10
        PUTC r1
        ADDI r4,r4,8
        MOVI r6,1
        SUB r5,r5,r6
        JMP @nll_l
nll_d:
        MOVI r1,0
        MOVI r3,0
        LD r15,r0,@nll_ret
        RET
nll_ret:
.word 0

; ---- addresses -----------------------------------------------------------
nn_addr_add: ; r1 = ifid, r2 = ip, r3 = prefix
        ST r15,r0,@naa_ret
        ST r1,r0,@nn_v1
        ST r2,r0,@nn_v2
        ST r3,r0,@nn_v3
        MOVI r6,16
        CMP r6,r3
        JMP[LT] @naa_bad
        CMP r2,r0
        JMP[EQ] @naa_bad
        LD r1,r0,@nn_v1
        CALL @nn_if_find
        CMP r5,r0
        JMP[EQ] @naa_no
        MOVI r4,@nn_addr
        MOVI r5,6
naa_l:
        CMP r5,r0
        JMP[EQ] @naa_full
        LD r6,r4,4
        CMP r6,r0
        JMP[EQ] @naa_fill
        LD r6,r4,0
        LD r7,r0,@nn_v1
        CMP r6,r7
        JMP[NE] @naa_next
        LD r6,r4,1
        LD r7,r0,@nn_v2
        CMP r6,r7
        JMP[NE] @naa_next
        JMP @naa_fill
naa_next:
        ADDI r4,r4,5
        MOVI r6,1
        SUB r5,r5,r6
        JMP @naa_l
naa_fill:
        LD r6,r0,@nn_v1
        ST r6,r4,0
        LD r6,r0,@nn_v2
        ST r6,r4,1
        LD r6,r0,@nn_v3
        ST r6,r4,2
        ST r0,r4,3
        MOVI r6,1
        ST r6,r4,4
        MOVI r3,0
        LD r15,r0,@naa_ret
        RET
naa_bad:
        MOVI r3,1
        LD r15,r0,@naa_ret
        RET
naa_no:
        MOVI r3,2
        LD r15,r0,@naa_ret
        RET
naa_full:
        MOVI r3,3
        LD r15,r0,@naa_ret
        RET
naa_ret:
.word 0

nn_addr_del: ; r1 = ifid, r2 = ip
        ST r15,r0,@nad_ret
        ST r1,r0,@nn_v1
        ST r2,r0,@nn_v2
        MOVI r4,@nn_addr
        MOVI r5,6
nad_l:
        CMP r5,r0
        JMP[EQ] @nad_no
        LD r6,r4,4
        CMP r6,r0
        JMP[EQ] @nad_next
        LD r6,r4,0
        LD r7,r0,@nn_v1
        CMP r6,r7
        JMP[NE] @nad_next
        LD r6,r4,1
        LD r7,r0,@nn_v2
        CMP r6,r7
        JMP[NE] @nad_next
        ST r0,r4,4
        MOVI r3,0
        LD r15,r0,@nad_ret
        RET
nad_next:
        ADDI r4,r4,5
        MOVI r6,1
        SUB r5,r5,r6
        JMP @nad_l
nad_no:
        MOVI r3,2
        LD r15,r0,@nad_ret
        RET
nad_ret:
.word 0

nn_addr_list:
        ST r15,r0,@nal_ret
        MOVI r4,@nn_addr
        MOVI r5,6
nal_l:
        CMP r5,r0
        JMP[EQ] @nal_d
        LD r6,r4,4
        CMP r6,r0
        JMP[EQ] @nal_next
        MOVI r1,65
        PUTC r1
        MOVI r1,32
        PUTC r1
        LD r6,r4,0
        ADDI r6,r6,48
        PUTC r6
        MOVI r1,32
        PUTC r1
        LD r1,r4,1
        CALL @nn_puthex4
        MOVI r1,32
        PUTC r1
        LD r1,r4,2
        CALL @nn_putsmall
        MOVI r1,10
        PUTC r1
nal_next:
        ADDI r4,r4,5
        MOVI r6,1
        SUB r5,r5,r6
        JMP @nal_l
nal_d:
        MOVI r3,0
        LD r15,r0,@nal_ret
        RET
nal_ret:
.word 0

; ---- routes --------------------------------------------------------------
nn_route_add: ; r1=dst, r2=prefix, r3=gw, r4=ifid, r5=metric
        ST r15,r0,@nra_ret
        ST r1,r0,@nn_v1
        ST r2,r0,@nn_v2
        ST r3,r0,@nn_v3
        ST r4,r0,@nn_v4
        ST r5,r0,@nn_v5
        MOVI r6,16
        CMP r6,r2
        JMP[LT] @nra_bad
        BOOL.14 r1,r4,r0
        CALL @nn_if_find
        CMP r5,r0
        JMP[EQ] @nra_no
        MOVI r4,@nn_route
        MOVI r5,8
nra_l:
        CMP r5,r0
        JMP[EQ] @nra_full
        LD r6,r4,5
        CMP r6,r0
        JMP[EQ] @nra_fill
        LD r6,r4,0
        LD r7,r0,@nn_v1
        CMP r6,r7
        JMP[NE] @nra_next
        LD r6,r4,1
        LD r7,r0,@nn_v2
        CMP r6,r7
        JMP[NE] @nra_next
        JMP @nra_fill
nra_next:
        ADDI r4,r4,6
        MOVI r6,1
        SUB r5,r5,r6
        JMP @nra_l
nra_fill:
        LD r6,r0,@nn_v1
        ST r6,r4,0
        LD r6,r0,@nn_v2
        ST r6,r4,1
        LD r6,r0,@nn_v3
        ST r6,r4,2
        LD r6,r0,@nn_v4
        ST r6,r4,3
        LD r6,r0,@nn_v5
        ST r6,r4,4
        MOVI r6,1
        ST r6,r4,5
        MOVI r3,0
        LD r15,r0,@nra_ret
        RET
nra_bad:
        MOVI r3,1
        LD r15,r0,@nra_ret
        RET
nra_no:
        MOVI r3,2
        LD r15,r0,@nra_ret
        RET
nra_full:
        MOVI r3,3
        LD r15,r0,@nra_ret
        RET
nra_ret:
.word 0

nn_route_del: ; r1=dst, r2=prefix
        ST r15,r0,@nrd_ret
        ST r1,r0,@nn_v1
        ST r2,r0,@nn_v2
        MOVI r4,@nn_route
        MOVI r5,8
nrd_l:
        CMP r5,r0
        JMP[EQ] @nrd_no
        LD r6,r4,5
        CMP r6,r0
        JMP[EQ] @nrd_next
        LD r6,r4,0
        LD r7,r0,@nn_v1
        CMP r6,r7
        JMP[NE] @nrd_next
        LD r6,r4,1
        LD r7,r0,@nn_v2
        CMP r6,r7
        JMP[NE] @nrd_next
        ST r0,r4,5
        MOVI r3,0
        LD r15,r0,@nrd_ret
        RET
nrd_next:
        ADDI r4,r4,6
        MOVI r6,1
        SUB r5,r5,r6
        JMP @nrd_l
nrd_no:
        MOVI r3,2
        LD r15,r0,@nrd_ret
        RET
nrd_ret:
.word 0

nn_route_list:
        ST r15,r0,@nrl_ret
        MOVI r4,@nn_route
        MOVI r5,8
nrl_l:
        CMP r5,r0
        JMP[EQ] @nrl_d
        LD r6,r4,5
        CMP r6,r0
        JMP[EQ] @nrl_next
        MOVI r1,82
        PUTC r1
        MOVI r1,32
        PUTC r1
        LD r1,r4,0
        CALL @nn_puthex4
        MOVI r1,32
        PUTC r1
        LD r1,r4,1
        CALL @nn_putsmall
        MOVI r1,32
        PUTC r1
        LD r1,r4,2
        CALL @nn_puthex4
        MOVI r1,32
        PUTC r1
        LD r6,r4,3
        ADDI r6,r6,48
        PUTC r6
        MOVI r1,32
        PUTC r1
        LD r1,r4,4
        CALL @nn_puthex4
        MOVI r1,10
        PUTC r1
nrl_next:
        ADDI r4,r4,6
        MOVI r6,1
        SUB r5,r5,r6
        JMP @nrl_l
nrl_d:
        MOVI r3,0
        LD r15,r0,@nrl_ret
        RET
nrl_ret:
.word 0

nn_route_lookup: ; r1 = dst -> r1 = gw, r2 = ifid, r3 status
        ST r15,r0,@nru_ret
        ST r1,r0,@nn_ldst
        MOVI r4,@nn_route
        MOVI r5,8
        MOVI r8,65535
        MOVI r9,0
        MOVI r10,0
nru_l:
        CMP r5,r0
        JMP[EQ] @nru_done
        LD r6,r4,5
        CMP r6,r0
        JMP[EQ] @nru_next
        LD r6,r4,1
        MOVI r7,65535
        CMP r8,r7
        JMP[EQ] @nru_proc
        CMP r6,r8
        JMP[LE] @nru_next
nru_proc:
        MOVI r7,16
        SUB r7,r7,r6
        LD r11,r0,@nn_ldst
        LD r12,r4,0
nru_sh:
        CMP r7,r0
        JMP[EQ] @nru_cmp
        SHR r11,r11,1
        SHR r12,r12,1
        MOVI r2,1
        SUB r7,r7,r2
        JMP @nru_sh
nru_cmp:
        CMP r11,r12
        JMP[NE] @nru_next
        BOOL.14 r8,r6,r0
        LD r9,r4,2
        LD r10,r4,3
nru_next:
        ADDI r4,r4,6
        MOVI r2,1
        SUB r5,r5,r2
        JMP @nru_l
nru_done:
        MOVI r7,65535
        CMP r8,r7
        JMP[EQ] @nru_none
        BOOL.14 r1,r9,r0
        BOOL.14 r2,r10,r0
        MOVI r3,0
        LD r15,r0,@nru_ret
        RET
nru_none:
        MOVI r1,0
        MOVI r2,0
        MOVI r3,2
        LD r15,r0,@nru_ret
        RET
nru_ret:
.word 0

; ---- dns -----------------------------------------------------------------
nn_dns_add: ; r1 = ip
        ST r15,r0,@nda_ret
        ST r1,r0,@nn_v1
        CMP r1,r0
        JMP[EQ] @nda_bad
        MOVI r4,@nn_dns
        MOVI r5,2
nda_l:
        CMP r5,r0
        JMP[EQ] @nda_full
        LD r6,r4,1
        CMP r6,r0
        JMP[EQ] @nda_fill
        LD r6,r4,0
        LD r7,r0,@nn_v1
        CMP r6,r7
        JMP[EQ] @nda_ok
        ADDI r4,r4,2
        MOVI r6,1
        SUB r5,r5,r6
        JMP @nda_l
nda_fill:
        LD r6,r0,@nn_v1
        ST r6,r4,0
        MOVI r6,1
        ST r6,r4,1
nda_ok:
        MOVI r3,0
        LD r15,r0,@nda_ret
        RET
nda_bad:
        MOVI r3,1
        LD r15,r0,@nda_ret
        RET
nda_full:
        MOVI r3,3
        LD r15,r0,@nda_ret
        RET
nda_ret:
.word 0

nn_dns_list:
        ST r15,r0,@ndl_ret
        MOVI r4,@nn_dns
        MOVI r5,2
ndl_l:
        CMP r5,r0
        JMP[EQ] @ndl_d
        LD r6,r4,1
        CMP r6,r0
        JMP[EQ] @ndl_next
        MOVI r1,68
        PUTC r1
        MOVI r1,32
        PUTC r1
        LD r1,r4,0
        CALL @nn_puthex4
        MOVI r1,10
        PUTC r1
ndl_next:
        ADDI r4,r4,2
        MOVI r6,1
        SUB r5,r5,r6
        JMP @ndl_l
ndl_d:
        MOVI r3,0
        LD r15,r0,@ndl_ret
        RET
ndl_ret:
.word 0

; ---- wifi fixture backend ------------------------------------------------
nn_wifi_scan: ; r1 = ifid -> r1 = count
        ST r15,r0,@nws_ret
        CALL @nn_if_find
        CMP r5,r0
        JMP[EQ] @nws_no
        LD r7,r5,2
        MOVI r6,2
        CMP r7,r6
        JMP[NE] @nws_bad
        LD r7,r5,3
        CMP r7,r0
        JMP[EQ] @nws_perm
        MOVI r7,2
        ST r7,r5,3
        MOVI r1,3
        MOVI r2,0
        MOVI r3,0
        LD r15,r0,@nws_ret
        RET
nws_no:
        MOVI r3,2
        LD r15,r0,@nws_ret
        RET
nws_bad:
        MOVI r3,1
        LD r15,r0,@nws_ret
        RET
nws_perm:
        MOVI r3,7
        LD r15,r0,@nws_ret
        RET
nws_ret:
.word 0

nn_wifi_join: ; r1 = ifid, r2 = scan index
        ST r15,r0,@nwj_ret
        ST r1,r0,@nn_v1
        ST r2,r0,@nn_v2
        CALL @nn_if_find
        CMP r5,r0
        JMP[EQ] @nwj_no
        LD r7,r5,2
        MOVI r6,2
        CMP r7,r6
        JMP[NE] @nwj_bad
        LD r7,r5,3
        MOVI r6,2
        CMP r7,r6
        JMP[NE] @nwj_proto
        LD r7,r0,@nn_v2
        MOVI r6,3
        CMP r6,r7
        JMP[LE] @nwj_bad
        MOVI r7,3
        ST r7,r5,3
        LD r7,r0,@nn_v2
        MOVI r6,2
        MUL r7,r7,r6
        MOVI r6,@nn_scan_tab
        ADD r7,r7,r6
        LD r8,r7,0
        ST r8,r0,@nn_wifi_ssid
        LD r8,r7,1
        ST r8,r0,@nn_wifi_rssi
        MOVI r7,4
        ST r7,r5,3
        LD r1,r0,@nn_wifi_ssid
        LD r2,r0,@nn_wifi_rssi
        MOVI r3,0
        LD r15,r0,@nwj_ret
        RET
nwj_no:
        MOVI r3,2
        LD r15,r0,@nwj_ret
        RET
nwj_bad:
        MOVI r3,1
        LD r15,r0,@nwj_ret
        RET
nwj_proto:
        MOVI r3,5
        LD r15,r0,@nwj_ret
        RET
nwj_ret:
.word 0

nn_wifi_leave: ; r1 = ifid
        ST r15,r0,@nwv_ret
        CALL @nn_if_find
        CMP r5,r0
        JMP[EQ] @nwv_no
        LD r7,r5,2
        MOVI r6,2
        CMP r7,r6
        JMP[NE] @nwv_bad
        MOVI r7,1
        ST r7,r5,3
        ST r0,r0,@nn_wifi_ssid
        ST r0,r0,@nn_wifi_rssi
        MOVI r3,0
        LD r15,r0,@nwv_ret
        RET
nwv_no:
        MOVI r3,2
        LD r15,r0,@nwv_ret
        RET
nwv_bad:
        MOVI r3,1
        LD r15,r0,@nwv_ret
        RET
nwv_ret:
.word 0

nn_wifi_list:
        ST r15,r0,@nwl_ret
        MOVI r4,@nn_scan_tab
        MOVI r5,3
        MOVI r6,0
nwl_l:
        CMP r5,r0
        JMP[EQ] @nwl_joined
        MOVI r1,83
        PUTC r1
        MOVI r1,32
        PUTC r1
        BOOL.14 r2,r6,r0
        ADDI r2,r2,48
        PUTC r2
        MOVI r1,32
        PUTC r1
        LD r1,r4,0
        CALL @nn_puts
        MOVI r1,32
        PUTC r1
        LD r1,r4,1
        CALL @nn_putsmall
        MOVI r1,10
        PUTC r1
        ADDI r4,r4,2
        ADDI r6,r6,1
        MOVI r7,1
        SUB r5,r5,r7
        JMP @nwl_l
nwl_joined:
        MOVI r1,87
        PUTC r1
        MOVI r1,32
        PUTC r1
        MOVI r1,2
        CALL @nn_if_find
        CMP r5,r0
        JMP[EQ] @nwl_nostate
        LD r6,r5,3
        JMP @nwl_st
nwl_nostate:
        MOVI r6,0
nwl_st:
        ADDI r6,r6,48
        PUTC r6
        MOVI r1,32
        PUTC r1
        LD r1,r0,@nn_wifi_ssid
        CMP r1,r0
        JMP[NE] @nwl_ssid
        MOVI r1,@nn_s_none
nwl_ssid:
        CALL @nn_puts
        MOVI r1,32
        PUTC r1
        LD r1,r0,@nn_wifi_rssi
        CALL @nn_putsmall
        MOVI r1,10
        PUTC r1
        MOVI r3,0
        LD r15,r0,@nwl_ret
        RET
nwl_ret:
.word 0

; ---- status --------------------------------------------------------------
nn_status:
        ST r15,r0,@nst_ret
        MOVI r1,83
        PUTC r1
        MOVI r1,84
        PUTC r1
        MOVI r1,32
        PUTC r1
        MOVI r4,@nn_ifs
        MOVI r5,4
        MOVI r6,0
nst_l:
        CMP r5,r0
        JMP[EQ] @nst_lp
        LD r7,r4,3
        CMP r7,r0
        JMP[EQ] @nst_ln
        ADDI r6,r6,1
nst_ln:
        ADDI r4,r4,8
        MOVI r7,1
        SUB r5,r5,r7
        JMP @nst_l
nst_lp:
        BOOL.14 r1,r6,r0
        CALL @nn_putsmall
        MOVI r1,32
        PUTC r1
        MOVI r4,@nn_addr
        MOVI r5,6
        MOVI r6,0
nst_a:
        CMP r5,r0
        JMP[EQ] @nst_ap
        LD r7,r4,4
        CMP r7,r0
        JMP[EQ] @nst_an
        ADDI r6,r6,1
nst_an:
        ADDI r4,r4,5
        MOVI r7,1
        SUB r5,r5,r7
        JMP @nst_a
nst_ap:
        BOOL.14 r1,r6,r0
        CALL @nn_putsmall
        MOVI r1,32
        PUTC r1
        MOVI r4,@nn_route
        MOVI r5,8
        MOVI r6,0
nst_r:
        CMP r5,r0
        JMP[EQ] @nst_rp
        LD r7,r4,5
        CMP r7,r0
        JMP[EQ] @nst_rn
        ADDI r6,r6,1
nst_rn:
        ADDI r4,r4,6
        MOVI r7,1
        SUB r5,r5,r7
        JMP @nst_r
nst_rp:
        BOOL.14 r1,r6,r0
        CALL @nn_putsmall
        MOVI r1,32
        PUTC r1
        MOVI r4,@nn_dns
        MOVI r5,2
        MOVI r6,0
nst_d:
        CMP r5,r0
        JMP[EQ] @nst_dp
        LD r7,r4,1
        CMP r7,r0
        JMP[EQ] @nst_dn
        ADDI r6,r6,1
nst_dn:
        ADDI r4,r4,2
        MOVI r7,1
        SUB r5,r5,r7
        JMP @nst_d
nst_dp:
        BOOL.14 r1,r6,r0
        CALL @nn_putsmall
        MOVI r1,10
        PUTC r1
        MOVI r3,0
        LD r15,r0,@nst_ret
        RET
nst_ret:
.word 0

; ---- driver registry -----------------------------------------------------
nn_drv_find: ; r1 = id -> r5 = record or 0
        MOVI r4,@nn_drv_reg
ndf_l:
        LD r6,r4,0
        CMP r6,r0
        JMP[EQ] @ndf_no
        CMP r6,r1
        JMP[EQ] @ndf_yes
        ADDI r4,r4,3
        JMP @ndf_l
ndf_yes:
        BOOL.14 r5,r4,r0
        RET
ndf_no:
        MOVI r5,0
        RET

nn_drv_set: ; r1 = id, r2 = 0|1
        ST r15,r0,@nds_ret
        ST r2,r0,@nn_tmp
        CALL @nn_drv_find
        CMP r5,r0
        JMP[EQ] @nds_no
        LD r2,r0,@nn_tmp
        ST r2,r5,2
        MOVI r3,0
        LD r15,r0,@nds_ret
        RET
nds_no:
        MOVI r3,2
        LD r15,r0,@nds_ret
        RET
nds_ret:
.word 0

; ---- config generations --------------------------------------------------
nn_cfg_snapshot:
        ST r15,r0,@ncs_ret
        MOVI r1,@nn_bif
        MOVI r2,@nn_ifs
        MOVI r3,32
        CALL @nn_copy
        MOVI r1,@nn_baddr
        MOVI r2,@nn_addr
        MOVI r3,30
        CALL @nn_copy
        MOVI r1,@nn_broute
        MOVI r2,@nn_route
        MOVI r3,48
        CALL @nn_copy
        MOVI r1,@nn_bdns
        MOVI r2,@nn_dns
        MOVI r3,4
        CALL @nn_copy
        LD r4,r0,@nn_wifi_ssid
        ST r4,r0,@nn_bwssid
        LD r4,r0,@nn_wifi_rssi
        ST r4,r0,@nn_brssi
        MOVI r4,0
        MOVI r8,13
ncs_dl:
        CMP r4,r8
        JMP[EQ] @ncs_dd
        MOVI r5,3
        MUL r5,r4,r5
        MOVI r6,@nn_drv_reg
        ADD r5,r5,r6
        LD r7,r5,2
        MOVI r6,@nn_bdrv
        ADD r6,r6,r4
        ST r7,r6,0
        ADDI r4,r4,1
        JMP @ncs_dl
ncs_dd:
        LD r15,r0,@ncs_ret
        RET
ncs_ret:
.word 0

nn_cfg_restore:
        ST r15,r0,@ncr_ret
        MOVI r1,@nn_ifs
        MOVI r2,@nn_bif
        MOVI r3,32
        CALL @nn_copy
        MOVI r1,@nn_addr
        MOVI r2,@nn_baddr
        MOVI r3,30
        CALL @nn_copy
        MOVI r1,@nn_route
        MOVI r2,@nn_broute
        MOVI r3,48
        CALL @nn_copy
        MOVI r1,@nn_dns
        MOVI r2,@nn_bdns
        MOVI r3,4
        CALL @nn_copy
        LD r4,r0,@nn_bwssid
        ST r4,r0,@nn_wifi_ssid
        LD r4,r0,@nn_brssi
        ST r4,r0,@nn_wifi_rssi
        MOVI r4,0
        MOVI r8,13
ncr_dl:
        CMP r4,r8
        JMP[EQ] @ncr_dd
        MOVI r5,3
        MUL r5,r4,r5
        MOVI r6,@nn_drv_reg
        ADD r5,r5,r6
        MOVI r6,@nn_bdrv
        ADD r6,r6,r4
        LD r7,r6,0
        ST r7,r5,2
        ADDI r4,r4,1
        JMP @ncr_dl
ncr_dd:
        LD r15,r0,@ncr_ret
        RET
ncr_ret:
.word 0

nn_tables_reset:
        ST r15,r0,@ntr_ret
        MOVI r4,@nn_addr
        MOVI r5,6
ntr_a:
        CMP r5,r0
        JMP[EQ] @ntr_ar
        ST r0,r4,4
        ADDI r4,r4,5
        MOVI r6,1
        SUB r5,r5,r6
        JMP @ntr_a
ntr_ar:
        MOVI r4,@nn_route
        MOVI r5,8
ntr_r:
        CMP r5,r0
        JMP[EQ] @ntr_rr
        ST r0,r4,5
        ADDI r4,r4,6
        MOVI r6,1
        SUB r5,r5,r6
        JMP @ntr_r
ntr_rr:
        MOVI r4,@nn_dns
        MOVI r5,2
ntr_d:
        CMP r5,r0
        JMP[EQ] @ntr_dd
        ST r0,r4,1
        ADDI r4,r4,2
        MOVI r6,1
        SUB r5,r5,r6
        JMP @ntr_d
ntr_dd:
        LD r15,r0,@ntr_ret
        RET
ntr_ret:
.word 0

; ---- config parser -------------------------------------------------------
nn_skipsp: ; r1 -> first non-space
        LDB r2,r1,0
        MOVI r3,32
        CMP r2,r3
        JMP[NE] @nss_d
        ADDI r1,r1,1
        JMP @nn_skipsp
nss_d:
        RET

nn_skiptok: ; r1 -> first space/newline/NUL after the current token
        LDB r2,r1,0
        CMP r2,r0
        JMP[EQ] @nkt_d
        MOVI r3,32
        CMP r2,r3
        JMP[EQ] @nkt_d
        MOVI r3,10
        CMP r2,r3
        JMP[EQ] @nkt_d
        ADDI r1,r1,1
        JMP @nn_skiptok
nkt_d:
        RET

nn_eol: ; r1 -> newline or NUL
        LDB r2,r1,0
        CMP r2,r0
        JMP[EQ] @ne_d
        MOVI r3,10
        CMP r2,r3
        JMP[EQ] @ne_d
        ADDI r1,r1,1
        JMP @nn_eol
ne_d:
        RET

nn_pnum: ; r1 ptr -> r1 value, r2 end, r3 0 ok / 1 bad
        BOOL.14 r6,r1,r0
        LDB r4,r6,0
        MOVI r5,48
        CMP r4,r5
        JMP[LT] @pn_bad
        MOVI r5,57
        CMP r5,r4
        JMP[LT] @pn_bad
        ADDI r6,r6,1
        LDB r4,r6,0
        MOVI r5,120
        CMP r4,r5
        JMP[EQ] @pn_hex
        MOVI r5,88
        CMP r4,r5
        JMP[EQ] @pn_hex
        MOVI r5,1
        SUB r6,r6,r5
        MOVI r7,0
pn_dl:
        LDB r4,r6,0
        MOVI r5,48
        CMP r4,r5
        JMP[LT] @pn_done
        MOVI r5,57
        CMP r5,r4
        JMP[LT] @pn_done
        MOVI r5,10
        MUL r7,r7,r5
        MOVI r5,48
        SUB r4,r4,r5
        ADD r7,r7,r4
        ADDI r6,r6,1
        JMP @pn_dl
pn_hex:
        MOVI r7,0
        ADDI r6,r6,1
pn_hl:
        LDB r4,r6,0
        MOVI r5,48
        CMP r4,r5
        JMP[LT] @pn_done
        MOVI r5,57
        CMP r5,r4
        JMP[LT] @pn_ha
        MOVI r5,48
        SUB r4,r4,r5
        JMP @pn_hadd
pn_ha:
        MOVI r5,65
        CMP r4,r5
        JMP[LT] @pn_hb
        MOVI r5,70
        CMP r5,r4
        JMP[LT] @pn_hb
        MOVI r5,55
        SUB r4,r4,r5
        JMP @pn_hadd
pn_hb:
        MOVI r5,97
        CMP r4,r5
        JMP[LT] @pn_done
        MOVI r5,102
        CMP r5,r4
        JMP[LT] @pn_done
        MOVI r5,87
        SUB r4,r4,r5
pn_hadd:
        SHL r7,r7,4
        ADD r7,r7,r4
        ADDI r6,r6,1
        JMP @pn_hl
pn_done:
        BOOL.14 r1,r7,r0
        BOOL.14 r2,r6,r0
        MOVI r3,0
        RET
pn_bad:
        MOVI r1,0
        BOOL.14 r2,r6,r0
        MOVI r3,1
        RET

nn_cfg_apply: ; r1 = byte address of .nancfg text
        ST r15,r0,@nca_ret
        BOOL.14 r11,r1,r0
        CALL @nn_cfg_snapshot
        CALL @nn_tables_reset
        ST r0,r0,@nn_cfgst
nca_line:
        LDB r4,r11,0
        CMP r4,r0
        JMP[EQ] @nca_commit
        MOVI r5,35
        CMP r4,r5
        JMP[EQ] @nca_eol
        MOVI r5,10
        CMP r4,r5
        JMP[EQ] @nca_adv
        MOVI r5,32
        CMP r4,r5
        JMP[EQ] @nca_adv
        BOOL.14 r8,r4,r0
        MOVI r5,97
        CMP r8,r5
        JMP[LT] @nca_uc
        MOVI r5,32
        SUB r8,r8,r5
nca_uc:
        ADDI r11,r11,1
        LDB r9,r11,0
        BOOL.14 r1,r11,r0
        CALL @nn_skiptok
        BOOL.14 r11,r1,r0
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        ST r1,r0,@nn_v1
        BOOL.14 r11,r2,r0
        MOVI r5,76
        CMP r8,r5
        JMP[EQ] @nca_l
        MOVI r5,65
        CMP r8,r5
        JMP[EQ] @nca_a
        MOVI r5,82
        CMP r8,r5
        JMP[EQ] @nca_r
        MOVI r5,68
        CMP r8,r5
        JMP[EQ] @nca_d
        MOVI r5,87
        CMP r8,r5
        JMP[EQ] @nca_w
        JMP @nca_bad
nca_d:
        MOVI r5,110
        CMP r9,r5
        JMP[EQ] @nca_n
        MOVI r5,78
        CMP r9,r5
        JMP[EQ] @nca_n
        MOVI r5,114
        CMP r9,r5
        JMP[EQ] @nca_v
        MOVI r5,82
        CMP r9,r5
        JMP[EQ] @nca_v
        JMP @nca_bad
nca_w:
        MOVI r5,115
        CMP r9,r5
        JMP[EQ] @nca_ws
        MOVI r5,83
        CMP r9,r5
        JMP[EQ] @nca_ws
        MOVI r5,106
        CMP r9,r5
        JMP[EQ] @nca_wj
        MOVI r5,74
        CMP r9,r5
        JMP[EQ] @nca_wj
        MOVI r5,108
        CMP r9,r5
        JMP[EQ] @nca_wl
        MOVI r5,76
        CMP r9,r5
        JMP[EQ] @nca_wl
        JMP @nca_bad
nca_ws:
        LD r1,r0,@nn_v1
        CALL @nn_wifi_scan
        JMP @nca_check
nca_wj:
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v2
        LD r1,r0,@nn_v1
        LD r2,r0,@nn_v2
        CALL @nn_wifi_join
        JMP @nca_check
nca_wl:
        LD r1,r0,@nn_v1
        CALL @nn_wifi_leave
        JMP @nca_check
nca_l:
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v2
        LD r1,r0,@nn_v1
        LD r2,r0,@nn_v2
        CALL @nn_link_set
        JMP @nca_check
nca_a:
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v2
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v3
        LD r1,r0,@nn_v1
        LD r2,r0,@nn_v2
        LD r3,r0,@nn_v3
        CALL @nn_addr_add
        JMP @nca_check
nca_r:
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v2
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v3
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v4
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v5
        LD r1,r0,@nn_v1
        LD r2,r0,@nn_v2
        LD r3,r0,@nn_v3
        LD r4,r0,@nn_v4
        LD r5,r0,@nn_v5
        CALL @nn_route_add
        JMP @nca_check
nca_n:
        LD r1,r0,@nn_v1
        CALL @nn_dns_add
        JMP @nca_check
nca_v:
        BOOL.14 r1,r11,r0
        CALL @nn_skipsp
        CALL @nn_pnum
        CMP r3,r0
        JMP[NE] @nca_bad
        BOOL.14 r11,r2,r0
        ST r1,r0,@nn_v2
        LD r1,r0,@nn_v1
        LD r2,r0,@nn_v2
        CALL @nn_drv_set
        JMP @nca_check
nca_check:
        CMP r3,r0
        JMP[NE] @nca_fail
        JMP @nca_eol
nca_eol:
        BOOL.14 r1,r11,r0
        CALL @nn_eol
        LDB r4,r1,0
        CMP r4,r0
        JMP[EQ] @nca_commit
        ADDI r11,r1,1
        JMP @nca_line
nca_adv:
        ADDI r11,r11,1
        JMP @nca_line
nca_commit:
        LD r4,r0,@nn_gen
        ADDI r4,r4,1
        ST r4,r0,@nn_gen
        MOVI r4,1
        ST r4,r0,@nn_cfgst
        MOVI r3,0
        LD r15,r0,@nca_ret
        RET
nca_bad:
        MOVI r3,1
nca_fail:
        ST r3,r0,@nn_estat
        CALL @nn_cfg_restore
        LD r3,r0,@nn_estat
        LD r15,r0,@nca_ret
        RET
nca_ret:
.word 0

nn_cfg_rollback:
        ST r15,r0,@ncb_ret
        CALL @nn_cfg_restore
        MOVI r3,0
        LD r15,r0,@ncb_ret
        RET
ncb_ret:
.word 0

nn_cfg_status:
        LD r1,r0,@nn_gen
        LD r2,r0,@nn_cfgst
        MOVI r3,0
        RET


; ---- identity / easter eggs ------------------------------------------------
; DHCP hostname, DNS HINFO/TXT text, QOTD quote and Finger banner.
nn_hostname:
.str "NaN0S"
nn_mac:
.str "02:4E:41:4E:30:53"
nn_hinfo_cpu:
.str "NaN-LATTICE"
nn_hinfo_os:
.str "NAN0S"
nn_txt:
.str "NaN0S: a temple that boots. NaN != NaN."
nn_qotd_text:
.str "There is a number that isn't one."
nn_finger_text:
.str "Login: nan0s  Name: NaN/0S  Project: NaN0S"
nn_qotd_port:
.word 17
nn_finger_port:
.word 79

nn_identity:
ST r15,r0,@ni_ret
MOVI r1,72
PUTC r1
MOVI r1,79
PUTC r1
MOVI r1,83
PUTC r1
MOVI r1,84
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,@nn_hostname
CALL @nn_puts
MOVI r1,32
PUTC r1
MOVI r1,@nn_mac
CALL @nn_puts
MOVI r1,10
PUTC r1
MOVI r1,72
PUTC r1
MOVI r1,73
PUTC r1
MOVI r1,78
PUTC r1
MOVI r1,70
PUTC r1
MOVI r1,79
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,@nn_hinfo_cpu
CALL @nn_puts
MOVI r1,32
PUTC r1
MOVI r1,@nn_hinfo_os
CALL @nn_puts
MOVI r1,10
PUTC r1
MOVI r1,84
PUTC r1
MOVI r1,88
PUTC r1
MOVI r1,84
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,@nn_txt
CALL @nn_puts
MOVI r1,10
PUTC r1
MOVI r1,81
PUTC r1
MOVI r1,79
PUTC r1
MOVI r1,84
PUTC r1
MOVI r1,68
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,@nn_qotd_text
CALL @nn_puts
MOVI r1,10
PUTC r1
MOVI r1,70
PUTC r1
MOVI r1,73
PUTC r1
MOVI r1,78
PUTC r1
MOVI r1,71
PUTC r1
MOVI r1,69
PUTC r1
MOVI r1,82
PUTC r1
MOVI r1,32
PUTC r1
MOVI r1,@nn_finger_text
CALL @nn_puts
MOVI r1,10
PUTC r1
LD r15,r0,@ni_ret
RET
ni_ret:
.word 0

; ---- browser service catalog (metadata only until TCP/TLS) ----------------
nn_svc_catalog:
.str "CHINET telnet://chinet.com:23\nTELEHACK telnet://telehack.com:23\nPROLINE telnet://prolinebbs.dyndns.org:23\nSDF telnet://sdf.org:23\nFLOODGAP gopher://gopher.floodgap.com:70\nFIDONET telnet://fidonet.org:23\nETERNAL-SEPTEMBER telnet://eternal-september.org:23\n"
nn_services:
ST r15,r0,@nsv_ret
MOVI r1,@nn_svc_catalog
CALL @nn_puts
LD r15,r0,@nsv_ret
RET
nsv_ret:
.word 0

.include "overbootstrap_route.nas"

.include "train_protocol.nas"
