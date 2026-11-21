#!/usr/bin/env python3
"""Prepare an NGR-importable Switch port with nx2android, without a game APK."""
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

from build_runtime import PINNED_NX2ANDROID_REVISION, TITLE_ID


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run(command: list[str], *, cwd: Path, env: dict[str, str]) -> str:
    result = subprocess.run(command, cwd=cwd, env=env, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    output = result.stdout or ""
    output = re.sub(r"(?i)(prod\.keys(?:\s*[:=]\s*)?)[^\s]+", r"\1[redacted]", output)
    print(output, end="", flush=True)
    if result.returncode:
        raise SystemExit(f"nx2android porter failed with exit code {result.returncode}")
    return output


def copy_tree(source: Path, destination: Path) -> None:
    if not source.is_dir() or source.is_symlink():
        raise SystemExit(f"Expected a regular donor data-pack directory: {source}")
    destination.mkdir(parents=True)
    for root, directories, files in os.walk(source, followlinks=False):
        base = Path(root)
        target_root = destination / base.relative_to(source)
        target_root.mkdir(parents=True, exist_ok=True)
        for name in directories:
            item = base / name
            if item.is_symlink() or not item.is_dir():
                raise SystemExit(f"Unsafe directory in donor data pack: {item}")
            (target_root / name).mkdir(exist_ok=True)
        for name in files:
            item = base / name
            if item.is_symlink() or not item.is_file():
                raise SystemExit(f"Unsafe file in donor data pack: {item}")
            shutil.copy2(item, target_root / name)


def validate_profile_binding(manifest: dict, generated_profile: Path) -> str:
    if not generated_profile.is_file() or generated_profile.is_symlink():
        raise SystemExit(f"Generated game_profile.h is missing or unsafe: {generated_profile}")
    actual_hash = sha256(generated_profile)
    if str(manifest.get("profile_h", "")).lower() != actual_hash:
        raise SystemExit("nx2android data pack profile_h does not match the generated game_profile.h")
    return actual_hash


def copy_overlay_assets(donor: Path, title_id: str, data_root: Path) -> None:
    """Copy the pinned donor's title theme and licensed shared overlay font."""
    android_tools = donor / "nxnative/dm/android/tools"
    sys.path.insert(0, str(android_tools))
    try:
        import android_profile
        theme_source = android_profile.theme(title_id)
    except Exception as error:
        raise SystemExit(f"Unable to resolve the donor gamepad theme: {error}") from error
    if not theme_source.is_dir():
        raise SystemExit(f"Donor gamepad theme directory is missing: {theme_source}")
    overlay_target = data_root / "assets/overlay"
    if overlay_target.exists():
        raise SystemExit("Data pack already contains assets/overlay; refusing to overwrite title assets")
    copy_tree(theme_source, overlay_target)

    font_source = donor / "nxnative/dm/third_party/fusion-pixel-font"
    font_file = font_source / "fusion-pixel-12px-proportional-zh_tw.nxpf"
    if not font_file.is_file() or font_file.is_symlink():
        raise SystemExit(f"Donor overlay font is missing or unsafe: {font_file}")
    font_target = data_root / "assets/font"
    font_target.mkdir(parents=True, exist_ok=True)
    if (font_target / "ui.bin").exists():
        raise SystemExit("Data pack already contains assets/font/ui.bin; refusing to overwrite")
    shutil.copy2(font_file, font_target / "ui.bin")
    for notice in font_source.rglob("*.txt"):
        if notice.is_file() and not notice.is_symlink():
            relative_notice = notice.relative_to(font_source)
            target_notice = font_target / relative_notice
            if target_notice.exists():
                raise SystemExit(f"Data pack font notice collision: {target_notice}")
            target_notice.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(notice, target_notice)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--nx2android", required=True, type=Path)
    parser.add_argument("--ngr-root", required=True, type=Path)
    parser.add_argument("--source-folder", required=True, type=Path)
    parser.add_argument("--keys", required=True, type=Path)
    parser.add_argument("--nx2a-home", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--language", default="en-US")
    parser.add_argument("--version", default="")
    parser.add_argument("--jobs", type=int, default=os.cpu_count() or 4)
    parser.add_argument("--experimental", action="store_true")
    parser.add_argument("--vulkan-driver", choices=("system",), default="system",
                        help="use Android's system Vulkan driver; custom Turnip packaging is not integrated yet")
    args = parser.parse_args()

    if not args.keys.is_file():
        parser.error("--keys must point to the user's existing prod.keys file")
    if not args.source_folder.is_dir():
        parser.error("--source-folder must be a directory containing game dumps")
    if not 1 <= args.jobs <= 128:
        parser.error("--jobs must be between 1 and 128")

    donor = args.nx2android.resolve(strict=True)
    ngr = args.ngr_root.resolve(strict=True)
    source_folder = args.source_folder.resolve(strict=True)
    keys = args.keys.resolve(strict=True)
    nx2a_home = args.nx2a_home.resolve(strict=True)
    output = args.output.resolve()
    if output.exists():
        parser.error("--output must not already exist")
    output.parent.mkdir(parents=True, exist_ok=True)
    donor_output = Path(tempfile.mkdtemp(prefix="ngr-nx2a-output-", dir=output.parent))
    patches = [
        (ngr / "patches/nx2android/0001-use-ngr-title-data-root.patch",
         donor / "nxnative/dm/android/front_android.c", "NGR_SWITCH_GAME_ROOT"),
        (ngr / "patches/nx2android/0002-ngrn-build-without-per-game-apk.patch",
         donor / "porter/pipeline.py", "NGR_NO_APK"),
        (ngr / "patches/nx2android/0003-use-ngr-per-title-save-and-temp-root.patch",
         donor / "nxnative/dm/android/front_android.c", "NGR_SWITCH_PRIVATE_ROOT"),
        (ngr / "patches/nx2android/0004-load-per-title-assets-from-ngr-data.patch",
         donor / "nxnative/dm/android/overlay_theme.c", "NGR_SWITCH_GAME_ROOT/assets"),
    ]
    for patch, target, marker in patches:
        if not patch.is_file():
            raise SystemExit(f"Required NGR donor patch is missing: {patch}")
        current = target.read_text(encoding="utf-8")
        if marker in current:
            continue
        check = subprocess.run(["git", "apply", "--check", str(patch)], cwd=donor,
                               text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if check.returncode:
            raise SystemExit(f"Donor source no longer matches reviewed patch {patch.name}:\\n" + check.stdout)
        subprocess.run(["git", "apply", str(patch)], cwd=donor, check=True)

    revision = subprocess.check_output(
        ["git", "-C", str(donor), "rev-parse", "HEAD"], text=True
    ).strip()
    if revision != PINNED_NX2ANDROID_REVISION:
        raise SystemExit(f"Expected nx2android {PINNED_NX2ANDROID_REVISION}, got {revision}")


    env = dict(os.environ)
    env.update({
        "NX2A_HOME": str(nx2a_home),
        "NGR_NO_APK": "1",
        "PYTHONUTF8": "1",
        "PYTHONIOENCODING": "utf-8",
        "PYTHONPATH": str(donor) + os.pathsep + env.get("PYTHONPATH", ""),
    })
    command = [sys.executable, "-m", "porter", "port", str(source_folder),
               "--keys", str(keys), "--out", str(donor_output),
               "--package", "com.ngr.app", "--language", args.language,
               "--jobs", str(args.jobs), "--vulkan-driver", args.vulkan_driver]
    if args.version:
        command += ["--version", args.version]
    if args.experimental:
        command += ["--experimental"]

    try:
        run(command, cwd=donor, env=env)
        packs = []
        for candidate in donor_output.glob("*-data"):
            manifest_file = candidate / "nx2a-data.json"
            if not manifest_file.is_file():
                continue
            try:
                data = json.loads(manifest_file.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            if TITLE_ID.fullmatch(str(data.get("title_id", ""))):
                packs.append((candidate, data))
        if len(packs) != 1:
            raise SystemExit(f"Expected exactly one nx2android data pack, found {len(packs)}")
        data_source, data_manifest = packs[0]
        title_id = str(data_manifest["title_id"]).upper()

        host = source_folder / f"{title_id}_build/android/host/libdmhost.so"
        if not host.is_file():
            raise SystemExit(f"nx2android did not leave the title runtime at {host}")
        generated_profile = source_folder / f"{title_id}_build/android/host/gen/{title_id}/game_profile.h"
        profile_hash = validate_profile_binding(data_manifest, generated_profile)

        temporary = output.with_name(output.name + ".partial")
        if temporary.exists():
            shutil.rmtree(temporary)
        temporary.mkdir(parents=True)
        runtime_dir = temporary / "runtime"
        runtime_dir.mkdir()
        shutil.copyfile(host, runtime_dir / "libdmhost.so")
        shutil.copyfile(generated_profile, runtime_dir / "game_profile.h")
        copy_tree(data_source, temporary / "data")

        # The donor APK normally carries a per-title controller theme and a shared
        # overlay font. NGR has one APK, so keep those assets in this title's data pack.
        copy_overlay_assets(donor, title_id, temporary / "data")

        # nx2android writes absolute paths rooted at its package's files folder.
        # In NGR, each title's pack lives one level below that folder.
        env_file = temporary / "data/env.txt"
        if env_file.is_file():
            donor_files_root = "/sdcard/Android/data/com.ngr.app/files"
            ngr_title_root = donor_files_root + f"/switch-ports/{title_id}/data"
            env_text = env_file.read_text(encoding="utf-8")
            env_file.write_text(env_text.replace(donor_files_root, ngr_title_root),
                                encoding="utf-8", newline="\n")

        license_candidates = [donor / "nxnative/LICENSE", donor / "LICENSE"]
        license_file = next((p for p in license_candidates if p.is_file()), None)
        if license_file:
            shutil.copyfile(license_file, temporary / "NX2ANDROID-LICENSE")

        receipt = {
            "format": "ngr-switch-runtime-build",
            "schema_version": 1,
            "title_id": title_id,
            "donor": "https://github.com/shirai91/nx2android",
            "donor_revision": revision,
            "runtime_library": "runtime/libdmhost.so",
            "runtime_sha256": sha256(runtime_dir / "libdmhost.so"),
            "profile_header": "runtime/game_profile.h",
            "profile_header_sha256": sha256(runtime_dir / "game_profile.h"),
            "data_manifest_sha256": sha256(temporary / "data/nx2a-data.json"),
            "abi": "arm64-v8a",
            "entry_point": "ANativeActivity_onCreate",
            "apk_generated": False,
            "vulkan_driver": args.vulkan_driver,
            "data_manifest": "data/nx2a-data.json",
        }
        (temporary / "runtime-build.json").write_text(
            json.dumps(receipt, indent=2) + "\n", encoding="utf-8"
        )
        temporary.rename(output)
    except BaseException:
        shutil.rmtree(output.with_name(output.name + ".partial"), ignore_errors=True)
        raise
    finally:
        shutil.rmtree(donor_output, ignore_errors=True)

    print(json.dumps({"result": "NGR_SWITCH_PORT_PACKAGE", "title_id": title_id,
                      "name": data_manifest.get("name", title_id),
                      "package_folder": str(output), "apk_generated": False}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
