#!/usr/bin/env python3
"""Unit tests for NGR's nx2android package adapter helpers."""
from __future__ import annotations

import hashlib
import tempfile
import unittest
from pathlib import Path

from port_game import validate_profile_binding


class ProfileBindingTests(unittest.TestCase):
    def test_accepts_matching_data_pack_profile_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory) / "game_profile.h"
            profile.write_bytes(b"synthetic generated profile")
            expected = hashlib.sha256(profile.read_bytes()).hexdigest()
            self.assertEqual(expected, validate_profile_binding({"profile_h": expected}, profile))

    def test_rejects_mismatched_data_pack_profile_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory) / "game_profile.h"
            profile.write_bytes(b"synthetic generated profile")
            with self.assertRaises(SystemExit):
                validate_profile_binding({"profile_h": "0" * 64}, profile)

    def test_rejects_missing_profile_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory) / "game_profile.h"
            profile.write_bytes(b"synthetic generated profile")
            with self.assertRaises(SystemExit):
                validate_profile_binding({}, profile)

    def test_rejects_symlinked_profile_header(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.h"
            source.write_bytes(b"profile")
            profile = root / "game_profile.h"
            profile.symlink_to(source)
            with self.assertRaises(SystemExit):
                validate_profile_binding({"profile_h": "0" * 64}, profile)


if __name__ == "__main__":
    unittest.main(verbosity=2)
