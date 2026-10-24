#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CPP="$ROOT/third_party/ghidra/Ghidra/Features/Decompiler/src/decompile/cpp"
OUT="$ROOT/build/host"
SPECS="$ROOT/app/src/main/assets/ghidra"

if [[ ! -d "$CPP" ]]; then
  echo "Ghidra sources are missing at $CPP" >&2
  exit 1
fi
if [[ ! -f "$SPECS/x86.ldefs" ]]; then
  echo "Ghidra assets are missing; run stage-assets.sh first" >&2
  exit 1
fi

mkdir -p "$OUT"

SOURCES=()
for file in "$CPP"/*.cc; do
  name="$(basename "$file")"
  case "$name" in
    analyzesigs.cc|bfd_arch.cc|codedata.cc|loadimage_bfd.cc|callgraph.cc|ifacedecomp.cc|ifaceterm.cc|interface.cc|rulecompile.cc|testfunction.cc|unify.cc|consolemain.cc|sleighexample.cc|test.cc|slgh_compile.cc|slghparse.cc|slghscan.cc|*ghidra*.cc)
      continue
      ;;
  esac
  SOURCES+=("$file")
done

echo "Compiling host engine (${#SOURCES[@]} sources)"
g++ -std=c++17 -O1 -I"$CPP" -I"$ROOT/app/src/main/cpp" \
  "$ROOT/app/src/main/cpp/vella_core.cc" \
  "$ROOT/tools/vella_cli.cc" \
  "${SOURCES[@]}" \
  -lz -o "$OUT/vella_cli"

# mov eax, 42; ret
printf '\xb8\x2a\x00\x00\x00\xc3' > "$OUT/test-x64.bin"
# mov w0, #42; ret
printf '\x40\x05\x80\x52\xc0\x03\x5f\xd6' > "$OUT/test-a64.bin"

echo "Decompiling x86-64 test function"
"$OUT/vella_cli" "$OUT/test-x64.bin" x86:LE:64:default 0x100000 0 0x10 test_func "$SPECS" \
  | tee "$OUT/test-x64.c"
grep -q "test_func" "$OUT/test-x64.c"
grep -Eqi "0x2a|42" "$OUT/test-x64.c"

echo "Decompiling AArch64 test function"
"$OUT/vella_cli" "$OUT/test-a64.bin" AARCH64:LE:64:v8A 0x400000 0 0x10 test_func "$SPECS" \
  | tee "$OUT/test-a64.c"
grep -q "test_func" "$OUT/test-a64.c"
grep -Eqi "0x2a|42" "$OUT/test-a64.c"

echo "Engine smoke test passed"
