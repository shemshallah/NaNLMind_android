#!/usr/bin/env python3
"""Write a minimal Android point-1 route diff.

This is a transport artifact, not a 2 GiB map. The bootstrap ring owns the map.
"""
import argparse
import hashlib
import json
import time
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="state/android-route.json")
    parser.add_argument("--base", type=int, default=0)
    parser.add_argument("--epoch", type=int, default=0)
    args = parser.parse_args()
    payload = {
        "universe": 0,
        "system_slot": 1,
        "layer": 0,
        "control_point": 1,
        "base_generation": args.base,
        "epoch": args.epoch or int(time.time()),
        "entries": [],
    }
    raw = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    payload["digest"] = hashlib.sha256(raw).hexdigest()
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2) + "\n")
    print(f"wrote {out} ({len(raw)} bytes, no 2 GiB map)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
