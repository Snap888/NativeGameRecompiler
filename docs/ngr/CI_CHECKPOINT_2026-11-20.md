# NGR CI checkpoint — 2026-11-20

## Failed build diagnosis

Actions run [44721580782](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44721580782), commit
`68e684a320076ebd99dd5bc99243c72ea44fa566`, passed checkout, XDroid360 payload download,
N64Recomp host build, native ABI tests, JNI wiring checks and JVM unit tests.

The Android APK step failed for two independent integration reasons:

1. D8 found `org.libsdl.app.HIDDevice` twice: once in `:ngr-xdroid360` and once in `:armsx2`.
   Both modules included their own SDL Java glue in the same package. The pinned XDroid360
   source carries SDL 3.4.18 Java glue; WinNative's `:armsx2` source carries SDL 3.5.0.
2. The app's native CMake graph calls `find_package(SDL3 REQUIRED CONFIG)`, but the SDL3
   Prefab AAR dependency had been removed, so CMake could not locate `SDL3Config.cmake`.

## Fix committed for CI validation

- Keep WinNative's existing SDL Java glue from `:armsx2` as the sole source owner of
  `org.libsdl.app`.
- Exclude only XDroid360's duplicate `org/libsdl/**` Java source subtree. Keep the rest of
  the donor launcher, BuildService, activities, resources, native runtime and compiler payload.
- Restore the native-only WinNative SDL3 Prefab AAR to the app runtime dependency graph so
  the existing app CMake target can resolve `SDL3::SDL3`.
- Do not remove or replace WinNative's SDL2/SDL3 runtime, and do not weaken D8 checks.

This fix is pending a fresh CI run. It is not considered validated until D8 merge, CMake
configuration, native linking, APK packaging and artifact upload all succeed. Also verify
XDroid360's Java calls remain compatible with the selected WinNative SDL 3.5 Java glue
against the pinned donor source.
