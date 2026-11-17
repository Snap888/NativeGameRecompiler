# NGR — Next Actions

Mark complete only when evidence exists.

## P0 — Device validation
- [x] Restore Stage 2 CI: JDK 17, Android SDK, NDK/CMake, Gradle, JVM tests and arm64-v8a APK all pass.
- [x] Confirm NGR-arm64-debug artifact upload in Actions run #56.
- [ ] Install latest diagnostics APK on Snapdragon/Adreno Android device.
- [ ] Verify app launch, Vulkan physical-device enumeration and controller hotplug.
- [ ] Exercise NGRPACK, folder import and ordinary ZIP import/list/update/remove and restart recovery on device; capture exported log for any failure.

## P1 — NGRPACK trust and security
- [x] Manifest schema parity, strict metadata typing and unknown-property rejection.
- [x] Reject path traversal, slash/backslash collisions, symlink roots/components and non-regular files.
- [x] Reject unlisted payload files; require entryPoint in the hashed file list.
- [x] ZIP central-directory inspection, symlink/special-file rejection, ZIP Slip defense and archive/entry/manifest/total-size/compression-ratio limits.
- [x] Bounded extraction into app-private staging and streamed-byte rechecks.
- [x] Full post-extraction SHA-256 and manifest validation.
- [x] Atomic directory replacement with backup/rollback and interrupted-install recovery.
- [x] Add Android SAF folder picker and ordinary ZIP picker alongside the existing NGRPACK importer.
- [x] Add bounded folder/ZIP scanner, Windows PE and Linux ELF detection, automatic manifest generation and SHA-256 inventory.
- [x] Use deterministic package IDs so repeated imports update the same library entry.
- [x] Verify folder/ZIP import builder and Android UI in CI run #108; JVM tests, arm64-v8a APK build and artifact upload passed.
- [x] Verify deterministic package identity and repeat-import update path in CI run #113; APK artifact uploaded.
- [ ] Device-test importing the user's Lethal Company ZIP and selecting a valid game root/entry point.
- [x] Package inventory, update and removal; corrupt package folders remain safely removable.
- [ ] Review local-header vs central-directory consistency, malformed UTF-8 names and archive edge cases.
- [ ] Add explicit primary-executable selection when multiple PE/ELF candidates are detected.
- [ ] Define manifest version migration.
- [ ] Define unsigned development vs signed release trust policy and signature verification before any code execution.

## P2 — Backend ABI and runtime
- [x] Define ABI v1 C contract for execution backends and compile-check public header as C and C++ (CI #71 green).
- [ ] Implement host-side ABI negotiation and validation (major/minor, struct sizes, IDs, required callbacks).
- [ ] Add trusted built-in/test backend fixture; never load NGRPACK game payloads as plugins.
- [ ] Implement backend registry and lifecycle: probe, create, load, start, request_stop, wait, destroy.
- [ ] Add conformance tests for ABI mismatch, truncated structs, missing callbacks, invalid string views and stop/destroy ordering.
- [x] Add structured logging with session IDs, bounded persistent files, in-app viewer and SAF export (CI verification pending).
- [ ] Add native crash/error reporting and cleanup rules.
- [ ] Do not mark plugin ABI available until loader and conformance tests pass.

## P3 — First static recompiler proof of concept
- [ ] Pin N64Recomp upstream commit and review SPDX/transitive licenses.
- [ ] Obtain legally distributable test input and metadata.
- [ ] Build toolchain reproducibly in CI.
- [ ] Generate C/C++ from test input and compile for Android ARM64.
- [ ] Implement only runtime services required by that test.
- [ ] Validate correctness before performance tuning.

## P4 — Graphics and input expansion
- [ ] Add Vulkan extension/feature report.
- [ ] Design per-game graphics-provider selection and fallback.
- [ ] Evaluate isolated Turnip loading without replacing system libraries.
- [ ] Evaluate GameActivity and Oboe when render/audio loops exist.
- [ ] Add actual touch mapping after first runnable backend.

## Definition of done
- Code committed to correct repository and branch.
- Tests/build run and result recorded.
- Limitations and follow-up tasks recorded in DEVELOPMENT_LOG.md.
- No fabricated success claims or unverified APK links.
