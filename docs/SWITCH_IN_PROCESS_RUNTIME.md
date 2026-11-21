# Nintendo Switch in-process runtime integration

Status: implementation stage, not yet device-verified.

## Required product behavior

NGR is the only installed Android application. A Switch title is represented by:
- a title-specific libdmhost.so built from nx2android's Android host;
- the nx2android data pack (mods/, RomFS/, optional aoc/ and sysdata/, shaders.dmsv, env.txt, settings.txt, nx2a-data.json);
- NGR-owned metadata and a game-library shortcut.

NGR must not install the donor's generated per-title APK. The donor APK is only a packaging mechanism for a NativeActivity plus libdmhost.so; NGR hosts the activity in its own manifest and forwards ANativeActivity_onCreate to the title-specific library.

## Upstream pin and verified layout

- Repository: https://github.com/shirai91/nx2android
- Pinned commit: f32b3d563f0615d0e825747285d4e38d780e4ebb
- Runtime build: nxnative/dm/android/build.py --game <title-id> --lib --release
- NativeActivity entry: nxnative/dm/android/front_android.c exports ANativeActivity_onCreate.
- The build produces libdmhost.so, a title-profile-specific shared library. It is not a generic emulator runtime.
- The donor's pack is separate from its APK. Its documented root includes mods/, RomFS/, optional aoc/ and sysdata/, shaders.dmsv, env.txt, settings.txt and nx2a-data.json.
- The donor's APK includes a gamepad theme and font as Android assets; NGR still needs a per-title asset-loading integration for exact theme/font parity.

## NGR changes

1. ngr_switch_loader is a small NativeActivity library shipped in the NGR APK. Android starts this activity; it validates a title-specific runtime path and game-data root, sets NGR_SWITCH_GAME_ROOT, loads the title's libdmhost.so from NGR private storage and forwards ANativeActivity_onCreate.
2. SwitchRuntimeLauncher selects the per-title runtime and data directories. The runtime is stored under files/switch-ports/<TITLE_ID>/runtime/libdmhost.so; the large data pack is stored under getExternalFilesDir(null)/switch-ports/<TITLE_ID>/data/.
3. tools/switch/apply_nx2android_patch.py applies a fail-closed, exact-match patch to the pinned donor's front_android.c. It makes the donor host use NGR's per-title data directory instead of assuming one game at the root of a standalone app's external files directory.
4. .github/workflows/switch-runtime.yml can build the title-specific runtime for an Android profile and upload it as an artifact. It does not yet run the complete NSP/XCI extraction and rewrite pipeline.

## Why the separate data root is mandatory

nx2android's NativeActivity normally sets its g_root to ANativeActivity.externalDataPath. In a multi-game NGR application, that directory is shared by every title. Without the NGR patch, games would overwrite each other's mods/, RomFS/, settings, shader cache and logs. The patch changes only the data-root selection; all original nx2android runtime behavior remains intact.

## Remaining work (required before a test build is called playable)

- Implement a secure NGR port-package importer that stages the donor's data pack and matching libdmhost.so atomically, validates title ID/profile/version and hashes, and keeps large game data out of internal storage.
- Add a Switch library entry/shortcut model and launch controls on the NGR home screen.
- Integrate the complete porter pipeline (LibHac extraction, NSO rewrite, shader split/translation, font generation, host build, data pack generation). The current raw NSP/XCI worker only stages input bytes.
- Decide how the desktop-only Python/.NET preparation dependencies are provided. Until an on-device or supported companion preparation flow exists, do not claim that NGR can port a raw NSP/XCI by itself.
- Add per-title theme/font assets to the embedded activity. Confirm runtime can load them from a private per-title asset source or implement a file-backed theme path.
- Build the workflow for a known Android-supported profile, inspect the ELF dependencies and page alignment, then run Android Gradle/JVM/native CI.
- Install on a Snapdragon/Adreno device; test NativeActivity lifecycle, Vulkan, audio, controller/touch input, suspend/resume, save path isolation, and two different titles without data collisions.
- Review exact nx2android commit, submodules and third-party notices before distributing binaries. nxnative root is MIT; third-party code retains its own licenses. Never package or upload prod.keys, firmware, title keys or game data.

## Current limitations

The NativeActivity bridge, build receipt verifier and prepared-port importer are authored but not yet CI-verified. No Switch title runtime has yet been built by the new workflow, imported into NGR, launched, or tested on a device. Existing NGR APK artifacts do not contain a playable Switch runtime.