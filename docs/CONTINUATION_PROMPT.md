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
- Active console targets are **Xbox 360 and Nintendo Switch only**. Xbox 360 is based on Snap888/XDroid360; Nintendo Switch is based on shirai91/nx2android. Do not add N64, Original Xbox, PlayStation, Xbox One, Switch 2 or generic PC/EXE targets.
- NGRPACK is a ZIP-compatible package with versioned manifest and SHA-256 hashes. Treat all package data as untrusted. Console build outputs need a separate profile-bound artifact/package contract.
- Never distribute copyrighted game data, ROMs, BIOS images, proprietary keys or game assets.

## Known handoff status (2026-11-21)

- Last observed `main` before this handoff refresh: `79b645d9097883a5692a85d47154cb3f1669de0f`; fetch the live branch SHA again before editing.
- Android CI #630 passed at commit `e60d9ca5bba5d0fce6c7d54b9533c2cfdd708b79`: Kotlin/JVM tests, native ABI/registry checks, CMake build including `libngr_switch_loader.so`, and ARM64 debug APK. Artifact `NGR-arm64-debug`, ID `14721940302`, archive SHA-256 `ed239a60f1efc0b71d513bd782d1538a8345436926b4277624ad2c95428296e`; extracted APK SHA-256 `1a94eb4784bc97380f3ca68e2e992efdec1fae05cb016e4015d6ef7403ab8eff`.
- Switch smoke #49 passed at commit `e60d9ca5bba5d0fce6c7d54b9533c2cfdd708b79`: all four pinned nx2android patches applied; donor `porter port --help` confirms the CLI flags NGR passes; theme/font staging and title-profiled ARM64 host build passed. Artifact `NGR-Switch-Runtime-Smoke`, ID `14721481165`, SHA-256 `2431a3de9e4ab68424d72ee9dd12d65df5f98a47ad77c0901758cbb2a4162c00`.
- Switch smoke #51 passed after the nested font-license copy/assertion changes; artifact ID `14721795180`.
- Active console scope remains Xbox 360 + Nintendo Switch only. Xbox 360 source staging exists; its XenonRecomp pipeline/runtime are not yet integrated.
- Switch integration includes:
  - pinned nx2android desktop adapter `tools/switch/port_game.py`, four donor patches; skips per-game APK generation and packages a per-title `libdmhost.so`, `game_profile.h`, data pack, controller theme/font/license notices and receipt;
  - NGR one-APK importer/library/NativeActivity dispatcher with per-title internal runtime/save/temp root and external game-data root;
  - SHA-256 checks for runtime and profile header; bounded SAF copy (64 GiB total, 32 GiB/file, 250k files, 500k entries, depth 128); install journal/recovery; background recovery/library listing;
  - donor `onDestroy` callback forwarding and delayed `dlclose`.
