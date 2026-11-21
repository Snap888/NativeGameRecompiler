# NGR — Static Recompiler Integration Audit

Audit date: 2026-11-21  
Repository: https://github.com/Snap888/NativeGameRecompiler  
Target: Android ARM64 (arm64-v8a), Qualcomm Adreno / Vulkan  
Status: source-level audit; no third-party code has been vendored and no game payloads were imported.

## Executive decision

NGR currently has an Android host/runtime shell, NGRPACK package manager, environment/component manager and a versioned execution-backend ABI. It does **not** yet contain a compiler plugin loader, static recompiler, title runtime or game execution backend.

The six upstream repositories reviewed are real and public. The best first implementation path is:

1. N64Recomp + N64ModernRuntime as the first static-recompilation vertical slice (existing NGR plan), subject to GPL-3.0 boundary review for the runtime.
2. XenonRecomp and ReXGlue as alternative/overlapping Xbox 360 code-generation SDKs. Do not integrate both into the first Xbox target.
3. Use UnleashedRecomp-Android as an Android integration reference, not as a generic Xbox runtime.
4. Use XboxXBERecomp plus SCDA-XBERecomp-android as the Original Xbox reference; the upstream PC SDK is Windows/D3D12/XAudio2-oriented and is not directly Android-ready.

No universal cross-console runtime can be built by combining these projects: each generated code format requires its own CPU state, guest memory model, kernel/XDK/libultra services, graphics/audio/input bridge and title-specific hooks.

## NGR baseline inspected

Root README and project documents inspected:
- README.md
- docs/NEXT_ACTIONS.md
- docs/DEVELOPMENT_LOG.md
- docs/LICENSING_AND_PROVENANCE.md
- docs/TECHNICAL_SPEC.md
- docs/TECHNICAL_AUDIT.md

Baseline facts from README/spec:
- Kotlin Android host + C++20 core; ARM64 first.
- Android NDK 27.2.12479018, CMake 3.22.1, Gradle 8.9, JDK 17.
- NGRPACK and component catalog/download/install/rollback foundation exists.
- Backend ABI v1 header exists, but host ABI negotiation, plugin loader, registry and lifecycle do not.
- Static recompiler, N64 integration, Wine process launch and physical Snapdragon validation are not implemented/verified.
- NGR README says the project is not yet a general-purpose game recompiler.
- Current license policy requires pinned provenance and transitive dependency review before vendoring.

## Upstream findings

### A. hedge-dev/XenonRecomp

Repository: https://github.com/hedge-dev/XenonRecomp  
Default branch: main  
Observed root license: MIT (LICENSE.md)  
Build: CMake >= 3.20, C++17; upstream README requires Clang >= 18 and recursive submodules.

Role:
- Xbox 360 XEX analysis and static PowerPC-to-C++ code generation.
- Separate XenonAnalyse and XenonRecomp tools.
- Emits C++ code and PPC context/config metadata.
- Uses SIMDe for ARM64 VMX/SIMD support.
- Has configurable jump-table metadata, register save/restore addresses, optimization switches and function hooks.

Important limitations:
- Upstream explicitly says it does not provide a runtime.
- MMIO is unimplemented; exception handling is unsupported.
- Jump-table and function-boundary analysis can need per-game TOML overrides.
- Register save/restore function addresses must be configured.
- It is a compiler/tool, not an Xbox system implementation.

NGR decision:
- Candidate module name: compiler-xbox360-xenon.
- Build XenonAnalyse/XenonRecomp as host tools in CI first.
- Keep generated PPC C++ in a per-title private build workspace; do not commit game-derived generated code.
- Compile generated code for Android ARM64 only after an Xbox 360 runtime ABI exists.
- Do not advertise game compatibility from successful code generation alone.

### B. rexglue/rexglue-sdk

Repository: https://github.com/rexglue/rexglue-sdk  
Default branch: main  
Observed root license: BSD-3-Clause-style terms with explicit Xenia-derived portions/copyright notice; preserve both the ReXGlue and Xenia notices and review all submodules. This is not MIT.  
Build: CMake >= 3.25, Clang >= 20, C++23, 64-bit ARM64/x86_64. Upstream CMake platform detection lists Windows, Linux and macOS; Android is not explicitly listed as a supported target. Upstream README warns the project is early development and its public API may change.

Role:
- Xbox 360 PowerPC ahead-of-time recompilation to C++.
- Newer alternative/overlapping implementation relative to XenonRecomp.
- Official CI lists Linux ARM64 builds, which is useful evidence for ARM64 but not proof of Android NDK support.

