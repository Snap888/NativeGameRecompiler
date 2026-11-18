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
- [x] Add versioned Kotlin environment profile model with standard x86_64/Box64 vs ARM64EC/FEX validation, Turnip/Wayland gating, ReShade/shader references, audio/display settings, per-game overrides and exact component-version pins.
- [x] Add JVM validation tests for architecture mismatch, Wayland prerequisites, ReShade requirements, protected environment variables and profile bounds.
- [x] Audit WinNative container model/manager, Proton catalog/install flow, CPU preset managers, graphics-driver catalog, Wayland capability gate, ReShade effect manager/downloader, component recipe installer and environment-variable representation.
- [x] Map WinNative XServerDisplayActivity, GuestProgramLauncherComponent, standard Box64 vs ARM64EC FEX/WOWBox64 launch selection, PRoot/Proton path and ReShade/vkBasalt environment handoff.
- [ ] Complete Wine layer version resolver and exact binary/runtime dependency provenance.
- [x] Map Box64/Box86 preset environment settings, FEXCore preset/SMC normalization, ARM64EC direct-Wine/HODLL routing and WOWBox64 selection from pinned donor sources.
- [ ] Complete exact binary/runtime dependency provenance and compatibility matrix.
- [x] Map DXVK async/cache and VKD3D feature-level environment configuration; NGR component store/install/rollback foundation exists.
- [ ] Audit remaining D8VK/D7VK selection details and validate actual runtime loading.
- [x] Map AdrenoTools and Linux-runtime ICD provider separation; preserve system Vulkan loader.
- [ ] Implement NGR's isolated Turnip provider activation and verify Vulkan ICD selection.
- [ ] Audit ReShade injection, shader catalogs, presets and texture packages; distinguish catalog availability from runtime support.
- [x] Record preliminary MIT NGR vs GPL-3.0 WinNative boundary and initial upstream license matrix in docs/LICENSING_AND_PROVENANCE.md.
- [ ] Complete release-pinned SPDX/SBOM and transitive dependency review; decide whether to relicense NGR or keep donor code isolated before any source port.
- [x] Pin donor source audit to WinNative commit 99c3305d163780de5ca39b493aefcde436acad5c.
- [ ] Create component provenance/SPDX inventory for donor submodules, downloaded binaries and every runtime dependency.
- [x] Add versioned typed component catalog model with target ABI, SHA-256, SPDX license, source provenance, dependency/conflict constraints, safe install layout and rollback slot.
- [x] Add strict bounded catalog JSON parser, schema fixture and dependency-cycle validation.
- [x] Add dependency/ABI/profile compatibility resolver with dependency-first install order, installed-component dependency traversal, pinned-version checks and conflict checks.
- [ ] Persist environment profiles, implement container roots/Wine prefix lifecycle and connect global/per-game overrides to runtime launch.
- [x] Implement bounded ZIP component archive inspection, SHA-256 verification, private snapshot, path-safe staging extraction and executable-bit preservation.
- [x] Add HTTPS-only component downloader with redirect downgrade protection, bounded streaming, timeouts, Content-Length validation and SHA-256 verification.
- [x] Add atomic component version promotion, active/previous state, rollback, inactive-version removal and uninstall for verified ZIP components.
- [x] Deterministic HTTPS downloader integration tests passed in CI run #264 (44192157349), including success, redirect, downgrade, hash and streaming-size-limit paths.\n- [ ] Add TAR.GZ/TAR.XZ/TAR.ZSTD/RAW handlers with bounded extraction and expected-layout validation. Do not port WinNative ComponentInstaller ZIP extraction or direct ReShade extraction; audit found unsafe path handling and non-atomic replacement.
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


## Current CI gate — 2026-11-18
- [x] Prior baseline commit `5969959ba40a2c7e75c6f625ca023b5e76e62198` passed Actions run #142 (`44180861281`).
- [x] Diagnosed runs #145 (`44181554144`) and #146 (`44181583098`): Kotlin compilation failed on invalid backslash/NUL escape literals; no APK was produced.
- [x] Corrected path validation and added Windows-style traversal regression test (commits `94f0ccdc`, `ee33c008`, `f9b2b5bd`).
- [ ] Verify corrected-code run #152 (`44181974186`) and latest documentation run #153 (`44182015525`); JVM tests, arm64 APK build and artifact upload must all pass before sharing a new APK.


