# Native Game Recompiler (NGR)

Native Game Recompiler is an Android ARM64 runtime and modular game-recompilation research project targeting Qualcomm Adreno GPUs.

> **Current status:** Stage 1 runtime shell, Stage 2 capability/manifest validation, and Stage 3 NGRPACK inspection + package install manager are implemented and CI-built. Physical-device validation and all game execution backends remain pending. NGR is not a general-purpose game recompiler.

## Project direction

- Android host application with Kotlin UI and a native C++20 runtime core.
- ARM64 as the initial Android target.
- Modular backends for static recompilation and dynamic translation.
- Vulkan graphics-provider abstraction; system Vulkan loader first, optional isolated per-game Turnip integration later.
- Touch controls and physical gamepad discovery.
- Versioned NGRPACK package manifest with SHA-256 payload hashes.

## Implemented foundation

- Kotlin Activity + JNI bridge.
- C++20 runtime information and Vulkan physical-device enumeration.
- Android gamepad/joystick discovery.
- Native capability registry with explicit available/planned status.
- Strict Kotlin manifest validator: schema-aligned metadata, path safety, symlink/non-regular file rejection, complete payload inventory and SHA-256 checks.
- Read-only NGRPACK ZIP inspector: ZIP Slip, Unix symlink/special-file, duplicate path, entry count, archive/manifest/payload size and compression-ratio checks.
- Bounded NGRPACK extraction into app-private staging; streamed-byte limits and full post-extraction hash validation.
- Package manager: atomic install/update, backup/rollback, interrupted-install recovery, inventory and removal.
- Android UI: Document Picker import/update, installed package list and removal, including corrupt-package cleanup.
- JVM regression tests for manifest validation, ZIP inspection, package installation/update/removal and recovery.
- Versioned execution-backend ABI v1 C header contract, compiled as both C and C++; the runtime plugin loader and lifecycle are not yet implemented.
- Latest successful CI: [run #71](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44162883678). APK artifact is available from the run page.
- GitHub Actions workflow configured to run unit tests and build arm64-v8a debug APK.

## Build

Requirements: JDK 17, Android SDK, Android NDK 27.2.12479018, CMake 3.22.1, Gradle 8.9.

```bash
git clone https://github.com/Snap888/NativeGameRecompiler.git
cd NativeGameRecompiler
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

CI workflow: [GitHub Actions](https://github.com/Snap888/NativeGameRecompiler/actions).

## Project journal

- [Development log](docs/DEVELOPMENT_LOG.md)
- [Next actions](docs/NEXT_ACTIONS.md)
- [Continuation prompt for new chats](docs/CONTINUATION_PROMPT.md)
- [Technical audit](docs/TECHNICAL_AUDIT.md)
- [Technical specification](docs/TECHNICAL_SPEC.md)

## Not implemented yet

No backend plugin ABI, static game recompiler, dynamic translator, N64Recomp integration, FEX, Box64, Wine, DXVK, VKD3D-Proton, Turnip provider manager or game execution is included yet. Package signing/trust policy and sandboxing are also not implemented; NGRPACK is currently a data-installation mechanism only and package payloads must not be executed. APK is a debug artifact and has not yet been validated on physical Snapdragon/Adreno hardware.

## Legal

NGR does not distribute copyrighted game data, ROMs, BIOS images, encryption keys or proprietary assets. Users must provide content they are legally entitled to use. Third-party components will be integrated only after license and compatibility review.
