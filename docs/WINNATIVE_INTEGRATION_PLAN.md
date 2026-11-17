# WinNative Runtime & Environment Integration Plan

Status: architecture approved; source-level porting has not started. This document separates verified donor capabilities from items that still require source audit and compatibility validation.

## Goal

Reuse the proven Android Windows-compatibility environment from WinNative / its Winlator Bionic lineage so NGR can launch ordinary Windows games early, while keeping static recompilation as a separate first-class execution path.

NGR must not become a thin fork of WinNative. It owns game identity, package/library management, compiler pipelines, execution selection, diagnostics and per-game profiles. The compatibility runtime is an isolated subsystem behind NGR interfaces.

## Execution architecture

- **Wine compatibility backend:** Windows PE games through a selected Wine/Proton layer, CPU translation runtime and graphics stack.
- **Static recompiled backend:** generated ARM64 code plus the title-specific NGR runtime; must not require Wine.
- **Native ARM64 backend:** already-native game ports or separately trusted native backends.

The game library records source format/architecture and available execution paths. Backend selection is explicit and explainable; it must never silently treat an imported EXE as a runnable native package.

## Environment Manager scope

### Container and Wine configuration
- Container/rootfs lifecycle, Wine prefix creation, backup, clone, reset and repair.
- Wine/Proton layer catalog, install/update/remove, pinning and rollback.
- Windows version, architecture mode, desktop/window resolution, DPI, display backend, audio/input options.
- Container-wide environment variables plus per-game overrides.
- DLL overrides, synchronization options and compatibility toggles.
- Per-game shortcut/profile settings layered over container defaults.

### CPU translation
- Box64 (and Box86 only if needed by a supported 32-bit path): versioned runtime, presets, environment variables and per-game overrides.
- FEXCore and FEX unix libraries for ARM64EC paths, versioned and independently selectable.
- WOWBox64 only where the chosen ARM64EC/FEX configuration requires it.
- Explicit architecture routing: x86_64/standard containers vs ARM64EC/FEX paths. These are not interchangeable toggles.
- Runtime capability checks before allowing incompatible combinations.

### Graphics and drivers
- DXVK for Direct3D 8/9/10/11 paths, with standard and ARM64EC variants where available.
- VKD3D-Proton for Direct3D 12; D8VK/D7VK only as separately catalogued optional components.
- Turnip/Adreno driver packages through an isolated provider mechanism (AdrenoTools or equivalent).
- Android system Vulkan loader remains untouched. Per-container/per-game driver selection must not overwrite system libraries.
- X11 first for initial compatibility; Wayland only when the selected Wine/Proton layer ships a matching winewayland driver and the compatible Wayland Vulkan path is present.
- Preserve component version, source, checksum, ABI/architecture and installation state.

### ReShade and shader ecosystem
- Audit the WinNative client integration separately from component-catalog support.
- Support catalogued ReShade builds, compatible shader/effect packages, presets and texture assets only after confirming the required Wine/Vulkan/DXVK injection path.
- Per-game enable/disable, preset selection, shader search paths and cache management.
- Never assume a ReShade catalog JSON means the donor APK already implements installation or runtime activation.

### Additional environment controls
- Audio bridge/device configuration, controller/input mapping, display options and session diagnostics.
- Import/export complete environment profiles with explicit component versions.
- Safe reset and rollback after failed component installation.

## Component catalog and package management

Use a typed catalog model inspired by Winlator/WinNative downloadable-content catalogs, not hard-coded download URLs in UI code. A catalog entry must include:
- stable component ID and type;
- semantic version/build and upstream commit where available;
- target ABI/container mode (x86_64, ARM64EC, aarch64 host, etc.);
- download URL, archive format and expected SHA-256;
- license identifier, source repository and attribution;
- compatibility constraints/conflicts and minimum runtime;
- install layout and uninstall/rollback metadata.

All downloaded packages are untrusted input. Validate HTTPS origin policy, size limits, archive paths, file types, hashes and expected layout before installation. Never load executable payloads from imported game packages as backend plugins. Component binaries are installed only into app-private managed locations and are activated through controlled runtime paths.

## Verified donor research

