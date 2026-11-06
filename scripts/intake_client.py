#!/usr/bin/env python3
"""Submit a route diff to the scoped intake endpoint.

Never uses DEEPREPO_PAT or the bootstrap ring secret.
"""
import argparse
import json
import os
import urllib.request
from urllib.parse import urlsplit
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("diff", nargs="?", default="state/android-route.json")
    parser.add_argument("--url", default=os.environ.get("NANLM_INTAKE_URL", ""))
    args = parser.parse_args()

    token = os.environ.get("NANLM_DEVICE_TOKEN", "")
    address = os.environ.get("NANLM_DEVICE_ADDRESS", "nan://GM0:SC0:MC0:BS0:HW1")
    parsed = urlsplit(args.url)
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
        print("NANLM_INTAKE_URL must be a public HTTPS origin", flush=True)
        return 2
    if not token or not address.startswith("nan://"):
        print("NANLM_DEVICE_TOKEN and NANLM_DEVICE_ADDRESS are required", flush=True)
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
            "X-NaN-Address": address,
            "User-Agent": "nanlmind-android",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        print(response.status, response.read().decode("utf-8", "replace"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
