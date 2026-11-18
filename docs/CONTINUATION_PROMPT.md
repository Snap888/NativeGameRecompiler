# Continuation Prompt — NativeGameRecompiler

Copy this prompt into a new ChatGPT conversation.

---

You are continuing active development of Native Game Recompiler (NGR).

Repository: https://github.com/Snap888/NativeGameRecompiler
Owner: Snap888
Primary branch: main
Target: Android ARM64, Qualcomm Adreno GPUs
Product: Android app/runtime for launching game builds that have been legally and successfully recompiled or adapted for NGR. Do not claim universal compatibility.

## Mandatory first steps in every new chat
1. Inspect the live GitHub repository and latest main commit.
2. Read README.md, docs/DEVELOPMENT_LOG.md, docs/NEXT_ACTIONS.md, docs/TECHNICAL_SPEC.md and relevant ADRs.
3. Inspect actual source files before editing; do not rely only on this prompt or old summaries.
4. Check GitHub Actions status, logs and artifacts for the latest commit. If unavailable, explicitly mark CI unverified.
5. Update DEVELOPMENT_LOG.md with current status before substantial changes.
6. Continue the highest-priority incomplete item in NEXT_ACTIONS.md. Do not bypass build failures to add unrelated features.

## Architecture and constraints
- Android host + Kotlin UI + JNI + C++20 native runtime.
- Initial ABI: arm64-v8a.
- Qualcomm Adreno is the first GPU target.
- Use Android system Vulkan loader first. Never overwrite system Vulkan libraries.
- Turnip is a future optional isolated per-game provider, not a system replacement.
- DXVK (D3D8-11) and VKD3D-Proton (D3D12) are future integrations, not currently integrated.
- LLVM is compiler infrastructure, not a ready-made game recompiler.
- FEX and Box64 are future dynamic translation backends, not static recompilers.
- First bounded static recompiler experiment: N64/MIPS with N64Recomp, only after dependency/license review and obtaining a legally usable test input.
- NGRPACK is a future ZIP-compatible package with versioned manifest and SHA-256 hashes. Treat all package data as untrusted.
- Never distribute copyrighted game data, ROMs, BIOS images, proprietary keys or game assets.

## Known handoff status (2026-11-17)
- Latest implementation commit: `d4799d8c0ca0deed9395fa58bedc75fbea0d39ca`.
- Stage 1: Android Activity, JNI, C++20 runtime, Vulkan physical-device probe, gamepad discovery and CI workflow.
- Stage 2: native capability registry, JNI report, strict extracted-package manifest validator and JVM regression tests. CI passed.
- Stage 3: read-only NGRPACK ZIP inspector, bounded staging extraction, streamed size checks, full manifest/SHA-256 validation, atomic install/update with rollback/recovery, package inventory/removal and Android Document Picker UI. CI run #56 passed.
- Latest artifact: `NGR-arm64-debug`, Actions run #71, artifact ID `14364007052`, archive SHA-256 `caeb05249b81f9543cd9868601419e59ce14deec5c41c647ac6680a26af261fc`.
- Backend execution ABI v1 C contract is defined in `native/ngr-runtime/include/ngr/backend_abi_v1.h`, with C/C++ compile checks and ADR 0003. Loader, runtime negotiation, registry and conformance tests are not implemented; capability remains planned.
- Physical-device validation remains pending. No static/dynamic game backend or game execution is implemented.
- Package signing/trust policy and sandbox are not implemented; never execute package payloads.
- Next priority: install/test APK on Snapdragon/Adreno device, then define signed package trust policy and stable backend ABI.

## Working rules
- Audit first, then implement in small reviewable commits.
- Work in Snap888/NativeGameRecompiler, normally main unless asked for a feature branch.
- Fetch current file contents and blob SHA before updates. Never overwrite from stale copies.
- After each stage update DEVELOPMENT_LOG.md and NEXT_ACTIONS.md, run available tests/build, inspect CI logs and report exact commit and artifact status.
- Distinguish implemented, partially implemented, planned and verified.
- Never invent commits, CI runs, APKs, test results or links.
- Do not mix this project with WinNative or ANLR.
- If a tool limitation blocks a task, state it and do not pretend it succeeded.

## Current user instruction
Continue implementation from the live repository. Maintain the development journal and next-action list. End with changes, exact commits, build/test status, known issues and the next task.


## Updated direction — WinNative runtime and Environment Manager
- Reuse WinNative/Winlator Bionic technology for a Wine compatibility backend, but do not collapse NGR into a WinNative fork.
- NGR must support three independent execution modes: Wine compatibility, static recompiled ARM64 and native ARM64.
- Environment Manager scope includes containers/Wine prefixes, Wine/Proton layers, Box64, FEXCore/FEX unixlibs, optional WOWBox64, DXVK, VKD3D-Proton, Turnip/Adreno drivers, ReShade/shader packs, environment variables, audio/input/display and per-game profiles.
- Standard x86_64 + Box64 and ARM64EC + FEX are distinct configurations with explicit compatibility checks.
- X11 is the first display path. Wayland is gated on a matching Wine/Proton winewayland driver and compatible Wayland Turnip libraries.
- Never replace Android system Vulkan libraries. Keep Turnip isolated.
- Catalog presence is not proof of runtime integration. ReShade activation must be audited and tested separately.
- WinNative is GPL-3.0; review NGR licensing and all component licenses before copying or linking code.
- Read `docs/WINNATIVE_INTEGRATION_PLAN.md` before continuing this work.
- Current session only records architecture/docs; no donor source or binary was ported. First next task is file-by-file donor source audit and provenance/license matrix.


## Current continuation state — 2026-11-18
- Latest verified CI/APK is run #411, commit 0d1f3c1332f455ffedcc58ef2c4c4546606d439d. It includes direct SAF folder import, but not the newer translator preset changes.
- Direct folder import now uses SAF -> private UUID staging -> in-place executable scan/SHA-256 manifest -> validated atomic promotion with rollback. NGRPACK/ZIP import paths remain separate and supported.
- Translator profile schema is now v3 in current source: Box64 preset already existed; FEX Compatibility/Balanced/Performance was added with v1/v2 migration defaulting to Balanced. Editor and launch preview expose both values.
- Translator presets are configuration intent only. They are not translated to Box64/FEX flags and do not affect a game process until a version-pinned runtime adapter exists.
- Latest translator preset commits: 522768655b7cc8c7fafd00a7f4110c51db479200, b5eaa86ee0dfb624bc1fd1615f97b39bf52ad3e9, 06f8b0a8fe561f8ad325e65e37786d331500f988, 14a959a9436b4a50d9071a6e0a322242c1170864, 6cc85681e6250162fbb7ce4b509e4ec615d793d4, 749480364aaa8d1960e10ffd51d225c4d8f12458, 41409ac1f1812a265680efb2850c184298eff417, 1946f9da99ee1403e149cd431967325c62da219b.
- Next: check the newest CI after these changes; fix failures; verify and inspect its APK. Then add abandoned staging cleanup and prepare a physical-device import test matrix. Do not start game execution/recompiler implementation before the Environment Manager completion gate in WINNATIVE_INTEGRATION_PLAN.md.
