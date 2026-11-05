#!/usr/bin/env python3
"""Submit a route diff to the scoped intake endpoint.

Never uses DEEPREPO_PAT or the bootstrap ring secret.
"""
import argparse
import json
import os
import urllib.request
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("diff", nargs="?", default="state/android-route.json")
    parser.add_argument("--url", default=os.environ.get("NANLM_INTAKE_URL", ""))
    args = parser.parse_args()

    token = os.environ.get("NANLM_DEVICE_TOKEN", "")
    if not args.url or not token:
        print("NANLM_INTAKE_URL and NANLM_DEVICE_TOKEN are required", flush=True)
        print("DEEPREPO_PAT is never used by this client", flush=True)
        return 2
    payload = Path(args.diff).read_bytes()
    req = urllib.request.Request(
        args.url.rstrip("/") + "/v1/intake/route",
        data=payload,
        method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "application/json",
            "User-Agent": "nanlmind-android",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        print(response.status, response.read().decode("utf-8", "replace"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
