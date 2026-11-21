# NGR — Xbox 360 + Nintendo Switch Recompiler/Runtime Integration Plan

Audit date: 2026-11-21  
Scope decision: **Xbox 360 and Nintendo Switch only**  
Target host: Android ARM64 (arm64-v8a), Vulkan; Adreno first  
NGR baseline before this work: main at commit 3fdbe9b42319f60e1ecf2c449dd821c5f5762cb7

## 1. Scope lock

NGR's console static-recompilation work is restricted to exactly two platforms:

1. Xbox 360 — based on the writable user fork Snap888/XDroid360, its upstream source lineage and title-runtime modules.
2. Nintendo Switch — based on shirai91/nx2android and its nxnative Android runtime.

Do not add N64, Original Xbox, PS1/PS2/PS3/PS4, Xbox One, Switch 2, GBA, Wii or generic PC/EXE targets to the console recompiler catalog, UI, roadmap or implementation work. Existing NGR package-management and Windows/Linux profile code is legacy foundation; it is not an invitation to expand those platforms in this workstream.

The new Kotlin target catalog contains only xbox360 and nintendo-switch. Its state is explicitly RESEARCHED_NOT_INTEGRATED; this is metadata, not a compatibility claim.

## 2. Source revisions inspected

### Xbox 360
- Writable user fork: https://github.com/Snap888/XDroid360
- Fork commit inspected: 6eefb7f738d4e63cb11cb6bb63911f55094e8ada
- Upstream project: https://github.com/udarmolota/XDroid360
- Current fork README and Android app build configuration inspected. The fork README still labels its alpha as 0.1.2 while upstream README reports 0.1.3; use the inspected fork commit and exact toolchain pins, not the stale version label.
- android/app/src/main/java/com/udarmolota/xdroid360/RecipeRunner.java inspected.
- android/app/build.gradle inspected.
- toolchain/build_tools.sh inspected.
- .gitmodules inspected: recomp is the DAORecomp Android submodule.
- NOTICE.md and root LICENSE inspected.

### Nintendo Switch
- Read-only upstream reference: https://github.com/shirai91/nx2android
- Commit inspected: 828ded46adb11722591c4353a2ba88175640d250
- README.md, RESEARCH.md, docs/ARCHITECTURE.md, packaging/INSTALL.md, porter/cli.py, porter/paths.py, porter/host.py, nxnative/README.md, nxnative/LICENSE, nxnative/dm/tools/patch_nso.py and nxnative/dm/android/build.py inspected.

NGR is writable. The two donor repositories are references; do not assume they are safe to copy wholesale. Pin exact source commits and dependency/submodule revisions before vendoring.

## 3. Xbox 360 architecture — XDroid360

### 3.1 Preparation / compiler

The reference workflow is on-device:
1. User imports their own Xbox 360 disc image or extracted package archive.
2. The game-specific recipe stages and validates its inputs.
3. XenonRecomp analyses the PowerPC/XEX-derived code and emits C++.
4. Android-hosted LLVM Clang/LLD compiles the generated C++ for ARM64.
5. Shader tools prepare the title's Xenos shaders.
6. A title runtime and native graphics renderer are packaged with the game data.

The user's fork has a native Android toolchain build script. It builds LLVM Clang/LLD for Android ARM64, an Android-targeted XenonRecomp executable, STFS extraction and shader compiler/DXC components. The Android RecipeRunner launches recipe tools as child processes, captures logs, supports cancellation and resumes completed steps using markers/fingerprints.

This is a strong reference for NGR's on-device compiler job manager, but NGR must add its own workspace isolation, quotas, timeouts, artifact manifest and source provenance.

### 3.2 Runtime

Important distinction: XDroid360 does not provide one finished universal runtime for every Xbox 360 game. The inspected project uses game-specific runtime modules:
- DAORecomp as the Dragon Age: Origins base;
- SoDRecomp derived for State of Decay;
- ME1Recomp for Mass Effect.

These title modules provide game-specific system behaviour and glue. The shared Android app/toolchain alone does not make arbitrary XEX titles playable.

### 3.3 Graphics/audio/input

