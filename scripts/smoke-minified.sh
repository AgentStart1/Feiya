#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 4 ]]; then
  echo "Usage: $0 SERIAL APK PACKAGE OUTPUT_DIRECTORY (use a disposable emulator)" >&2
  exit 2
fi
script_dir="$(cd "$(dirname "$0")" && pwd)"
exec "$script_dir/adb-device-lock.sh" run \
  --serial "$1" --project-dir "$PWD" --test-name "minified-startup-$3" \
  --max-timeout-seconds 300 --wait-timeout-seconds 300 -- \
  python3 "$script_dir/smoke-minified.py" "$@"
