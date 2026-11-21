#!/usr/bin/env python3
"""Assemble an NGR-ready Switch port folder from nx2android runtime + data pack.

The input data pack must come from the player's own nx2android porter run.
This tool never reads, uploads or stores prod.keys or firmware.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import tempfile

TITLE_ID = re.compile(r"^[0-9A-Fa-f]{16}$")
HASH = re.compile(r"^[0-9a-fA-F]{64}$")
DONOR_REVISION = "f32b3d563f0615d0e825747285d4e38d780e4ebb"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def reject_symlinks(root: Path) -> None:
    for path in root.rglob("*"):
        if path.is_symlink():
            raise SystemExit(f"Symlinks are not allowed in port bundles: {path}")


def copy_tree(source: Path, destination: Path) -> None:
    reject_symlinks(source)
    shutil.copytree(source, destination, symlinks=False)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime-package", required=True, type=Path,
                        help="NGR files/switch-ports/<TITLE_ID> containing runtime/ and runtime-build.json")
    parser.add_argument("--data-pack", required=True, type=Path,
                        help="nx2android porter output directory ending in -data")
    parser.add_argument("--output", required=True, type=Path,
                        help="Output directory selected for NGR folder import")
    args = parser.parse_args()

    runtime_package = args.runtime_package.resolve(strict=True)
    data_pack = args.data_pack.resolve(strict=True)
    output = args.output.resolve()
    receipt_path = runtime_package / "runtime-build.json"
    runtime = runtime_package / "runtime/libdmhost.so"
    data_manifest = data_pack / "nx2a-data.json"

    if not receipt_path.is_file() or not runtime.is_file():
        raise SystemExit("Runtime package must contain runtime/libdmhost.so and runtime-build.json")
    if not data_manifest.is_file() or not (data_pack / "mods").is_dir() or not (data_pack / "env.txt").is_file():
        raise SystemExit("nx2android data pack must contain nx2a-data.json, mods/ and env.txt")

    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    manifest = json.loads(data_manifest.read_text(encoding="utf-8"))
    title_id = str(receipt.get("title_id", "")).upper()
    if not TITLE_ID.fullmatch(title_id) or str(manifest.get("title_id", "")).upper() != title_id:
        raise SystemExit("Runtime and data pack title IDs do not match")
    if receipt.get("format") != "ngr-switch-runtime-build" or receipt.get("schema_version") != 1:
        raise SystemExit("Unsupported runtime build receipt")
    if receipt.get("donor_revision") != DONOR_REVISION:
        raise SystemExit("Runtime donor revision is not the pinned nx2android commit")
    if receipt.get("runtime_library") != "runtime/libdmhost.so" or receipt.get("abi") != "arm64-v8a":
        raise SystemExit("Runtime path or ABI does not match NGR")
    if receipt.get("entry_point") != "ANativeActivity_onCreate" or receipt.get("apk_generated") is not False:
        raise SystemExit("Runtime receipt does not describe an embedded NativeActivity library")
    if not HASH.fullmatch(str(receipt.get("runtime_sha256", ""))) or sha256(runtime) != receipt["runtime_sha256"]:
        raise SystemExit("Runtime library SHA-256 mismatch")
    if not HASH.fullmatch(str(receipt.get("profile_header_sha256", ""))):
        raise SystemExit("Missing game profile header fingerprint")

    if output.exists():
        raise SystemExit(f"Output already exists; choose a new path: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="ngr-switch-bundle-", dir=output.parent) as temp:
        staging = Path(temp) / "NGR-Switch-Port"
        (staging / "runtime").mkdir(parents=True)
        shutil.copy2(runtime, staging / "runtime/libdmhost.so")
        shutil.copy2(receipt_path, staging / "runtime-build.json")
        license_file = runtime_package / "NX2ANDROID-LICENSE"
        if license_file.is_file():
            shutil.copy2(license_file, staging / "NX2ANDROID-LICENSE")
        copy_tree(data_pack, staging / "data")
        staging.rename(output)
    print(json.dumps({
        "output": str(output),
        "title_id": title_id,
        "name": manifest.get("name"),
        "version": manifest.get("display", manifest.get("version")),
        "runtime_sha256": sha256(output / "runtime/libdmhost.so"),
        "data_files": manifest.get("files"),
        "data_bytes": manifest.get("bytes"),
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
