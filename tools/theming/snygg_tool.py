#!/usr/bin/env python3
"""
tools/theming/snygg_tool.py

CLI tool for Snygg theme authoring, validation, inspection, and packaging.
Supports:
- validate: Validate stylesheets and extension manifests against FlorisImeUiSpec and Snygg v2 schema
- audit: Check element coverage against FlorisImeUi inventory
- package: Pack a theme directory or extension into a .flex ZIP package
- unpack: Extract a .flex package into editable directory structure
- inspect: Summary of theme colors, variables, rules, and contrast checks
"""

import argparse
import json
import os
import re
import sys
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

# Authoritative FlorisImeUi element names derived from FlorisImeUi.kt and FlorisImeUiSpec.kt
FLORIS_IME_UI_ELEMENTS: Dict[str, Dict[str, Any]] = {
    "window": {
        "selectors": [],
        "attributes": [],
        "properties": ["background", "foreground", "clip"],
    },
    "keyboard": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "keyboard-chassis": {
        "selectors": [],
        "attributes": [],
        "properties": ["background", "background-image"],
    },
    "key": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": ["code", "groupId", "shiftstate"],
        "properties": [
            "background",
            "foreground",
            "font-size",
            "shadow-elevation",
            "shape",
            "text-max-lines",
            "text-overflow",
        ],
    },
    "key-hint": {
        "selectors": [],
        "attributes": [],
        "properties": [
            "background",
            "foreground",
            "font-family",
            "font-size",
            "padding",
            "text-max-lines",
        ],
    },
    "key-popup-box": {
        "selectors": [],
        "attributes": [],
        "properties": [
            "background",
            "foreground",
            "font-size",
            "shape",
            "shadow-elevation",
        ],
    },
    "key-popup-element": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "key-popup-element-extended-indicator": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "glide-trail": {
        "selectors": [],
        "attributes": [],
        "properties": ["foreground"],
    },
    "incognito-indicator": {
        "selectors": [],
        "attributes": [],
        "properties": ["foreground"],
    },
    "smartbar": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-shared-actions-row": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-extended-actions-row": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-extended-actions-toggle": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-action-key": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-action-tile": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-actions-overflow": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-actions-editor": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-actions-editor-header": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-actions-editor-subheader": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-candidate-word": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-candidate-clip": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "smartbar-candidate-spacer": {
        "selectors": [],
        "attributes": [],
        "properties": ["foreground"],
    },
    "clipboard": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "clipboard-header": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "clipboard-content": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "clipboard-item": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "clipboard-item-popup": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "media": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "media-emoji-key": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "media-emoji-key-popup": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "media-emoji-key-popup-extended-indicator": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "media-emoji-tab": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "one-handed-panel": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "subtype-panel": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "subtype-panel-header": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "subtype-panel-list": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "subtype-panel-list-item": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "extracted-landscape-layout": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "extracted-landscape-field": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
    "extracted-landscape-action": {
        "selectors": [":hover", ":focus", ":pressed", ":disabled"],
        "attributes": [],
        "properties": ["background", "foreground"],
    },
}

# Valid property names from stylesheet.schema.json
VALID_SNYGG_PROPERTIES: Set[str] = {
    "background",
    "background-image",
    "content-scale",
    "foreground",
    "border-color",
    "border-width",
    "font-family",
    "font-size",
    "font-style",
    "font-weight",
    "letter-spacing",
    "line-height",
    "margin",
    "padding",
    "shadow-color",
    "shadow-elevation",
    "shape",
    "clip",
    "text-align",
    "text-decoration-line",
    "text-max-lines",
    "text-overflow",
}

