#!/usr/bin/env python3
"""Smoke-test donor theme/font staging against the pinned nx2android checkout."""
from __future__ import annotations

import argparse
from pathlib import Path

from port_game import copy_overlay_assets


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--nx2android", required=True, type=Path)
    parser.add_argument("--title-id", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    donor = args.nx2android.resolve(strict=True)
    output = args.output.resolve()
    if output.exists():
        parser.error("--output must not already exist")
    output.mkdir(parents=True)
    copy_overlay_assets(donor, args.title_id.upper(), output)
    theme = output / "assets/overlay/theme.tsv"
    font = output / "assets/font/ui.bin"
    if not theme.is_file() or theme.stat().st_size == 0:
        raise SystemExit("Theme staging did not produce assets/overlay/theme.tsv")
    if not font.is_file() or font.stat().st_size == 0:
        raise SystemExit("Font staging did not produce assets/font/ui.bin")
    print(f"Verified donor theme and font for title {args.title_id.upper()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
