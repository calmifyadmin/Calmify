# Meditation TTS — Architecture Decision Required

**Status**: blocked on user decision  
**Owner**: user  
**Created**: 2026-05-03 (Phase 3.B)  
**Branch**: `backend-architecture-refactor`

The redesigned meditation flow exposes 3 audio modes on the Configure screen:
**SILENT** / **CHIMES** / **VOICE**. Phase 3.A wired keyboard + reduced motion;
Phase 3.B (this doc) gates the chime on `audio != SILENT` (commit `_pending_`).
**Voice mode is not yet wired** because there is no obviously-right TTS
backend — every option has real trade-offs and they all touch APK size, cost,
or quality. This document surveys the four candidates and recommends one,
but the call is yours.

## Why we can't just reuse the existing chat TTS

The chat module ships `GeminiNativeVoiceSystem` (`features/chat/src/androidMain/.../GeminiNativeVoiceSystem.kt:26`) which calls Gemini 2.0 Flash for native audio output. This is **wrong-shaped for meditation**:

| Concern | Chat (works fine) | Meditation (breaks) |
|---|---|---|
| Utterance count | 1-3 turns/min | ~75 utterances per 15-min session (cue per segment + coach lines) |
| Latency tolerance | 200-800ms is fine | Cue must sync to breath visual ±50ms |
| Cost | Per-turn negligible | ~$0.05-$0.20 per session × millions of sessions = real money |
| Connectivity | Already required | Meditation works offline today; voice mode would silently fail |
| Battery | Burst | 15 min continuous network = drain |

The chat TTS gets cue word #1 ("Breathe in") spoken at second 0.0 then waits 200-800ms. By the time it lands, the visual is already 200ms into the inhale — the user hears "Breathe in" while the circle is mid-expand. Breaks the contemplative rhythm completely.

**Bottom line**: meditation needs **low-latency, offline-capable, predictable-cost** TTS. Cloud is the wrong shape.

## The four candidates

### Option A — Pre-generated audio bundled in the APK

Ship cue words and coach lines as `.ogg` files inside `composeResources/files/`.
A simple lookup table maps `Strings.Meditation.Cue.breatheIn` to
`audio/{lang}/cue_breathe_in.ogg`. Local playback via `MediaPlayer`.

**Inventory**:
- Cue words: 6 cues × 1 voice × 12 langs × ~30 KB each = **2.2 MB**
- Coach lines: 6 techniques × 3 lines + 3 settle + 3 integrate = 24 lines × 12 langs × ~80 KB = **23 MB**
- **Total APK overhead: ~25 MB** for full coverage; **~13 MB** for 6 Latin only

**Pros**:
- Zero latency (single `MediaPlayer.start()`)
- 100% offline
- Zero per-session cost
- Voice quality is whatever you generate it as (we control the recording quality once)
- No platform fragmentation — bytes are bytes