- Current Switch graphics limitation: adapter forces `--vulkan-driver system`. Optional nx2android Turnip/adrenotools packaging and integration with NGR's component store are not implemented.
- The complete NSP/XCI extraction/NSO rewrite/shader/host/data pipeline is desktop-assisted (Python 3.11+, .NET 10, NDK r27c, user's own keys/firmware). NGR Android does not run it on-device; it imports the prepared output folder.
- No real Switch dump has been ported/imported/launched in NGR. No physical-device game test exists. Do not call any title playable. The CI profile host is a smoke artifact, not a game port.
- Development journal: `docs/DEVELOPMENT_LOG.md`; active task list: `docs/NEXT_ACTIONS.md`; Switch design/usage: `docs/SWITCH_RUNTIME_INTEGRATION.md`.

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


## Legacy direction — WinNative runtime and Environment Manager (deferred; not active scope)
- Reuse WinNative/Winlator Bionic technology for a Wine compatibility backend, but do not collapse NGR into a WinNative fork.
- Earlier Wine/Box64/FEX execution-mode work is legacy foundation and deferred while the active Xbox 360 + Switch workstream is implemented.
- Environment Manager scope includes containers/Wine prefixes, Wine/Proton layers, Box64, FEXCore/FEX unixlibs, optional WOWBox64, DXVK, VKD3D-Proton, Turnip/Adreno drivers, ReShade/shader packs, environment variables, audio/input/display and per-game profiles.
- Standard x86_64 + Box64 and ARM64EC + FEX are distinct configurations with explicit compatibility checks.
- X11 is the first display path. Wayland is gated on a matching Wine/Proton winewayland driver and compatible Wayland Turnip libraries.
- Never replace Android system Vulkan libraries. Keep Turnip isolated.
- Catalog presence is not proof of runtime integration. ReShade activation must be audited and tested separately.
- WinNative is GPL-3.0; review NGR licensing and all component licenses before copying or linking code.
- Do not expand WinNative/Wine integration during the active console work. Historical notes remain in `docs/WINNATIVE_INTEGRATION_PLAN.md`.
- Current session only records architecture/docs; no donor source or binary was ported. First next task is file-by-file donor source audit and provenance/license matrix.


## Current continuation state — 2026-11-18
- Latest verified integrated CI/APK is run #429, source commit 03f369e5fed7bf657bca68c76674cdfe466030cd. It includes direct SAF folder import, Box64/FEX preset schema/editor/preview, and stale staging cleanup.
- Direct folder import now uses SAF -> private UUID staging -> in-place executable scan/SHA-256 manifest -> validated atomic promotion with rollback. NGRPACK/ZIP import paths remain separate and supported.
- Translator profile schema is now v3 in current source: Box64 preset already existed; FEX Compatibility/Balanced/Performance was added with v1/v2 migration defaulting to Balanced. Editor and launch preview expose both values.
- Translator presets are configuration intent only. They are not translated to Box64/FEX flags and do not affect a game process until a version-pinned runtime adapter exists.
- Latest translator preset commits: 522768655b7cc8c7fafd00a7f4110c51db479200, b5eaa86ee0dfb624bc1fd1615f97b39bf52ad3e9, 06f8b0a8fe561f8ad325e65e37786d331500f988, 14a959a9436b4a50d9071a6e0a322242c1170864, 6cc85681e6250162fbb7ce4b509e4ec615d793d4, 749480364aaa8d1960e10ffd51d225c4d8f12458, 41409ac1f1812a265680efb2850c184298eff417, 1946f9da99ee1403e149cd431967325c62da219b.
- Latest APK artifact ID 14433695246 was downloaded and inspected; APK SHA-256 5e3dd973b753f935939f9f0bf64ad70a9f072b556bda9c53b67429a801f79d44. It includes lib/arm64-v8a/libngr_runtime.so.
- Stale folder staging cleanup is implemented and tested: UUID-only real directories older than 30 days are removed at startup; recent directories are retained.
- Historical next step as of 2026-11-18: physical-device SAF import validation and Environment Manager completion. This is superseded by the explicit 2026-11-21 scope decision below; do not block Xbox 360/Switch recompiler work on the legacy Environment Manager checklist.


## Active continuation state — 2026-11-21: Xbox 360 + Nintendo Switch only
- Scope is locked to Xbox 360 and Nintendo Switch. No other console or PC/EXE target may be added.
- Read README.md, docs/XBOX360_SWITCH_INTEGRATION_PLAN.md, docs/NEXT_ACTIONS.md, docs/DEVELOPMENT_LOG.md and docs/LICENSING_AND_PROVENANCE.md before changing code.
- Donor source revisions audited: Snap888/XDroid360 commit 6eefb7f738d4e63cb11cb6bb63911f55094e8ada; shirai91/nx2android commit 828ded46adb11722591c4353a2ba88175640d250.
- Added app/src/main/java/com/ngr/app/recompiler/ConsoleRecompilerTargets.kt and matching JVM tests. Catalog has exactly Xbox 360 and Nintendo Switch and marks both RESEARCHED_NOT_INTEGRATED.
- Important Switch distinction: nx2android currently builds the APK/data pack on a desktop host; its Android runtime runs the resulting title natively. Do not claim on-device Switch recompilation until the .NET/LibHac, Python rewrite/shader and NDK build pipeline is proven on Android.
- Important Xbox distinction: XDroid360 uses an on-device XenonRecomp/Clang/LLD pipeline, but game runtime modules are title-specific (DAO/SoD/ME1); compiler output alone does not run arbitrary XEX games.
- Current code/docs commits are in main. CI for the newly added catalog has not yet been verified; inspect Actions before claiming a passing build or APK.
- Next task: verify JVM tests and arm64-v8a CI; then build the compiler job contract and start the Xbox 360 PPC fixture vertical slice. Switch work follows with NSO/NRO rewrite fixture and Android Horizon/NVN Vulkan runtime.
- Never commit user game data, firmware, prod.keys/title keys, generated copyrighted game code or proprietary shader assets. Redact keys and sensitive paths from logs.
