# NGR — Next Actions

Mark complete only when evidence exists.

## Active scope lock — 2026-11-21
- Active console targets: **Xbox 360 and Nintendo Switch only**.
- Donor foundations: Snap888/XDroid360 and shirai91/nx2android.
- N64, Original Xbox, PlayStation, Xbox One, Switch 2 and generic PC/EXE recompilation are out of scope for this workstream.
- Earlier Wine/Box64/FEX checklist items are legacy foundation and deferred; do not expand them while implementing the two console targets.
- Full design: [docs/XBOX360_SWITCH_INTEGRATION_PLAN.md](XBOX360_SWITCH_INTEGRATION_PLAN.md).

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

## P4 — Xbox 360 + Nintendo Switch static recompilers
- [x] Lock the active console scope to Xbox 360 and Nintendo Switch only.
- [x] Audit the writable Snap888/XDroid360 fork, its on-device recipe runner, Android toolchain, DAORecomp submodule and NOTICE.
- [x] Audit nx2android porter, NSO/NRO rewrite, Horizon host, Android Vulkan/NVN runtime and desktop preparation requirements.
- [x] Add typed ConsoleRecompilerTargets catalog containing exactly Xbox 360 and Nintendo Switch.
- [x] Add JVM tests for target exclusivity, accepted formats, preparation modes and key requirements.
- [x] Add docs/XBOX360_SWITCH_INTEGRATION_PLAN.md with source revisions, architecture boundaries, licensing and staged acceptance criteria.
- [x] Compile the target catalog in isolation with kotlinc and run runtime assertions (two-target exclusivity, file detection, key requirement).
- [x] Full Gradle JVM tests and arm64-v8a APK CI passed in run #483 at commit d8b1a08a85902b407bee92f513b1d6cc719acb9b; latest SAF unknown-size fix is under run #484.
- [x] Implement compiler job contract separately from execution Backend ABI v1 (progress, cancel, process-death recovery, bounded/redacted logs, quotas).
- [x] Connect the job store to the SAF import UI with persistent read grants and a visible cancellable queue.
- [x] Add WorkManager-backed foreground source intake: private staging copy, SHA-256, size/free-space bounds, target format checks and ZIP path inspection.
- [ ] Verify background source intake and cancellation on a physical Android device.
- [ ] Implement compiler workers; current WorkManager worker only validates and stages source bytes, it does not invoke compiler tools.
- [ ] Add immutable source/toolchain/profile fingerprints and per-title profile validation.
- [ ] Xbox 360: pin XDroid360 recomp submodule, XenonRecomp patch, Clang/LLD, DAO/SoD runtime and shader dependencies; finish SPDX/SBOM review.
- [ ] Xbox 360: build Android-hosted XenonRecomp + Clang/LLD in CI; validate a non-copyrighted PPC fixture.
- [ ] Xbox 360: bring up a matching title runtime and Xenos Vulkan path before claiming a playable game.
- [x] Switch: pin nx2android donor revision `f32b3d563f0615d0e825747285d4e38d780e4ebb` for the initial embedded-runtime adapter; donor submodules/dependencies still require a complete SPDX/SBOM lock.
- [x] Switch: add a desktop-assisted `port_game.py` adapter that invokes the real nx2android porter, skips the donor APK step, and assembles runtime + data into an NGR-importable package.
- [ ] Switch: verify the no-APK patch applies cleanly and the pinned donor host builds in CI; verify NGR Android NativeActivity loader compiles in the same run.
- [ ] Switch: validate NSO/NRO rewrite and shader conversion with synthetic fixtures and record toolchain/profile fingerprints in a complete package manifest.
- [ ] Switch: import a generated package into NGR, test NativeActivity lifecycle/surface/audio/input, and launch a supported title on Adreno hardware.
- [x] Decide first Switch preparation path: desktop-assisted nx2android pipeline with package import into NGR; Android-hosted preparation remains unimplemented and must not be advertised as available.
- [ ] Add user-owned Switch key storage with strict redaction; never package keys, firmware or game data.
- [ ] Add only Xbox 360 and Switch import flows; do not add other console targets.

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