**Cons**:
- 25 MB APK bloat (Play Store cap is 200 MB; we have headroom but it's not free)
- Pre-generation is a one-time effort: pick a TTS provider (ElevenLabs, Google Cloud TTS, Azure Neural), generate the 24 × 12 = 288 audio files, encode to .ogg, ship
- Adding a new technique or coach line requires regenerating + APK update (no hot-fix)
- If we want multiple voice options ("male Italian voice", "female English voice"), bundle size multiplies
- License: depends on TTS provider; ElevenLabs explicitly allows commercial redistribution of generated audio, Google Cloud TTS mostly does — verify before shipping

**Recommendation**: this is the meditation industry standard (Calm, Headspace, Insight Timer all bundle pre-generated audio). It is the right shape for the problem.

### Option B — Android system `TextToSpeech`

Use the built-in `android.speech.tts.TextToSpeech` API. Free, offline (when language pack is downloaded), supports every locale Android supports.

**Pros**:
- Zero APK overhead
- Zero per-session cost
- Locale-aware automatically (uses the user's downloaded TTS language pack)
- Supports text we haven't pre-generated (future-proof)
- Trivially simple integration: `tts.speak(text, QUEUE_FLUSH, null, "cue_id")`

**Cons**:
- **Voice quality**: ranges from "acceptable" (Pixel/Samsung) to "deeply unpleasant" (some OEMs). Not the meditative-tone bar.
- User may not have their language's TTS pack downloaded. Need fallback (silent? prompt to install?)
- Voice character is whatever the system's default is. Cannot enforce a brand voice.
- Some langs (especially the Phase A'' additions: AR / ZH / JA / KO / HI / TH) may not have TTS at all on a given device

**Recommendation**: Good fallback if Option A is too heavy. Bad as primary because quality is the entire point of meditation voice guidance.

### Option C — Sherpa-ONNX on-device neural TTS

Sherpa-ONNX is the framework the original plan referenced. It runs neural TTS models (e.g. VITS, Piper) on-device. **Currently NOT integrated in the Calmify codebase** despite `KMP_TIER4_MIGRATE.md` mentioning it (the chat module uses Gemini cloud, not Sherpa).

**Pros**:
- High quality (neural; better than system TTS)
- Offline
- Zero per-session cost after the initial integration
- Models can be swapped without rebuilding the audio bundle

**Cons**:
- **Models are large**: a single English Piper model is 60-80 MB. Multi-locale = 500+ MB. Far exceeds Play Store APK cap. Would require Play Asset Delivery (dynamic feature module + on-demand download)
- **Integration complexity**: bind a native library, ship the model, manage downloads, handle download-failure edge cases, support Play Asset Delivery
- **CPU/battery**: neural inference is meaningful. A 15-min session running the model continuously is noticeable on low-end devices
- Voice character constrained by what models exist; not as high quality as a professionally-recorded voice actor would deliver via Option A
- Realistically a 4-6 week integration project (binding, models, asset delivery, testing across devices)

**Recommendation**: Right answer for an open-ended assistant feature where text varies. Wrong answer for meditation where text is a fixed catalog of ~150 phrases.

### Option D — Defer voice indefinitely

Ship meditation with SILENT and CHIMES only. Remove VOICE from the audio
selector or grey it out as "Coming soon".

**Pros**:
- Zero work
- App ships sooner
- No risk of a half-baked voice feature degrading the meditation experience

**Cons**:
- The Configure screen has VOICE as one of three audio options — removing it
  means narrower choice, telling users "Coming soon" feels apologetic
- Voice guidance is the #1 differentiator vs. just-a-timer apps. Calm /
  Headspace built billion-dollar businesses on voice
- Eventually we'll want voice; deferring just delays the inevitable decision

## Recommendation

**Option A — pre-generated audio**, bundle for 6 Latin langs at launch (~13 MB
APK overhead), expand to all 12 if Phase A'' user demand justifies it.

Sub-recommendation: pick **ElevenLabs** as the TTS provider for generation.
Per-character pricing is reasonable for the 24-line catalog × 12 langs × 6
techniques (~7K characters total = under $5 one-time generation cost).
Quality is consistent and editable. No runtime API dependency.

Voice character: a single calm, gender-neutral-leaning voice per language.
"Calmify Voice" — own it as a brand asset.

Engineering work for Option A:
1. Pick a voice per language (collaborate with you / a designer on tone)
2. Write a generation script that takes a CSV of `(key, text, lang)` and
   produces `audio/{lang}/{key}.ogg` files
3. Generate the audio bundle (one-time)
4. Add `MeditationVoicePlayer` (`features/meditation/src/androidMain/.../MeditationVoicePlayer.kt`):
   - `play(audioRes: String)` — async fire-and-forget
   - `stop()` — for pause / sub-phase change
   - Audio focus + ducking against the chime
5. New ViewModel effects: `SpeakCue(BreathSegmentKind)`, `SpeakCoach(SubPhase, idx)`
6. Wire emissions in the VM: cue at segment boundary (only when audio == VOICE),
   coach at sub-phase + every 12s rotation tick
7. Listen in entry point, resolve audio resource path from the localized key,
   call `voicePlayer.play(...)` (gated on audio == VOICE)
8. Dispose on session end

Estimated effort: **2-3 days** of focused work once audio assets are ready.

## What I need from you

1. **Approve Option A** (or pick a different one — if so, justify so I understand
   the constraint I'm missing)
2. **Voice character preference**: gender-neutral / female / male / other per
   language? Default tempo / pitch?
3. **TTS provider preference**: ElevenLabs (recommended) / Google Cloud TTS /
   Azure Neural / hire a voice actor for each language?
4. **Launch lang scope**: 6 Latin only at v1 (smaller APK, ship sooner) or all
   12 at v1?
5. **Coach line policy**: speak every 12s rotation tick, or only on first display
   of each line? (Speaking every 12s = repeating the same line if rotation lands
   the user on the same line twice in a row; intent here is "remind without
   nagging".)

Until these are settled, **VOICE mode in the Configure screen does nothing
audio-wise** (it just plays the chime same as CHIMES). Phase 3.B closes the
SILENT bug; voice itself stays unwired.
