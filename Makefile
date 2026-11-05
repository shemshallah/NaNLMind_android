CORE ?= .nanlmind
CORE_REPO ?= https://github.com/shemshallah/NaNLMind.git
BUILD := build

.PHONY: all core bootstrap verify route-diff clean

all: core

$(CORE):
	git clone --depth 1 $(CORE_REPO) $(CORE)

core: $(CORE)
	$(MAKE) -C $(CORE) -j2

bootstrap: core
	./$(CORE)/build/nanlm-universal bootstrap data/alice_full.txt 1 8

verify: core
	./$(CORE)/build/nanlm-universal verify

route-diff: core
	python3 scripts/route_diff.py --out state/android-route.json

clean:
	rm -rf $(CORE) $(BUILD) state
