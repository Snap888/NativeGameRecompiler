# Android Clang/LLD kit for Xbox 360 recompilation

This build stage creates the Android-hosted Clang 22.1.8 and LLD 22.1.8 executables plus the matching NDK sysroot/resource headers required to compile XenonRecomp-generated C++ on the phone.

Pins:
- LLVM release llvmorg-22.1.8, source archive SHA-256 922f1817a0df7b1489272d18134ee0087a8b068828f87ac63b9861b1a9965888.
- Android NDK 27.3.13750724.
- Android API 29, arm64-v8a, 16 KB page-size compatibility.

The CI build first compiles host llvm-tblgen/clang-tblgen from the same LLVM source, then cross-compiles Clang and LLD for Android. The output artifact contains libclang.so, liblld.so, kit.zip, provenance, checksums and license notices. LLD must be called with -flavor gnu because it is packaged under the name liblld.so.

This is an offline-capable compile/link toolchain component only. It does not translate PPC code, provide Xbox 360 kernel/MMIO services, link a game profile, implement Xenos graphics/audio/input, or make a game playable. Do not put these tools into the APK until the worker, API-level gating, extraction permissions, disk quotas and source/license distribution are in place.

Run the workflow "Xbox 360 Android Clang and LLD kit" or reproduce locally with NDK 27.3.13750724, CMake 3.22.1, Ninja, Python 3 and curl:

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/27.3.13750724"
export CMAKE="$ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake"
export NINJA="$(command -v ninja)"
bash toolchains/xbox360/build_clang_kit_android.sh
```
