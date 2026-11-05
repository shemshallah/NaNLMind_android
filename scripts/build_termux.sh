#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v clang++ >/dev/null 2>&1; then
  echo "clang++ missing; install it with: pkg install clang" >&2
  exit 1
fi
make core
echo "core built at .nanlmind/build/nanlm-universal"