## P4 — Xbox 360 + Nintendo Switch static recompilers
- [x] Lock the active console scope to Xbox 360 and Nintendo Switch only.
- [x] Audit the writable Snap888/XDroid360 fork, its on-device recipe runner, Android toolchain, DAORecomp submodule and NOTICE.
- [x] Audit nx2android porter, NSO/NRO rewrite, Horizon host, Android Vulkan/NVN runtime and desktop preparation requirements.
- [x] Add typed ConsoleRecompilerTargets catalog containing exactly Xbox 360 and Nintendo Switch.
- [x] Add JVM tests for target exclusivity, accepted formats, preparation modes and key requirements.
- [x] Add docs/XBOX360_SWITCH_INTEGRATION_PLAN.md with source revisions, architecture boundaries, licensing and staged acceptance criteria.
- [x] Compile the target catalog in isolation with kotlinc and run runtime assertions (two-target exclusivity, file detection, key requirement).
- [ ] Run full Gradle JVM tests and arm64-v8a APK CI for the target catalog.
- [ ] Implement compiler job ABI separately from execution Backend ABI v1 (progress, cancel, resume, bounded logs, quotas).
- [ ] Add immutable source/toolchain/profile fingerprints and per-title profile validation.
- [ ] Xbox 360: pin XDroid360 recomp submodule, XenonRecomp patch, Clang/LLD, DAO/SoD runtime and shader dependencies; finish SPDX/SBOM review.
- [ ] Xbox 360: build Android-hosted XenonRecomp + Clang/LLD in CI; validate a non-copyrighted PPC fixture.
- [ ] Xbox 360: bring up a matching title runtime and Xenos Vulkan path before claiming a playable game.
- [ ] Switch: pin nx2android/nxnative and all third-party revisions; finish SPDX/SBOM review.
- [ ] Switch: build nx_extract/LibHac and shader tools reproducibly on a supported desktop CI host; validate NSO/NRO rewrite with a synthetic fixture.
- [ ] Switch: build Android ARM64 Horizon/NVN Vulkan runtime and load a profile-matched test package.
- [ ] Decide desktop-assisted Switch preparation vs a separately validated Android-hosted port pipeline; do not label it on-device until proven.
- [ ] Add user-owned Switch key storage with strict redaction; never package keys, firmware or game data.
- [ ] Add only Xbox 360 and Switch import flows; do not add other console targets.


## Compiler job manager — 2026-11-21
- [x] Add typed job metadata for Xbox 360 and Nintendo Switch, SAF content URI + grant flags, source/profile/toolchain SHA-256 fingerprints, workspace ID, stage, progress, state and timestamps.
- [x] Add target-specific stage sequences (PPC recompilation vs NSO rewrite/shader build).
- [x] Add atomic private persistence, 256-job quota, schema validation, immutable source identity, write-once fingerprints and monotonic progress/stages.
- [x] Add cancellation states, queued-job cancellation and process-death recovery for interrupted workers.
- [x] Add bounded per-job logs with control-character cleanup and redaction for key/password/token assignments.
- [x] Add JVM regression tests for both targets, URI/path validation, state transitions, recovery, log redaction and corrupt metadata.
- [x] Full Gradle JVM tests and arm64-v8a APK CI passed in run #467 (44914879736) at a83cb286057b40cda4f5ccd59a1d71246c0ce0cb; artifact NGR-arm64-debug ID 14711137187.
- [x] Connect SAF permission acquisition and job creation to the Android import UI; file extensions are validated against the selected console.
- [ ] Implement the actual Xbox 360 worker and synthetic PPC fixture; the job manager does not execute compiler tools.


### Compiler job manager CI verification — 2026-11-21
- [x] Actions run #467 (44914879736) passed native ABI conformance, backend registry, full JVM unit tests, ARM64 debug APK build and artifact upload at commit a83cb286057b40cda4f5ccd59a1d71246c0ce0cb.
- [x] Artifact NGR-arm64-debug, ID 14711137187, downloaded and inspected. ZIP SHA-256: ca754e23c03f9d433a90155494b0630d1856a61417ba421c6c9d5ff51b37b60e.
- [x] Extracted APK SHA-256: bd0a984bdd7c0a27c45f9785a949810fdbcae3372a8c74fadc882b381725ed4b; APK contains lib/arm64-v8a/libngr_runtime.so.
- [ ] Install and smoke-test this APK on the target Snapdragon/Adreno device.


### Console build queue UI CI verification — 2026-11-21
- [x] Actions run #470 (44915313963) passed native ABI conformance, backend registry, full JVM unit tests, ARM64 debug APK build and artifact upload at commit 36d464ba65be57e4896612535d905f771cf08f07.
- [x] Artifact NGR-arm64-debug, ID 14711342035. ZIP SHA-256: c9758a075144fd4f3406dbbeb81f907f37800b5c92bbb96ba0cccfbf95aae29b.
- [x] Extracted APK SHA-256: a01790c140a4a8ab7755bb86306617d58bc5052c47a904503ffa7b98aa2078e2; contains lib/arm64-v8a/libngr_runtime.so.
- [ ] Install the queue UI APK on Snapdragon/Adreno hardware and test SAF grants across app restarts.


