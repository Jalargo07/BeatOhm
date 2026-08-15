#!/usr/bin/env python3
"""
Validate SVG pathData in *Paths.kt files.

After every A/a (arc) command there must be exactly 7 numeric tokens:
  rx ry rotation large-arc-flag sweep-flag x y

Implicit arc repetition: after an explicit A/a, subsequent groups of 7
numbers are also arcs (no command letter needed).

Exit code 0 = all packs clean, 1 = errors found.
"""

import re
import sys
from pathlib import Path

KT_FILES = [
    "app/src/main/java/com/beatohm/ui/GlassPaths.kt",
    "app/src/main/java/com/beatohm/ui/GradientPaths.kt",
    "app/src/main/java/com/beatohm/ui/NeonPaths.kt",
    "app/src/main/java/com/beatohm/ui/PhosphorPaths.kt",
]

# Regex to extract: "iconKey" to "pathData"
ENTRY_RE = re.compile(r'"(\w+)"\s+to\s+"([^"]+)"')

# Tokenise SVG path data into commands and numbers.
# Numbers: optional sign, then (digits[.digits] or .digits), optional exponent
NUM_RE = re.compile(
    r'[A-Za-z]'                                           # command letter
    r'|[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?'       # number
)

ARC_PARAMS = 7


def tokenise_path(d):
    return NUM_RE.findall(d)


def validate_pathdata(icon_key, path_data):
    """Validate that every arc has exactly 7 numeric params (with implicit repetition)."""
    errors = []
    tokens = tokenise_path(path_data)

    i = 0
    while i < len(tokens):
        tok = tokens[i]
        if tok in "Aa":
            # Collect all numeric tokens until the next command letter
            j = i + 1
            while j < len(tokens) and not tokens[j].isalpha():
                j += 1
            nums = tokens[i + 1 : j]

            # Split into groups of ARC_PARAMS
            if len(nums) % ARC_PARAMS != 0:
                fused = " ".join(nums) if nums else "(none)"
                errors.append(
                    "  %s: %s has %d params (not multiple of %d): %s"
                    % (icon_key, tok, len(nums), ARC_PARAMS, fused)
                )
            i = j
        else:
            i += 1

    return errors


def extract_entries_from_kt(filepath):
    text = filepath.read_text(encoding="utf-8")
    for m in ENTRY_RE.finditer(text):
        yield m.group(1), m.group(2)


def main():
    root = Path(__file__).resolve().parent.parent
    all_errors = []

    for rel in KT_FILES:
        fpath = root / rel
        pack_name = fpath.stem
        if not fpath.exists():
            print("  SKIP %s: file not found" % pack_name)
            continue

        errors = []
        for icon_key, path_data in extract_entries_from_kt(fpath):
            errors.extend(validate_pathdata(icon_key, path_data))

        if errors:
            all_errors.append((pack_name, errors))
            print("FAIL %s:" % pack_name)
            for e in errors:
                print(e)
        else:
            print("OK   %s" % pack_name)

    if all_errors:
        total = sum(len(e) for _, e in all_errors)
        print("\nFAILED - %d error(s) in %d pack(s)" % (total, len(all_errors)))
        sys.exit(1)
    else:
        print("\nALL PACKS OK")
        sys.exit(0)


if __name__ == "__main__":
    main()
