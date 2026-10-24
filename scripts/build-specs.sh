#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GHIDRA="$ROOT/third_party/ghidra"
CPP="$GHIDRA/Ghidra/Features/Decompiler/src/decompile/cpp"
OUT="$ROOT/build/sla"

if [[ ! -d "$CPP" ]]; then
  echo "Ghidra sources are missing at $CPP" >&2
  exit 1
fi

mkdir -p "$OUT"

echo "Building SLEIGH compiler (host)"
make -C "$CPP" -j"$(nproc)" sleigh_opt CXX="g++ -std=c++17"

compile() {
  local spec="$1"
  local name="$2"
  echo "Compiling $name"
  "$CPP/sleigh_opt" "$spec" "$OUT/$name.sla"
}

X86="$GHIDRA/Ghidra/Processors/x86/data/languages"
ARM="$GHIDRA/Ghidra/Processors/ARM/data/languages"
A64="$GHIDRA/Ghidra/Processors/AARCH64/data/languages"

compile "$X86/x86.slaspec" x86
compile "$X86/x86-64.slaspec" x86-64
compile "$ARM/ARM7_le.slaspec" ARM7_le
compile "$A64/AARCH64.slaspec" AARCH64

ls -lh "$OUT"