NGR decision:
- Treat as an alternative Xbox 360 backend/toolchain, not a dependency of XenonRecomp.
- Before selecting it, inspect exact SDK API, XEX support, runtime service coverage, build dependencies and license.
- Run a dedicated Android NDK cross-build experiment. Do not equate Linux ARM64 with Android ARM64: libc, loader, syscalls, linker and platform libraries differ.
- NGR currently uses NDK r27.2 and CMake 3.22.1; the upstream minimums imply a toolchain upgrade or isolated build environment.

### C. SansNope/UnleashedRecomp-Android

Repository: https://github.com/SansNope/UnleashedRecomp-Android  
Default branch: main  
Type: unofficial Android port of one title, Sonic Unleashed (Xbox 360); not a general Xbox 360 runtime.

README-confirmed Android areas:
- UnleashedRecomp/os/android/
- android-apk/
- thirdparty/libadrenotools/
- thirdparty/plume/
- thirdparty/SDL/
- UnleashedRecomp/ui/touch_controls.*

README-confirmed features include ARM64 Android, game ZIP/folder staging, title updates/DLC, touch controls, gamepads, audio, Turnip driver provisioning/import, settings/saves/mod manager, diagnostics and signed updates.

Build notes:
- Windows host, Visual Studio 2022 Build Tools, CMake, Ninja, Android SDK/NDK r29, JDK 17 and vcpkg.
- Host-side recompilation tools are built on Windows before Android ARM64 cross-compilation.
- Some helper scripts contain machine-specific paths and need review.
- Game-derived private and generated PPC directories must not be committed.

NGR decision:
- Use as a reference for Android storage, SDL lifecycle/input, Vulkan/Turnip isolation, touch controls, audio, diagnostics and CI packaging.
- Do not copy Sonic-specific game hooks, private game code, generated PPC output, branding, game assets or unrelated app code.
- Extract only generic reusable patterns after file-by-file license and dependency review.
- Its Windows-host codegen pipeline is not the same thing as a self-contained on-device compiler.

### D. TGP482/XboxXBERecomp

Repository: https://github.com/TGP482/XboxXBERecomp  
Default branch: main  
Observed repository license: MIT (GitHub repository metadata); verify the exact root license file and every bundled/fetched dependency before vendoring.

Role:
- Original Xbox XBE x86 to native 64-bit Windows C++.
- XBE symbol/signature analysis and function discovery.
- x87/SSE/MMX translation, exception handling, hooks and incremental generated-code builds.
- Xbox kernel and XDK API implementations.
- D3D8 translation to native D3D12 and DirectSound to XAudio2.
- SDL3 input/controller layer.

Critical Android limitation:
- The upstream SDK describes a Windows-native D3D12/XAudio2 host. Those graphics/audio backends cannot be linked as-is into Android.
- Its templates/game integrations are title-specific and cannot be treated as a universal Original Xbox runtime.

NGR decision:
- Reuse the XBE analysis/recompiler layer only after a clean backend split is proven.
- Replace D3D12 with Vulkan and XAudio2 with an Android-compatible audio bridge (Oboe/AAudio/SDL audio).
- Implement Android-compatible kernel/XDK service and memory/threading layers.
- Keep system-link/networking and non-campaign services out of the first proof of concept.

### E. mogeldev/SCDA-XBERecomp-android

Repository: https://github.com/mogeldev/SCDA-XBERecomp-android  
Type: Android port/fork of the Original Xbox Splinter Cell: Double Agent recompilation.

README/search-confirmed:
- Android 10+, ARM64 or x86_64, Vulkan 1.1.
- SDL3 and Vulkan.
- Touch controls and SDL-compatible controllers.
- Import of user-provided game and on-device compilation on ARM64.
- Approx. 3.5 GB assets plus staging and at least 512 MiB compilation space.
- Current README labels the build a testing prerelease; device compatibility is limited/experimental.
- Project says it contains no game assets or generated game code; dependencies retain their own licenses.

NGR decision:
- This is the stronger Android reference for Original Xbox than upstream PC SDK alone.
- Audit its Android-specific diff and build scripts before reusing any code.
- Do not assume the upstream generic SDK has inherited Android support; the Android fork contains port-specific adaptations.
- The on-device compilation mechanism must be inspected precisely before designing NGR's compiler service.

### F. N64Recomp

Repository: https://github.com/N64Recomp/N64Recomp  
Default branch: main  
Observed license: MIT.  
Build: CMake >= 3.20, C++20, recursive submodules.

