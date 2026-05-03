# Meditation voice assets

This directory holds pre-generated voice utterances played during the meditation
flow when the user selects **Audio: Voice** on the Configure screen.

```
voice/
├── README.md        ← this file
├── en/              ← English (default — also acts as fallback)
│   ├── cue_breathe_in.mp3
│   ├── cue_breathe_out.mp3
│   ├── cue_hold.mp3
│   ├── cue_arrive.mp3
│   ├── cue_release.mp3
│   ├── settle_coach_1.mp3
│   ├── settle_coach_2.mp3
│   ├── settle_coach_3.mp3
│   ├── integrate_coach_1.mp3
│   ├── integrate_coach_2.mp3
│   ├── integrate_coach_3.mp3
│   ├── coherent_coach_1.mp3
│   ├── coherent_coach_2.mp3
│   ├── coherent_coach_3.mp3
│   ├── extended_exhale_coach_1.mp3
│   ├── extended_exhale_coach_2.mp3
│   ├── extended_exhale_coach_3.mp3
│   ├── box_breathing_coach_1.mp3
│   ├── box_breathing_coach_2.mp3
│   ├── box_breathing_coach_3.mp3
│   ├── relaxation_478_coach_1.mp3
│   ├── relaxation_478_coach_2.mp3
│   ├── relaxation_478_coach_3.mp3
│   ├── belly_natural_coach_1.mp3
│   ├── belly_natural_coach_2.mp3
│   ├── belly_natural_coach_3.mp3
│   ├── body_scan_natural_coach_1.mp3
│   ├── body_scan_natural_coach_2.mp3
│   └── body_scan_natural_coach_3.mp3
├── it/              ← same 29 files in Italian
├── es/, fr/, de/, pt/    ← Latin
└── ar/, zh/, ja/, ko/, hi/, th/   ← Asian / Arabic
```

**29 unique keys × 12 locales = 348 .mp3 files** when fully populated.

## Status (initial commit)

This directory is **empty** in the initial Phase 3.B' commit. The Kotlin code
ships with full voice support wired (player, catalog, VM effects, audio focus),
but there are no audio files yet — VOICE mode currently behaves as CHIMES (chime
plays, no voice utterances) until you generate the assets.

The voice player gracefully handles missing assets:
- Tries `voice/{requested_locale}/{key}.mp3`
- Falls back to `voice/en/{key}.mp3`
- If neither exists, logs a debug line and silently no-ops

So generating just `en/` first is a perfectly valid intermediate state — voice
will work for English users; non-English users will hear English voice with their
own UI text. Add other locales as you can.

## How to generate the assets

### 1. Create an ElevenLabs account

https://elevenlabs.io — paid Creator tier ($22/mo) gives you ~100K characters/mo,
which is way more than the catalog needs (the full 12-locale catalog is ~7K
characters total). The free tier (10K chars/mo) is enough to generate one or
two languages for testing.

### 2. Pick a voice

Browse https://elevenlabs.io/app/voice-library and pick a voice you want as
the "Calmify Voice". The default in the script is **Rachel** (`21m00Tcm4TlvDq8ikWAM`)
— calm, clear, gender-neutral-leaning. To use a different one, copy its voice
ID and:

```bash
export ELEVENLABS_VOICE_ID=YourVoiceIdHere
```

The same voice is used for ALL 12 languages — `eleven_multilingual_v2` renders
the same character across them. This is a brand consistency choice.

### 3. Run the generator

```bash
# From the repo root
pip install requests
export ELEVENLABS_API_KEY=sk_...
python scripts/generate-meditation-voice.py
```

The script is **idempotent** — it skips files that already exist on disk.
Re-running after a partial failure resumes from where it left off.

Useful flags:
- `--locales en,it` — generate only specified locales
- `--keys cue_breathe_in,cue_hold` — generate only specified utterances (use
  this for spot-fixing one bad file)
- `--force` — regenerate even if the .mp3 already exists
- `--dry-run` — print what would be generated without calling the API
- `--throttle 0.5` — seconds to sleep between calls (default 0.3)

### 4. Listen + spot-fix

Listen to the generated files in their target context (the meditation flow
on a real device with headphones). For any utterance that sounds off,
re-run with `--keys <key> --locales <locale> --force`.

The catalog text comes from `core/ui/src/commonMain/composeResources/values{,-it,-es,...}/strings.xml` —
to change what an utterance SAYS, edit those XML files first, then regenerate
with `--force`.

### 5. Commit the assets

```bash
git add features/meditation/src/androidMain/assets/meditation/voice/
git commit -m "audio(meditation): add voice assets for {locales}"
```

## Cost estimate

Catalog text per locale ≈ 600 chars. Across 12 locales ≈ 7,200 chars total.
ElevenLabs Creator ($22/mo, 100K chars) generates the entire catalog with
plenty of headroom for re-generation iterations.

If you change the catalog text (edit a coach line, add a technique), only
the affected utterances need regeneration thanks to the script's idempotency.

## APK size

Each .mp3 is ~30-100 KB depending on text length and ElevenLabs codec settings.
Full 12-locale catalog ≈ 25 MB APK overhead. Single locale (en only) ≈ 2 MB.

## Adding a new utterance

1. Add a `<string name="...">` to all 12 strings.xml files
2. Add a row to `scripts/meditation-voice-catalog.csv` mapping
   `your_asset_key` → `your_xml_key`
3. Add a `VoiceUtterance` variant in
   `features/meditation/src/commonMain/.../voice/MeditationVoiceCatalog.kt`
4. Wire emission in `MeditationViewModel`
5. Run `python scripts/generate-meditation-voice.py --keys your_asset_key`

## Adding a new locale

1. Add the locale folder in `composeResources/values-{tag}/strings.xml`
   with the keys translated
2. Add the locale to `LOCALES` dict in `generate-meditation-voice.py`
3. Run `python scripts/generate-meditation-voice.py --locales {tag}`

## Troubleshooting

**"missing xml key 'meditation_xxx_yyy'"** — the catalog CSV references an XML
key that doesn't exist in the locale's strings.xml. Either add the translation
or remove the catalog row.

**HTTP 401 / 403** — wrong or missing API key. `echo $ELEVENLABS_API_KEY` to
verify; regenerate at https://elevenlabs.io/app/settings/api-keys if needed.

**HTTP 429** — rate-limited. Increase `--throttle 1.0` or run smaller batches.

**Voice sounds wrong** — try changing `VOICE_SETTINGS` in the script
(stability/style/similarity). For per-locale tuning, you can hardcode different
settings inline. For a different voice character entirely, change
`ELEVENLABS_VOICE_ID`.

**APK build fails after adding assets** — assets directory is auto-merged by
AGP; confirm the path is exactly
`features/meditation/src/androidMain/assets/meditation/voice/{lang}/{key}.mp3`
(case-sensitive on Linux build agents).