## Environment Manager CI checkpoint — 2026-11-18
- [x] Run #155 (`44184022132`) for catalog URI import correction: JVM tests, ARM64 APK build and artifact upload succeeded.
- [x] Run #166 (`44184560868`) at commit `ca08211817af5f0a8ed7fc5216bd395c7140faad`: JVM tests, ARM64 APK build and artifact upload succeeded. Artifact `NGR-arm64-debug`, ID `14374685465`, SHA-256 `3191e73181fec3bc57c3db651cc37b1d353bbbb61dc2daebf278725dce154408`.
- [x] Verify private archive snapshotting and executable-bit preservation: run #172 (`44184927065`) passed JVM tests, ARM64 APK build and artifact upload at commit `2d4dd105f6c8e5ab4f16f4bc42da61d3ff32a620`.
- [ ] Verify the test-fixture cleanup commit `7955754a4d2c303cb4495436154e7d12a2fb5b97` and latest docs-only commit. Production code in the successful #172 APK matches the current implementation.


## Latest component staging checkpoint
- [x] Run #172 passed; artifact `NGR-arm64-debug` ID `14373866839`, SHA-256 `487fef223d1c011e6008d1a8fd3ab8192cab5090a1f0f0ed5635eb77b0ea5f50`, expires 2026-12-01.
- [ ] Latest main includes test-only temporary-directory cleanup and documentation commits after #172; verify their CI before calling the entire branch green.


## HTTPS downloader checkpoint
- [x] Add downloader implementation and URL-policy tests. Network permission is declared; redirects remain HTTPS-only and only HTTP 200 payloads are accepted.
- [x] Downloader, manifest permission and URL policy tests passed in CI run #186 (44185530255).
- [ ] Verify latest main after ZIP path normalization and launch-audit documentation commits.


## Latest resolver/store integration
- [x] Component store and rollback tests passed in run #190 (44185844227).
- [x] Payload layout and local archive install pipeline tests passed in run #202 (44186403039).
- [x] Component resolver tests passed in run #205 (44186662469), artifact ID 14375191502.
- [ ] Verify latest ZIP backslash normalization regression test and current main CI.


## CI follow-up — 2026-11-18
- [x] Fix Kotlin compile error in profile editor: form container is now initialized before local field helper (`9b3e91eb`).
- [x] Run #231 (`44189626075`, profile editor fix) passed.
- [x] Runs #232 (`44189719581`), #233 (`44189758325`), #234 (`44189803672`), #235 (`44189890969`) and #236 (`44189916010`) passed JVM tests, ARM64 APK build and artifact upload.
- [x] Consolidate on the original bounded `NgrComponentCatalogParser`; remove the duplicate `NgrComponentCatalogJson` decoder and redundant tests after CI identified a brittle duplicate test failure.
- [x] Diagnose #239/#240: failing assertions belonged to the duplicate parser tests; remove duplicate parser and tests.
- [x] Diagnose #241: required-component-cycle test used positional constructor arguments incorrectly; fix with named arguments (`b7563d59`).
- [ ] Verify runs #242 (`44190645517`), #243 (`44190651170`), and the newest retry after the cycle-test fix.

## Component pin CI gate — 2026-11-18
- [ ] Verify runs #252 (44191419786) through #257 (44191685428) for component-version pin model, codec, resolver, UI and tests. The latest main source still requires a complete green CI and uploaded APK.

## Latest code CI gate — 2026-11-18
- [ ] Confirm #261 (44191960463) downloader integration tests and #262 (44191981786) cleanup run; #263 (44192131858) and #264 (44192157349) include installed-dependency resolver fix/tests.


