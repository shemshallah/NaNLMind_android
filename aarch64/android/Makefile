CC ?= cc
CFLAGS ?= -std=gnu99 -O2 -Wall -Wno-misleading-indentation -ffp-contract=off -fno-fast-math
SRC = core/nan16.c core/nan_dev.c core/nan_load.c core/nan_render.c core/nan_state.c core/nan_net_driver.c
all: nan_cli
nan_cli: $(SRC) tools/nan_cli.c core/*.h core/*.inc
	$(CC) $(CFLAGS) $(SRC) tools/nan_cli.c -o $@ -lm
# default NaNFS volume shipped in the APK assets; the app copies it to filesDir once and
# attaches it over a persistent fd (guest reads/writes it over ATA PIO; SAF "Disk" picks
# any other volume from Android storage). Seed content = the nfix fixpoint source.
volume:
	sh ../../tools/nmkfs.sh src/tests/nfix_test.nas aarch64/android/app/src/main/assets/disk.nanfs
test: tests/test_core tests/test_nannet_driver
	./tests/test_core
	./tests/test_nannet_driver
tests/test_core: $(SRC) tests/test_core.c core/*.h core/*.inc
	$(CC) $(CFLAGS) $(SRC) tests/test_core.c -o $@ -lm
clean:
	rm -f nan_cli tests/test_core tests/test_core_asan tests/test_nannet_driver
asan: $(SRC) tests/test_core.c core/*.h core/*.inc
	$(CC) -std=gnu99 -O1 -g -Wno-misleading-indentation -fsanitize=address,undefined -fno-sanitize-recover=undefined -ffp-contract=off $(SRC) tests/test_core.c -o tests/test_core_asan -lm
	./tests/test_core_asan
# Gate: every cartridge must still load after any other one has run (catches store/SLOT overlap -> SEAL MISMATCH).
# Boot now lands in the GUI (one CLI window): the first ESCs close it and reach the museum menu.
seals: nan_cli
	@bad=0; for a in 1 2 3 4 5 6 7 8; do for b in 1 2 3 4 5 6 7 8; do \
	  o=$$(./nan_cli --img app/src/main/assets/nan0s.img --dump-text --keys "ESC,ESC,ESC,ESC,ESC,$$a,ESC,$$b" --steps 6000000 2>&1); \
	  case "$$o" in *"SEAL MISMATCH"*) echo "FAIL $$a then $$b"; bad=1;; esac; done; done; \
	  [ $$bad = 0 ] && echo "seals: 64 pairs OK"

tests/test_nannet_driver: $(SRC) tests/test_nannet_driver.c core/*.h core/*.inc
	$(CC) $(CFLAGS) $(SRC) tests/test_nannet_driver.c -o $@ -lm