# Snygg parser regex patterns matching SnyggRule.kt
SNYGG_RULE_REGEX = re.compile(r"^(?P<element>[a-zA-Z0-9-_]+)(?P<attributes>(?:\[[a-zA-Z0-9-_]+=[^\]]+\])*)(?P<selectors>(?::[a-zA-Z0-9-_]+)*)$", re.IGNORECASE)
SNYGG_ATTR_REGEX = re.compile(r"\[(?P<name>[a-zA-Z0-9-_]+)=(?P<values>[^\]]+)\]", re.IGNORECASE)
SNYGG_SELECTOR_REGEX = re.compile(r":[a-zA-Z0-9-_]+", re.IGNORECASE)
VAR_REF_REGEX = re.compile(r"^var\((--[a-zA-Z0-9-_]+)\)$", re.IGNORECASE)
COLOR_HEX_REGEX = re.compile(r"^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
RGBA_REGEX = re.compile(r"^rgba?\s*\([^)]*\)$", re.IGNORECASE)


def parse_snygg_rule(rule_str: str) -> Optional[Tuple[str, List[Tuple[str, List[str]]], List[str]]]:
    match = SNYGG_RULE_REGEX.match(rule_str)
    if not match:
        return None
    element = match.group("element")
    attrs: List[Tuple[str, List[str]]] = []
    raw_attrs = match.group("attributes")
    if raw_attrs:
        for am in SNYGG_ATTR_REGEX.finditer(raw_attrs):
            name = am.group("name")
            vals = [v.strip() for v in am.group("values").split(",")]
            attrs.append((name, vals))
    selectors: List[str] = []
    raw_selectors = match.group("selectors")
    if raw_selectors:
        for sm in SNYGG_SELECTOR_REGEX.finditer(raw_selectors):
            selectors.append(sm.group(0))
    return element, attrs, selectors


class ValidationError:
    def __init__(self, path: str, rule: Optional[str], prop: Optional[str], message: str, is_warning: bool = False):
        self.path = path
        self.rule = rule
        self.prop = prop
        self.message = message
        self.is_warning = is_warning

    def __str__(self) -> str:
        prefix = "WARNING" if self.is_warning else "ERROR"
        loc = self.path
        if self.rule:
            loc += f" > {self.rule}"
        if self.prop:
            loc += f" > {self.prop}"
        return f"[{prefix}] {loc}: {self.message}"


def validate_stylesheet_data(data: Dict[str, Any], path: str = "") -> List[ValidationError]:
    errors: List[ValidationError] = []

    # 1. Schema check
    schema = data.get("$schema")
    if not schema:
        errors.append(ValidationError(path, None, "$schema", "Missing $schema declaration", is_warning=True))
    elif "schemas.florisboard.org/snygg/v2" not in schema:
        errors.append(ValidationError(path, None, "$schema", f"Unrecognized or outdated schema: {schema}", is_warning=True))

    # 2. Defines check
    defines = data.get("@defines", {})
    if not isinstance(defines, dict):
        errors.append(ValidationError(path, None, "@defines", "@defines must be an object (map of --variable to value)"))
        defines = {}
    else:
        for var_name, var_val in defines.items():
            if not var_name.startswith("--"):
                errors.append(ValidationError(path, "@defines", var_name, f"Variable '{var_name}' must start with '--'"))
            if isinstance(var_val, str) and VAR_REF_REGEX.match(var_val):
                errors.append(ValidationError(path, "@defines", var_name, "Nested var() references in @defines are not supported"))

    # 3. Rules validation
    for key, value in data.items():
        if key.startswith("$") or key.startswith("@"):
            continue
        
        parsed = parse_snygg_rule(key)
        if not parsed:
            errors.append(ValidationError(path, key, None, f"Syntax error in selector '{key}'"))
            continue
        
        element, attrs, selectors = parsed
        if element not in FLORIS_IME_UI_ELEMENTS:
            errors.append(ValidationError(path, key, None, f"Unknown element '{element}' (not in FlorisImeUi)", is_warning=True))
            spec = {"selectors": [":hover", ":focus", ":pressed", ":disabled"], "attributes": [], "properties": list(VALID_SNYGG_PROPERTIES)}
        else:
            spec = FLORIS_IME_UI_ELEMENTS[element]
        
        # Check selectors
        for sel in selectors:
            allowed_sel = spec.get("selectors", [])
            if allowed_sel and sel not in allowed_sel:
                errors.append(ValidationError(path, key, None, f"Selector '{sel}' is not supported for element '{element}' (supported: {allowed_sel})", is_warning=True))
        
        # Check attributes
        for attr_name, _ in attrs:
            allowed_attrs = spec.get("attributes", [])
            if allowed_attrs and attr_name not in allowed_attrs:
                errors.append(ValidationError(path, key, None, f"Attribute '[{attr_name}]' is not supported for element '{element}' (supported: {allowed_attrs})", is_warning=True))
        
        if not isinstance(value, dict):
            errors.append(ValidationError(path, key, None, f"Property set for '{key}' must be an object"))
            continue
        
        # Check properties against valid Snygg properties (and warn if not in element-specific spec)
        for prop_name, prop_val in value.items():
            if prop_name not in VALID_SNYGG_PROPERTIES:
                errors.append(ValidationError(path, key, prop_name, f"Invalid Snygg property '{prop_name}' (not in stylesheet schema)"))
            elif prop_name not in spec.get("properties", []):
                errors.append(ValidationError(path, key, prop_name, f"Property '{prop_name}' not commonly styled on element '{element}'", is_warning=True))
            
            # Check variable references
            if isinstance(prop_val, str):
                var_match = VAR_REF_REGEX.match(prop_val)
                if var_match:
                    target_var = var_match.group(1)
                    if target_var not in defines:
                        errors.append(ValidationError(path, key, prop_name, f"Referenced variable '{target_var}' is not defined in @defines"))

    return errors


def validate_extension_dir(ext_dir: Path) -> List[ValidationError]:
    errors: List[ValidationError] = []
    manifest_path = ext_dir / "extension.json"
    if not manifest_path.exists():
        errors.append(ValidationError(str(ext_dir), None, None, "Missing extension.json manifest"))
        return errors

    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception as e:
        errors.append(ValidationError(str(manifest_path), None, None, f"Failed to parse extension.json: {e}"))
        return errors

    if manifest.get("$") != "ime.extension.theme":
        errors.append(ValidationError(str(manifest_path), None, "$", f"Expected '$': 'ime.extension.theme', got '{manifest.get('$')}'"))

    meta = manifest.get("meta", {})
    for req_field in ["id", "version", "title", "maintainers", "license"]:
        if req_field not in meta:
            errors.append(ValidationError(str(manifest_path), "meta", req_field, f"Missing required field '{req_field}' in meta"))

    themes = manifest.get("themes", [])
    if not isinstance(themes, list) or not themes:
        errors.append(ValidationError(str(manifest_path), None, "themes", "Manifest must declare at least one theme in 'themes'"))
        return errors

    for idx, theme in enumerate(themes):
        theme_loc = f"themes[{idx}]"
        theme_id = theme.get("id")
        stylesheet_rel = theme.get("stylesheet", f"stylesheets/{theme_id}.json")
        if not theme_id:
            errors.append(ValidationError(str(manifest_path), theme_loc, "id", "Missing theme 'id'"))
        
        ss_path = ext_dir / stylesheet_rel
        if not ss_path.exists():
            # Check if stylesheet was omitted in manifest and default path doesn't exist either
            if "stylesheet" not in theme:
                errors.append(ValidationError(str(manifest_path), theme_loc, "stylesheet", f"Theme '{theme_id}' has no 'stylesheet' field and fallback '{stylesheet_rel}' was not found", is_warning=True))
            else:
                errors.append(ValidationError(str(manifest_path), theme_loc, "stylesheet", f"Referenced stylesheet '{stylesheet_rel}' does not exist at {ss_path}"))
        else:
            try:
                ss_data = json.loads(ss_path.read_text(encoding="utf-8"))
                errors.extend(validate_stylesheet_data(ss_data, str(ss_path)))
            except Exception as e:
                errors.append(ValidationError(str(ss_path), None, None, f"Failed to parse stylesheet JSON: {e}"))

    return errors


def package_extension(ext_dir: Path, output_file: Path) -> bool:
    errors = validate_extension_dir(ext_dir)
    fatal = [e for e in errors if not e.is_warning]
    if fatal:
        print(f"Cannot package '{ext_dir}': {len(fatal)} validation error(s) found:", file=sys.stderr)
        for err in fatal:
            print(f"  {err}", file=sys.stderr)
        return False

    output_file.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output_file, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(ext_dir):
            # skip hidden or git dirs
            dirs[:] = [d for d in dirs if not d.startswith(".")]
            for file in files:
                if file.startswith(".") or file.endswith(".flex"):
                    continue
                full_path = Path(root) / file
                rel_path = full_path.relative_to(ext_dir)
                zf.write(full_path, arcname=str(rel_path))
    print(f"Successfully created package: {output_file} ({output_file.stat().st_size} bytes)")
    return True


def unpack_extension(flex_path: Path, output_dir: Path) -> bool:
    if not zipfile.is_zipfile(flex_path):
        print(f"Error: '{flex_path}' is not a valid zip / .flex archive", file=sys.stderr)
        return False
    output_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(flex_path, "r") as zf:
        zf.extractall(output_dir)
    print(f"Successfully extracted '{flex_path}' to '{output_dir}'")
    return True


def audit_coverage(stylesheet_path: Path) -> Dict[str, Any]:
    data = json.loads(stylesheet_path.read_text(encoding="utf-8"))
    rules = [k for k in data.keys() if not k.startswith("$") and not k.startswith("@")]
    covered_elements = set()
    for r in rules:
        parsed = parse_snygg_rule(r)
        if parsed:
            covered_elements.add(parsed[0])

    all_elements = set(FLORIS_IME_UI_ELEMENTS.keys())
    missing_elements = sorted(list(all_elements - covered_elements))
    coverage_pct = (len(covered_elements) / len(all_elements)) * 100.0

    return {
        "path": str(stylesheet_path),
        "total_elements": len(all_elements),
        "covered_elements": len(covered_elements),
        "coverage_pct": coverage_pct,
        "missing_elements": missing_elements,
        "total_rules": len(rules),
    }


def main():
    parser = argparse.ArgumentParser(description="Snygg theme authoring and validation tool for OmniBoard")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # validate
    val_parser = subparsers.add_parser("validate", help="Validate a stylesheet JSON or theme extension directory")
    val_parser.add_argument("path", type=str, help="Path to stylesheet JSON or extension directory")

    # audit
    audit_parser = subparsers.add_parser("audit", help="Audit element coverage of a stylesheet")
    audit_parser.add_argument("path", type=str, help="Path to stylesheet JSON")

    # package
    pkg_parser = subparsers.add_parser("package", help="Package a theme extension directory into a .flex ZIP")
    pkg_parser.add_argument("ext_dir", type=str, help="Path to theme extension directory")
    pkg_parser.add_argument("-o", "--output", type=str, required=True, help="Output .flex file path")

    # unpack
    unp_parser = subparsers.add_parser("unpack", help="Extract a .flex package")
    unp_parser.add_argument("flex_file", type=str, help="Path to .flex file")
    unp_parser.add_argument("-o", "--output", type=str, required=True, help="Destination directory")

    args = parser.parse_args()

    if args.command == "validate":
        target = Path(args.path)
        if not target.exists():
            print(f"Error: '{target}' does not exist", file=sys.stderr)
            sys.exit(1)
        
        if target.is_dir():
            errors = validate_extension_dir(target)
        else:
            try:
                data = json.loads(target.read_text(encoding="utf-8"))
                errors = validate_stylesheet_data(data, str(target))
            except Exception as e:
                print(f"Error parsing JSON at '{target}': {e}", file=sys.stderr)
                sys.exit(1)

        fatal = [e for e in errors if not e.is_warning]
        warnings = [e for e in errors if e.is_warning]
        
        for w in warnings:
            print(w)
        for f in fatal:
            print(f, file=sys.stderr)

        if fatal:
            print(f"FAILED: {len(fatal)} error(s), {len(warnings)} warning(s)", file=sys.stderr)
            sys.exit(1)
        else:
            print(f"PASSED: Valid Snygg theme with {len(warnings)} warning(s)")
            sys.exit(0)

    elif args.command == "audit":
        target = Path(args.path)
        if not target.exists() or not target.is_file():
            print(f"Error: '{target}' must be a valid file", file=sys.stderr)
            sys.exit(1)
        res = audit_coverage(target)
        print(f"=== Snygg Element Coverage Audit ===")
        print(f"Stylesheet: {res['path']}")
        print(f"Total rules: {res['total_rules']}")
        print(f"Coverage: {res['covered_elements']} / {res['total_elements']} ({res['coverage_pct']:.1f}%)")
        if res['missing_elements']:
            print("Missing element rules:")
            for m in res['missing_elements']:
                print(f"  - {m}")
        else:
            print("Full 100% element coverage!")

    elif args.command == "package":
        ext_dir = Path(args.ext_dir)
        out_file = Path(args.output)
        if not ext_dir.is_dir():
            print(f"Error: '{ext_dir}' is not a directory", file=sys.stderr)
            sys.exit(1)
        if not package_extension(ext_dir, out_file):
            sys.exit(1)

    elif args.command == "unpack":
        flex_path = Path(args.flex_file)
        out_dir = Path(args.output)
        if not unpack_extension(flex_path, out_dir):
            sys.exit(1)


if __name__ == "__main__":
    main()
