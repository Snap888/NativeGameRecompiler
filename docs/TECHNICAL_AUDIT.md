# NGR — Initial Technical Audit

## Scope

Target: Android ARM64 with Qualcomm Adreno. "All platforms / any games" is a long-term research objective, not something a single translator can guarantee. Each title needs a supported CPU ISA, executable format, OS ABI, system API, graphics path and often game-specific integration.

## Candidate technology roles

| Technology | Intended role | Boundary |
|---|---|---|
| XDroid360 / XenonRecomp | Xbox 360 PowerPC-to-C++ preparation pipeline | Needs title-specific runtime, kernel/XAM, Xenos graphics and XMA audio; compiler alone is not a playable backend |
| nx2android / nxnative | Nintendo Switch NSO/NRO rewrite and Android Horizon/NVN runtime | Desktop-assisted preparation currently; per-title profiles and Vulkan feature support are mandatory |
| LLVM | IR, optimization and ARM64 code generation | Not a turnkey game recompiler |
| FEX / Box64 | Future dynamic x86-64 Linux translation backends | Not static recompilers; syscall/library compatibility is substantial work |
| QEMU | Diagnostic/reference execution backend | Performance and integration constraints |
| DXVK | D3D8–11 to Vulkan | Requires suitable Vulkan support and Wine integration |
| VKD3D-Proton | D3D12 to Vulkan | Later milestone; validate required device features first |
| Mesa Turnip | Optional Adreno Vulkan provider | Isolate per game; never replace Android system Vulkan libraries |
| GameActivity | Android lifecycle/input foundation | Evaluate when a native render loop is introduced |
| Oboe | Low-latency Android audio | Later milestone |

## Active scope decision (2026-11-21)

Only Xbox 360 and Nintendo Switch are in scope. Their detailed source audit and integration plan is [docs/XBOX360_SWITCH_INTEGRATION_PLAN.md](XBOX360_SWITCH_INTEGRATION_PLAN.md). Other console platforms are excluded from active development.

## Stage 1 result

A minimal host/runtime shell: Kotlin Activity, JNI bridge, C++20 core, Vulkan physical-device enumeration and Android controller discovery. No game execution or recompilation is implemented yet.

## Risks

1. Binary formats and legal distribution rights vary by platform and title.
2. CPU translation does not implement an OS ABI.
3. Graphics translation needs extension/feature validation and correct synchronization.
4. Games may depend on custom filesystem, timing, threading, audio and platform services.
5. Driver packaging must be isolated per title.
6. Review SPDX licenses, transitive dependencies and pinned revisions before vendoring.

## Validation gate

Build the arm64 debug APK, install it on a Snapdragon/Adreno device, and verify Vulkan enumeration and controller hotplug before adding a game backend.
