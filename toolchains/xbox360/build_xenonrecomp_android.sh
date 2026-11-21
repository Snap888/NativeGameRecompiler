#!/usr/bin/env bash
# Build the XDroid360-pinned XenonRecomp CLI for Android arm64.
# Compiler only: this does not provide an Xbox 360 title runtime.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
WORK=${NGR_XBOX360_WORK_DIR:-${RUNNER_TEMP:-/tmp}/ngr-xbox360-toolchain}
OUT=${NGR_XBOX360_OUT_DIR:-$ROOT/toolchains/xbox360/out}
XR_REPO=https://github.com/hedge-dev/XenonRecomp.git
XR_COMMIT=ddd128bcca99fe8bfbb99bea583c972351fa6ace
PATCH_REPO=udarmolota/Dragon-Age-Origins-Recomp-Android
PATCH_COMMIT=bfbaa874323181191228ca20a7775f1725ee287a
PATCH_SHA256=55d637e3157206a8267e25d24823448bc956f2a1955ddbc389a15f511e1812e6
NDK_VERSION=27.3.13750724
ANDROID_API=29
ABI=arm64-v8a

SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
NDK=${ANDROID_NDK_HOME:-$SDK/ndk/$NDK_VERSION}
CMAKE=${CMAKE:-$SDK/cmake/3.22.1/bin/cmake}
NINJA=${NINJA:-$(command -v ninja || true)}
[[ -d "$NDK" ]] || { echo "Android NDK $NDK_VERSION not found: $NDK" >&2; exit 2; }
[[ -x "$CMAKE" ]] || { echo "CMake 3.22.1 not found: $CMAKE" >&2; exit 2; }
[[ -n "$NINJA" && -x "$NINJA" ]] || { echo "Ninja is required" >&2; exit 2; }
for tool in git curl python3 sha256sum tar; do command -v "$tool" >/dev/null || exit 2; done

SRC_ROOT=$WORK/src
XR_SRC=$SRC_ROOT/XenonRecomp
BUILD=$WORK/build-android
PATCH=$WORK/patch_recompiler.py
mkdir -p "$SRC_ROOT" "$OUT"
rm -rf "$OUT"/*

if [[ ! -d "$XR_SRC/.git" ]]; then git clone "$XR_REPO" "$XR_SRC"; fi
git -C "$XR_SRC" fetch --depth 1 origin "$XR_COMMIT"
git -C "$XR_SRC" checkout --force "$XR_COMMIT"
git -C "$XR_SRC" submodule update --init --recursive --depth 1
test "$(git -C "$XR_SRC" rev-parse HEAD)" = "$XR_COMMIT"

curl -fsSL --retry 3 \
  "https://raw.githubusercontent.com/$PATCH_REPO/$PATCH_COMMIT/scripts/patch_recompiler.py" -o "$PATCH"
echo "$PATCH_SHA256  $PATCH" | sha256sum --check -
python3 -I "$PATCH" "$XR_SRC/XenonRecomp/recompiler.cpp"
grep -q '(XDroid360) A config path' "$XR_SRC/XenonRecomp/recompiler_config.cpp"

"$CMAKE" -G Ninja -S "$XR_SRC" -B "$BUILD" \
  -DCMAKE_MAKE_PROGRAM="$NINJA" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$ANDROID_API" \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
  -DCMAKE_EXE_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
  -DCMAKE_BUILD_TYPE=Release
"$NINJA" -C "$BUILD" XenonRecomp
BUILT=$BUILD/XenonRecomp/XenonRecomp
[[ -s "$BUILT" ]]

NDK_BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
"$NDK_BIN/llvm-strip" --strip-all -o "$OUT/XenonRecomp-arm64" "$BUILT"
"$NDK_BIN/llvm-readelf" -h "$OUT/XenonRecomp-arm64" > "$OUT/elf-header.txt"
grep -q 'AArch64' "$OUT/elf-header.txt"

# Retain exact corresponding source, patch and notices beside the build artifact.
tar -czf "$OUT/XenonRecomp-source.tar.gz" -C "$SRC_ROOT" --exclude=.git XenonRecomp
cp "$XR_SRC/LICENSE.md" "$OUT/XenonRecomp-LICENSE-MIT.md"
cp "$PATCH" "$OUT/patch_recompiler.py"
curl -fsSL "https://raw.githubusercontent.com/$PATCH_REPO/$PATCH_COMMIT/LICENSE" -o "$OUT/DAORecomp-LICENSE-MIT.txt"
if [[ -f /usr/share/common-licenses/GPL-2 ]]; then
  cp /usr/share/common-licenses/GPL-2 "$OUT/GPL-2.0.txt"
else
  curl -fsSL https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt -o "$OUT/GPL-2.0.txt"
fi
cat > "$OUT/PROVENANCE.txt" <<EOF
NGR Xbox 360 XenonRecomp Android ARM64 build
XenonRecomp repository: $XR_REPO
XenonRecomp commit: $XR_COMMIT
XDroid360 patch repository: https://github.com/$PATCH_REPO
Patch commit: $PATCH_COMMIT
Patch SHA-256: $PATCH_SHA256
Android NDK: $NDK_VERSION
Android API: $ANDROID_API
ABI: $ABI
Page size: 16 KB compatible
The XenonRecomp project source is MIT; the built executable as a whole is GPL-2.0-or-later due to its GNU binutils-derived disassembler. Corresponding source and GPL-2.0 text are included.
This executable translates PPC code to C++; it is not an Xbox 360 game runtime.
EOF
(cd "$OUT" && sha256sum XenonRecomp-arm64 XenonRecomp-source.tar.gz patch_recompiler.py GPL-2.0.txt DAORecomp-LICENSE-MIT.txt > SHA256SUMS)
cat "$OUT/PROVENANCE.txt"
cat "$OUT/SHA256SUMS"