Role:
- Static MIPS-to-C code generation from N64 binaries plus ELF/symbol metadata.
- Rabbitizer instruction analysis, ELFIO, fmt and toml++.
- Supports overlays and some RSP microcode recompilation.
- Runtime is separate: N64ModernRuntime.

Important limitation:
- Recompiler metadata currently expects an ELF/symbol information workflow; a ROM alone is not sufficient for a general game.
- Generated code requires runtime macros/services and game-specific integration.
- N64Recomp is a build-time tool, not a complete Android game player.

NGR decision:
- Keep as first compiler proof-of-concept because it is already named in NGR's roadmap and has a clear CLI/build structure.
- Build host CLI in CI and compile a legally distributable synthetic/test input first.
- Only proceed to a title after its metadata/decompilation and asset rights are understood.

### G. N64Recomp/N64ModernRuntime

Repository: https://github.com/N64Recomp/N64ModernRuntime  
Observed license: GPL-3.0.  
Build: CMake >= 3.20, C++20.

Role:
- ultramodern: libultra-like threads, controllers, audio, queues, timers, RSP tasks and VI timing.
- librecomp: wrappers between generated code and ultramodern, overlays, PI DMA and save hardware.
- Requires the consuming project to supply/register a graphics renderer; RT64 is recommended.

NGR decision:
- Technically valuable runtime candidate, but GPL-3.0 is a release-level licensing decision for NGR.
- Do not copy it into the MIT host or link it into the app before a deliberate license decision and dependency audit.
- First evaluate a separate GPL-3.0 N64 runtime module with documented distribution/source obligations, or implement a clean independent N64 runtime against the generated-code interface if maintaining MIT is a hard requirement.
- RT64 is a separate dependency and needs its own Android and license audit.

## Android proof points discovered

These repositories demonstrate that Android recompilation ports exist, but they are title integrations, not universal console emulators:

- Xbox 360: https://github.com/SansNope/UnleashedRecomp-Android
- Original Xbox: https://github.com/mogeldev/SCDA-XBERecomp-android
- N64: https://github.com/linkzenic/Zelda64Recomp-Android
- N64: https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved
- N64: https://github.com/alondero/automobililamborghini-recomp (Android APK release assets)
- Android native port catalog: https://github.com/abduznik/awesome-android-native-ports

The N64 examples are important new evidence: N64Recomp/N64ModernRuntime are not limited to desktop ports; community projects have Android ARM64/Vulkan builds. Each project still needs its own device/build/license verification.

## Proposed NGR architecture and file map

Keep compiler tools separate from execution backends. A compiler is not an executable game runtime.

Proposed source layout (new files; adapt names to existing package conventions):

- app/src/main/java/com/ngr/app/recompiler/
  - RecompilerCatalog.kt — supported compiler metadata and status.
  - RecompilerImportCoordinator.kt — user file selection, immutable source snapshot and per-title workspace.
  - RecompilerJobRepository.kt — job state, cancellation and persistent diagnostics.
  - RecompilerSettings.kt — compiler version, target platform and per-title overrides.
- native/ngr-recompiler/include/ngr/recompiler/
  - compiler_abi_v1.h — separate compiler ABI; do not overload execution backend ABI v1.
  - compiler_descriptor.h — stable IDs, versions, input/output formats and host/target architecture.
- native/ngr-recompiler/src/
  - compiler_registry.cpp — trusted built-in compiler registry.
  - job_runner.cpp — subprocess/library execution, bounded output, cancellation and timeout.
  - artifact_manifest.cpp — hashes, generated file inventory and provenance.
- native/ngr-recompiler/adapters/
  - n64recomp/ — CLI/config adapter and metadata validation.
  - xenonrecomp/ — XenonAnalyse/XenonRecomp adapter.
  - rexglue/ — experimental alternative, disabled until Android build and license gates pass.
  - xbox_xbe/ — XBE analyser/recompiler adapter.
- native/ngr-runtime/src/backends/
  - n64/ — N64-specific runtime, initially separate GPL boundary if N64ModernRuntime is used.
  - xbox360/ — PPC CPU state, guest memory, kernel/MMIO and XDK service interface.
  - xbox/ — x86 guest state, Xbox kernel/XDK service interface.
- native/ngr-platform/android/
  - android_vulkan_provider.cpp
  - android_audio_provider.cpp
  - android_input_provider.cpp
  - android_storage_provider.cpp

The exact package path for Kotlin should be reconciled with the existing app package before implementation. This document proposes boundaries, not a claim that these files already exist.

## Compiler execution model: required decision

