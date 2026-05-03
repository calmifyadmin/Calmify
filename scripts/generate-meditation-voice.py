#!/usr/bin/env python3
"""
Meditation voice generator.

Reads `scripts/meditation-voice-catalog.csv` (asset_key → strings_xml_key
mapping) and the 12 `core/ui/.../composeResources/values{,-it,-es,...}/strings.xml`
files. For each (locale, asset_key) pair, calls ElevenLabs to synthesize the
text and writes the .mp3 to:

    features/meditation/src/androidMain/assets/meditation/voice/{locale}/{asset_key}.mp3

Run from the repo root:
    pip install requests
    export ELEVENLABS_API_KEY=sk_...
    python scripts/generate-meditation-voice.py [--locales en,it] [--keys cue_breathe_in,cue_hold] [--dry-run]

Total catalog: 29 unique keys × 12 locales = 348 files.

The script is **idempotent**: it skips files that already exist on disk
unless `--force` is passed. Re-running after a partial failure resumes from
where it left off without spending API credits on re-generation.

After successful generation, commit the assets directory and the APK ships
with voice mode functional. No further code changes needed.
"""

from __future__ import annotations

import argparse
import csv
import os
import re
import sys
import time
from html import unescape
from pathlib import Path
from xml.etree import ElementTree as ET

REPO_ROOT = Path(__file__).resolve().parents[1]
CATALOG_CSV = REPO_ROOT / "scripts" / "meditation-voice-catalog.csv"
STRINGS_XML_BASE = REPO_ROOT / "core" / "ui" / "src" / "commonMain" / "composeResources"
OUTPUT_BASE = REPO_ROOT / "features" / "meditation" / "src" / "androidMain" / "assets" / "meditation" / "voice"

# Maps the locale folder suffix in composeResources to the locale tag
# Calmify uses for asset paths (the second is what `currentLocaleTag()` in
# MeditationEntryPoint.kt returns from `Locale.getDefault().language`).
LOCALES = {
    "values":    "en",   # default
    "values-it": "it",
    "values-es": "es",
    "values-fr": "fr",
    "values-de": "de",
    "values-pt": "pt",
    "values-ar": "ar",
    "values-zh": "zh",
    "values-ja": "ja",
    "values-ko": "ko",
    "values-hi": "hi",
    "values-th": "th",
}

# ElevenLabs config
# - Voice ID: pick once from https://elevenlabs.io/app/voice-library and pin here.
#   Default below is "Rachel" (calm, clear, neutral) — change if you want a
#   different brand voice. Same voice ID is used for ALL locales because
#   eleven_multilingual_v2 produces the same character across languages.
# - Model: eleven_multilingual_v2 supports all 12 of our target locales with a
#   single model + voice.
ELEVENLABS_VOICE_ID = os.environ.get("ELEVENLABS_VOICE_ID", "21m00Tcm4TlvDq8ikWAM")  # Rachel
ELEVENLABS_MODEL_ID = "eleven_multilingual_v2"
ELEVENLABS_API_BASE = "https://api.elevenlabs.io/v1"

# Voice settings tuned for calm, instructional delivery
VOICE_SETTINGS = {
    "stability": 0.65,       # higher = less variation, more consistent
    "similarity_boost": 0.75,
    "style": 0.20,           # low = more neutral; high = more expressive
    "use_speaker_boost": True,
}