## UI refresh — 2026-11-18
- [x] Replace the plain main screen with a dark WinNative-inspired NGR dashboard, blue accent system, hero import panel and grouped library/runtime/device/diagnostics sections.
- [x] Align Android Material dialogs with the dark dashboard theme.
- [x] Preserve existing package import/install/remove, environment profile, Vulkan, input and diagnostics behavior.
- [ ] Verify latest UI commit in Android CI: JVM tests, arm64-v8a APK build and artifact upload.
- [ ] Install the UI build on the target phone and review contrast, scrolling, dialog styling and compact-screen layout.
- [ ] Follow up with polished game/profile list cards and navigation only after device feedback.


## Wine prefix lifecycle — 2026-11-18
- [x] Add private prefix storage with PREPARED / READY / INVALID status and Wine marker validation.
- [x] Add bounded tree traversal, symlink/special-file rejection and path-scoped deletion.
- [x] Add atomic clone, private SHA-256-verified backup snapshots and restore.
- [x] Add reset and duplicate-ID protections.
- [x] Add JVM regression tests and basic Android prefix-management UI.
- [ ] Verify latest JVM tests and ARM64 APK CI.
- [ ] Device-test prepare/list/reset/remove with a test prefix.
- [ ] Connect prefix IDs to environment profiles after profile schema migration.
- [ ] Add Wine runtime + wineboot before allowing prefix initialization or game launch.
- [ ] Design portable backup export/import separately; current snapshots remain app-private.


## Environment / prefix / component binding — 2026-11-18
- [x] Migrate profile schema v1 to v2 with optional managed Wine prefix ID and backward-compatible v1 decoding.
- [x] Add strict prefix ID validation and codec round-trip/migration tests.
- [x] Expose prefix ID assignment in the profile editor and show prefix state in profile cards.
- [x] Prevent deleting a prefix referenced by a saved environment profile.
- [x] Add integrated readiness evaluator for profile validation, prefix structure, installed component versions, version pins and runtime capabilities.
- [x] Add a prefix picker instead of manual ID entry.
- [ ] Connect a trusted catalog source to the UI and expose full readiness report.
- [x] Add readiness tests for PREPARED prefix, missing prefix and missing runtime components.
- [ ] Add READY-prefix plus fully installed-component success-path test.
- [ ] Implement component activation and Wine runtime initialization only after binary provenance and license checks.
- [ ] Run CI and verify JVM tests, ARM64 APK build and artifact upload.


## P0 CI repair — 2026-11-18
- [x] Audit current HEAD and read the project source-of-truth docs.
- [x] Diagnose current HEAD run #312: Kotlin compile error at `NgrWinePrefixManager.kt:280` (`files += file` collection reassignment/type mismatch); SDK/NDK/CMake/Gradle setup succeeded.
- [x] Fix using explicit `files.add(file)` in commit `18b02fcb8454b2f43b44106b968ca58c9c7d0e49`.
- [x] Verify run #315 (`44249623666`) at commit `489384e384ef386dcde70b9e14622251d9201943`: JVM unit tests, ARM64 APK build and artifact upload all passed.
- [x] Download and verify artifact ID `14402883613`; APK SHA-256 `10327bc566746291a1f403b025b62b36774eca321ebca55675e463c23c5af8be`.
- [x] Add host/backend ABI v1 table validation and native conformance tests; CI integration is pending.


## P2 — Backend ABI v1 validation foundation (2026-11-18)
- [x] Add host and backend API table validators for struct sizes, ABI version, backend kind, bounded identifiers and required callbacks.
- [x] Add native conformance tests for valid/invalid tables and ABI mismatch cases.
- [x] Compile validator into Android runtime and run native tests in CI.
- [ ] Verify CI for commits `45a4f8fe` through `c09e1a84`; fix any native test/compiler errors before proceeding.
- [ ] Implement trusted backend registration and serialized lifecycle; no NGRPACK payload may be loaded as a shared library.
- [ ] Add app-bundled/verified library provenance policy before enabling dynamic loading.