- Native Xenos-to-Vulkan renderer for supported title profiles.
- Xenia Vulkan backend remains a fallback path for games/configurations not using the native renderer.
- Xenos shader translation and DXC are separate build/runtime dependencies.
- SDL3 is used for input/platform support.
- XMA audio uses FFmpeg in the donor stack.
- Turnip is optional for Adreno; keep the Android system Vulkan loader untouched and isolate optional driver loading.
- Touch controls, gamepad layouts, save import/export, per-title settings and diagnostics are useful Android UX references.

### 3.4 Critical limitations

- Recompilation output is not a game runtime. Each title needs a suitable runtime module, API coverage, graphics path and validated profile.
- A title may require XEX/import handling, kernel/XAM exports, guest memory, thread/synchronisation semantics, file/content services, Xenos PM4, EDRAM, shader translation and XMA audio.
- Do not infer compatibility for a new game from another title's successful build.
- The upstream README's supported games/editions are version-specific; title ID and executable/build fingerprint must be checked.

### 3.5 NGR implementation boundary

Create separate NGR modules for:
- Xbox 360 game intake and XEX/STFS/ISO package inspection.
- XenonRecomp tool adapter and C++ generation.
- Android ARM64 Clang/LLD toolchain manager.
- Per-title runtime package and profile registry.
- Xbox guest services / memory / kernel / XAM bridge.
- Xenos graphics frontend and Vulkan provider.
- XMA audio and SDL3/input integration.

Do not fold Xbox 360 into the generic NGR Wine/Box64 launch path. Do not load a generated game library until its signed/verified manifest and matching runtime profile pass validation.

## 4. Nintendo Switch architecture — nx2android

### 4.1 Critical distinction: desktop preparation vs Android execution

The inspected nx2android is primarily a Windows/macOS/Linux desktop port builder that produces an Android APK and a game data pack, then optionally installs them with ADB. Its documented toolchain needs a desktop host, Python, .NET SDK, Android NDK and platform-tools. It is not currently an Android on-device importer/recompiler application.

Therefore NGR must not claim that Switch recompilation is on-device merely because the resulting game runs natively on Android.

Recommended staged approach:
1. First prove the Android runtime can load and run a legally prepared, profile-matched test package.
2. Integrate the preparation pipeline behind a SwitchPorter service with a desktop/CI-assisted preparation contract.
3. Evaluate porting the required extraction, rewrite and build tools to Android only after each host dependency is proven to run under Android/Bionic and storage/memory limits.
4. Keep the Android player/runtime independent from the preparation host, so packages prepared on a desktop can be installed and updated by NGR.

### 4.2 Port pipeline

The inspected architecture documents this flow:
1. Accept the user's own NSP/XCI/NSZ/XCZ files, updates and DLC.
2. nx_extract (.NET / LibHac) extracts ExeFS and RomFS and layers updates.
3. patch_nso.py rewrites NSO/NRO ARM64 code: Horizon svc, thread-local register usage, timer reads and spin waits are redirected through host gates; title/build-specific cuts and observation gates are validated.
4. Shader extraction/splitting reads the game's own shader data.
5. Ryujinx shader translator converts Maxwell shaders to SPIR-V; Android shader packaging compresses and stores them.
6. A game profile supplies build IDs, memory layout, shader facts/tables, title patches and Android settings.
7. dm/android/build.py compiles the Android host/runtime for ARM64 using the NDK.
8. APK and a separate game data pack are produced; the data pack contains the user's RomFS/mods/system data/shader bundle/settings as required by that profile.

### 4.3 Android runtime

The nxnative/dm/android layer includes:
- NativeActivity Android frontend and lifecycle.
- Vulkan NVN replay backend.
- OpenGL ES path for supported GL-rendering titles.
- Horizon host services: thread, filesystem, HID, audio, time and other service shims.
- Touch overlay, controller support, pause/settings, saves/import/export, text input, haptics and Android performance hints.
- NDK build and custom APK writer/signing pipeline.

The shared core is separated from Android runtime and per-title profiles. Preserve this separation in NGR.

### 4.4 Graphics and device constraints