### Xbox 360 compiler toolchain build — 2026-11-21
- [x] Pin XenonRecomp source commit and XDroid360 patch commit + SHA-256 in a reproducible Android arm64 build script.
- [x] Add an isolated GitHub Actions workflow that builds the Android API 29 / arm64-v8a / 16 KB-page executable and uploads its corresponding source and license materials.
- [x] Verify the XenonRecomp Android cross-build and artifact contents in Actions run #1 (44915893900); artifact NGR-Xbox360-XenonRecomp-arm64 ID 14712235777.
- [ ] Build the Android Clang/LLD kit and run a synthetic PPC-to-ARM64 fixture; XenonRecomp alone is not a playable runtime.


### XenonRecomp Android ARM64 build verified — 2026-11-21
- Actions run 44915893900 passed. Artifact ID 14712235777 contains the AArch64 Android 29 executable, patched corresponding source archive, MIT notices, GPL-2.0 text, provenance and SHA-256 manifest.
- XenonRecomp executable SHA-256: 1ae719f34b888fc15d113e700ec4e695ef826ecf0b2399cfb733bfe474390fe2.
- Source archive SHA-256: 31af71f8eed4858551099c879d94778c5b1e10ba209651fda3baac751cc0d024.
- ELF: AArch64, Android API 29, NDK r27d, dynamically linked only against libm.so, libdl.so and libc.so; 16 KB page compatibility enabled.
- [ ] Run a synthetic PPC fixture through XenonRecomp and compile its generated C++ with Android Clang/LLD.


### Xbox 360 Clang/LLD Android kit — 2026-11-21
- [x] Add pinned LLVM 22.1.8 source hash and NDK r27d build script for Android-hosted Clang/LLD plus matching sysroot/resource headers.
- [x] Add isolated 180-minute GitHub Actions workflow; first build result pending.
- [ ] Verify Clang/LLD Android cross-build, 16 KB ELF and kit contents.
- [ ] Use the tools to compile generated C++ from a synthetic PPC fixture.


### Synthetic Xbox 360 PPC fixture — 2026-11-21
- [x] Add a redistributable synthetic XEX2 fixture generator (no commercial game data) with one PPC function: addi + blr.
- [x] Add a host XenonRecomp workflow to translate the fixture and verify generated PPC C++ files and entry-point symbols.
- [ ] Confirm the synthetic translation workflow passes; then compile the generated C++ with the Android Clang/LLD kit.


## Source staging worker — 2026-11-21
- [x] Add durable WorkManager unique work per console build job and foreground notification for large source copies.
- [x] Copy selected SAF source into an app-private per-job workspace; compute SHA-256 while streaming.
- [x] Enforce 64 GiB source limit, free-space reserve, source extension checks, non-empty/size consistency and cancellation checkpoints.
- [x] For Xbox ZIP, inspect the central directory, reject traversal-like entry paths and require at least one ISO. Do not extract the archive yet.
- [x] For Switch, stage bounded NSP/XCI/NSZ/XCZ bytes only; cryptographic/package parsing remains assigned to the pinned nx2android toolchain.
- [x] Add source-staged lifecycle stage and restart-safe worker re-entry.
- [ ] Run Gradle/JVM and arm64 APK CI; resolve all compile/test failures before calling this stage verified.
- [ ] Device-test 4-8 GB ISO and Switch package staging, process death, cancellation, low-storage and provider permission revocation.
- [ ] Implement Xbox extraction and XenonRecomp only after staging validation.


## Source staging CI verification — 2026-11-21
- [x] Run #483 (44917831975), commit d8b1a08a85902b407bee92f513b1d6cc719acb9b: native ABI conformance, backend registry tests, all JVM tests, ARM64 debug APK and artifact upload passed.
- [x] Artifact NGR-arm64-debug ID 14712576422; ZIP SHA-256 e5a0b9075bd0000bd12e57e6b7c3f94500438476ee29e3e673ef3c7c457726bf.
- [x] Extracted APK SHA-256 d581caae4745d5252e17190842d847842e5bb53ff7105af0f6ca2a4535a1f762; includes lib/arm64-v8a/libngr_runtime.so and WorkManager runtime metadata.
- [x] Run #484 (44917991394) passed all native/JVM tests, ARM64 APK build and artifact upload at commit 2eb0757b84f8288a53d3cbe73c0531b139ba9f85. Artifact ID 14712008705; ZIP SHA-256 068bfaa3e551f9c7623f331d41e537bd9f59f4e2d8349675210b246c56e35f79; APK SHA-256 93feb3b6efd730dbbd59a29810480380fb0e18595fd5eb3f912d428fa5d37087.
- [ ] Physical-device test on Snapdragon/Adreno: SAF persistent grant, multi-GB source copy, notification, cancellation, restart and low-storage behavior.

 
## Nintendo Switch embedded runtime — 2026-11-21
- [x] Add pinned nx2android runtime build adapter; invoke donor `dm/android/build.py` and store `libdmhost.so` under NGR per-title package without creating an APK.
- [x] Add runtime build receipt and SHA-256/donor revision/ABI/entry-point checks before NativeActivity launch.
- [x] Add dedicated CI workflow to build a profile-only nx2android runtime from the pinned donor revision.
- [ ] Verify donor runtime smoke workflow; fix build/toolchain errors.
- [ ] Verify NGR Android CI compiles `ngr_switch_loader` and packages the NativeActivity.
- [ ] Add a synthetic runtime library fixture and test create/pause/resume/destroy/surface lifecycle forwarding.

