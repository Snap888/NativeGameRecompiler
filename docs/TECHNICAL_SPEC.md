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

The Kotlin validator checks an already-extracted package directory. A separate read-only NGRPACK ZIP inspector is now being implemented and CI-verified; it checks central-directory metadata, path safety, symlink/special-file metadata, entry and size limits, compression ratio and the bounded root manifest. It does not extract or install archives, verify extracted payload hashes, or create a security sandbox. Payload execution is prohibited until bounded extraction, atomic installation and a trust model are implemented.

## Milestones

- Stage 1: Android/JNI/C++ shell, Vulkan probe, controller discovery, CI APK.
- Stage 2: capability registry, extracted-package manifest validator, path/hash checks and JVM tests. CI run #38 passed and published the arm64 debug APK; device validation remains pending.
- Stage 3: read-only NGRPACK inspection is implemented with CI verification pending; bounded extraction, SHA-256 revalidation, atomic package installation and package inventory remain.
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


## Backend plugin ABI v1

The public C contract is defined in `native/ngr-runtime/include/ngr/backend_abi_v1.h` and is currently ABI 1.0. It is for trusted execution backends only. The exported entry point is `ngr_backend_get_api_v1`; versioned structs use `struct_size`, API tables carry major/minor versions, and strings use explicit UTF-8 pointer/length views. The host API exposes bounded logging, package-handle-relative file access and monotonic time. The lifecycle table defines probe/create/load/start/request_stop/destroy.

N64Recomp is treated as a build-time compiler, not as a runtime execution plugin. Graphics-provider and compiler APIs require separate contracts. The header is compiled as both C and C++ by the Android native build, but the host loader, ABI negotiation, backend registry binding and conformance tests are not implemented. The plugin ABI capability therefore remains planned. The ABI is not a sandbox; NGRPACK payloads must never be loaded as plugins. Only trusted app-bundled or separately signature-verified backend libraries may be loaded after trust policy and loader validation are implemented.
