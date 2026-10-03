#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 4 ]]; then
  echo "Usage: $0 SERIAL APK PACKAGE OUTPUT_DIRECTORY (use a disposable emulator)" >&2
  exit 2
fi
script_dir="$(cd "$(dirname "$0")" && pwd)"
exec python3 "$script_dir/smoke-minified.py" "$@"
