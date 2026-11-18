# N64 title runtime integration audit

Audit date: 2026-11-18

## Recompiler/runtime boundary

The pinned N64Recomp core translates supported MIPS/N64 ELF functions into C. Its output is not a standalone executable: generated code expects a title runtime header (normally `recomp.h`), `recomp_context`, memory/register helpers, function lookup, relocation support and platform services.

The current NGR bridge now emits C sources and a function header, but deliberately marks every generated manifest as non-launchable until a title runtime is present.

## Candidate runtime: N64ModernRuntime

- Repository: https://github.com/N64Recomp/N64ModernRuntime
- Audited upstream revision: `e4342ae030a9d94aa97a81aafd3368ed7daa74df` (2026-11-03)
- License: GPL-3.0, compatible with the current combined NGR/WinNative GPL-3.0 distribution, subject to retaining notices for its dependencies.
- Core modules: `librecomp` (libultra services, memory, overlays, ROM/save handling, RSP integration) and `ultramodern` (threading, audio, input, renderer and platform callbacks).
- It is a runtime library, not a complete game port or a drop-in player for arbitrary ROMs.

## Useful host integration points

- `ultramodern::input::callbacks_t` accepts controller polling, button/axis state, rumble and connected-device information. This is a viable adapter point for WinNative's existing controller manager and touch/gamepad input pipeline.
- `ultramodern::renderer::callbacks_t` creates a game renderer from an SDL window handle. The Android/Linux build uses `SDL_Window*`; WinNative already builds SDL3, but an N64 display-list renderer still has to be supplied.
- Audio, event, error-handling and thread callbacks are also injected through `recomp::Configuration`.
- Runtime startup requires a game registration with title metadata, entrypoint and generated function symbols.

## Blocking work before launch

1. Select and pin a compatible N64 game runtime/rendering backend (e.g. an independently licensed Fast3D/RT64 implementation); the generic N64ModernRuntime does not supply a complete renderer by itself.
2. Integrate `librecomp` and `ultramodern` for Android. Audit their transitive submodules and patch CMake only where necessary to reuse the already-pinned N64Recomp targets instead of building duplicate targets.
3. Generate the title-specific `recomp.h`, context/register definitions, function lookup tables, overlays and entrypoint from N64Recomp metadata.
4. Implement SDL/Vulkan renderer callbacks using the Android surface and WinNative Vulkan/SDL lifecycle.
5. Bind input callbacks to WinNative controller/touch mappings; bind audio callbacks to the host audio subsystem.
6. Import the legally supplied ROM/data required by the selected title runtime and validate its expected hash/region. An ELF alone is not the game data.
7. Package the generated ARM64 library, title runtime and required data into an NGR game package, then test launch/exit/restart on device.

## Input and game prerequisites

- The current N64Recomp UI accepts a supported MIPS/N64 ELF with symbol and relocation metadata. A retail ROM/ISO by itself cannot be passed to this stage.
- A decompilation/build project or equivalent ELF+symbol metadata is needed to produce useful input for N64Recomp.
- Touch controls and gamepads are available in the WinNative host, but they are not connected to an N64 title runtime until the `ultramodern::input` callbacks are wired.
- Do not advertise game launch, controller support in a recompiled title, or generated ARM64 binaries until a title runtime and device test exist.
