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
- [ ] Audit Box64/Box86 presets, FEXCore/FEX unixlibs, ARM64EC routing and WOWBox64 compatibility.
- [ ] Audit DXVK/VKD3D/D8VK/D7VK selection and component install/rollback.
- [ ] Audit AdrenoTools/Turnip provider selection and Vulkan isolation.
- [ ] Audit ReShade injection, shader catalogs, presets and texture packages; distinguish catalog availability from runtime support.
- [ ] Review GPL-3.0 and every transitive component license; confirm NGR licensing strategy before copying donor code.
- [x] Pin donor source audit to WinNative commit 99c3305d163780de5ca39b493aefcde436acad5c.
- [ ] Create component provenance/SPDX inventory for donor submodules, downloaded binaries and every runtime dependency.
- [x] Add versioned typed component catalog model with target ABI, SHA-256, SPDX license, source provenance, dependency/conflict constraints, safe install layout and rollback slot.
- [x] Add strict bounded catalog JSON parser, schema fixture and dependency-cycle validation.
- [x] Add dependency/ABI/profile compatibility resolver with dependency-first install order and conflict checks.
- [ ] Persist environment profiles, implement container roots/Wine prefix lifecycle and connect global/per-game overrides to runtime launch.
- [x] Implement bounded ZIP component archive inspection, SHA-256 verification, private snapshot, path-safe staging extraction and executable-bit preservation.
- [x] Add HTTPS-only component downloader with redirect downgrade protection, bounded streaming, timeouts, Content-Length validation and SHA-256 verification.
- [x] Add atomic component version promotion, active/previous state, rollback, inactive-version removal and uninstall for verified ZIP components.
- [ ] Add HTTPS downloader integration tests and TAR.GZ/TAR.XZ/TAR.ZSTD/RAW handlers with bounded extraction and expected-layout validation. Do not port WinNative ComponentInstaller ZIP extraction or direct ReShade extraction; audit found unsafe path handling and non-atomic replacement.
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
