#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GHIDRA="$ROOT/third_party/ghidra/Ghidra/Processors"
SLA="$ROOT/build/sla"
ASSETS="$ROOT/app/src/main/assets/ghidra"

rm -rf "$ASSETS"
mkdir -p "$ASSETS"

copy_specs() {
  local src="$1"
  cp "$src"/*.ldefs "$src"/*.pspec "$src"/*.cspec "$ASSETS"/
}

copy_specs "$GHIDRA/x86/data/languages"
copy_specs "$GHIDRA/ARM/data/languages"
copy_specs "$GHIDRA/AARCH64/data/languages"

cp "$SLA/x86.sla" "$SLA/x86-64.sla" "$SLA/ARM7_le.sla" "$SLA/AARCH64.sla" "$ASSETS"/

test -f "$ASSETS/x86.ldefs"
test -f "$ASSETS/x86.sla"
test -f "$ASSETS/x86-64.sla"
test -f "$ASSETS/ARM.ldefs"
test -f "$ASSETS/ARM7_le.sla"
test -f "$ASSETS/AARCH64.ldefs"
test -f "$ASSETS/AARCH64.sla"

du -sh "$ASSETS"
find "$ASSETS" -maxdepth 1 -type f | sort
