# Xbox 360 backend — XDroid360 integration audit

## Source and pin

- Android fork: https://github.com/Snap888/XDroid360
- Upstream project: https://github.com/udarmolota/XDroid360
- Pinned fork commit: `f388d11fc207502cd5975899c8adc06600cfe34f`
- Submodule path: `native/recompilers/XDroid360`
- Fork's own license: MIT (`LICENSE`); retain all upstream notices and third-party licenses.
- The nested `recomp` submodule points to `udarmolota/Dragon-Age-Origins-Recomp-Android` at `9726e79a5ef747ba8085984d3848d792b00ee57b`. Recursive submodule initialization is required.
- The distribution is not MIT-only: `NOTICE.md` documents GPL-3.0 native renderer/profile obligations, XenonRecomp GPL-2.0-or-later (GNU binutils disassembler), BSD-3-Clause Xenia, LGPL FFmpeg and other components. Keep the full license/provenance tree. NGR's GPL-3.0 distribution is compatible with GPL-2.0-or-later; do not relicense third-party files.

## What the backend actually does

XDroid360 is a title-specific static-recompilation pipeline for Xbox 360 PowerPC Xenon:

1. Accepts selected user game content (XISO/ISO, supported STFS packages/folder, or ZIP).
2. Extracts XEX and game data using XISO/STFS readers and XenonUtils.
3. Validates a supported title/version against a game profile and hashes such as `default.xex`.
4. Runs a patched XenonRecomp (XenonRecomp upstream commit `ddd128bcca99fe8bfbb99bea583c972351fa6ace`, with the patch referenced in NOTICE) to translate PowerPC instructions to C++.
5. Builds generated C++ for Android ARM64 with the bundled Clang/LLD toolchain.
6. Links generated code with a title-specific runtime/profile.
7. Uses a title runtime for Xbox kernel/XAM, memory, threads, VFS, audio/XMA, GPU calls and game hooks.
8. Uses the native Vulkan renderer and/or Xenia's Vulkan GPU backend; XenosRecomp + DXC translate game shaders to SPIR-V.

The Android front end is Java + SDL3 and is not the part NGR should duplicate. NGR should reuse its existing library, shortcut settings, input, audio, Vulkan/driver and logging systems.

## Supported-title boundary

The checked-in README for XDroid360 alpha 0.1.2 names only:
- Dragon Age: Origins — supported disc editions are validated by title-specific metadata/hash.
- State of Decay — supported console folder/STFS package layout and matching profile.

Each title has its own recipe and runtime. DAORecomp is the runtime for Dragon Age: Origins; SoDRecomp is derived from it. This is not a generic Xbox 360 runtime and the existence of an XEX loader or XenonRecomp does not make arbitrary XEX files runnable. Other profiles may exist in upstream/fetched toolchain repositories but are not to be marked supported until included, version-matched and tested end-to-end.

## Integration strategy in NGR

- Keep this project pinned as an isolated backend source, not as a second Android application.
- Reuse only backend layers: XenonRecomp + its patches, XenonUtils/XEX, recipe/profile model, Clang/LLD toolchain packaging, per-title runtime, Xenia Vulkan/Xenos shader tooling where required.
- Do not copy XDroid360 launcher Activities, instance library, settings screens, SDL activity, touch overlay, controller manager, driver manager or generic Android lifecycle. Those capabilities already exist in WinNative/NGR.
- Adapt the build recipe runner to NGR's managed compiler jobs: bounded extraction, disk estimates, cancellation, resumable step journal, per-step logs, hashes and strict profile/toolchain compatibility checks.
- Register an NGR game shortcut only after a profile validates the user's input and the ARM64 library is linked and package validation passes. A generated source folder is not a runnable game.
- The runtime needs an explicit NGR host adapter for Android filesystem/storage, Vulkan surface, audio, controller input, lifecycle and per-game settings. Do not start a second SDL activity or a second Vulkan/driver stack.
- Initially expose Xbox 360 as an experimental backend with title allow-list. First end-to-end target: Dragon Age: Origins or State of Decay using a legally supplied user dump and its matching public profile. Expand only after the first title boots and passes input/audio/save/render checks.

## Build and validation gates

1. Recursive submodules resolve at the pinned commits, including nested `recomp`.
2. Audit and preserve every license; record all source commits and patch hashes.
3. Build XenonRecomp and its Android/host tests in CI, then validate XEX parsing on synthetic/redistributable fixtures.
4. Build and package the ARM64 toolchain (Clang, LLD, compiler-rt, libc++ and XenonRecomp executable/library) without embedding game data.
5. Port recipe/profile execution to NGR's job/log/shortcut infrastructure.
6. Build one title runtime and generated library for ARM64; verify ABI and all required exported symbols.
7. Connect the existing WinNative Vulkan surface, audio and input systems through a dedicated host adapter.
8. Device-test a legally supplied supported title; only then enable Launch and call the backend playable.

## Legal and security constraints

- Never commit ROMs, ISOs, XEX files, title updates, keys, BIOS/system images, save files or copyrighted test assets.
- Treat game archives and recipes as untrusted input: path traversal checks, bounded file counts/expanded bytes, hash validation, no shell execution, argument arrays, process cancellation and private app storage.
- Preserve source notices, exact dependency pins and GPL source-offer requirements for distributed combined builds.
