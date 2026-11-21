#!/usr/bin/env bash
# Build Android-hosted Clang/LLD and the game-agnostic NDK kit for the Xbox 360 PPC pipeline.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
WORK=${NGR_LLVM_WORK_DIR:-${RUNNER_TEMP:-/tmp}/ngr-llvm-android}
OUT=${NGR_LLVM_OUT_DIR:-$ROOT/toolchains/xbox360/clang-out}
LLVM_VERSION=22.1.8
LLVM_SHA256=922f1817a0df7b1489272d18134ee0087a8b068828f87ac63b9861b1a9965888
NDK_VERSION=27.3.13750724
API=29
TRIPLE=aarch64-linux-android
ABI=arm64-v8a

SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
NDK=${ANDROID_NDK_HOME:-$SDK/ndk/$NDK_VERSION}
CMAKE=${CMAKE:-$SDK/cmake/3.22.1/bin/cmake}
NINJA=${NINJA:-$(command -v ninja || true)}
[[ -d "$NDK" ]] || { echo "NDK $NDK_VERSION missing: $NDK" >&2; exit 2; }
[[ -x "$CMAKE" ]] || { echo "CMake 3.22.1 missing: $CMAKE" >&2; exit 2; }
[[ -n "$NINJA" && -x "$NINJA" ]] || { echo "Ninja is required" >&2; exit 2; }
for tool in curl sha256sum tar python3; do command -v "$tool" >/dev/null || exit 2; done