## Backend ABI validation — 2026-11-18
- [x] Implement host/backend ABI v1 table validation for struct sizes, major/minor versions, backend kind, bounded ID/version views and required callbacks.
- [x] Add native conformance tests for valid and malformed API tables.
- [x] Cover host major/minor/struct-size mismatch and backend minor-version mismatch.
- [x] Link validator into the Android C++20 runtime target.
- [x] Add native conformance test step to Android CI.
- [x] Fix over-escaped shell line continuations in CI (#321–#324); corrected in `28ad3d4eac093b58849f93872fcc102248b1396a`.
- [ ] Verify run #327 (`44270340309`) native tests, JVM tests, ARM64 APK and artifact upload.
- [ ] After green CI, add trusted built-in backend fixture and lifecycle state machine; NGRPACK payloads remain data-only.


## P0 — ABI v1 registry foundation
- [x] Verify ABI validation run #334 (`44270549436`): native conformance tests, JVM tests, ARM64 APK and artifact upload all passed.
- [x] Download and verify artifact ID `14413025119`; APK SHA-256 `8abd8b7c6dc6ed1bdef25e2b4fb4d16316547f3d44fed6703a1e020f0aec501f`.
- [x] Audit ABI header, validator, CMake, JNI and capability registry.
- [x] Add duplicate-safe, thread-safe in-process built-in backend registry foundation; no dynamic loading or package payload access.
- [ ] Verify new registry native tests and complete Android CI.
- [ ] Implement lifecycle state machine with explicit stop-completion/join contract and tests.
- [ ] Design signed/provenance-verified library policy before considering dynamic loading; NGRPACK game payloads remain data-only.


## P0 — Game shortcut and launch configuration foundation (2026-11-18)
- [x] Add validated per-game shortcut metadata with package/profile identity, guest executable/working directory, argument vector, launch mode, per-game environment overrides, icon asset reference and launch timestamps.
- [x] Add bounded atomic app-private shortcut store; reject symlinks, unsafe IDs/paths, protected environment variables, unknown schema fields and corrupt metadata.
- [ ] Verify shortcut store JVM tests and ARM64 APK in latest Actions run #351 (44280992531); #344 failed because the test compared records with independently generated timestamps, corrected in d3dc6110.
- [x] Integrate basic shortcut CRUD into the game library UI: create from installed package/profile, edit title/executable/working directory/arguments/profile/mode/environment, delete and remove orphan records.
- [ ] Add custom icon/cover import and Android pinned-shortcut publication.
- [ ] Add Android pinned-shortcut adapter with immutable launch identity and no host-path leakage.
- [ ] Add per-game launch settings editor and deterministic profile/container override resolution.
- [ ] Add launcher/session API; do not claim launch capability until process start/stop and exit reporting work.

## P1 — Complete runtime management layer before static recompilation
- [ ] Persisted game library and shortcut UI, custom icon/cover import, executable chooser and package-to-shortcut binding.
- [ ] Full container manager and Wine prefix initialization through selected Wine runtime (wineboot), repair/reset/clone/backup/restore.
- [ ] Wine/Proton, Box64/Box86, FEXCore/FEX UnixLibs/WOWBox64 managers, preset editors and architecture routing.
- [ ] DXVK/VKD3D/D8VK/D7VK configuration and per-game graphics profiles.
- [ ] Driver catalog/import/download/install/rollback/remove with isolated Turnip provider activation and Vulkan ICD verification.
- [ ] ReShade-compatible vkBasalt effects integration; separate verified Wine ReShade DLL injection research, shader/preset/texture management.
- [ ] Frame-generation/upscaling provider API, Vulkan capture stages, pacing/latency settings and real provider validation; keep UI gated until provider exists.
- [ ] Controller assignment, mapping editor, touch layouts, dead zones/curves, rumble and per-game profiles.
- [ ] Audio device/backend controls, display/window/resolution/DPI settings, X11 first; Wayland gated.
- [ ] Runtime diagnostics, process/session manager, logs, crash reporting, profile export/import and safe rollback.
- [ ] Release-pinned SPDX/SBOM/provenance review for every runtime binary and transitive dependency.
- [ ] Completion audit and target-device test matrix before starting static recompilation.


## P0 — Deterministic launch configuration (2026-11-18)
- [x] Centralize runtime-owned environment variable protection across profile and per-game shortcut validation (`LD_*`, `NGR_*`, PATH/HOME/PREFIX, Vulkan ICD/layer paths, Adrenotools, Wine prefix/loader and FEX rootfs control variables).
- [x] Resolve profile defaults + package-specific override + shortcut override in deterministic precedence; validate profile identity and launch mode requirements.
- [x] Resolve per-game Wine/DXVK/VKD3D/Turnip/display choices and reject unverified Wayland combinations.
- [x] Add in-app resolved-configuration preview; it explicitly does not start a process.
- [x] Verify resolver JVM tests and ARM64 build in Actions run #400 (all jobs passed).
- [ ] Add component catalog selection and installed-version readiness to preview; never infer availability from IDs alone.
- [ ] Implement Android pinned shortcuts and safe icon import.


## P1 — Component catalog and installer UI
- [x] Persist imported catalogs after strict schema/provenance-field validation with bounded size, atomic replacement, safe IDs and symlink checks.
- [x] Add dependency-first manual component install planner with conflict/forbidden checks and installed-component skipping.
- [x] Add in-app catalog import/list, source link, install/update action, SHA-256-verified HTTPS download, bounded ZIP extraction and atomic version promotion.
- [x] Verify catalog/planner JVM tests, full JVM suite and ARM64 APK in Actions run #400.
- [ ] Add signed catalog trust roots or explicit user-pinned catalog fingerprints before treating remote catalogs as trusted.
- [ ] Add archive handlers for TAR_GZ/TAR_XZ/TAR_ZSTD/RAW only with dedicated security tests; ZIP remains the only enabled installer format.
- [ ] Add installed component detail/rollback/uninstall UI and prevent removal of components referenced by profiles.
- [ ] Add component version compatibility checks and launch-readiness integration for per-game overrides.


### Component version lifecycle follow-up
- [x] Pass installed component version metadata into dependency planning so mismatched versions are treated as updates, not silently accepted.
- [x] Reactivate a previously stored version without re-downloading its archive; validate the stored tree and payload path before atomically changing the version pointer.
- [x] Add rollback and uninstall actions in the component manager; block uninstall while a profile/game override or installed dependent component references the component.
- [x] Verify stored-version activation and manager maintenance actions in Actions run #400.


## P1 — Controller mapping profiles
- [x] Add persisted controller profiles bound to Android device descriptors, with game-specific optional assignment.
- [x] Add validated key/axis/hat bindings, duplicate physical-input rejection, dead zones, per-stick sensitivity, inversion, stick curves, trigger threshold, vibration and touch layout tuning.
- [x] Add controller profile create/edit/delete UI and current connected-device status.
- [x] Verify controller profile JVM tests and Android build in Actions run #400.
- [ ] Add event-capture/remap wizard and per-game profile selection.
- [ ] Connect profiles to the native session input bridge; no mapping affects game input until this integration exists.


### CI repair
- [x] Normalize component install/activation branch results to a common typed outcome after CI caught Kotlin's incompatible branch result types.
- [x] Refresh controller profile connection status on InputManager hotplug events.
- [x] Latest integrated build passed in Actions run #400.


- [x] Fix controller enumeration to process Android's `IntArray` device IDs through a sequence.
- [x] Diagnose run #397: 126 JVM tests, one planner assertion failed; native ABI and registry checks passed, APK steps skipped.
- [x] Fix installed-ID-only planner compatibility while retaining explicit version mismatch updates (commit `255a4684614b69319b5328787b9ad4db725c01aa`).
- [x] Planner compatibility fix verified in Actions run #398; full integrated commit verified in run #400.

## P0 — Direct folder import (2026-11-18)
- [x] Copy SAF folder contents directly into private package staging; eliminate cacheDir payload copy and generated multi-GB NGRPACK for folder import.
- [x] Generate executable inventory and SHA-256 manifest in place; preserve multiple-executable selection without recopying payload.
- [x] Validate staged package and atomically promote with backup/rollback; restrict staging path to UUID child under private managed root.
- [x] Add JVM tests for direct folder preparation/install, manifest integrity and staging-path rejection.
- [x] Verify direct-import code and JVM tests in Actions run #409; inspect APK artifact ID 14422691411 and confirm ARM64 native library.
- [ ] Test large folder import on Android with constrained free storage; verify cancellation, interrupted import recovery and update rollback.

### Latest direct-import build
- [x] Actions run #409 passed native ABI, backend registry, JVM tests, ARM64 APK and artifact upload.
- [x] Downloaded and inspected NGR-arm64-debug; verified lib/arm64-v8a/libngr_runtime.so.
- [ ] Physical-device test of direct SAF import with a multi-GB game folder.


## P1 — Translator preset management
- [x] Add FEX Compatibility/Balanced/Performance preference to profile model and advance schema to v3.
- [x] Migrate v1/v2 profiles by defaulting missing FEX preset to Balanced.
- [x] Add Box64 and FEX preset controls to profile editor and details; mark as saved configuration only.
- [x] Include translator presets in resolved launch configuration and preview.
- [x] Verify profile schema migration, codec, resolver, native tests and ARM64 APK in CI run #429; downloaded and inspected artifact ID 14433695246.
- [ ] Implement verified runtime-specific option mapping only after pinning and auditing Box64/FEX versions; do not claim presets are active until a launch adapter consumes them.

## P0 — Direct folder import follow-up
- [x] CI run #409 passed direct-import JVM tests and ARM64 APK build.
- [x] CI run #411 passed full workflow and its APK artifact was downloaded and inspected.
- [ ] Test a multi-GB SAF folder on physical Android, including low-storage failure, user cancellation, process death and rollback.
- [x] Add startup cleanup for abandoned UUID staging directories older than 30 days; verify retention behavior in JVM test and CI run #429.


### Latest verified integrated build — CI #429
- [x] Native ABI conformance and backend registry tests passed.
- [x] JVM unit tests passed, including FEX schema migration and stale folder staging cleanup.
- [x] ARM64 Debug APK built and uploaded; artifact ID 14433695246 downloaded and inspected.
- [ ] Install APK and test SAF import of a multi-GB game folder on physical Snapdragon/Adreno hardware.


## P4 — N64 static recompiler (active)
- [x] Pin N64Recomp upstream commit `598ce2080004a5672d604dceadbda436045307b7`.
- [x] Build pinned N64Recomp and RSPRecomp host tools in CI.
- [x] Link the N64Recomp core into the Android ARM64 JNI runtime; verified by Actions run [44343916041](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44343916041).
- [x] Add JNI pipeline to parse supported N64 ELF and emit per-function C source, static-function discoveries, `funcs.h` and a machine-readable generation manifest.
- [x] Add Android “Recompile to C” action.
- [ ] Verify N64 code-generation and source-set repair in the current Android CI runs; run #44379837404 exposed the omitted `src/main/java` source root.
- [ ] Add synthetic, redistributable MIPS ELF fixture tests for actual generated C and error paths.
- [x] Include the pinned upstream `recomp.h` ABI header and N64Recomp license in generated output; add a README describing the missing title-runtime services.
- [ ] Add N64Recomp TOML configuration import and preserve required metadata/entrypoint.
- [ ] Provision Android ARM64 Clang and compile generated C against a real title runtime.
- [ ] Integrate a compatible N64 title runtime, including CPU/memory, RSP, graphics, audio, save data and platform services.
- [ ] Connect WinNative touch and gamepad input to the N64 runtime's controller API.
- [ ] Package generated ARM64 library + runtime assets as an installable NGR game package.
- [ ] Launch and validate a legally supplied test title on device. Do not mark launch complete before this test.


## N64Recomp implementation checkpoint — 2026-11-18
- [x] Add the recompiler screen to the WinNative drawer.
- [x] Add Android JNI code generation from supported N64 ELF to per-function C sources.
- [x] Follow N64Recomp-discovered static functions across sections and bound generated function/instruction counts and ROM offsets.
- [x] Add direct branch Android CI workflow: `.github/workflows/ngr-android-build.yml`.
- [x] Audit N64ModernRuntime input/renderer callback seams and GPL-3.0 provenance in `docs/ngr/N64_RUNTIME_INTEGRATION_AUDIT.md`.
- [ ] Confirm latest direct CI run green after fixing the missing std::filesystem::path declarations and adding shared shortcut registration.
- [x] Register successfully generated N64 projects in the shared GAME SHORTCUTS list with editable launch settings.
- [x] Show a disabled Launch control and an explicit runtime-pending state; do not attempt to launch C source files.
- [ ] Enable launch only after ARM64 library build, title runtime integration, package validation and device verification.
- [ ] Add synthetic MIPS ELF fixture and execute the actual NGR JNI/codegen path in a host test harness.
- [ ] Add N64Recomp metadata/configuration import (entrypoint, relocatable sections, manual sizes, references, ignored/stubbed symbols).
- [ ] Integrate N64ModernRuntime `librecomp` / `ultramodern` for Android without duplicate N64Recomp targets.
- [ ] Select and integrate a compatible N64 display-list renderer.
- [ ] Connect SDL/Vulkan surface lifecycle, WinNative controller/touch callbacks and audio callbacks.
- [ ] Compile generated C to ARM64 using a verified managed Clang toolchain and link the title runtime.
- [ ] Package title-specific runtime, generated library and legally supplied ROM/data.
- [ ] Device-test a legally supplied title; only then mark “launchable”.


## N64 recompiler build repair — 2026-11-18
- [x] Diagnose Kotlin source resolution failure: NGR sources were preserved under `app/src/main/java`, but WinNative's custom Gradle source roots excluded that directory.
- [x] Add the source root in Gradle and make the donor-import workflow restore it after every import.
- [x] Bundle upstream N64Recomp ABI header and license in generated C output.
- [ ] Verify latest CI, then add synthetic ELF code-generation regression tests.


## Recompiler intake / backend expansion
- [x] Add a dedicated Recompiler entry to the Library add-game dialog.
- [x] Add file, multi-file and folder selection with bounded recursive scan.
- [x] Add auto-detection for MIPS ELF and manual target-system selection.
- [x] Keep only N64/MIPS selectable until another backend is actually integrated.
- [x] Add persistent diagnostics for picker detection, SAF URI access, folder scans, N64 ELF import/inspection, C generation and shortcut registration; expose log viewer/share action.
- [x] Detect N64 .z64/.n64/.v64 ROM magic and explain that the current N64Recomp backend needs a MIPS ELF plus decompilation metadata, not a retail ROM directly.
- [x] Expand planned target catalog to PS3, PS4, Nintendo Switch, Nintendo Switch 2, Xbox, Xbox 360, Xbox One and Windows PC EXE; entries remain unavailable until real backends exist.
- [x] Scan selected folders for all catalogued extensions, not only .elf.
- [ ] Verify current CI and test .z64/.n64/.v64 detection, folder permissions and log export on Android.
- [ ] Add unit tests for N64 ROM byte-order probes and malformed headers.
- [ ] Implement a legal N64 ROM + decompilation-project metadata workflow before exposing ROM-to-native compilation.
- [ ] Add dedicated backend interfaces and implementations for each planned console/PC system; do not mark catalog entries available prematurely.



## Xbox 360 / XDroid360 integration
- [x] Audit `Snap888/XDroid360` fork and upstream architecture; record supported-title limits and license provenance.
- [x] Pin XDroid360 source at `f388d11fc207502cd5975899c8adc06600cfe34f` under `native/recompilers/XDroid360`; nested DAORecomp submodule requires recursive checkout.
- [ ] Verify recursive checkout and inspect nested submodule commit/license state in CI.
- [ ] Port XenonRecomp + XenonUtils/XEX as an NGR backend without the donor launcher UI.
- [ ] Port the pinned Clang/LLD toolchain packaging and version/hash compatibility checks.
- [ ] Adapt bounded recipe execution, extraction, progress, cancellation, resume and detailed per-step logs to NGR build jobs.
- [ ] Integrate a first title-specific runtime/profile (DAO or State of Decay); arbitrary Xbox 360 XEX/ISO must remain unsupported without a matching profile.
- [ ] Add NGR Android host adapters for Vulkan surface, audio/XMA, controller input, storage, lifecycle and per-game settings; reuse WinNative services instead of adding duplicate managers.
- [ ] Compile/link one legally supplied title to ARM64, validate its package, register a shortcut and verify actual launch on device before enabling the backend.
