#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GHIDRA="$ROOT/third_party/ghidra/Ghidra/Processors"
SLA="$ROOT/build/sla"
ASSETS="$ROOT/app/src/main/assets/ghidra"

rm -rf "$ASSETS"
mkdir -p "$ASSETS/x86" "$ASSETS/ARM" "$ASSETS/AARCH64"

copy_specs() {
  local src="$1"
  local dst="$2"
  cp "$src"/*.ldefs "$src"/*.pspec "$src"/*.cspec "$dst"/
}

copy_specs "$GHIDRA/x86/data/languages" "$ASSETS/x86"
copy_specs "$GHIDRA/ARM/data/languages" "$ASSETS/ARM"
copy_specs "$GHIDRA/AARCH64/data/languages" "$ASSETS/AARCH64"

cp "$SLA/x86.sla" "$SLA/x86-64.sla" "$ASSETS/x86/"
cp "$SLA/ARM7_le.sla" "$ASSETS/ARM/"
cp "$SLA/AARCH64.sla" "$ASSETS/AARCH64/"

test -f "$ASSETS/x86/x86.ldefs"
test -f "$ASSETS/x86/x86.sla"
test -f "$ASSETS/x86/x86-64.sla"
test -f "$ASSETS/ARM/ARM.ldefs"
test -f "$ASSETS/ARM/ARM7_le.sla"
test -f "$ASSETS/AARCH64/AARCH64.ldefs"
test -f "$ASSETS/AARCH64/AARCH64.sla"

du -sh "$ASSETS"
find "$ASSETS" -maxdepth 2 -type f | sort
