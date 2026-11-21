# Xbox 360 compiler toolchain — first Android ARM64 stage

Status: reproducible Android ARM64 build passed in Actions run 44915893900. Artifact NGR-Xbox360-XenonRecomp-arm64 ID 14712235777.

## What this stage builds

This stage builds the pinned XenonRecomp command-line compiler as an Android ARM64 executable:
- XenonRecomp: hedge-dev/XenonRecomp at ddd128bcca99fe8bfbb99bea583c972351fa6ace.
- XDroid360 Android patch: Dragon-Age-Origins-Recomp-Android at bfbaa874323181191228ca20a7775f1725ee287a; SHA-256 is checked before use.
- Android NDK 27.3.13750724, Android API 29, arm64-v8a, 16 KB page-size compatibility.

The workflow keeps the corresponding patched source archive, patch script, license texts, provenance and SHA-256 manifest beside the executable. The binary is a CI artifact only at this stage; it is not copied into the NGR APK.

## Important limits

XenonRecomp converts an Xbox 360 XEX/PPC image into C++ source. Upstream explicitly states that it does not provide the game runtime. A title-specific runtime, imports/kernel services, graphics, audio, input, shader preparation, link recipe and profile are still required before a game can launch.

This stage does not yet build Clang/LLD, does not run a synthetic PPC fixture, and does not support arbitrary XEX files. The next proof is to build the complete pinned Android Clang/LLD toolchain and run a redistributable synthetic PPC fixture through translation and ARM64 compilation. A title runtime (starting from an audited XDroid360 title profile) remains a separate stage.

## Licensing boundary

XenonRecomp's own source is MIT, but XDroid360's NOTICE identifies the executable as GPL-2.0-or-later as a whole because its disassembler contains GNU binutils-derived code. The artifact includes the corresponding patched source archive, GPL-2.0 text and the patch donor's MIT license. Do not bundle or redistribute the executable without preserving the source and notice obligations. The GPL-3.0 native renderer and shader library are not built by this stage.

## Reproduce locally

Install Android NDK 27.3.13750724, CMake 3.22.1, Ninja, Git, curl and Python 3, then run:

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/27.3.13750724"
export CMAKE="$ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake"
export NINJA="$(command -v ninja)"
bash toolchains/xbox360/build_xenonrecomp_android.sh
```

Output: toolchains/xbox360/out/. The script verifies the compiler's ELF machine is AArch64.
