#!/usr/bin/env bash
set -euo pipefail
base="/d/code/mcmod/TinkersConstruct-1.21.1/.codex-artifacts/overslime-harvest-durability/baseline/ToolDamageUtil.java"
work="/d/code/mcmod/TinkersConstruct-1.21.1/.codex-artifacts/overslime-harvest-durability/work/ToolDamageUtil.java"
target="/d/code/mcmod/TinkersConstruct-1.21.1/.codex-artifacts/overslime-harvest-durability/rollback-test/ToolDamageUtil.java"
rollback="/d/code/mcmod/TinkersConstruct-1.21.1/.codex-artifacts/overslime-harvest-durability/ROLLBACK.sh"
cp -- "$work" "$target"
before=$(sha256sum "$target" | awk '{print $1}')
"$rollback"
after=$(sha256sum "$target" | awk '{print $1}')
expected=$(sha256sum "$base" | awk '{print $1}')
printf 'before=%s\nafter=%s\nexpected=%s\n' "$before" "$after" "$expected"
test "$after" = "$expected"
printf 'result=PASS\n'
