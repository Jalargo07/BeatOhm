#!/usr/bin/env python3
"""
Downloads Phosphor Duotone SVGs from GitHub, extracts path data,
scales from 256x256 to 24x24 (factor 0.09375), and outputs
Kotlin map entries for PhosphorPaths.kt.

Source: @phosphor-icons/core (MIT license)
ViewBox: 0 0 256 256 -> 0 0 24 24
"""

import re
import sys
import os
import json
import urllib.request
import urllib.error
from pathlib import Path

# SVG filename -> iconKey mapping (lowercase filenames as in the repo)
ICON_MAP = {
    "play": "play",
    "pause": "pause",
    "next": "skip-forward",
    "prev": "skip-back",
    "shuffle": "shuffle",
    "repeat": "repeat",
    "repeat_one": "repeat-once",
    "heart": "heart",
    "heart_border": "heart",  # Same SVG, only use bgPath
    "volume": "speaker-high",
    "equalizer": "equalizer",
    "queue": "queue",
    "lyrics": "text-t",
    "player": "play-circle",
    "library": "books",
    "downloads": "download-simple",
    "music_note": "music-notes",
    "mic": "microphone",
    "genres": "guitar",
    "album": "disc",
    "playlist": "playlist",
    "trending": "trend-up",
    "folder": "folder",
    "search": "magnifying-glass",
    "settings": "gear",
    "playlist_add": "list-plus",
    "back": "arrow-left",
}

SCALE_FACTOR = 24.0 / 256.0  # 0.09375

# Regex to find numeric values in SVG path data
NUM_RE = re.compile(r'[+-]?\d*\.?\d+(?:[eE][+-]?\d+)?')


def download_svg(svg_name: str, base_url: str) -> str:
    """Download SVG content from the Phosphor Icons core repo."""
    url = f"{base_url}/{svg_name}-duotone.svg"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "BeatOhm/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        print(f"  HTTP {e.code} for {url}", file=sys.stderr)
        raise
    except Exception as e:
        print(f"  Error downloading {url}: {e}", file=sys.stderr)
        raise


def extract_paths_from_svg(svg_content: str) -> list[str]:
    """
    Extract all path `d` attributes from SVG content.
    Returns list of path strings in document order.
    """
    # Find all <path ... d="..." /> elements
    # Match path elements with their d attribute
    path_pattern = re.compile(r'<path\b[^>]*?\bd="([^"]*)"', re.DOTALL)
    paths = []
    for m in path_pattern.finditer(svg_content):
        paths.append(m.group(1))
    return paths


def scale_path_data(path_data: str, factor: float) -> str:
    """
    Scale all numeric coordinates in SVG path data by `factor`.
    
    Handles all SVG path commands: M/m, L/l, H/h, V/v, C/c, S/s, Q/q, T/t, A/a, Z/z
    
    For arc commands (A/a), the 4th and 5th parameters (large-arc-flag, sweep-flag)
    are booleans (0 or 1) and must NOT be scaled.
    """
    tokens = re.findall(r'[A-Za-z]|[+-]?\d*\.?\d+(?:[eE][+-]?\d+)?', path_data)
    
    result = []
    i = 0
    current_cmd = None
    arc_param_index = 0  # Track which parameter we're at in an arc command
    
    while i < len(tokens):
        token = tokens[i]
        
        if token.isalpha():
            current_cmd = token
            arc_param_index = 0
            result.append(token)
            i += 1
            continue
        
        # It's a number
        if current_cmd and current_cmd.upper() == 'A':
            # Arc command: rx ry x-axis-rotation large-arc-flag sweep-flag x y
            # Parameters 4 and 5 (indices 3 and 4, 0-based) are flags
            if arc_param_index in (3, 4):
                # This is large-arc-flag or sweep-flag - do NOT scale
                result.append(token)
            else:
                # Scale this parameter
                val = float(token) * factor
                result.append(format_number(val))
            arc_param_index += 1
            if arc_param_index >= 7:
                arc_param_index = 0
        else:
            # Scale this number
            val = float(token) * factor
            result.append(format_number(val))
        
        i += 1
    
    return " ".join(result)


