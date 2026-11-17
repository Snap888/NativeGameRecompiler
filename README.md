# Native Game Recompiler (NGR)

Native Game Recompiler is an Android ARM64 runtime and modular game-recompilation research project targeting Qualcomm Adreno GPUs.

> **Current status:** Stage 1 runtime shell and initial Stage 2 capability/package-validation work. It is not a general-purpose game recompiler and does not currently execute commercial games.

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
- Kotlin validator for already-extracted package directories, including metadata, path and SHA-256 checks.
- JVM tests for valid package, path traversal and hash mismatch.
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

No ZIP extraction/installation, game backend, N64Recomp integration, FEX, Box64, Wine, DXVK, VKD3D-Proton, Turnip provider manager or game execution is included yet. CI/APK status must be checked from Actions; a workflow file alone does not prove a successful build.

## Legal

NGR does not distribute copyrighted game data, ROMs, BIOS images, encryption keys or proprietary assets. Users must provide content they are legally entitled to use. Third-party components will be integrated only after license and compatibility review.
