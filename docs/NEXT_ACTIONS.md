# NGR — Next Actions

Mark complete only when evidence exists.

## P0 — Build and validate current Stage 2
- [ ] Inspect latest GitHub Actions run for main.
- [ ] Fix compile/test failures before unrelated feature work.
- [ ] Confirm testDebugUnitTest passes.
- [ ] Confirm NGR-arm64-debug artifact exists and downloads.
- [ ] Install APK on Snapdragon/Adreno device; record Vulkan and controller results.

## P1 — Harden package validation
- [ ] Add schema parity tests for required manifest fields.
- [ ] Reject symlink escapes and non-regular files.
- [ ] Design safe archive extraction with ZIP-slip protection, entry-count/size and decompression-bomb limits.
- [ ] Define unsigned development vs signed release trust policy.
- [ ] Define manifest version migration.
- [ ] Require entryPoint to be present in the hashed files list.

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
