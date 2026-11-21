# Native Game Recompiler — Technical Specification

## Objective
An Android application focused on preparing and launching legally obtained Xbox 360 and Nintendo Switch games through static recompilation and platform-specific native ARM64 runtimes. NGR does not promise universal game compatibility.

## Runtime layers
1. Android host: lifecycle, storage, touch/gamepad, display and audio surfaces.
2. NGR Core (C++20): sessions, capability registry, logging and backend selection.
3. Console preparation pipelines: separate Xbox 360 and Switch import, inspection, rewrite/recompile, shader and package jobs.
4. Console-specific execution runtimes: Xbox 360 PPC/XEX/kernel/XAM/Xenos/XMA and Switch Horizon/NSO/NRO/NVN/Maxwell services.
5. Shared Android providers: system Vulkan first, isolated optional Turnip, audio output, input, storage and diagnostics.
6. Per-title profile registry: exact title/build IDs, patches, memory layout, shader facts, runtime requirements and compatibility evidence.
7. Package manager: manifest validation, hashes, install/update/remove and per-game configuration.
8. Compiler job pipeline: bounded workspaces, cancellation, resume, logs, artifact hashes and source/toolchain provenance.

## Console targets
- **Xbox 360:** PowerPC Xenon/XEX code is statically recompiled to C++ and built for Android ARM64; requires a matching title runtime and Xenos graphics/audio services. The XDroid360 reference prepares games on-device.
- **Nintendo Switch:** ARM64 NSO/NRO modules are rewritten for Android host gates; Horizon services are supplied by the Android runtime and NVN is replayed through Vulkan. The nx2android reference currently prepares the APK/data pack on a desktop host.

The two console runtimes are separate. Wine, Box64 and FEX are not prerequisites for either static-recompilation path. A generated native module is not playable without a matching runtime and title profile.

## Legacy Environment Manager requirements (deferred; not used by the Xbox 360 or Switch runtimes)
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
- Stage 5: Xbox 360 source audit and XDroid360-based compiler/runtime integration.
- Stage 6: Nintendo Switch source audit and nx2android-based rewrite/Horizon/NVN Vulkan integration.
- Stage 7: Shared compiler job manager, per-title profile registry and artifact provenance for these two targets.
- Stage 8: Android device validation on Adreno; optional Turnip remains isolated and per-title.

No additional console targets are planned in this workstream.

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

Xbox 360 and Switch compiler pipelines are not runtime plugins. Graphics-provider and compiler APIs require separate contracts. The header is compiled as C and C++, but host loader, ABI negotiation, backend registry binding and conformance tests are not implemented. The ABI is not a sandbox; NGRPACK payloads must never be loaded as plugins. Only trusted app-bundled or separately signature-verified backend libraries may be loaded after trust policy and loader validation.


## Legacy translator preset preferences (deferred; not used by the console runtimes)
Environment profile schema v3 stores Box64 and FEX preset choices (Compatibility/Balanced/Performance). v1/v2 documents migrate to FEX Balanced when the field is absent. These fields represent user intent only; a runtime adapter must map them to version-specific Box64/FEX settings after the exact upstream version and supported configuration keys are audited. A saved preset is not proof that a translator is installed, configured or active.


## Locked console integration scope

Only Xbox 360 and Nintendo Switch are admitted to the console target registry. See [XBOX360_SWITCH_INTEGRATION_PLAN.md](XBOX360_SWITCH_INTEGRATION_PLAN.md) for the audited donor architecture, source revisions, licensing boundaries and acceptance criteria. The generic Wine/Box64/FEX and N64 roadmap text from earlier project stages is superseded for active development.