- [x] Audit the current nx2android host build and APK writer: the reusable per-title runtime output is `libdmhost.so`; APK generation is a packaging wrapper, not the runtime itself.
- [x] Add the first in-package NativeActivity loader and register it in the NGR manifest.
- [x] Add Kotlin launch/path validation for per-title runtime libraries stored under NGR private files.
- [x] Add JVM tests for title ID and runtime path validation.
- [ ] Verify the new CMake target and NativeActivity manifest in Android CI.
- [ ] Add a synthetic `libdmhost.so` fixture to verify NativeActivity lifecycle forwarding.
- [ ] Add a signed/hash-verified Switch Port Package manifest and atomic installer.
- [ ] Pin/vendor nxnative and all required dependency/submodule revisions; generate SPDX/SBOM and preserve notices.
- [ ] Integrate nx2android extraction, NSO rewrite, shader translation and title-specific host build; start with desktop/CI-assisted preparation.
- [ ] Launch a profile-matched game through NGR's embedded runtime on a physical Adreno device.

- [ ] Check CI triggered by commit `3b44bee839cc93db3c02ed43445c01071a6adbfe`; confirm both Android loader build and donor runtime smoke build.
- [ ] If donor smoke build fails, inspect exact NDK/toolchain or donor dependency error and fix adapter/workflow.


## Nintendo Switch — embedded nx2android runtime

- [x] Audit nx2android NativeActivity, per-title libdmhost.so build and separate data-pack output at pinned commit f32b3d563f0615d0e825747285d4e38d780e4ebb.
- [x] Add NGR NativeActivity loader target and register the embedded activity in NGR's manifest.
- [x] Add fail-closed nx2android patcher to select a separate NGR data root for each title ID.
- [x] Add per-title runtime/data path validation and in-process launch intent.
- [x] Add prepared-port folder importer with ELF64/AArch64 and SHA-256 validation, staging and rollback.
- [x] Add Switch library UI with import and launch actions (same NGR APK; no separate game APK).
- [x] Add manual workflow to build a pinned title-specific libdmhost.so for an nx2android Android profile.
- [ ] Run Android Gradle, JVM and native CI after the integration commits; fix every failure.
- [ ] Run switch-runtime workflow for a known supported Android profile and verify the artifact.
- [ ] Add an nx2android port-bundle packager (runtime + data pack + manifest + required per-title assets).
- [ ] Integrate the actual NSP/XCI -> LibHac extraction -> NSO rewrite -> shader translation -> data pack pipeline.
- [ ] Decide and implement on-device or supported companion execution for Python/.NET/LibHac dependencies.
- [ ] Add per-title gamepad theme/font assets to the in-process NativeActivity.
- [ ] Physical-device test on Adreno: import, launch, Vulkan, audio, controls, lifecycle, saves and two-title isolation.



## Nintendo Switch validation checkpoint — 2026-11-21
- [x] Android CI #585: JVM/native tests, NGR NativeActivity loader compile, ARM64 APK and artifact upload passed (artifact ID 14720757607).
- [x] Switch smoke #38: sequentially apply all four pinned nx2android patches and compile the patched profile-specific ARM64 NativeActivity runtime (artifact ID 14720643069).
- [x] Package the donor's per-title controller theme and shared licensed overlay font in each NGR Switch data pack; load them through the NGR per-title data root.
- [x] Require theme/font assets during import, library enumeration and native launch validation.
- [x] Bound SAF data-pack copying (64 GiB total, 32 GiB/file, 250k files, 500k entries, depth 128) and add unit tests.
- [ ] Verify the latest Android CI after copy-budget implementation.
- [ ] Add a durable install journal and recovery for interrupted two-root runtime/data replacement.
- [ ] Add a synthetic NativeActivity host and lifecycle forwarding test.
- [ ] Run the full donor `porter port` pipeline with a user-owned supported title; verify package output and that no game APK is generated.
- [ ] Import and launch on a physical Adreno device; verify graphics, sound, input, saves and title isolation.
- [ ] Decide and implement the on-device or companion execution path for Python/.NET/LibHac; desktop-assisted preparation is the current boundary.
