# NGR — Next Actions

Mark complete only when evidence exists.

## P0 — Device validation
- [x] Restore Stage 2 CI: JDK 17, Android SDK, NDK/CMake, Gradle, JVM tests and arm64-v8a APK all pass.
- [x] Confirm NGR-arm64-debug artifact upload in Actions run #129.
- [ ] Install latest diagnostics APK on Snapdragon/Adreno Android device.
- [ ] Verify app launch, Vulkan enumeration and controller hotplug.
- [x] Exercise folder/ordinary ZIP import, install, update, remove and inventory on device; Lethal Company imports pass with correct Windows x86_64 entry point.
- [ ] Exercise restart recovery and multi-EXE chooser on device.

## P1 — NGRPACK trust and security
- [x] Strict manifest/schema, path/hash checks, ZIP inspection, bounded extraction, SHA-256 validation, atomic install/update and rollback/recovery.
- [x] Folder/ordinary ZIP import, generated manifest, executable detection, candidate chooser and package Details metadata.
- [ ] Review local-header vs central-directory consistency, malformed UTF-8 names and archive edge cases.
- [ ] Define manifest version migration.
- [ ] Define unsigned development vs signed release trust policy and signature verification before any code execution.

## P2 — Backend ABI and runtime
- [x] Define ABI v1 C contract and compile-check public header as C and C++.
- [ ] Implement host-side ABI negotiation and validation (major/minor, struct sizes, IDs, required callbacks).
- [ ] Add trusted built-in/test backend fixture; never load NGRPACK game payloads as plugins.
- [ ] Implement backend registry and lifecycle with conformance tests.
- [ ] Add native crash/error reporting and cleanup rules.

## P3 — WinNative runtime and Environment Manager
- [x] Record target architecture and donor integration plan in docs/WINNATIVE_INTEGRATION_PLAN.md.
- [ ] Complete file-by-file source audit of WinNative container model/manager, launch pipeline, Wine/Proton layer resolver, WCP installer, catalog parser and environment variable UI.
- [ ] Audit Box64/Box86 presets, FEXCore/FEX unixlibs, ARM64EC routing and WOWBox64 compatibility.
- [ ] Audit DXVK/VKD3D/D8VK/D7VK selection and component install/rollback.
- [ ] Audit AdrenoTools/Turnip provider selection and Vulkan isolation.
- [ ] Audit ReShade injection, shader catalogs, presets and texture packages; distinguish catalog availability from runtime support.
- [ ] Review GPL-3.0 and every transitive component license; confirm NGR licensing strategy before copying donor code.
- [ ] Pin donor commit and create component provenance/SPDX inventory.
- [ ] Design versioned component catalog model with ABI, hashes, license, constraints, install layout and rollback metadata.
- [ ] Implement container profiles, Wine prefix lifecycle and global/per-game override schema.
- [ ] Implement secure catalog download, archive inspection, install, activation, rollback and uninstall.
- [ ] Integrate Wine + Box64 standard x86_64 path and launch a legal test executable.
- [ ] Add ARM64EC + FEXCore path using a matching Wine/Proton layer.
- [ ] Add DXVK/VKD3D and isolated Turnip provider.
- [ ] Add ReShade/shader activation after injection compatibility is verified.
- [ ] Add Wayland only for validated Wine/Proton + Wayland Turnip combinations; X11 first.

## P4 — Static recompiler
- [ ] Pin N64Recomp upstream commit and review SPDX/transitive licenses.
- [ ] Obtain legally distributable test input and metadata.
- [ ] Build toolchain reproducibly in CI.
- [ ] Generate C/C++ from test input and compile for Android ARM64.
- [ ] Implement only runtime services required by that test.
- [ ] Validate correctness before performance tuning.
- [ ] Keep static-recompiled backend independent of Wine and the compatibility environment.

## P5 — Graphics, input and audio
- [ ] Add Vulkan extension/feature report.
- [ ] Evaluate GameActivity and Oboe when render/audio loops exist.
- [ ] Add actual touch mapping after first runnable backend.

## Definition of done
- Code committed to correct repository and branch.
- Tests/build run and result recorded.
- Development log and this list updated.
- Distinguish implemented, partially implemented, planned and verified.
- No fabricated success claims or unverified APK links.

## Current validation gate
- [x] CI run #129 (code/test commit `6b546a682a316cdc96a1d96c96065ebe6599be14`) passes JVM tests, arm64-v8a APK build and artifact upload; artifact ID `14371675507`.
- [ ] Device-test executable chooser with a multi-EXE game archive and verify Details for Lethal Company reports `Lethal Company.exe`, Windows, x86_64.
