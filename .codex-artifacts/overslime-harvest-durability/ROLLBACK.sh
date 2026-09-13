#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
BASELINE="$SCRIPT_DIR/baseline/ToolDamageUtil.java"
TARGET="${1:-$SCRIPT_DIR/rollback-test/ToolDamageUtil.java}"

if [[ ! -f "$BASELINE" ]]; then
  printf 'baseline missing: %s\n' "$BASELINE" >&2
  exit 1
fi
mkdir -p "$(dirname -- "$TARGET")"
cp -- "$BASELINE" "$TARGET"
printf 'restored=%s\n' "$TARGET"
printf 'sha256=%s\n' "$(sha256sum "$TARGET" | awk '{print $1}')"
