# Native Game Recompiler (NGR)

Native Game Recompiler is an Android ARM64 runtime and modular game-recompilation research project targeting Qualcomm Adreno GPUs.

> **Current status:** Android runtime shell, NGRPACK package management, environment profile persistence, component catalog/download/install pipeline and dependency resolver are implemented. Xbox 360 source staging and the first in-package Nintendo Switch NativeActivity dispatch scaffold are present; the donor's per-title runtime, extraction/rewrite pipeline and playable title integration are not yet built or verified. Physical-device validation remains pending. NGR is not a general-purpose game recompiler.

## Project direction

- Android host application with Kotlin UI and a native C++20 runtime core.
- Console static-recompilation scope is locked to **Xbox 360 and Nintendo Switch only**.
- Xbox 360 foundation: the user's XDroid360 fork (PowerPC/XEX recompilation, Android toolchain and title-specific runtime modules).
- Nintendo Switch foundation: nx2android/nxnative (NSO/NRO rewrite, Horizon service host and NVN-to-Vulkan Android runtime). NGR is integrating its per-title `libdmhost.so` into one NGR installation instead of generating a separate APK for each game.
- Both targets produce/run Android ARM64 (`arm64-v8a`) code; their CPU, OS-service and graphics runtimes remain separate.
- Existing generic Wine/environment/package-management code is legacy foundation and is not an active platform-expansion target.
- NGR Environment Manager foundation: versioned profiles, per-game overrides, component version pins, typed catalog, HTTPS/SHA-256 download, ZIP staging, atomic component store/rollback and dependency resolution.
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
- WinNative source audit and integration plan documented in `docs/WINNATIVE_INTEGRATION_PLAN.md`; no WinNative source/binary has been copied into NGR. Licensing/provenance inventory is in `docs/LICENSING_AND_PROVENANCE.md`.
- GitHub Actions runs JVM tests and builds an arm64-v8a debug APK. Check the Actions page for the latest branch status; no APK is claimed for a commit until its complete CI run succeeds.
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
- [Licensing and provenance](docs/LICENSING_AND_PROVENANCE.md)
- [Next actions](docs/NEXT_ACTIONS.md)
- [Continuation prompt for new chats](docs/CONTINUATION_PROMPT.md)
- [Technical audit](docs/TECHNICAL_AUDIT.md)
- [Technical specification](docs/TECHNICAL_SPEC.md)

## Not implemented yet

Xbox 360 and Nintendo Switch compiler pipelines, console-specific runtimes, title profiles and native game execution are not integrated yet. The new two-target catalog is metadata only. No other console targets are in scope. Wine, Box64, FEXCore, DXVK, VKD3D-Proton, Turnip and vkBasalt/ReShade-compatible effect support currently exist only as profile/catalog/component-management foundations; they are not yet wired into a running game session. Package signing/trust policy and runtime sandboxing are also not implemented; NGRPACK is currently a data-installation mechanism only and package payloads must not be executed. APK is a debug artifact and has not yet been validated on physical Snapdragon/Adreno hardware.

## Legal

NGR does not distribute copyrighted game data, ROMs, BIOS images, encryption keys or proprietary assets. Users must provide content they are legally entitled to use. Third-party components will be integrated only after license and compatibility review.

- [Xbox 360 + Switch integration plan](docs/XBOX360_SWITCH_INTEGRATION_PLAN.md) — source audit, architecture boundaries, licensing gates and staged implementation.
- [Switch runtime integration](docs/SWITCH_RUNTIME_INTEGRATION.md) — nx2android's `libdmhost.so`, in-package NativeActivity dispatch, per-game port package layout and remaining integration steps.
- [Static recompiler integration audit](docs/RECOMPILER_INTEGRATION_AUDIT.md) — historical research checkpoint.
