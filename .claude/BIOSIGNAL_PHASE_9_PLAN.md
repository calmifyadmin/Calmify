# Bio-Signal Phase 9 — Visible Substance Plan

> **Started**: 2026-05-17
> **Trigger**: user opened the new APK and saw "merda totale" — Phase 5-8 architecture invisible, container surfaces generic, no re-engineering visible.
> **Plan**: `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` (compass) + this file (Phase 9 execution).
> **Status tracker**: `.claude/BIOSIGNAL_INTEGRATION_STATUS.md` — add Phase 9 section when work lands.

---

## 0. Honest assessment — what was wrong with Phases 5-8

Phases 5-8 shipped solid architectural work (atoms, use cases, KMP, multimodularity, baselines, AI narrative, telemetry, 15 commits). But the user's first impression after install was **"nothing changed"**. Three root causes:

1. **Silence-by-default** = invisible work. Cards only render with bio data in the last 24h. A fresh-install user sees nothing on Home/Journal/Meditation until they wear the wearable for a week.

2. **Container screens never got the 1:1 mockup treatment**:
   - `BioContextScreen` (556 LOC) ≠ `Calmify Bio Context.html` (1267 LOC of design)
   - `BioOnboardingScreen` — only StepIntro is 1:1; 4 of 5 steps still generic Compose
   - `BioSettings` screen doesn't exist; `Calmify Bio Settings.html` (1488 LOC) never ported

3. **No re-engineering** — I followed mockups + the plan as a stenographer, never as a director. I never said "this point doesn't make sense, let's redo it" or "we have X data, let's surface it in a way the mockup didn't think of".

**Phase 9 fixes all three.**

---

## 1. Three waves, sequenced by visible impact

### Wave 1 — Mockup parity (the 3 missing 1:1 ports)

The screens the user reaches with one tap from the drawer. These MUST match Claude Design's HTML 1:1 in visual grammar — same typography hierarchy, same spacing rhythm, same accent usage, same component vocabulary.

| # | Surface | Source | Target effort |
|---|---------|--------|--------------:|
| 9.1.1 | `BioContextScreen` rewrite | `Calmify Bio Context.html` | ~1 commit, 600-800 LOC |
| 9.1.2 | `BioOnboardingScreen` 4 missing steps | `Calmify Bio Onboarding.html` | ~1 commit, 400-600 LOC |
| 9.1.3 | `BioSettings` new screen | `Calmify Bio Settings.html` | ~1 commit, 500-700 LOC |

### Wave 2 — Re-engineered surfaces (beyond mockup)

The surfaces Claude Design didn't think of, that our actual data architecture makes possible. These are the proof that I can be a director, not a stenographer.

| # | Surface | What's new beyond mockup | Why it's possible |
|---|---------|--------------------------|-------------------|
| 9.2.1 | **Bio Timeline drill-down** | Full-screen per-signal timeline (7d/30d/90d) with overlay markers for journal entries + meditation sessions | We have 30d raw locale + journal + meditation history all locally |
| 9.2.2 | **Pattern Feed** | Dedicated surface showing ALL cross-signal correlations (sleep×mood, HRV×meditation, steps×evenings, etc.) — not just one card in Home | We have BioBaseline + JournalInsights + MeditationRepository |
| 9.2.3 | **Journal × Bio overlay** | DiaryDetailScreen surfaces bio data of that day inline ("when you wrote this, resting HR was 71 (+9 vs your usual)") | Diary has `dateMillis`, bio has `getRawSamples(type, from, until)` |
| 9.2.4 | **Empty states intelligenti + debug preview** | Instead of silence-by-default everywhere, cards show "X/7 days tracked" progress + a debug toggle in DEV builds forces all cards to render with mock data | Atom-level addition + Compose `@Preview` mock state |
| 9.2.5 | **Predictive baseline visualization** | Mini-card "your HRV median has drifted +X% over the last 90 days" — surfaces baseline evolution | `BioBaseline.computedAtMillis` history (need new SQLDelight schema for baseline history) |

### Wave 3 — Polish + connective tissue

| # | What | Why |
|---|------|-----|
| 9.3.1 | Granular Settings (retention slider, per-source toggles, paranoid mode) | BioSettings stops being a list of toggles, becomes a consent architecture |
| 9.3.2 | Uniform grammar audit across all bio surfaces | Atom + surface visual consistency — same chip rhythm, same divider density, same icon size scale |
| 9.3.3 | Empty state semantic uniformity | Every "no data" path uses the same atom + same copy pattern, never a blank screen |

---

## 2. Sequencing — what ships in what order

**Cardinal rule**: every commit ships value, builds green, gets pushed. No "atom commits" without a user-visible surface improvement landing in the same PR.

```
9.1.1  BioContextScreen 1:1 rewrite         [LAND FIRST — most-tapped surface]
9.2.4  Empty states + debug preview         [LAND SECOND — makes Phase 5-8 visible NOW]
9.1.2  BioOnboarding 4 missing steps        [LAND THIRD — second-most-tapped surface]
9.1.3  BioSettings new screen               [LAND FOURTH — new surface, slower to discover]
9.2.1  Bio Timeline drill-down              [WAVE 2 starts — re-engineering proof]
9.2.3  Journal × Bio overlay                [WAVE 2 — cross-feature integration]
9.2.2  Pattern Feed                         [WAVE 2 — third re-engineered surface]
9.2.5  Predictive baseline visualization    [WAVE 2 — fourth re-engineered surface]
9.3.x  Polish wave                          [WAVE 3]
```

Wave 1 is the user-frustration fix. Wave 2 is the re-engineering proof. Wave 3 is the directorial polish.

---

## 3. Architectural rules (non-negotiable, inherited from CLAUDE.md + previous phases)

1. **KMP-first**: every new file in `commonMain` unless it genuinely needs `androidMain`
2. **Atoms before surfaces**: anything that could be reused goes in `:core:ui/components/biosignal/` first
3. **Use case per surface**: domain logic stays in `features/<host>/domain/usecase/`; surfaces are thin Compose
4. **Silence-by-default stays the dogma** but is now paired with **honest progress disclosure** ("3/7 nights tracked") so the user knows the silence is a "not yet" not a "broken"
5. **i18n EN+IT at commit time** for every new key
6. **Build verify per commit, push after every commit** (memoria `feedback_commit_before_deploy.md` regola rafforzata)
7. **Tracker update concurrently** (`BIOSIGNAL_INTEGRATION_STATUS.md` Phase 9 section)

---

## 4. Definition of "wow" for this Phase

The user's litmus test:
- Open the app → Home shows bio cards (real OR mock in debug)
- Tap Settings → "I tuoi dati biologici" → see a screen that matches `Calmify Bio Context.html` visual quality
- Tap "Connetti un wearable" → all 5 onboarding steps look like the mockup
- Tap a bio card → drill down to timeline (no dead-end taps)
- Bio surfaces have **uniformity**: same chip pattern, same confidence footer rhythm, same empty-state grammar everywhere
- Bio cards have **substance**: they don't just say "65 bpm" — they say "65 bpm, in your typical range, here's what was going on that day"

If after Phase 9 the user opens the app and still says "merda totale", Phase 9 has failed and we go back to the drawing board.