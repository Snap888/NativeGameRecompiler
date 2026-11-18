# N64Recomp integration

## Pinned upstream

- Project: https://github.com/N64Recomp/N64Recomp
- Pinned commit: `598ce2080004a5672d604dceadbda436045307b7`
- License: MIT (upstream LICENSE retained in the submodule).
- Location: `native/recompilers/N64Recomp`.
- The upstream repository is a git submodule. Its own dependency submodules are initialized recursively by the integration workflow.

## Scope

N64Recomp is the first static-recompilation frontend in NGR. It accepts an N64/MIPS binary plus ELF/symbol metadata and emits C code. That generated code must then be compiled for Android ARM64 and linked with a compatible N64 title runtime. The recompiler alone is not a complete playable game port.

No ROM, ELF, proprietary game assets or copyrighted test title is included in this repository. Test fixtures must be synthetic or legally redistributable. User-supplied game data remains private package data and is never loaded as a backend plugin.

## Current milestone

- [x] Pin upstream source and preserve its MIT license.
- [x] Add recursive submodule initialization to the WinNative+NGR build path.
- [ ] Build N64Recomp and RSPRecomp in CI from the pinned source.
- [ ] Expose a bounded NGR compiler job API through JNI.
- [ ] Compile generated C/C++ with the Android NDK for `arm64-v8a`.
- [ ] Link generated objects with an NGR N64 title runtime and platform services.
- [ ] Package the result as a signed/validated NGR native game package.
- [ ] Execute a synthetic or legally distributable end-to-end test on Android.

The host-tool CI build is a reproducibility gate only; it does not claim that the compiler is embedded in the APK or that an N64 game can already be launched.

## Android integration design

1. **Inspect**: identify ROM byte order/size, ELF architecture and symbol/section metadata; reject unsupported or malformed inputs before work begins.
2. **Plan**: resolve output directory inside app-private compiler workspace; enforce file count, byte, CPU-time and cancellation limits.
3. **Recompile**: invoke N64Recomp core APIs (not an untrusted package shared library) to emit source and metadata.
4. **RSP**: invoke RSPRecomp only when the title configuration provides supported RSP microcode metadata.
5. **Compile**: use the NDK Clang toolchain for Android ARM64; never execute arbitrary shell text from the game package.
6. **Link**: combine generated objects with a separately versioned NGR N64 runtime and required Android/Vulkan/audio/input adapters.
7. **Validate**: hash all outputs, record upstream/compiler/runtime versions and produce an NGR package manifest.
8. **Run**: hand the validated native package to a trusted built-in static backend; Wine/Box64/FEX are not prerequisites.

## Important upstream constraints

- N64Recomp currently relies on ELF/symbol metadata to identify functions; a ROM-only workflow is not assumed.
- Recompiled output depends on runtime macros/services and title-specific platform work.
- TLB relocation support and some modern compiler output cases may remain limited upstream.
- N64/MIPS support is a narrow proof of concept, not universal game recompilation.

## License and provenance

N64Recomp is MIT-licensed. Its dependency submodules keep their own licenses and provenance. Do not flatten or remove their license files. NGR's combined distribution uses the WinNative GPL-3.0 host license while preserving the MIT N64Recomp notices and each dependency's license obligations.


## Android integration — first implementation

- Added the pinned N64Recomp source directory to the Android CMake graph with upstream CLI tools excluded from the APK build.
- Linked the upstream `N64Recomp` and `N64RecompElf` core libraries into the app-private `ngr_runtime` JNI library.
- Added a JNI ELF inspection entry point using `N64Recomp::Context::from_elf_file`. It enforces a 64-byte..512-MiB input bound and reports parsed sections, executable sections, functions and relocations.
- Added an English/Russian N64 Recompiler screen with SAF import into app-private storage and a language toggle.
- This is a real ELF-analysis stage. C source generation, NDK compilation, title runtime and launch are not yet complete.