def read_catalog() -> list[tuple[str, str]]:
    """Returns list of (asset_key, strings_xml_key) tuples."""
    with CATALOG_CSV.open(encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return [(row["asset_key"], row["strings_xml_key"]) for row in reader]


def read_strings(values_folder: str) -> dict[str, str]:
    """Parses strings.xml from one locale folder into {key: text}."""
    xml_path = STRINGS_XML_BASE / values_folder / "strings.xml"
    if not xml_path.exists():
        print(f"  ! strings.xml missing for {values_folder} — skipping locale", file=sys.stderr)
        return {}

    try:
        tree = ET.parse(xml_path)
    except ET.ParseError as e:
        print(f"  ! parse error in {xml_path}: {e}", file=sys.stderr)
        return {}

    out: dict[str, str] = {}
    for el in tree.getroot().findall("string"):
        name = el.get("name")
        if not name:
            continue
        text = el.text or ""
        # Android strings escape \' for apostrophes; unescape for TTS.
        text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
        text = unescape(text)
        # Strip any trailing/leading whitespace
        text = text.strip()
        out[name] = text
    return out


def synthesize(text: str, output_path: Path, api_key: str) -> bool:
    """POST to ElevenLabs, write MP3 to output_path. Returns True on success."""
    import requests

    url = f"{ELEVENLABS_API_BASE}/text-to-speech/{ELEVENLABS_VOICE_ID}"
    headers = {
        "Accept": "audio/mpeg",
        "Content-Type": "application/json",
        "xi-api-key": api_key,
    }
    payload = {
        "text": text,
        "model_id": ELEVENLABS_MODEL_ID,
        "voice_settings": VOICE_SETTINGS,
    }

    try:
        r = requests.post(url, headers=headers, json=payload, timeout=60)
    except requests.RequestException as e:
        print(f"    network error: {e}", file=sys.stderr)
        return False

    if r.status_code != 200:
        print(f"    HTTP {r.status_code}: {r.text[:200]}", file=sys.stderr)
        return False

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(r.content)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--locales", help="Comma-separated locale tags to generate (e.g. en,it). Default: all.")
    parser.add_argument("--keys", help="Comma-separated asset keys to generate. Default: all from catalog.")
    parser.add_argument("--force", action="store_true", help="Regenerate even if the .mp3 already exists.")
    parser.add_argument("--dry-run", action="store_true", help="Print what would be generated without calling the API.")
    parser.add_argument("--throttle", type=float, default=0.3, help="Seconds to sleep between calls (default 0.3).")
    args = parser.parse_args()

    api_key = os.environ.get("ELEVENLABS_API_KEY")
    if not api_key and not args.dry_run:
        print("error: set ELEVENLABS_API_KEY env var (or use --dry-run)", file=sys.stderr)
        return 2

    catalog = read_catalog()
    if args.keys:
        wanted = set(args.keys.split(","))
        catalog = [(a, s) for a, s in catalog if a in wanted]
        if not catalog:
            print(f"error: --keys filter matched nothing", file=sys.stderr)
            return 2

    if args.locales:
        wanted_locales = set(args.locales.split(","))
        locale_pairs = [(folder, tag) for folder, tag in LOCALES.items() if tag in wanted_locales]
    else:
        locale_pairs = list(LOCALES.items())

    print(f"Catalog: {len(catalog)} keys × {len(locale_pairs)} locales = {len(catalog) * len(locale_pairs)} files")
    if args.dry_run:
        print("(dry-run — no API calls)")

    total = 0
    skipped = 0
    failed = 0
    generated = 0

    for values_folder, locale_tag in locale_pairs:
        strings = read_strings(values_folder)
        if not strings:
            continue
        for asset_key, xml_key in catalog:
            total += 1
            text = strings.get(xml_key)
            if not text:
                print(f"  ! [{locale_tag}] {asset_key}: missing xml key '{xml_key}'", file=sys.stderr)
                failed += 1
                continue

            output_path = OUTPUT_BASE / locale_tag / f"{asset_key}.mp3"
            if output_path.exists() and not args.force:
                skipped += 1
                continue

            print(f"  → [{locale_tag}] {asset_key} ← \"{text[:60]}{'...' if len(text) > 60 else ''}\"")
            if args.dry_run:
                continue

            ok = synthesize(text, output_path, api_key)
            if ok:
                generated += 1
            else:
                failed += 1

            if args.throttle > 0:
                time.sleep(args.throttle)

    print()
    print(f"Done. Total {total} | generated {generated} | skipped (already exist) {skipped} | failed {failed}")
    if failed:
        print("Some files failed — re-run to retry only the missing ones (script is idempotent).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
