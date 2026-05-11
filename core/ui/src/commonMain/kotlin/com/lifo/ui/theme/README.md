# Calmify Theme — Design System Source of Truth

> This package is the **single source of truth** for all visual tokens in Calmify (colors, spacing, radius, typography, elevation, motion). The CSS port at [`design/biosignal/calmify.css`](../../../../../../../design/biosignal/calmify.css) maps 1:1 onto these Kotlin tokens for web mockups and visual review.
>
> If a value used in a Composable does **not** come from this package, it's either intentional (icon size, content-driven dimension, decorative animation) and **must** carry a `// custom — <reason>` comment, OR it's drift and must be refactored.

---

## The 5 token files

### [Color.kt](Color.kt) — palette
**Calmify is "Obsidian Edition"**: pure neutral canvas + ONE accent (sage green `#4CAF7D`).
- Accent ramp: `SeedSage`, `AccentBright`, `AccentDefault`, `AccentDim`, `AccentSubtle`
- M3 light + dark theme schemes (`md_theme_light_*` / `md_theme_dark_*`) — full palette
- Mood legacy colors — used only by chips/pills in feed/write features, frozen palette

**Rule**: never add new colors. New emotional categories map to existing mood values.

### [Dimensions.kt](Dimensions.kt) — spacing + radius
```kotlin
CalmifySpacing.xs   =  4.dp
CalmifySpacing.sm   =  8.dp
CalmifySpacing.md   = 12.dp
CalmifySpacing.lg   = 16.dp
CalmifySpacing.xl   = 24.dp
CalmifySpacing.xxl  = 32.dp
CalmifySpacing.xxxl = 48.dp

CalmifyRadius.sm   =   8.dp
CalmifyRadius.md   =  12.dp
CalmifyRadius.lg   =  16.dp
CalmifyRadius.xl   =  20.dp
CalmifyRadius.xxl  =  28.dp   // M3 Expressive card default
CalmifyRadius.pill = 999.dp
```

### [Type.kt](Type.kt) — Material 3 13-style scale
Full M3 typography (Display/Headline/Title/Body/Label × Large/Medium/Small) wired into `MaterialTheme.typography` via `CalmifyTypography`.

**Calmify customization**: `headline*` uses `FontWeight.SemiBold` (600) instead of M3 default Normal (400) — more decisive section titles.

**Font family**: `CalmifyFontFamily` is currently `FontFamily.SansSerif` (renders Roboto on Android, San Francisco on iOS). To activate the full Roboto Flex Variable Font, see [composeResources/font/README.md](../../../composeResources/font/README.md).

### [Motion.kt](Motion.kt) — easings + durations
```kotlin
CalmifyEasing.Emphasized        // primary transitions (screen changes)
CalmifyEasing.EmphasizedDecel   // incoming elements
CalmifyEasing.EmphasizedAccel   // outgoing elements
CalmifyEasing.Standard          // simple property animations
CalmifyEasing.SpringBouncy      // playful confirmations

CalmifyDuration.Short1  = 100   // micro-interactions
CalmifyDuration.Short2  = 200   // hover/press transitions
CalmifyDuration.Medium1 = 300   // default tweens
CalmifyDuration.Medium2 = 400   // content transitions
CalmifyDuration.Long1   = 500   // screen-level enter/exit
```

### [Elevation.kt](Elevation.kt) — surface-tint levels
```kotlin
Elevation.Level0..Level5 // 0/1/3/6/8/12 dp
```

**Important**: in Material 3 dark theme, elevation renders as **surface tint** (via `surfaceContainer*` color ladder), not drop shadows. Use `Surface(tonalElevation = Elevation.Level2)` rather than `Modifier.shadow(...)`.

---

## How to use

### Composable example
```kotlin
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing

@Composable
fun MyCard() {
    Card(
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        modifier = Modifier.padding(CalmifySpacing.lg),
    ) {
        Column(
            modifier = Modifier.padding(CalmifySpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            Text(
                text = stringResource(Strings.X.title),
                style = MaterialTheme.typography.headlineSmall,  // Calmify SemiBold
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Strings.X.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

### Animation example
```kotlin
import com.lifo.ui.theme.CalmifyEasing
import com.lifo.ui.theme.CalmifyDuration

