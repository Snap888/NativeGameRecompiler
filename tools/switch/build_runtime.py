#!/usr/bin/env python3
"""Build nx2android's per-title Android NativeActivity host for an NGR Switch port.

This deliberately builds libdmhost.so only. It never calls mkapk.py and never
creates or installs a game-specific APK.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

PINNED_NX2ANDROID_REVISION = "f32b3d563f0615d0e825747285d4e38d780e4ebb"
TITLE_ID = re.compile(r"^[0-9A-Fa-f]{16}$")


def run(command: list[str], *, cwd: Path | None = None) -> str:
    result = subprocess.run(
        command, cwd=cwd, check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    return result.stdout


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--nx2android", required=True, type=Path)
    parser.add_argument("--title-id", required=True)
    parser.add_argument("--ndk", required=True, type=Path)
    parser.add_argument("--package-dir", required=True, type=Path,
                        help="NGR files/switch-ports/<TITLE_ID> directory")
    parser.add_argument("--jobs", type=int, default=os.cpu_count() or 4)
    parser.add_argument("--python", default=sys.executable)
    args = parser.parse_args()

    if not TITLE_ID.fullmatch(args.title_id):
        parser.error("--title-id must be exactly 16 hexadecimal characters")
    if args.jobs < 1 or args.jobs > 128:
        parser.error("--jobs must be between 1 and 128")

    donor = args.nx2android.resolve(strict=True)
    ndk = args.ndk.resolve(strict=True)
    build_py = donor / "nxnative/dm/android/build.py"
    profile_dir = donor / "nxnative/dm/games" / args.title_id.upper()
    if not build_py.is_file():
        raise SystemExit(f"nx2android Android host builder not found: {build_py}")
    if not profile_dir.is_dir():
        raise SystemExit(f"No nx2android game profile for title {args.title_id}")

    revision = run(["git", "-C", str(donor), "rev-parse", "HEAD"]).strip()
    if revision != PINNED_NX2ANDROID_REVISION:
        raise SystemExit(
            "Unpinned nx2android checkout: expected "
            f"{PINNED_NX2ANDROID_REVISION}, got {revision}"
        )

    package_dir = args.package_dir.resolve()
    if package_dir.name.upper() != args.title_id.upper() or package_dir.parent.name != "switch-ports":
        raise SystemExit("package-dir must be NGR files/switch-ports/<TITLE_ID>")
    runtime_dir = package_dir / "runtime"
    runtime_dir.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="ngr-switch-host-") as temporary:
        output = Path(temporary) / "host"
        output.mkdir()
        command = [
            args.python, str(build_py),
            "--game", args.title_id.upper(),
            "--ndk", str(ndk),
            "--out", str(output),
            "--jobs", str(args.jobs),
            "--lib", "--release",
        ]
        print("+", " ".join(command), flush=True)
        print(run(command, cwd=donor), flush=True)

        library = output / "libdmhost.so"
        generated_profile = output / "gen" / args.title_id.upper() / "game_profile.h"
        if not library.is_file() or library.stat().st_size == 0:
            raise SystemExit("nx2android build completed without libdmhost.so")
        if not generated_profile.is_file():
            raise SystemExit("nx2android build completed without generated game_profile.h")

        readelf = ndk / "toolchains/llvm/prebuilt"
        candidates = list(readelf.glob("*/bin/llvm-readelf"))
        if not candidates:
            candidates = list(readelf.glob("*/bin/llvm-readelf.exe"))
        if not candidates:
            raise SystemExit("NDK llvm-readelf was not found")
        report = run([str(candidates[0]), "-h", "-d", "--dyn-syms", str(library)])
        if "AArch64" not in report or "ANativeActivity_onCreate" not in report:
            raise SystemExit("libdmhost.so is not an AArch64 NativeActivity runtime")

        staged = runtime_dir / "libdmhost.so.partial"
        shutil.copyfile(library, staged)
        os.replace(staged, runtime_dir / "libdmhost.so")

        metadata = {
            "format": "ngr-switch-runtime-build",
            "schema_version": 1,
            "title_id": args.title_id.upper(),
            "donor": "https://github.com/shirai91/nx2android",
            "donor_revision": revision,
            "runtime_library": "runtime/libdmhost.so",
            "runtime_sha256": sha256(runtime_dir / "libdmhost.so"),
            "profile_header_sha256": sha256(generated_profile),
            "abi": "arm64-v8a",
            "entry_point": "ANativeActivity_onCreate",
            "apk_generated": False,
        }
        manifest_tmp = package_dir / "runtime-build.json.partial"
        manifest_tmp.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        os.replace(manifest_tmp, package_dir / "runtime-build.json")
        print(json.dumps(metadata, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
