#!/usr/bin/env python3
"""Register and manage one NANNET device installation.

The generated secret is a temporary installation credential stored in a
0600 local state file. Native Android builds should move it into Android
Keystore before production use. No MAC, IMEI, serial, or Android ID is read.
"""
import argparse
import json
import os
import platform
import secrets
import stat
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit

TERMS = "nannet-device-registration-v1"
DEFAULT_STATE = Path.home() / ".config" / "nanlmind" / "nannet-device.json"


def state_path(value: str) -> Path:
    return Path(value).expanduser()


def read_state(path: Path) -> dict:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RuntimeError("device state file is unreadable; back it up before replacing") from exc
    if not isinstance(value, dict):
        raise RuntimeError("device state file must contain an object")
    return value


def write_state(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        path.parent.chmod(0o700)
    except OSError:
        pass
    temp = path.with_name(path.name + ".tmp")
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, path)
        path.chmod(0o600)
    finally:
        try:
            temp.unlink()
        except FileNotFoundError:
            pass


def system_report() -> dict:
    os_version = os.environ.get("NANLM_DEVICE_OS_VERSION", platform.release())
    report = {
        "platform": os.environ.get("NANLM_DEVICE_PLATFORM", platform.system() or "unknown"),
        "os_version": os_version or "unknown",
        "app_version": os.environ.get("NANLM_DEVICE_APP_VERSION", "0.1.0"),
        "abi": platform.machine() or "unknown",
        "cpu_cores": os.cpu_count() or 0,
        "capabilities": ["nannet-intake", "device-lease", "route-diff"],
    }
    try:
        with open("/proc/meminfo", "r", encoding="ascii") as stream:
            for line in stream:
                if line.startswith("MemTotal:"):
                    report["ram_mb"] = int(line.split()[1]) // 1024
                    break
    except (OSError, ValueError, IndexError):
        pass
    try:
        stats = os.statvfs(str(Path.home()))
        report["storage_free_mb"] = (stats.f_bavail * stats.f_frsize) // (1024 * 1024)
    except OSError:
        pass
    return report


def request(base: str, path: str, token: str = "", payload: dict | None = None) -> dict:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    headers = {"Accept": "application/json", "User-Agent": "nanlmind-device/0.1"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base.rstrip("/") + path, data=body, headers=headers, method="GET" if body is None else "POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read(65536)
    except urllib.error.HTTPError as exc:
        raw = exc.read(65536)
        try:
            message = json.loads(raw.decode("utf-8")).get("error", "HTTP " + str(exc.code))
        except Exception:
            message = "HTTP " + str(exc.code)
        raise RuntimeError(message) from exc
    value = json.loads(raw.decode("utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("NANNET returned an invalid response")
    return value


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="NANNET device registration and lease manager")
    parser.add_argument("--url", default=os.environ.get("NANNET_URL", os.environ.get("NANLM_INTAKE_URL", "")))
    parser.add_argument("--state", default=os.environ.get("NANLM_DEVICE_STATE", str(DEFAULT_STATE)))
    sub = parser.add_subparsers(dest="command", required=True)
    register = sub.add_parser("register", help="claim the next free HW slot")
    register.add_argument("--consent", action="store_true", help="confirm device registration and capability reporting")
    sub.add_parser("heartbeat", help="renew the active device lease")
    sub.add_parser("release", help="release the slot after pending work is complete")
    report = sub.add_parser("report", help="submit a device or system issue")
    report.add_argument("--type", choices=("device_error", "system_error", "network_error", "security_concern", "other"), default="device_error")
    report.add_argument("--summary", required=True)
    report.add_argument("--details", default="")
    args = parser.parse_args(argv)

    parsed = urlsplit(args.url)
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
        print("NANNET_URL must be a public HTTPS origin", file=sys.stderr)
        return 2
    path = state_path(args.state)
    try:
        state = read_state(path)
        if args.command == "register":
            if not args.consent:
                print("Registration requires --consent after reviewing the system report.", file=sys.stderr)
                return 2
            if state.get("token"):
                # Re-running registration resumes this installation and rotates
                # its bearer atomically; it must not claim a second HW slot.
                result = request(args.url, "/v1/devices/rotate", state["token"], {})
                state.update({k: result[k] for k in ("address", "lease_id", "lease_expires_at", "token")})
                write_state(path, state)
                print(json.dumps({k: result[k] for k in ("address", "lease_id", "lease_expires_at")}, indent=2))
            else:
                secret = state.get("device_secret") or secrets.token_urlsafe(32)
                state["device_secret"] = secret
                write_state(path, state)
                result = request(args.url, "/v1/devices/register", payload={
                    "device_secret": secret,
                    "consent": True,
                    "terms_version": TERMS,
                    "system_report": system_report(),
                })
                state.update({k: result[k] for k in ("device_id", "address", "lease_id", "lease_expires_at", "token")})
                write_state(path, state)
                print(json.dumps({k: result[k] for k in ("device_id", "address", "lease_id", "lease_expires_at", "head_count")}, indent=2))
        else:
            token = state.get("token", "")
            if not token:
                raise RuntimeError("no active device token; run register --consent first")
            if args.command == "heartbeat":
                result = request(args.url, "/v1/devices/heartbeat", token, {})
                state["lease_expires_at"] = result["lease_expires_at"]
                write_state(path, state)
                print(json.dumps(result, indent=2))
            elif args.command == "release":
                result = request(args.url, "/v1/devices/release", token, {})
                state.pop("token", None)
                state.pop("lease_id", None)
                state.pop("lease_expires_at", None)
                state["last_released_address"] = result["address"]
                state.pop("address", None)
                write_state(path, state)
                print(json.dumps(result, indent=2))
            elif args.command == "report":
                result = request(args.url, "/v1/devices/report", token, {
                    "report_type": args.type,
                    "summary": args.summary,
                    "details": args.details,
                    "system_report": system_report(),
                })
                print(json.dumps(result, indent=2))
        return 0
    except (OSError, RuntimeError, KeyError, ValueError) as exc:
        print("NANNET device error: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
