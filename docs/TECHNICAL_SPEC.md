# Native Game Recompiler — Technical Specification

## Objective
An Android application that manages and launches legally obtained game builds through multiple independent execution paths: Wine compatibility, static recompilation and native ARM64 backends. NGR does not promise universal game compatibility.

## Runtime layers
1. Android host: lifecycle, storage, touch/gamepad, display and audio surfaces.
2. NGR Core (C++20): sessions, capability registry, logging and backend selection.
3. NGR Environment Manager: containers, Wine/Proton layers, CPU translators, graphics components, drivers and per-game profiles.
4. Execution backends: Wine compatibility, static recompilation or native ARM64.
5. Compatibility adapters: Windows/Linux OS APIs, libraries, syscalls and platform services.
6. Graphics providers: Android system Vulkan first; isolated per-game Turnip provider; DXVK/VKD3D only when compatible.
7. Package manager: manifest validation, hashes, install/update/remove and per-game configuration.
8. Compiler pipeline: binary inspection, lifting/IR, optimization, ARM64 code generation and title-specific runtime services.

## Execution modes
- **Wine compatibility:** Windows PE through a compatible Wine/Proton layer, CPU translation backend and selected graphics stack.
- **Static recompiled:** generated ARM64 code and NGR title runtime; Wine is not a prerequisite.
- **Native ARM64:** legally supplied native build or trusted app-integrated backend.

Imported Windows executables remain data until a compatible execution backend is installed and selected. Importing an EXE does not make it executable through NGR by itself.

## Environment Manager requirements
### Containers and Wine
- Container/rootfs and Wine prefix lifecycle: create, clone, backup, repair, reset and remove.
- Versioned Wine/Proton layer catalog with install, update, pin, rollback and compatibility checks.
- Windows version, architecture mode, resolution, DPI, display backend, audio/input and synchronization options.
- Container defaults plus per-game overrides for environment variables, DLL overrides and compatibility flags.
- Reproducible profile export/import and schema migration.

### CPU translation
- Box64 for supported standard x86_64 paths; Box86 only if required for a validated 32-bit path.
- FEXCore and FEX unix libraries for compatible ARM64EC paths.
- Optional WOWBox64 only for configurations that require it.
- Explicit routing and compatibility validation; Box64 and FEX are not interchangeable toggles.

### Graphics and drivers
- DXVK for supported Direct3D 8–11 paths.
- VKD3D-Proton for Direct3D 12; D8VK/D7VK are separate optional components.
- Turnip/Adreno through isolated provider loading; never overwrite Android system Vulkan libraries.
- X11 first. Wayland is enabled only when the selected Wine/Proton layer and game-side Vulkan/Turnip components satisfy the documented ABI and compositor requirements.
- Track exact component version, ABI, source, license, checksum, install state and conflicts.

### ReShade and shaders
- Separate audit of ReShade catalog availability vs actual Wine/Vulkan injection support.
- Versioned ReShade builds, compatible shader/effect packages, presets and texture assets.
- Per-game activation, preset, search paths and shader cache controls.
- Never claim support solely because a ReShade catalog file exists.

### Additional settings
Audio bridge/device selection, input/gamepad mapping, display controls, diagnostics, environment variables, cache management and safe rollback.

## Component catalog and installation security
Catalog entries must include stable ID/type, version/build/upstream commit, target ABI/container mode, download URL, archive format, expected SHA-256, license, source repository, compatibility constraints, conflicts, install layout and rollback metadata.

Treat catalog responses and archives as untrusted. Enforce HTTPS policy, size limits, safe archive paths, regular-file-only extraction, hashes and expected layouts before installation. Keep components in app-private managed locations. Imported NGRPACK game payloads are never backend plugins.

## Package format
NGRPACK is a ZIP-compatible container with a versioned manifest, payload and SHA-256 hashes. Metadata declares backend ID/version, target ABI, entry point, minimum runtime and graphics requirements. Define signed release trust before executing any payload.

The Kotlin package manager currently performs bounded extraction, hash validation, atomic install/update, rollback/recovery and inventory. It is a data installer, not a sandbox or execution backend.

## Milestones
- Stage 1–3: Android/JNI/C++ shell, Vulkan/input discovery, NGRPACK validation and package management — implemented and CI-built.
- Stage 4: trusted backend ABI negotiation, registry and lifecycle — pending.
- Stage 5: WinNative/Winlator runtime and Environment Manager source audit + port map.
- Stage 6: Wine + Box64 standard x86_64 launch path.
- Stage 7: FEXCore + ARM64EC path, after matching Wine layer validation.
- Stage 8: DXVK/VKD3D and isolated Turnip manager.
- Stage 9: ReShade/shader activation and validated Wayland support.
- Stage 10: N64/MIPS static recompilation proof of concept with N64Recomp and legal test inputs; expand compiler targets individually.

## Non-functional requirements
- ARM64 Android is the first host; Qualcomm Adreno is the first GPU target.
- Treat all packages, catalogs and downloaded components as untrusted.
- Keep Android system Vulkan intact.
- Produce actionable diagnostics and isolate backend failures where practical.
- Do not distribute copyrighted game data, ROMs, BIOS images, proprietary keys or game assets.
- Pin source revisions and review SPDX/transitive licenses before vendoring.
- CI publishes artifacts only after successful tests and builds.

## Backend plugin ABI v1
The public C contract is defined in `native/ngr-runtime/include/ngr/backend_abi_v1.h` and is ABI 1.0. It is for trusted execution backends only. The exported entry point is `ngr_backend_get_api_v1`; versioned structs use `struct_size`, API tables carry major/minor versions, and strings use explicit UTF-8 pointer/length views. The host API exposes bounded logging, package-handle-relative file access and monotonic time. The lifecycle table defines probe/create/load/start/request_stop/destroy.

N64Recomp is a build-time compiler, not a runtime plugin. Graphics-provider and compiler APIs require separate contracts. The header is compiled as C and C++, but host loader, ABI negotiation, backend registry binding and conformance tests are not implemented. The ABI is not a sandbox; NGRPACK payloads must never be loaded as plugins. Only trusted app-bundled or separately signature-verified backend libraries may be loaded after trust policy and loader validation.
