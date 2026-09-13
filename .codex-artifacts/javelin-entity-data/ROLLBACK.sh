#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BASELINE_FILE="$SCRIPT_DIR/baseline/ThrownTool.java"
TARGET_FILE="${1:-$SCRIPT_DIR/rollback-test/ThrownTool.java}"

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "baseline file not found: $BASELINE_FILE" >&2
  exit 2
fi
mkdir -p "$(dirname -- "$TARGET_FILE")"
cp -- "$BASELINE_FILE" "$TARGET_FILE"
printf 'ROLLBACK_OK target=%s sha256=' "$TARGET_FILE"
sha256sum -- "$TARGET_FILE" | awk '{print $1}'
printf '\n'
