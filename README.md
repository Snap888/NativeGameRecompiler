# Native Game Recompiler (NGR)

Native Game Recompiler is an Android ARM64 runtime and modular game-recompilation research project targeting Qualcomm Adreno GPUs.

> **Stage 1 status:** runtime foundation scaffold. It is not yet a general-purpose game recompiler and does not currently load or run commercial games.

## Project direction

- Android host application with a native C++20 runtime core.
- ARM64 as the initial Android target.
- Modular backends for static recompilation and dynamic translation.
- Vulkan graphics-provider abstraction; system Vulkan loader first, optional per-game Turnip integration later.
- Touch controls and physical gamepad discovery.
- Reproducible `.ngrpack` packages with manifests and content hashes.

## Stage 1

The first milestone establishes the Android/JNI boundary, reports runtime/ABI information, probes Vulkan physical devices, detects connected game controllers, and builds an arm64-v8a debug APK in GitHub Actions.

No game backend, DXVK, custom driver installer, or game execution is included in Stage 1.

## Build

Requirements: JDK 17, Android SDK, Android NDK 27.2.12479018, CMake 3.22.1.

```bash
git clone https://github.com/Snap888/NativeGameRecompiler.git
cd NativeGameRecompiler
gradle --no-daemon :app:assembleDebug
```

The CI workflow builds the debug APK and publishes it as a workflow artifact.

## Architecture and scope

See [`docs/TECHNICAL_AUDIT.md`](docs/TECHNICAL_AUDIT.md), [`docs/TECHNICAL_SPEC.md`](docs/TECHNICAL_SPEC.md), and the architecture decision records in [`docs/ADR`](docs/ADR).

## Legal

NGR does not distribute copyrighted game data, ROMs, BIOS images, encryption keys, or proprietary assets. Users must provide content they are legally entitled to use. Third-party components will be integrated only after license and compatibility review.