val scale by animateFloatAsState(
    targetValue = if (selected) 1.05f else 1f,
    animationSpec = tween(
        durationMillis = CalmifyDuration.Medium1,
        easing = CalmifyEasing.EmphasizedDecel,
    ),
    label = "selection-scale",
)
```

---

## Refactor rules (when refactoring an existing file)

1. **Every `X.dp` literal** where `X ∈ {4, 8, 12, 16, 24, 32, 48}` → `CalmifySpacing.{xs, sm, md, lg, xl, xxl, xxxl}`
2. **Every `RoundedCornerShape(X.dp)`** where `X ∈ {8, 12, 16, 20, 28, 999}` → `CalmifyRadius.{sm, md, lg, xl, xxl, pill}`
3. **Every `X.sp` standalone** → check if `MaterialTheme.typography.X` already provides it; if yes, use it
4. **Every `Color(0xFF...)`** in screen Kotlin (not palette/decorative files) → `MaterialTheme.colorScheme.X` if semantic, leave if decorative (charts, particles, mood palette)
5. **Custom values** (icon sizes 18/24/48, hero card heights, button vertical 14, micro spacings 1/2/6) → keep + add `// custom — <reason>` comment

**Off-scale values that should snap to scale**:
- `20.dp` → `xl` (24)
- `14.dp` → `lg` (16) snap
- `10.dp` → `md` (12) or `sm` (8) depending on context
- `24.dp` for radius → `xxl` (28)
- `40.dp` → `xxxl` (48) snap, or keep with comment

---

## Hard exclusions

The following are **never** refactored to use these tokens:

- **`features/home/.../ExpressiveHero.kt`** — user explicit directive 2026-05-11 ("tranne prima card nella home")
- **`Color.kt` palette itself** — sincronizzato 1:1 con CSS port
- **Decorative files**: `*Particle*`, `*Chart*`, `*Animation*`, `*Shape*`, `EmotionAwareColors`, `MoodColors`, `IntensityShapes`, `ParticleSystem`, `LikeParticleBurst`, `FluidAudioIndicator`, `WizardAnimations`, `DonutChart` — their literal values are intentional decorative choices, not spacing/sizing drift
- **Canvas drawing values** (`X.dp.toPx()` inside `drawScope`) — geometry math, not tokens

---

## Bridge to web design

The CSS port at [`design/biosignal/calmify.css`](../../../../../../../design/biosignal/calmify.css) declares every Kotlin token here as a CSS variable for parity with HTML mockups (e.g. those in `design/biosignal/` for the bio-signal feature). Mapping:

| Kotlin                          | CSS variable           |
|---------------------------------|------------------------|
| `CalmifySpacing.xs..xxxl`       | `--space-xs..3xl`      |
| `CalmifyRadius.sm..pill`        | `--radius-sm..pill`    |
| `MaterialTheme.typography.X`    | `.display/headline/title/body/label-{large,medium,small}` |
| `MaterialTheme.colorScheme.X`   | `--bg`, `--fg`, `--accent`, etc. |
| `Elevation.LevelN`              | `--elev-N` (CSS shadow equivalent) |
| `CalmifyEasing.X`               | `--ease-X` |
| `CalmifyDuration.X`             | `--dur-X` |

When a designer updates `calmify.css`, the matching Kotlin token here is the authoritative implementation.

---

## History

This system was built in the `design-system-refactor` workstream (2026-05-11):
- **R0**: drift audit (Kotlin vs CSS, 30+ hot-spot files identified)
- **R1**: token canonical (Dimensions extension + Motion creation + Type rebuild + Elevation KDoc + Roboto Flex VF wiring path)
- **R3**: surgical refactor of 11 high-visibility surfaces (Home, Wizards, Auth, 5 Meditation screens, Profile, Paywall, Composer, ChatBubble)
- **R4**: this README

See [`.claude/THEME_DRIFT_AUDIT.md`](../../../../../../.claude/THEME_DRIFT_AUDIT.md) for the original drift inventory, [`memory/project_design_system_refactor.md`](../../../../../../memory/) for workstream state.