- NVN command replay is a Vulkan implementation, not a generic pass-through of all Switch graphics calls.
- Some titles using different graphics APIs or shader paths are refused or need additional capture/translation work.
- The inspected nx2android installation guide currently lists Adreno and Xclipse as supported GPU families and says Mali/PowerVR are not supported for this port. Do not claim Mali support for this path without tests.
- BCn texture support, memory mapping/external memory, descriptor/indexing features and driver stability are device-specific. Probe Vulkan extensions/features at runtime.
- Android system Vulkan must remain intact. Optional Turnip must be isolated and selectable per title/device.

### 4.5 Per-title profile is mandatory

A profile is not optional metadata. It supplies build IDs, memory alias/headroom, shader facts, patches, system data, graphics/API choices and Android settings. The same title with a different update/build can need different code cuts or shaders. Reject mismatched versions rather than applying guessed offsets.

### 4.6 NGR implementation boundary

Create separate NGR modules for:
- Switch package intake and title/version inspection.
- User-owned key configuration (never bundled, logged or uploaded).
- NSP/XCI/NSZ/XCZ extraction adapter.
- NSO/NRO rewrite adapter and fixup/gate manifest.
- Shader extraction, Maxwell-to-SPIR-V and Vulkan shader bundle.
- Switch Android Horizon service host.
- NVN-to-Vulkan renderer and optional supported GL route.
- Per-title profile registry and strict build/version compatibility.
- APK/game-data pack installer and runtime update validation.

NGR must never include commercial game files, firmware, prod.keys, title keys or copyrighted shader/game assets. The user supplies content and keys they are legally entitled to use. Logs and crash reports must redact key material and content paths where appropriate.

## 5. Shared NGR services vs separate console runtimes

Shared Android host services can include:
- Android lifecycle and surface management.
- Vulkan physical-device/extension capability probe.
- Optional isolated Turnip provider.
- SDL3 or a common input adapter, touch overlay and gamepad profiles.
- Audio output abstraction (AAudio/Oboe/SDL), not console-specific decoding.
- Storage grants, private workspace, save backup and export.
- Compiler job queue, progress/cancel/resume, structured logs and artifact hashes.
- Per-title settings, package update and rollback.

Keep separate:
- Xbox PPC CPU state, XEX imports, kernel/XAM, Xenos PM4/EDRAM/shaders, XMA.
- Switch Horizon services, NSO/NRO rewriting, NVN, Maxwell shader translation, Switch profile semantics.

Do not force either console through the generic Wine environment manager. Do not treat NGRPACK data packages as executable backend plugins.

## 6. Licensing / provenance gates

### Xbox 360 donor

- XDroid360 root launcher/source: MIT.
- native-renderer/: GPL-3.0.
- XenonRecomp's own code is MIT, but the donor's patched libxenonrecomp.so is documented as GPL-2.0-or-later as a whole because the disassembler includes GNU binutils code.
- DAORecomp: MIT except its Xenia-derived portions.
- Xenia: BSD-3-Clause.
- LLVM: Apache-2.0 with LLVM exception.
- DXC and all third-party shader/tool dependencies have their own licenses/notices.
- ReXGlue renderer host layer: BSD-3-Clause.

The donor NOTICE is authoritative for the exact bundled release. Do not treat the entire XDroid360 tree as MIT. Linking the GPL-3.0 native renderer into a title library/APK has GPL-3.0 consequences; the donor NOTICE explains this boundary.

### Nintendo Switch donor

- nxnative/LICENSE is MIT.
- nx2android README describes the tool as MIT, but third-party code retains its own license.
- LibHac, Ryujinx shader translator, SPIRV-Cross, glslang, deko3d-derived texture logic, libvpx, zstd, Capstone, LZ4, fontTools, Vulkan headers and other dependencies need a pinned SBOM/license inventory.
- Do not infer that all bundled dependencies are MIT from nxnative/LICENSE.

Before copying source: pin commits, record submodule SHAs, preserve LICENSE/NOTICE files, generate an SPDX/SBOM inventory and resolve whether NGR's distribution model can comply with GPL-2.0-or-later/GPL-3.0 components. No donor code is copied merely by writing this plan.

