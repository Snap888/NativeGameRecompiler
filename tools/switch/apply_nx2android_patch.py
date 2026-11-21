#!/usr/bin/env python3
"""Apply NGR's per-title data-root integration to a pinned nx2android checkout.

The patch is intentionally exact-match and fails closed when upstream changes the
NativeActivity path setup. It never edits a user's game data.
"""
from __future__ import annotations

import argparse
from pathlib import Path

OLD = '    snprintf(g_root, sizeof g_root, "%s", act->externalDataPath ? act->externalDataPath : act->internalDataPath);'
NEW = """    // NGR embeds multiple title-specific data packs in one application.
    // The NGR NativeActivity loader validates this private path before dlopen.
    const char *ngr_game_root = getenv("NGR_SWITCH_GAME_ROOT");
    snprintf(g_root, sizeof g_root, "%s",
             (ngr_game_root && ngr_game_root[0]) ? ngr_game_root :
             (act->externalDataPath ? act->externalDataPath : act->internalDataPath));"""

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("nx2android_root", type=Path)
    args = parser.parse_args()
    source = args.nx2android_root / "nxnative/dm/android/front_android.c"
    if not source.is_file():
        raise SystemExit(f"nx2android source not found: {source}")
    text = source.read_text(encoding="utf-8")
    if NEW in text:
        print("NGR per-title data-root patch already applied")
        return 0
    count = text.count(OLD)
    if count != 1:
        raise SystemExit(f"expected exactly one upstream data-root assignment, found {count}; refusing to patch")
    source.write_text(text.replace(OLD, NEW), encoding="utf-8", newline="\n")
    print(f"patched {source}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
