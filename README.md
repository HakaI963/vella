# Vella

**Ghidra's decompiler engine, ported to Android.**

Vella is a native Android app that runs the C++ decompiler core from
[Ghidra](https://github.com/NationalSecurityAgency/ghidra) on arm64 Android
devices. Pick a binary, choose the processor, and get C-like decompilation
on the phone.

> Ghidra's desktop UI is Java/Swing and cannot run on Android. Vella ports the
> part that matters for decompilation: Ghidra's SLEIGH disassembler and
> decompiler pipeline. It is not a reimplementation and it does not use a
> remote server.

## What is inside

- Ghidra 12.2 decompiler C++ sources, compiled for `arm64-v8a` with the NDK.
- SLEIGH processor specifications for:
  - x86-64 (`x86:LE:64:default`)
  - x86 32-bit (`x86:LE:32:default`)
  - ARMv7 (`ARM:LE:32:v7`)
  - AArch64 (`AARCH64:LE:64:v8A`)
- A JNI bridge that creates a raw-binary architecture, follows control flow
  from the entry point, runs Ghidra's action pipeline, and prints C.
- A small Android UI: file picker, architecture/base/entry/size controls,
  ELF auto-detection, copy/share/save.

## Build

Everything is built on GitHub Actions. There is no local build step and no
prebuilt APK in the repository.

The workflow:

1. Checks out this repository.
2. Sparse-checks out Ghidra `Ghidra_12.2_build` (decompiler + processor specs).
3. Builds the host SLEIGH compiler and compiles `.slaspec` files into `.sla`.
4. Builds the Android native library with CMake/NDK.
5. Assembles, aligns, and signs the release APK.
6. Publishes `vella.apk` and its SHA-256 to a GitHub release.

Every push to `main` updates the `ci-latest` pre-release. A `v*` tag creates
a versioned release.

## Usage

1. Install the APK from Releases.
2. Tap **Choose binary** and select a raw binary or ELF file.
3. Vella fills in architecture, base address, entry offset, and size when it
   can parse the ELF header. Adjust them if needed.
4. Tap **Decompile**.

The app treats the file as a flat image mapped at the base address. For ELF
files it derives the base from the first `PT_LOAD` segment and the entry from
`e_entry`, which works for typical executables and shared objects. It does
not load symbols, relocations, or multiple segments; this is a raw-image
decompiler, not a full Ghidra program database.

## Limitations

- One function at a time, starting at the given entry offset.
- Raw image only: no imports, exports, relocations, or debug symbols.
- arm64 devices only.
- Large functions can take a while; analysis is bounded by the "bytes" field.
- Ghidra's Java analysis passes, GUI, and project database are not included.

## License

Vella is licensed under the Apache License 2.0. It links code from Ghidra,
also Apache 2.0. See `LICENSE` and `NOTICE`.
