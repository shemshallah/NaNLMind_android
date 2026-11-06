#include "../core/nan_net_driver.h"
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

int main(void) {
    uint8_t frame[38] = {
        'N','1','6','T', 1,0, 1,0,
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
        1,0,0,0, 2,0,0,0, 2,0, 0xB7,0xF5,0xAD,0x6D
    };
    uint8_t received[64] = {0}, copied[64] = {0};
    size_t length = 0;
    int sv[2];
    if (socketpair(AF_UNIX, SOCK_STREAM, 0, sv)) return 1;
    if (nan_net_send_frame_fd(sv[0], frame, sizeof frame)) return 2;
    if (nan_net_recv_frame_fd(sv[1], received, sizeof received, &length)) return 3;
    close(sv[0]); close(sv[1]);
    if (length != sizeof frame || memcmp(frame, received, sizeof frame)) return 4;
    nan_vm *vm = nan_create();
    if (!vm) return 5;
    if (nan_net_stage_frame(vm, 131072u, received, length)) return 6;
    if (nan_net_extract_frame(vm, 131072u, copied, sizeof copied, length)) return 7;
    if (memcmp(received, copied, length)) return 8;
    if (nan_net_stage_frame(vm, 131072u, received, NAN_NET_MAX_FRAME + 1u) == 0) return 9;
    nan_destroy(vm);
    puts("NaN16 C network boundary: PASS");
    return 0;
}
