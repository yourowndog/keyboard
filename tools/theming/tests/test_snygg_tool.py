#!/usr/bin/env python3
"""
Tests for Snygg theme tool (tools/theming/snygg_tool.py).
"""

import json
import tempfile
import unittest
from pathlib import Path

from tools.theming.snygg_tool import (
    FLORIS_IME_UI_ELEMENTS,
    audit_coverage,
    package_extension,
    parse_snygg_rule,
    unpack_extension,
    validate_extension_dir,
    validate_stylesheet_data,
)


class TestSnyggTool(unittest.TestCase):
    def test_parse_snygg_rule(self):
        # Simple element
        res = parse_snygg_rule("key")
        self.assertIsNotNone(res)
        self.assertEqual(res[0], "key")
        self.assertEqual(res[1], [])
        self.assertEqual(res[2], [])

        # State pseudo-class
        res = parse_snygg_rule("key:pressed")
        self.assertEqual(res[0], "key")
        self.assertEqual(res[2], [":pressed"])

        # Attribute selector with comma-separated values
        res = parse_snygg_rule("key[code=-201,-202,-203]")
        self.assertEqual(res[0], "key")
        self.assertEqual(res[1], [("code", ["-201", "-202", "-203"])])

        # Combined attribute and state
        res = parse_snygg_rule("key[code=-11][shiftstate=`caps_lock`]:pressed")
        self.assertEqual(res[0], "key")
        self.assertEqual(len(res[1]), 2)
        self.assertEqual(res[2], [":pressed"])

    def test_validate_valid_stylesheet(self):
        data = {
            "$schema": "https://schemas.florisboard.org/snygg/v2/stylesheet",
            "@defines": {
                "--primary": "#FF6A4D",
                "--surface": "#5E6573",
            },
            "window": {
                "background": "#0B0D13",
                "clip": "no",
            },
            "key": {
                "background": "var(--surface)",
                "foreground": "var(--primary)",
            },
        }
        errors = validate_stylesheet_data(data)
        fatal = [e for e in errors if not e.is_warning]
        self.assertEqual(len(fatal), 0)

    def test_validate_invalid_rules(self):
        # Unknown element
        data1 = {
            "nonexistent-element": {"background": "#000000"},
        }
        errs1 = validate_stylesheet_data(data1)
        self.assertTrue(any("Unknown element" in str(e) for e in errs1))

        # Unsupported / invalid property
        data2 = {
            "glide-trail": {"font-size": "14sp", "invalid-prop": "value"},
        }
        errs2 = validate_stylesheet_data(data2)
        self.assertTrue(any("Property 'font-size' not commonly styled" in str(e) for e in errs2))
        self.assertTrue(any("Invalid Snygg property 'invalid-prop'" in str(e) for e in errs2))

        # Undefined variable reference
        data3 = {
            "@defines": {},
            "key": {"background": "var(--undefined-bg)"},
        }
        errs3 = validate_stylesheet_data(data3)
        self.assertTrue(any("Referenced variable '--undefined-bg' is not defined" in str(e) for e in errs3))

    def test_package_and_unpack(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            src = Path(tmp_dir) / "theme_ext"
            src.mkdir()
            (src / "stylesheets").mkdir()
            (src / "extension.json").write_text(json.dumps({
                "$": "ime.extension.theme",
                "meta": {
                    "id": "test.theme",
                    "version": "1.0.0",
                    "title": "Test",
                    "maintainers": ["Test"],
                    "license": "MIT",
                },
                "themes": [{
                    "id": "test_theme",
                    "label": "Test Theme",
                    "authors": ["Test"],
                    "isNight": True,
                    "stylesheet": "stylesheets/test.json",
                }],
            }))
            (src / "stylesheets" / "test.json").write_text(json.dumps({
                "$schema": "https://schemas.florisboard.org/snygg/v2/stylesheet",
                "window": {"background": "#000000"},
            }))

            out_flex = Path(tmp_dir) / "test.flex"
            self.assertTrue(package_extension(src, out_flex))
            self.assertTrue(out_flex.exists())

            unpacked = Path(tmp_dir) / "unpacked"
            self.assertTrue(unpack_extension(out_flex, unpacked))
            self.assertTrue((unpacked / "extension.json").exists())
            self.assertTrue((unpacked / "stylesheets" / "test.json").exists())

    def test_audit_coverage(self):
        repo_root = Path(__file__).resolve().parent.parent.parent.parent
        tactical_ss = repo_root / "app/src/main/assets/ime/theme/com.brokentooth.lcars/stylesheets/lcars_tactical.json"
        res = audit_coverage(tactical_ss)
        self.assertEqual(res["total_elements"], len(FLORIS_IME_UI_ELEMENTS))
        self.assertGreater(res["coverage_pct"], 90.0)
        self.assertIn("keyboard-chassis", res["missing_elements"])


if __name__ == "__main__":
    unittest.main()