## 7. NGR target catalog and code added

New code:
- app/src/main/java/com/ngr/app/recompiler/ConsoleRecompilerTargets.kt
- app/src/test/java/com/ngr/app/recompiler/ConsoleRecompilerTargetsTest.kt

The catalog currently contains exactly:
- xbox360: PowerPC Xenon to Android arm64-v8a; on-device preparation reference; ISO/ZIP intake.
- nintendo-switch: ARM64 NSO/NRO rewrite to Android arm64-v8a; desktop-assisted preparation reference; NSP/XCI/NSZ/XCZ intake; user-provided keys required.

Both are explicitly marked RESEARCHED_NOT_INTEGRATED. The registry is a first typed foundation for the UI/import/job pipeline, not a working compiler.

## 8. Implementation phases

### Phase A — NGR foundations
- [x] Lock scope to Xbox 360 and Nintendo Switch only.
- [x] Inspect current XDroid360 fork, nx2android architecture and Android runtime source documentation.
- [x] Add typed two-platform target catalog and unit tests.
- [ ] Run NGR JVM tests and Android ARM64 CI build after this commit.
- [ ] Add a compiler job model separate from execution Backend ABI v1.
- [ ] Add per-title profile and source/build fingerprint model.
- [ ] Add secure, cancellable workspaces, quotas, resumable steps and structured logs.

### Phase B — Xbox 360 vertical slice
- [ ] Pin XDroid360 submodule, DAORecomp and toolchain dependencies.
- [ ] Audit the existing recipe format and all game profile build requirements.
- [ ] Build XenonRecomp/Clang/LLD for Android arm64 in reproducible CI.
- [ ] Port one existing donor title runtime as a legally distributable test harness, with source/license notices.
- [ ] Verify generated code, runtime loading, audio/input, save path and Vulkan output on an actual Android device.
- [ ] Add generic XEX/game intake only after profile/build-ID validation works.

### Phase C — Switch vertical slice
- [ ] Pin nx2android and nxnative commits plus all submodules/dependencies.
- [ ] Build nx_extract and shader translator reproducibly on a supported desktop CI host.
- [ ] Produce a non-commercial synthetic/legally redistributable NSO rewrite fixture and validate host gates.
- [ ] Build the Android ARM64 Horizon/NVN Vulkan host.
- [ ] Load a profile-matched test package on Android and verify lifecycle, input, audio, shader cache and save path.
- [ ] Decide whether NGR supports desktop-assisted preparation first or funds a full Android-hosted port pipeline.
- [ ] Add strict key redaction and private key storage.

### Phase D — unify the NGR experience
- [ ] Add only Xbox 360 and Switch import cards.
- [ ] Show platform-specific intake requirements and supported title/build IDs.
- [ ] Display preparation vs runtime progress as separate jobs.
- [ ] Install runtime/data packs atomically and keep save data separate.
- [ ] Add per-title diagnostics and exportable sanitized logs.
- [ ] Add compatibility status only when a title has device evidence.

## 9. Acceptance criteria

A console is not considered integrated until:
1. The source and all dependency revisions are pinned and licenses recorded.
2. A compiler/preparation job produces a deterministic, fingerprinted artifact.
3. The artifact targets Android arm64-v8a and is validated against the matching profile.
4. The Android runtime loads it without Wine/Box64/FEX.
5. CPU/system service behavior is tested for a named title or synthetic fixture.
6. Vulkan output, audio, input, storage, saves and clean shutdown are validated.
7. CI builds and tests pass.
8. Physical-device evidence, GPU/driver, Android version, memory and title build ID are recorded.

A successful APK build or a successful code-generation run alone is not a playable-game milestone.

## 10. Sources