Do not assume Android NDK's desktop compiler can run inside the Android app. NDK Clang is a host toolchain. For on-device code generation, NGR needs one of:
1. A supported Android-hosted compiler toolchain/runtime (bundle and license-audit it; substantial size and security work).
2. A carefully isolated LLVM/Clang library or JIT/codegen integration built for Android.
3. CI/desktop precompilation for the first vertical slice, with on-device compilation deferred.
4. A title-specific proven compiler service, after auditing how SCDA/Unleashed Android actually performs compilation.

For the first milestone, prefer host-side reproducible compiler tools in CI and a small Android ARM64 generated-code/runtime test. Add on-device compilation only after a dedicated design and resource/security budget.

## Recommended staged implementation

### Stage 0 — preserve baseline
- Pin NGR main commit and confirm CI green before changes.
- Keep all upstream code out of the app until license and dependency inventory is complete.
- Create an integration branch and add this audit to the project journal.

### Stage 1 — N64 proof of concept
- Pin N64Recomp commit and recursive submodule SHAs.
- Audit all submodule licenses and RT64 Android requirements.
- Build N64Recomp CLI on CI host.
- Use synthetic or legally redistributable test input; do not place commercial ROMs/ELFs in GitHub.
- Generate C code and compile a minimal ARM64 test harness.
- Implement only the N64 runtime services needed by the harness.
- Decide GPL-3.0 runtime boundary before shipping.

### Stage 2 — compiler framework
- Add compiler catalog and compiler ABI independent of execution backend ABI.
- Add job state, logs, cancellation, bounded stdout/stderr, timeouts, disk quota and safe workspace cleanup.
- Store generated artifacts with SHA-256, upstream commit, toolchain version and input hash.
- Never execute generated output until a trusted title runtime/backend is selected.

### Stage 3 — Original Xbox
- Diff upstream XboxXBERecomp against SCDA Android fork.
- Document Android replacements for D3D12, XAudio2, filesystem, threading and kernel calls.
- Build only the XBE analysis/codegen CLI first.
- Run one legally obtained test title only after service/runtime gaps are enumerated.

### Stage 4 — Xbox 360
- Compare XenonRecomp and ReXGlue on the same non-copyrighted PPC fixture.
- Record instruction coverage, codegen output, build time, ARM64 compatibility, runtime API, MMIO and exception gaps.
- Choose one codegen engine; do not maintain two duplicate PPC backends without a clear compatibility reason.
- Build the host tool and ARM64 generated-code harness separately.
- Implement guest memory, PPC state, kernel/XDK and MMIO incrementally.

### Stage 5 — Android integration
- Port generic SDL3/Android lifecycle, Vulkan surface, audio, touch/gamepad and storage patterns only after per-file license review.
- Use Android system Vulkan first; optional Turnip remains isolated and per-game.
- Add Android device tests on Adreno and Mali separately; do not infer universal GPU support.
- Add release APK only after CI and physical-device smoke tests pass.

## Acceptance criteria for each platform

A platform is not "supported" until all are true:
1. Compiler accepts a defined input format and produces deterministic generated output.
2. Generated code compiles for Android arm64-v8a.
3. Runtime implements the required guest CPU/memory and OS/platform services.
4. Graphics, audio, input, storage and timing work for a named test title.
5. Save/load and clean shutdown pass.
6. CI build and JVM/native tests pass.
7. Physical Android device test evidence is recorded.
8. Exact source revisions, license texts, submodules and binary provenance are recorded.

## Explicit non-goals for this integration pass

- No copyrighted ROM/XEX/XBE/game assets or generated game code committed.
- No claim of universal PS3/PS4/Switch/Xbox One/Switch 2 support.
- No copying of game-specific hooks, assets, generated PPC files or branding.
- No loading NGRPACK payloads as native plugins.
- No merging Wine/Box64/FEX compatibility mode with static-recompiled backends.
- No APK release claim without successful CI artifact and device validation.

## Sources

- https://github.com/hedge-dev/XenonRecomp
- https://github.com/rexglue/rexglue-sdk
- https://github.com/SansNope/UnleashedRecomp-Android
- https://github.com/TGP482/XboxXBERecomp
- https://github.com/mogeldev/SCDA-XBERecomp-android
- https://github.com/N64Recomp/N64Recomp
- https://github.com/N64Recomp/N64ModernRuntime
- https://github.com/linkzenic/Zelda64Recomp-Android
- https://github.com/codepdbh/CBFD-Recompiled-Android-Evolved
- https://github.com/alondero/automobililamborghini-recomp
- https://github.com/abduznik/awesome-android-native-ports
