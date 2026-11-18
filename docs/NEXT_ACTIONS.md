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
- [ ] Verify run #313 (`44249549118`): JVM unit tests, ARM64 APK build and artifact upload. Do not continue to ABI loader until green.
- [ ] After green CI, download and verify `NGR-arm64-debug`; then implement ABI v1 host negotiation and validation.
