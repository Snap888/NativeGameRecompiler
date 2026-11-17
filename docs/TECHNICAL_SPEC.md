# Native Game Recompiler — Technical Specification

## Objective

An Android application that manages and launches legally obtained game builds produced for the NGR runtime. Backends declare supported input formats, CPU/OS ABI requirements, graphics requirements and package metadata.

## Runtime layers

1. Android host: lifecycle, storage, touch/gamepad, display and audio surfaces.
2. NGR Core (C++20): sessions, capability registry, logging and backend selection.
3. NGR ABI: files, clocks, threads, input, audio, display, memory and diagnostics.
4. Execution backends: static recompilation, dynamic translation or native ARM64.
5. Compatibility adapters: OS APIs, libraries, syscalls and platform services.
6. Graphics providers: system Vulkan first; isolated Turnip provider later; DXVK/VKD3D only when supported.
7. Package manager: manifest validation, hashes, install/update/remove and per-game configuration.

## Package format

Future NGRPACK packages use a ZIP-compatible container with a versioned manifest, payload and SHA-256 hashes. Metadata declares backend ID/version, target ABI, entry point, minimum runtime and graphics requirements. Signature verification is a later security gate; unsigned development packages must be clearly marked.

The current Kotlin validator checks an already-extracted package directory. It does not extract archives, install packages or create a security sandbox. Payload execution is prohibited until the archive extraction and trust model are implemented.

## Milestones

- Stage 1: Android/JNI/C++ shell, Vulkan probe, controller discovery, CI APK.
- Stage 2: capability registry, extracted-package manifest validator, path/hash checks and JVM tests. Initial implementation is committed; CI verification remains pending.
- Stage 3: secure NGRPACK inspection/extraction and atomic package installation.
- Stage 4: stable backend ABI, lifecycle and structured diagnostics.
- Stage 5: N64/MIPS static recompilation proof of concept using N64Recomp and legally supplied test inputs.
- Stage 6+: evaluate additional platform backends individually.

## Non-functional requirements

- ARM64 Android is the first host.
- Treat all package inputs as untrusted.
- Reject path traversal, symlink escapes, missing files and hash mismatches.
- Produce actionable diagnostics and isolate backend failures where practical.
- Do not distribute copyrighted ROMs, BIOS images, proprietary keys or game assets.
- CI publishes artifacts only after successful tests and builds.
