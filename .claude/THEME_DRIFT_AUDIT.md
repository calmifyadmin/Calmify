# Theme Drift Audit — Kotlin source-of-truth ↔ Claude Design CSS port

> **Phase R0 deliverable** of `design-system-refactor` workstream.
> **Run date**: 2026-05-11
> **Branch**: `design-system-refactor` (off `backend-architecture-refactor` @ `7a0b7ca`)
> **CSS port**: `design/biosignal/calmify.css` (versioned from `C:\Design\Calmify\Calmify\export\`)

---

## Verdict per token category

| Category | Kotlin file | Severity | Decision |
|---|---|---|---|
| Color | `core/ui/.../theme/Color.kt` | ✅ None | **No action** — sincronizzato 1:1, palette completa (light + dark M3 + seed + accent ramp + mood legacy). 98 literal `Color()` calls dentro questo file sono legittimi (è la palette stessa). |
| Spacing | `core/ui/.../theme/Dimensions.kt:5-12` | ⚠️ Minor | **Add `3xl = 48.dp`** — CSS ha 7 values, Kotlin 6. CSS `--space-3xl: 48px` non ha controparte. |
| Radius | `core/ui/.../theme/Dimensions.kt:14-20` | ⚠️ Minor | **Add `pill = 999.dp` o `CircleShape` shorthand** — CSS ha `--radius-pill: 999px` per buttons/chips/tabs. |
| Typography | `core/ui/.../theme/Type.kt` | 🔴 **MAJOR** | **Rebuild full M3 scale** — Type.kt attuale è scaffold template Compose (solo `bodyLarge`, resto commentato). M3 defaults coprono i missing styles a runtime ma il design system Kotlin ufficiale è vuoto. |
| Font family | `Type.kt` usa `FontFamily.Default` | 🔴 **MAJOR** | **Bundle Roboto Flex Variable** — CSS importa Roboto Flex via Google Fonts CDN. Compose MP richiede asset bundlato (`composeResources/font/`) per parità iOS+Web+Desktop. |
| Elevation | `core/ui/.../theme/Elevation.kt` | 🟡 Conceptual | **Documenta + no value change** — Kotlin ha `Level0..5: Dp`. CSS ha shadow recipes (`box-shadow rgba`). M3 dark theme Compose usa **surface-tint**, non shadows — i `Dp` Kotlin sono il primitive corretto. Action: aggiungere KDoc che chiarisce il pattern + light theme variant pubblica se serve. |
| Motion (ease + dur) | **Non esiste** | 🔴 **MAJOR** | **Create `Motion.kt`** — CSS definisce 5 easings (`--ease-emphasized`, `-decel`, `-accel`, `-standard`, `-spring-bouncy`) + 5 durations (`--dur-short-1..long-1`). Kotlin non ha nessun token equivalente. Servono `CalmifyEasing` object + `CalmifyDuration` object. |
| Semantic local tokens (semantic-good / -attention) | **Non esiste** | 🟡 Component-scope | **Decision deferred** — CSS dichiara `--semantic-good`/`--semantic-attention` (ambers, no reds) come token a livello componente per BioMetricCard. In Kotlin questi sono component-internal, non globali. Si introducono insieme al componente bio-signal, non in Theme. |

---

## Quantificazione dell'hardcoded drift

Grep audit globale (feature modules + core/ui + core/social-ui, escluso `Color.kt`):

| Pattern | Occurrences | Files | Status |
|---|---:|---:|---|
| `Color(0xFF...)` literal | 436 | 28 | Mostly OK (palette files, particle/emotion providers, mood definitions). ~50-80 sospetti in feature screens. |
| `\b(20\|18\|14\|10\|36\|40\|60\|80\|100\|120\|140\|200\|240\|280)\.dp\b` non-scale | 216 | 30+ | **Refactor candidates**. 20.dp è il più frequente — andrebbe normalizzato a `CalmifySpacing.xl` (24dp) o `lg` (16dp) in base al contesto. |
| `\d+\.sp` literal | 126 | 30+ | **Refactor candidates** — quasi tutto dovrebbe passare per `MaterialTheme.typography.X`. |
| `FontWeight.X` o `fontSize =` override | 109 | 20+ | **Refactor candidates** — overrides intenzionali su `MaterialTheme.typography.X.copy(fontWeight = …)` sono OK; standalone `TextStyle()` ricostruzioni sono drift. |

**Note di metodo**: queste sono stime di *candidates*, non di drift confermato. Phase R3 separa il legitimate (charts, particle systems, decorative effects) dal drift refactorabile.

---

## File-by-file hot spots (top candidates per R3)

Ordinati per density × visibility utente:

| File | Drift signals | User-visibility | R3 priority |
|---|---|---|---|
| `features/home/.../HomeContent.kt` | `20.dp` padding contentPadding, `120.dp` size | Very high (home screen) | **High** |
| `features/home/.../ExpressiveHero.kt` | 5 `Color()` + 3 `dp` | **EXCLUDED** (user explicit: "tranne prima card") | **N/A — preserved** |
| `features/meditation/screens/Meditation*.kt` (5 files) | 36-45 sp/dp literals × 5 | High (recent Phase 1+2 work) | **Medium** — verifica che le Phase 1+2 abbiano già usato tokens prima di refactor |
| `features/write/.../WizardComponents.kt` + 16 wellness screens | ~100 dp/sp literals | High (wellness flow) | **High** |
| `features/profile/.../ProfileDashboard.kt` | 31 Color() + 6 dp + 6 sp | Medium | **Medium** |
| `features/avatar-creator/.../AvatarListScreen.kt` | 16 Color() + 1 dp + 1 sp | Medium | **Medium** |
| `features/auth/.../AuthenticationContent.kt` | 2 dp + 1 sp | High (first impression) | **High** |
| `features/chat/.../ChatBubble.kt` | 8 Color() + 1 sp | High (chat surface) | **Medium** — verifica intent (alcuni Color sono mood-based, legitimate) |
| `features/composer/.../ComposerScreen.kt` | 10 dp/sp | High (social composer) | **Medium** |

**Charts / decorative**: `DonutChart.kt`, `EmotionAwareColors.kt`, `IntensityShapes.kt`, `ParticleSystem.kt`, `LikeParticleBurst.kt`, `FluidAudioIndicator.kt`, `WizardAnimations.kt` → **leave alone**, decorative color usage is legitimate.

---

## Action items — Phase R1 (immediate)

1. **Extend `Dimensions.kt`**: add `CalmifySpacing.xxxl = 48.dp` (Kotlin convention won't accept `3xl` as prop name — use `xxxl`) + `CalmifyRadius.pill = 999.dp`.
2. **Create `Motion.kt`**: 5 easings + 5 durations matching CSS exactly.
3. **Rebuild `Type.kt`**: full M3 13-style scale with `FontFamily.SansSerif` as placeholder (will switch to bundled Roboto Flex in R1.4).
4. **Document `Elevation.kt`**: add KDoc clarifying surface-tint vs shadow pattern.
5. **Bundle Roboto Flex VF**: download `RobotoFlex-VariableFont.ttf` from Google Fonts → `core/ui/src/commonMain/composeResources/font/`. Reference in `Type.kt` via `org.jetbrains.compose.resources.Font(Res.font.RobotoFlex_VariableFont, weight = FontWeight.X)`.

---

## Action items — Phase R2 (audit deepening)

Run targeted greps per file to separate legitimate from drift:

```
- Filter out core/ui/theme/Color.kt (palette source)
- Filter out *Particle*.kt, *Chart*.kt, *Animation*.kt, *Shape*.kt (decorative)
- Filter out MoodColors/EmotionAwareColors (intentional palette)
- Result: shortlist for R3 surgical refactor
```

---

## Action items — Phase R3 (surgical refactor, 1:1 fidelity)

**Excluded by user directive**: `ExpressiveHero.kt` (the first card on Home).

**Priority refactor list** (in order):
1. `HomeContent.kt` (excluding ExpressiveHero subtree) — paddings + spacedBy values
2. `AuthenticationContent.kt` — first-impression surface
3. `features/write/` wellness wizard screens (16 files) — high user dwell time
4. `WizardComponents.kt` (shared chrome across all wizards)
5. `features/meditation/screens/` (5 files) — verify Phase 1+2 token usage first
6. `ComposerScreen.kt` + `ChatBubble.kt` — social/chat surfaces
7. `features/profile/ProfileDashboard.kt` + `features/avatar-creator/AvatarListScreen.kt`

**Refactor rule per file**:
- Every `X.dp` literal where X ∉ {4, 8, 12, 16, 20, 24, 28, 32, 48} → replace with `CalmifySpacing.X` if in scale, document if intentional, refactor if drift
- Every `X.sp` standalone → check if `MaterialTheme.typography.X` already provides it; if yes replace, if no document why custom
- Every `Color(0xFF...)` in screen Kotlin (not palette files) → replace with `MaterialTheme.colorScheme.X` if semantic, leave if decorative

---

## Action items — Phase R4 (final)

- Expose custom tokens via `CompositionLocal`:
  - `LocalCalmifyMotion` providing `CalmifyEasing` + `CalmifyDuration` instances
  - (Component-scope semantic tokens — `LocalSemanticGood/Attention` — TBD if global or component-internal)
- Update `CalmifyAppTheme` to provide these LocalComposition values
- Document in `core/ui/.../theme/README.md` (new file)

---

## Non-goals

- **Not** refactoring `Color.kt` itself — it's already 1:1 with CSS.
- **Not** touching `ExpressiveHero.kt` (user directive).
- **Not** touching decorative files (`*Particle*`, `*Chart*`, `*Animation*`, `*Shape*`, `EmotionAware*`, `MoodColors`).
- **Not** introducing new colors. Calmify is Obsidian Edition: ONE accent (sage green).

---

## Build verification

Each R1-R4 sub-phase ends with `assembleDebug` green. Phase R3 ends with full smoke test on emulator + visual review against `design/biosignal/calmify.css` rendering.