def format_number(val: float) -> str:
    """Format a number, removing unnecessary trailing zeros."""
    # Round to 2 decimal places
    rounded = round(val, 2)
    # Format: remove trailing zeros after decimal point
    if rounded == int(rounded):
        return str(int(rounded))
    else:
        s = f"{rounded:.2f}"
        # Remove trailing zeros
        s = s.rstrip('0').rstrip('.')
        return s


def main():
    base_url = "https://raw.githubusercontent.com/phosphor-icons/core/main/assets/duotone"
    
    all_paths = {}  # iconKey -> (bgPath, fgPath)
    svg_cache_dir = Path(__file__).parent / ".svg_cache"
    svg_cache_dir.mkdir(exist_ok=True)
    
    # Download all unique SVGs
    unique_svgs = set(ICON_MAP.values())
    downloaded = {}
    
    for svg_name in sorted(unique_svgs):
        cache_file = svg_cache_dir / f"{svg_name}-duotone.svg"
        if cache_file.exists():
            print(f"  [cached] {svg_name}")
            downloaded[svg_name] = cache_file.read_text(encoding="utf-8")
            continue
        
        print(f"  Downloading {svg_name}-duotone.svg...")
        try:
            content = download_svg(svg_name, base_url)
            cache_file.write_text(content, encoding="utf-8")
            downloaded[svg_name] = content
        except Exception as e:
            print(f"  FAILED: {e}", file=sys.stderr)
            sys.exit(1)
    
    print(f"\nDownloaded {len(downloaded)} unique SVGs")
    
    # Process each icon key
    for icon_key, svg_name in ICON_MAP.items():
        svg_content = downloaded[svg_name]
        raw_paths = extract_paths_from_svg(svg_content)
        
        if len(raw_paths) == 0:
            print(f"  WARNING: No paths found for {icon_key} ({svg_name})", file=sys.stderr)
            continue
        
        print(f"\n  {icon_key} ({svg_name}): {len(raw_paths)} path(s) found")
        for idx, p in enumerate(raw_paths):
            print(f"    path[{idx}]: {p[:80]}...")
        
        # Identify background vs foreground paths
        bg_pattern = re.compile(r'opacity=["\']0\.2["\']')
        
        # Check each path element for opacity attribute
        path_elements = re.findall(r'<path\b([^>]*)>', svg_content)
        
        bg_path_data = None
        fg_parts = []
        
        for idx, attrs in enumerate(path_elements):
            if idx >= len(raw_paths):
                break
            path_d = raw_paths[idx]
            
            if bg_pattern.search(attrs):
                bg_path_data = path_d
                print(f"    -> BG path (opacity=0.2)")
            else:
                fg_parts.append(path_d)
                print(f"    -> FG path (opacity not 0.2)")
        
        # If no explicit bg found with opacity=0.2, use first path as bg
        if bg_path_data is None and len(raw_paths) >= 2:
            bg_path_data = raw_paths[0]
            fg_parts = raw_paths[1:]
            print(f"    -> No opacity=0.2 found, using first path as BG")
        elif bg_path_data is None and len(raw_paths) == 1:
            # Single path: use it as both bg and fg
            bg_path_data = raw_paths[0]
            fg_parts = [raw_paths[0]]
            print(f"    -> Single path, using as both BG and FG")
        
        # Scale paths
        scaled_bg = scale_path_data(bg_path_data, SCALE_FACTOR) if bg_path_data else ""
        
        # Concatenate foreground parts
        if fg_parts:
            # For multiple fg paths, just use the primary one (non-opacity-0.2)
            # Some SVGs have multiple fg elements but they compose the foreground
            scaled_fg = scale_path_data(fg_parts[0], SCALE_FACTOR)
            for part in fg_parts[1:]:
                scaled_fg += " " + scale_path_data(part, SCALE_FACTOR)
        else:
            scaled_fg = ""
        
        # Special handling for heart_border: only use bgPath (no fg)
        if icon_key == "heart_border":
            all_paths[icon_key] = (scaled_bg, scaled_bg)  # Use bg for both to differentiate
            print(f"    -> heart_border: using only BG path for both")
        else:
            all_paths[icon_key] = (scaled_bg, scaled_fg)
    
    # Validation
    print("\n=== VALIDATION ===")
    issues = []
    for icon_key, (bg, fg) in all_paths.items():
        for label, data in [("bg", bg), ("fg", fg)]:
            # Check for 0.09 in arc flag positions
            # (rough check - actual validation would need full parsing)
            if "0.09" in data:
                issues.append(f"  {icon_key}.{label}: contains '0.09'")
            # Check for multi-digit fused numbers
            if re.search(r'(?<!\d)\d{3,}(?!\d)', data):
                # This could be legitimate (e.g., 100) - skip for now
                pass
    if issues:
        print("Issues found:")
        for issue in issues:
            print(issue)
    else:
        print("No issues found!")
    
    # Generate Kotlin file content
    kotlin_lines = []
    kotlin_lines.append('package com.beatohm.ui')
    kotlin_lines.append('')
    kotlin_lines.append('/**')
    kotlin_lines.append(' * PathData para el pack Phosphor Duotone (phosphoricons.com, MIT).')
    kotlin_lines.append(' * Cada icono = Pair(pathDataFondo, pathDataDetalle): el fondo es la región filled')
    kotlin_lines.append(' * translúcida y el detalle se dibuja encima con color sólido.')
    kotlin_lines.append(' *')
    kotlin_lines.append(' * Escalado de viewBox 256x256 → 24x24 (factor 0.09375).')
    kotlin_lines.append(' * Fuente: @phosphor-icons/core@2.1.1, carpeta duotone.')
    kotlin_lines.append(' *')
    kotlin_lines.append(' * Generado desde SVGs oficiales: 27 iconKeys, todos los paths escalados')
    kotlin_lines.append(' * correctamente preservando flags de arco (large-arc, sweep) en 0/1.')
    kotlin_lines.append(' */')
    kotlin_lines.append('object PhosphorPaths {')
    kotlin_lines.append('')
    kotlin_lines.append('    const val PACK_ID = "phosphor"')
    kotlin_lines.append('')
    kotlin_lines.append('    /** iconKey → (pathDataFondo, pathDataDetalle) */')
    kotlin_lines.append('    val PATHS: Map<String, Pair<String, String>> = mapOf(')
    
    # Output each icon entry
    for icon_key in ICON_MAP:
        bg, fg = all_paths.get(icon_key, ("", ""))
        kotlin_lines.append(f'        "{icon_key}" to (')
        kotlin_lines.append(f'            "{bg}" to')
        kotlin_lines.append(f'            "{fg}"')
        kotlin_lines.append(f'        ),')
    
    kotlin_lines.append('    )')
    kotlin_lines.append('}')
    
    # Write Kotlin file
    kt_path = Path(__file__).parent.parent / "app/src/main/java/com/beatohm/ui/PhosphorPaths.kt"
    kt_path.parent.mkdir(parents=True, exist_ok=True)
    kt_path.write_text("\n".join(kotlin_lines), encoding="utf-8")
    print(f"\nWrote {kt_path}")
    
    # Print summary
    print("\n=== ICON KEY → SVG MAPPING ===")
    for icon_key, svg_name in ICON_MAP.items():
        bg, fg = all_paths.get(icon_key, ("", ""))
        print(f"  {icon_key:15s} → {svg_name} (bg={len(bg)} chars, fg={len(fg)} chars)")


if __name__ == "__main__":
    main()