- XDroid360 user fork: https://github.com/Snap888/XDroid360
- XDroid360 inspected commit: https://github.com/Snap888/XDroid360/commit/6eefb7f738d4e63cb11cb6bb63911f55094e8ada
- XDroid360 upstream: https://github.com/udarmolota/XDroid360
- nx2android: https://github.com/shirai91/nx2android
- nx2android inspected commit: https://github.com/shirai91/nx2android/commit/828ded46adb11722591c4353a2ba88175640d250
- nx2android architecture: https://github.com/shirai91/nx2android/blob/main/docs/ARCHITECTURE.md
- nx2android research: https://github.com/shirai91/nx2android/blob/main/RESEARCH.md
- XenonRecomp: https://github.com/hedge-dev/XenonRecomp
- XDroid360 DAORecomp submodule: https://github.com/udarmolota/Dragon-Age-Origins-Recomp-Android


## 11. Exact toolchain facts and NGR compatibility gaps

### XDroid360 toolchain pins observed in toolchain/env.sh
- Android NDK: 27.3.13750724 (NDK r27d).
- LLVM/Clang/LLD: 22.1.8; the toolchain script pins the LLVM source tarball SHA-256.
- XenonRecomp:  hedge-dev/XenonRecomp commit ddd128bcca99fe8bfbb99bea583c972351fa6ace.
- XenonRecomp Android patch: udarmolota/Dragon-Age-Origins-Recomp-Android commit bfbaa874323181191228ca20a7775f1725ee287a; patch SHA-256 55d637e3157206a8267e25d24823448bc956f2a1955ddbc389a15f511e1812e6.
- SDL: commit 829a65d769d935c4852f8159e964312c0957260a (SDL 3.4.18).
- DirectXShaderCompiler: commit 01b62ad47db7dee3dd33f90e7339cf9e860b3f93 (1.9.2609).
- Android app build config: NDK r27d, minSdk 29, arm64-v8a; native linker targets include 16 KB max page size.
- Android tool package includes clang, lld, XenonRecomp, STFS extractor, shaderlib and DXC, plus a game-agnostic NDK/sysroot/header kit and separately packaged game profiles.

NGR currently declares NDK 27.2.12479018, CMake 3.22.1 and minSdk 26. This differs from the donor's NDK r27d and minSdk 29. Do not silently mix their sysroots or prebuilt libraries. The Xbox compiler/toolchain should initially build in a pinned isolated CI environment; decide deliberately whether NGR upgrades its NDK/minSdk or carries a separately versioned toolchain kit. Validate 16 KB page-size alignment for every shipped native executable/library.

### nx2android toolchain facts observed
- Python 3.11+ for porter and rewrite/shader tools.
- .NET SDK 10 for nx_extract (LibHac) and shader translation build tooling.
- Android NDK r27c for the ARM64 Android host build.
- Android platform-tools/ADB for optional installation from the desktop host.
- The documented preparation host is Windows, macOS Apple Silicon or recent Linux x64/ARM64, with approximately 30 GB free space.
- The installation guide requires user-owned Switch dumps and user-provided prod.keys; it lists Adreno and Xclipse GPUs and excludes Mali/PowerVR for the inspected build.
- nxnative's Android host build script targets arm64 Android and has a separate host binary and libdmhost.so output. The custom APK writer is distinct from the desktop GUI/porter.

NGR must keep the Switch preparation-host toolchain separate from the Android runtime toolchain. The initial Switch milestone should use desktop-assisted preparation or CI to generate a test package, then validate the Android runtime. A full on-device port pipeline is a separate feasibility project, not a checkbox to turn on.

## 12. Immediate implementation sequence after this audit

1. Verify the new NGR Kotlin catalog with the full Gradle test task and Android APK CI.
2. Implement a compiler-job contract with source URI/SAF grant, platform ID, profile/build fingerprint, workspace quota, progress, cancellation, resumable step records, and structured log reference. Keep this contract separate from execution Backend ABI v1.
3. Implement the Xbox recipe reader/job persistence in NGR using the donor's recipe schema as a reference, but use a new NGR schema with strict validation and no shell execution.
4. Pin the XenonRecomp source + patch and build the tool for Android arm64 in CI. First validate only a synthetic PPC fixture; do not yet bundle a game runtime.
5. In parallel, add a Switch desktop-preparation adapter contract and synthetic NSO/NRO rewrite test. Then build and validate the Android Horizon/NVN host.
6. Only after the two vertical slices run, add end-user import cards and game-library entries for the two platforms.
