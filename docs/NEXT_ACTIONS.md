# NGR — Next Actions

Mark complete only when evidence exists.

## P0 — Build and validate current Stage 2
- [x] Inspect GitHub Actions: Stage 2 run #38 is green; archive-inspector runs #40–#42 still require final verification.
- [x] Fix the observed MainActivity IntArray/mapNotNull Kotlin compile failure (commit eef1bfec); verify the fix in CI.
- [x] Fix the observed MainActivity compile failure; no remaining Stage 2 failure in run #38.
- [x] Confirm testDebugUnitTest passes in run #38.
- [x] Confirm NGR-arm64-debug artifact was uploaded in run #38 (download/device install not yet tested).
- [ ] Install APK on Snapdragon/Adreno device; record Vulkan and controller results.

## P1 — Harden package validation
- [x] Add manifest/schema parity and metadata-limit regression tests (run #37/#38 green).
- [x] Reject symlink package roots/components and non-regular payload files in extracted-package validator (run #38 green).
- [ ] Verify NgrPackArchiveInspector CI/tests; then implement bounded extraction with ZIP-slip protection, actual streamed-byte limits and no-follow file creation.
- [ ] Define unsigned development vs signed release trust policy.
- [ ] Define manifest version migration.
- [x] Require entryPoint to be present in the hashed files list (implementation and regression test committed; CI verification pending).

## P2 — Package manager
- [ ] Inspect and safely extract NGRPACK archives into app-private storage.
- [ ] Atomic install using staging directory, validation and rename.
- [ ] Package inventory, uninstall, update and rollback.
- [ ] Never auto-launch untrusted payloads.

## P3 — Runtime architecture
- [ ] Define stable C ABI for backend plugins and version negotiation.
- [ ] Add machine-readable capability query API.
- [ ] Add structured logging with session IDs and bounded files.
- [ ] Add backend lifecycle: probe, initialize, load, run, stop, destroy.
- [ ] Add crash/error reporting and cleanup rules.

## P4 — First recompiler proof of concept
- [ ] Pin N64Recomp upstream commit and review SPDX/transitive licenses.
- [ ] Obtain legally distributable test input and metadata.
- [ ] Build toolchain reproducibly in CI.
- [ ] Generate C/C++ from test input and compile for Android ARM64.
- [ ] Implement only runtime services required by that test.
- [ ] Validate correctness before performance tuning.

## P5 — Graphics and input expansion
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