SRC_ROOT=$WORK/src
SRC=$SRC_ROOT/llvm-project-$LLVM_VERSION.src
TARBALL=$WORK/llvm-project-$LLVM_VERSION.src.tar.xz
HOST_BUILD=$WORK/build-host
ANDROID_BUILD=$WORK/build-android
mkdir -p "$SRC_ROOT" "$OUT"
rm -rf "$OUT"/*

if [[ ! -d "$SRC/llvm" ]]; then
  mkdir -p "$WORK"
  if [[ ! -s "$TARBALL" ]]; then
    curl -fL --retry 3 -o "$TARBALL" \
      "https://github.com/llvm/llvm-project/releases/download/llvmorg-$LLVM_VERSION/llvm-project-$LLVM_VERSION.src.tar.xz"
  fi
  echo "$LLVM_SHA256  $TARBALL" | sha256sum --check -
  tar -xJf "$TARBALL" -C "$SRC_ROOT" \
    "llvm-project-$LLVM_VERSION.src/llvm" \
    "llvm-project-$LLVM_VERSION.src/clang" \
    "llvm-project-$LLVM_VERSION.src/lld" \
    "llvm-project-$LLVM_VERSION.src/cmake" \
    "llvm-project-$LLVM_VERSION.src/third-party" \
    "llvm-project-$LLVM_VERSION.src/libunwind"
fi

# Host tablegens are built from the same pinned LLVM sources and are used only during cross-build.
"$CMAKE" -G Ninja -S "$SRC/llvm" -B "$HOST_BUILD" \
  -DCMAKE_MAKE_PROGRAM="$NINJA" -DCMAKE_BUILD_TYPE=Release \
  -DLLVM_ENABLE_PROJECTS=clang -DLLVM_TARGETS_TO_BUILD=AArch64 \
  -DLLVM_INCLUDE_TESTS=OFF -DLLVM_INCLUDE_EXAMPLES=OFF -DLLVM_INCLUDE_BENCHMARKS=OFF \
  -DLLVM_ENABLE_ZLIB=OFF -DLLVM_ENABLE_ZSTD=OFF -DLLVM_ENABLE_LIBXML2=OFF -DLLVM_ENABLE_TERMINFO=OFF
"$NINJA" -C "$HOST_BUILD" -j2 llvm-tblgen clang-tblgen
HOST_BIN=$HOST_BUILD/bin
test -x "$HOST_BIN/llvm-tblgen"
test -x "$HOST_BIN/clang-tblgen"

NDK_LLVM=$NDK/toolchains/llvm/prebuilt/linux-x86_64
"$CMAKE" -G Ninja -S "$SRC/llvm" -B "$ANDROID_BUILD" \
  -DCMAKE_MAKE_PROGRAM="$NINJA" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API" -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
  -DCMAKE_EXE_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS_RELEASE="-O3 -DNDEBUG -g0" -DCMAKE_CXX_FLAGS_RELEASE="-O3 -DNDEBUG -g0" \
  -DLLVM_ENABLE_PROJECTS="clang;lld" -DLLVM_TARGETS_TO_BUILD=AArch64 \
  -DLLVM_HOST_TRIPLE=aarch64-linux-android29 -DLLVM_DEFAULT_TARGET_TRIPLE=aarch64-linux-android29 \
  -DLLVM_TABLEGEN="$HOST_BIN/llvm-tblgen" -DLLVM_HEADERS_TABLEGEN="$HOST_BIN/llvm-tblgen" \
  -DCLANG_TABLEGEN="$HOST_BIN/clang-tblgen" -DLLVM_NATIVE_TOOL_DIR="$HOST_BIN" \
  -DLLVM_INCLUDE_TESTS=OFF -DLLVM_INCLUDE_EXAMPLES=OFF -DLLVM_INCLUDE_BENCHMARKS=OFF \
  -DLLVM_INCLUDE_DOCS=OFF -DLLVM_INCLUDE_UTILS=OFF -DLLVM_BUILD_UTILS=OFF \
  -DCLANG_INCLUDE_TESTS=OFF -DCLANG_INCLUDE_DOCS=OFF \
  -DCLANG_ENABLE_ARCMT=OFF -DCLANG_ENABLE_STATIC_ANALYZER=OFF -DCLANG_BUILD_TOOLS=ON \
  -DLLVM_ENABLE_ZLIB=OFF -DLLVM_ENABLE_ZSTD=OFF -DLLVM_ENABLE_LIBXML2=OFF \
  -DLLVM_ENABLE_LIBEDIT=OFF -DLLVM_ENABLE_TERMINFO=OFF -DLLVM_ENABLE_BINDINGS=OFF \
  -DLLVM_PARALLEL_LINK_JOBS=2 -DLLVM_ENABLE_ASSERTIONS=OFF
"$NINJA" -C "$ANDROID_BUILD" -j2 clang lld

"$NDK_LLVM/bin/llvm-strip" --strip-all -o "$OUT/libclang.so" "$ANDROID_BUILD/bin/clang"
"$NDK_LLVM/bin/llvm-strip" --strip-all -o "$OUT/liblld.so" "$ANDROID_BUILD/bin/lld"
"$NDK_LLVM/bin/llvm-readelf" -h "$OUT/libclang.so" > "$OUT/clang-elf-header.txt"
"$NDK_LLVM/bin/llvm-readelf" -h "$OUT/liblld.so" > "$OUT/lld-elf-header.txt"
grep -q AArch64 "$OUT/clang-elf-header.txt"
grep -q AArch64 "$OUT/lld-elf-header.txt"

# Reproducible game-agnostic NDK kit consumed by the on-device compiler worker.
NDK_SYSROOT=$NDK_LLVM/sysroot
NDK_RT=$NDK_LLVM/lib/clang/18/lib/linux
CLANG_HEADERS=$ANDROID_BUILD/lib/clang/22/include
KIT=$OUT/kit
mkdir -p "$KIT/sysroot/usr/lib/$TRIPLE" "$KIT/clang/22" "$KIT/clang/lib"
cp -r "$NDK_SYSROOT/usr/include" "$KIT/sysroot/usr/"
cp -r "$NDK_SYSROOT/usr/lib/$TRIPLE/$API" "$KIT/sysroot/usr/lib/$TRIPLE/"
cp "$NDK_SYSROOT/usr/lib/$TRIPLE/libc++_static.a" "$NDK_SYSROOT/usr/lib/$TRIPLE/libc++abi.a" "$KIT/sysroot/usr/lib/$TRIPLE/"
cp "$NDK_RT/aarch64/libunwind.a" "$NDK_RT/aarch64/libatomic.a" "$KIT/sysroot/usr/lib/$TRIPLE/"
cp "$NDK_RT/libclang_rt.builtins-aarch64-android.a" "$KIT/clang/lib/"
cp -r "$CLANG_HEADERS" "$KIT/clang/22/"
cp "$SRC/llvm/LICENSE.TXT" "$OUT/LLVM-LICENSE.txt"
cp "$NDK/NOTICE" "$OUT/NDK-NOTICE.txt"
cp "$NDK/NOTICE.toolchain" "$OUT/NDK-NOTICE-toolchain.txt"
cat > "$KIT/kit.json" <<EOF
{
  "kit": 1,
  "llvm": "$LLVM_VERSION",
  "ndk": "$NDK_VERSION",
  "androidApi": $API,
  "abi": "$ABI",
  "tools": { "clang": "$LLVM_VERSION", "lld": "$LLVM_VERSION" }
}
EOF
cat > "$OUT/PROVENANCE.txt" <<EOF
NGR Android-hosted Clang/LLD kit for Xbox 360 recompilation
LLVM project release: llvmorg-$LLVM_VERSION
LLVM source SHA-256: $LLVM_SHA256
Android NDK: $NDK_VERSION
Android API: $API
ABI: $ABI
Page size: 16 KB compatible
The source is unmodified LLVM under Apache-2.0 WITH LLVM-exception. NDK license and notice files are included.
LLD must be invoked with -flavor gnu when called as liblld.so.
EOF
python3 -I - "$KIT" "$OUT/kit.zip" <<'PY'
import os, sys, zipfile
root, output = sys.argv[1:]
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for current, dirs, files in os.walk(root):
        dirs.sort()
        for name in sorted(files):
            path = os.path.join(current, name)
            archive.write(path, os.path.relpath(path, root).replace(os.sep, "/"))
PY
(
  cd "$OUT"
  sha256sum libclang.so liblld.so kit.zip LLVM-LICENSE.txt NDK-NOTICE.txt NDK-NOTICE-toolchain.txt > SHA256SUMS
)
cat "$OUT/PROVENANCE.txt"
cat "$OUT/SHA256SUMS"