### WinNative repository
- Repository: https://github.com/Snap888/WinNative (upstream identity documented as WinNative-Emu/WinNative).
- README describes an Android x86_64 Windows emulation environment and links build, Wayland and attribution documentation.
- Build guide requires JDK 17, Android SDK compileSdk 35, NDK 27.3.13750724, CMake 3.22.1, Git LFS and recursive submodules.
- Native CMake confirms a Winlator native library, Vulkan compositor helpers, AdrenoTools, embedded Wayland compositor, PRoot, audio shims and WFG. This is not a drop-in NGR library: source ownership, JNI surface, app lifecycle and dependency graph need extraction boundaries.
- Wayland documentation confirms strict requirements: Adreno, a Wine/Proton layer containing matching winewayland and Wayland Turnip libraries, and per-session socket/ICD configuration. X11 remains the safer first milestone.
- WinNative is GPL-3.0 according to its README. Any direct code reuse must preserve GPL obligations and all per-component licenses/attributions. Confirm NGR licensing before copying or linking donor code.

### Runtime/component families confirmed by the Winlator Bionic lineage and component catalogs
- Box64 is used for standard x86_64 containers.
- FEXCore is used for ARM64EC execution paths; FEX unix libraries are distinct artifacts.
- Optional WOWBox64 may serve a 32-bit ARM64EC path.
- DXVK, VKD3D-Proton and Turnip drivers are separately installable components in WCP-style catalogs.
- Component catalogs also list Wine/Proton layers and ReShade-related catalogs. This establishes an ecosystem to investigate, not proof that every catalog item is integrated in the current WinNative APK.
- Reference catalogs: https://github.com/The412Banner/winlator-contents and https://github.com/nicholasx417/WinNative-Components.
- ReShade catalog reference: https://github.com/The412Banner/winlator-contents/blob/main/reshade.json.

## Important distinction: confirmed vs not yet verified

| Area | Current evidence | NGR decision |
|---|---|---|
| Wine/Proton + Box64 | Donor lineage and app purpose confirm | Port after code/license audit |
| FEXCore + ARM64EC | Component ecosystem confirms | Separate backend/configuration path |
| DXVK / VKD3D | Catalogs and donor native build ecosystem confirm | Managed optional components |
| Turnip | WinNative Wayland docs and AdrenoTools integration confirm | Isolated provider only |
| X11 / Wayland | WinNative Wayland docs confirm both paths and strict prerequisites | X11 first; Wayland gated |
| ReShade | ReShade catalog exists | Client-side install/activation still needs direct source audit |
| Shader packs/presets | Catalog ecosystem exists | Implement only after ReShade activation is proven |
| Exact donor settings UI and install code | Not fully audited in this pass | Must map source classes and data flows before port |

## Implementation sequence

1. Complete source-level donor audit: container model/manager, launch pipeline, Wine/Proton layer resolver, WCP installer, component catalog parser, environment variable editor, Box64/FEX settings, DXVK/VKD3D selection, driver manager, ReShade injection and shader UI.
2. Produce a file-by-file port map with upstream commit pins, SPDX/license inventory, ABI constraints and binary provenance.
3. Define NGR Environment Manager models and storage: immutable component records, container profiles, per-game overrides, schema versioning and migration.
4. Implement secure component catalog/download/verification/install/rollback.
5. Integrate Wine + Box64 standard x86_64 path and launch a known legal test executable/game.
6. Add ARM64EC + FEXCore path and verify exact Wine layer compatibility.
7. Add DXVK/VKD3D and isolated Turnip provider selection.
8. Add ReShade/shader activation after a real compatibility test.
9. Add Wayland only for validated matching Wine/Proton + driver combinations.
10. Continue static recompilation as an independent track; validate that a recompiled game launches without Wine.

## Acceptance criteria

- A game can have its own complete reproducible environment profile.
- Every selected component has an exact version, ABI, source, license and checksum.
- Failed downloads/installations leave the previous working environment intact.
- No Android system Vulkan libraries are replaced.
- Wine, Box64, FEX, DXVK, VKD3D, Turnip and ReShade are reported as available only after install/probe succeeds.
- Logs identify the selected container, Wine layer, CPU translator, graphics stack, driver, environment overrides and display backend.
- A normal Windows game can launch through Wine; a static recompiled title can launch independently through its native backend.
