# Xbox 360 integration in NGR

## Pinned implementation

- Repository: https://github.com/Snap888/XDroid360
- Pinned submodule: `native/recompilers/XDroid360`
- Commit: `f388d11fc207502cd5975899c8adc06600cfe34f`
- This is an Android launcher and on-device static-recompilation pipeline, not a generic Xbox 360 emulator for every title.

## Existing pipeline to reuse

1. Intake: `GameSource`, `Xiso`, `ZipSource`, and STFS reader identify disc/package content and title ID.
2. Validation: game-specific `Recipe` profiles check title ID, accepted media/build variants and `default.xex` hashes.
3. Recompiler: pinned/patched XenonRecomp translates Xbox 360 PowerPC code to C++.
4. ARM64 build: bundled clang/lld compile the translated code on-device.
5. Runtime: DAORecomp-derived title runtime supplies Xbox kernel, memory, threading, VFS, XAM, audio and title hooks.
6. Graphics: native Vulkan renderer and Xenia Vulkan GPU path; Xenos shader translation uses DXC.
7. Installation/launch: `BuildService`, `RecipeRunner`, `Instance`, and `GameActivity`.

The repository README documents Dragon Age: Origins and State of Decay as playable profiles. Do not mark arbitrary XEX/ISO/STFS files runnable merely because their container header is recognized.

## Current NGR state

- Source is already pinned as a gitlink and recursive submodule checkout is required by CI.
- CI verifies the expected XDroid360 commit, runtime CMake entry point, BuildService source and nested `recomp` checkout.
- NGR intake recognizes `.iso`, `.zip`, `.xex`, `.con`, `.live`, and `.pirs` as Xbox 360 candidates in addition to checking XEX2/STFS magic.
- Candidate detection is not title validation. It does not yet invoke XDroid360's GameSource/Recipe pipeline or create a playable instance.
- The XDroid360 Android app has its own namespace, manifest, resources, foreground build service, SDL GameActivity, JNI STFS library and native CMake graph. Adding its Java source directory alone is insufficient.

## Integration work remaining

1. Port XDroid360's Android application layer into an NGR feature/module without replacing NGR's WinNative host application. Preserve package isolation and merge only required manifest entries, permissions, resources and services.
2. Integrate the donor's toolchain build/package process: clang/lld, XenonRecomp, STFS JNI, shaderlib/DXC, kit.zip and supported title profiles. Keep downloads pinned and SHA-256 verified; never commit proprietary game data or translated game code.
3. Connect the donor's CMake runtime graph (DAORecomp-derived runtime, Xenia Vulkan, native renderer, SDL3, XenosRecomp/DXC) to an isolated Xbox 360 target. Do not add it unconditionally to WinNative's existing native target.
4. Adapt BuildService progress, cancellation, resume, logs, storage accounting and instance lifecycle to NGR's game library.
5. Add tests using synthetic XEX/STFS/XISO fixtures and profile metadata; add CI validation for profile/kit compatibility and ARM64 native outputs.
6. Enable the catalog backend only after the integrated launcher builds and supported title profiles pass end-to-end device tests.

## Licensing and distribution

- XDroid360 top-level project files are MIT, but its native renderer is GPL-3.0 and its XenonRecomp binary is GPL-2.0-or-later; the resulting game libraries/APK have additional GPL obligations.
- Preserve `LICENSE`, `NOTICE.md`, native-renderer notices and every transitive third-party license.
- Keep user-provided game images and generated game code out of the repository and CI artifacts.
- Re-audit the full NOTICE and nested submodule licenses before distribution.
