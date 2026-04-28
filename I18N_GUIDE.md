# I18N_GUIDE.md — Internationalisation in Calmify

> Updated 2026-04-29 — Sprint i18n COMPLETE (Fases A→E, 2026-04-19) + Phase J Tier 1+2
> screenshot-driven closure (2026-04-28). Default EN, 12 languages, typed `Strings` facade with 17+ semantic groups.
> Sprint A→E: 140 keys / 31 files. Phase J Tier 1+2: +189 keys / +28 files.
> **Cumulative: ~329 keys × 6 Latin langs ≈ 1974 translations + 59 Kotlin files refactored.**
> Tier 3 (DateFormatters / JournalHomeScreen prompts / wellness wizards) deferred — see `memory/project_i18n_phase_j.md`.

## Overview

Calmify is a KMP project targeting Android (iOS + Web coming next as Level 3).
All user-visible strings must live in `core/ui/src/commonMain/composeResources/values*/strings.xml` so that:

- the app is localised without touching Kotlin code
- the `lintHardcodedStrings` Gradle task detects regressions automatically
- Detekt (configured 2026-04-19) enforces code-quality norms across i18n-touched modules
- iOS + Web share the same translations transparently via Compose Multiplatform Resources

## Supported Languages (2026-04-19 → 12 total)

| Tag | Language | Script | RTL | Status |
|-----|----------|--------|-----|--------|
| `values/` (default) | **English** | Latin | — | Active (since 2026-04-19) |
| `values-it/` | Italian | Latin | — | Active |
| `values-es/` | Spanish | Latin | — | Active |
| `values-fr/` | French | Latin | — | Active |
| `values-de/` | German | Latin | — | Active |
| `values-pt/` | Portuguese | Latin | — | Active |
| `values-ar/` | Arabic | Arabic | ✅ yes | Baseline scaffold (2026-04-19) — rest falls back to EN |
| `values-zh/` | Chinese (Simplified) | Han | — | Baseline scaffold (2026-04-19) — rest falls back to EN |
| `values-ja/` | Japanese | Kanji + Kana | — | Baseline scaffold (2026-04-19) — rest falls back to EN |
| `values-ko/` | Korean | Hangul | — | Baseline scaffold (2026-04-19) — rest falls back to EN |
| `values-hi/` | Hindi | Devanagari | — | Baseline scaffold (2026-04-19) — rest falls back to EN |
| `values-th/` | Thai | Thai | — | Baseline scaffold (2026-04-19) — rest falls back to EN |

Font bundling (Noto Sans CJK / Arabic / Devanagari / Thai) is documented at [core/ui/src/commonMain/composeResources/font/README.md](core/ui/src/commonMain/composeResources/font/README.md) — deferred post-sprint (non-blocking: system fonts render acceptably on Android for MVP). Bundle before Level 3 (iOS + Web).

Supported locales are declared in [app/src/main/res/xml/locales_config.xml](app/src/main/res/xml/locales_config.xml) (the file Android's per-app-language API reads) and are switched at runtime via `AppCompatDelegate.setApplicationLocales(...)` from [SettingsEntryPoint.kt](features/settings/src/androidMain/kotlin/com/lifo/settings/navigation/SettingsEntryPoint.kt).

## Preferred Call Sites (since 2026-04-19)

Prefer the **typed `Strings` facade** over raw `Res.string.*`:

```kotlin
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource

// Instead of this:
Text(stringResource(Res.string.action_save))

// Write this:
Text(stringResource(Strings.Action.save))

// Parametric:
Text(stringResource(Strings.Screen.Home.greeting, username))

// IconButton with required a11y description:
IconButton(onClick = ::open) {
    Icon(Icons.Default.Menu, contentDescription = stringResource(Strings.A11y.menu))
}
```

See [Strings.kt](core/ui/src/commonMain/kotlin/com/lifo/ui/i18n/Strings.kt) for the facade.

> Note: an earlier scaffold (`LocaleController.kt`, `Helpers.kt` with `AppText`/`LocalizedIconButton`/`LocalizedTextButton`) was removed in 2026-04-20 because runtime locale switching is handled directly by Android's per-app-language API + `AppCompatDelegate`, and the helpers had zero call sites across the 37 migrated files — callers use `stringResource(Strings.X.y)` directly. If you want thin composable wrappers, reintroduce them per-module on demand.

---

## How to Add a String

### 1. Define it in the default locale (EN)

```xml
<!-- core/ui/src/commonMain/composeResources/values/strings.xml (EN default since 2026-04-19) -->
<string name="screen_home_greeting_title">Hello, %1$s!</string>
```

Group strings by screen/feature using comment banners and the semantic naming
convention from `memory/i18n_strategy.md` (scope-prefixed):

```xml
<!-- ============================================================ -->
<!-- Home Screen -->
<!-- ============================================================ -->
<string name="screen_home_greeting_title">Hello, %1$s!</string>
<string name="screen_home_no_entries">No entries yet. Start writing!</string>
```

### 2. Add the key to the typed `Strings` facade

Keep the facade in `core/ui/src/commonMain/kotlin/com/lifo/ui/i18n/Strings.kt`
in sync so callers get compile-time safety:

```kotlin
object Strings {
    object Screen {
        object Home {
            val greetingTitle get() = Res.string.screen_home_greeting_title
            val noEntries get() = Res.string.screen_home_no_entries
        }
    }
}
```

### 3. Access it in Compose via the typed facade

```kotlin
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource

// Simple
Text(stringResource(Strings.Screen.Home.noEntries))

// Parametrised
Text(stringResource(Strings.Screen.Home.greetingTitle, userName))
```

The typed facade gives you compile-safe access (typo on `Strings.X.y` is a compile
error in all 12 locales), IDE autocomplete, and a single rename point. Raw
`stringResource(Res.string.xxx)` still works for one-offs, but the facade is the
canonical call site for any reused key.

### 4. Add translations for the other 11 locales

Add the same key to each of the 11 non-default locales with the translated value:

```xml
<!-- values-it/strings.xml -->
<string name="screen_home_greeting_title">Ciao, %1$s!</string>

<!-- values-es/strings.xml -->
<string name="screen_home_greeting_title">¡Hola, %1$s!</string>

<!-- ...and so on for fr/de/pt/ar/zh/ja/ko/hi/th -->
```

Missing keys fall back to the default `values/` (EN) automatically via Compose
Resources. Prefer translating all 12 at once to avoid "silent English" in 11 locales.

---

## Naming Conventions

```
<screen>_<element>_<variant>
```

| Pattern | Example |
|---------|---------|
| `<screen>_<noun>` | `home_greeting_title`, `chat_input_hint` |
| `<action>_<noun>` | `delete_confirm_message`, `save_success_toast` |
| Common verbs | `back`, `next`, `save`, `cancel`, `close`, `ok`, `confirm`, `delete` |
| Feature-prefixed | `habit_streak_label`, `meditation_duration_minutes` |

Avoid:
- Generic names like `text1`, `string_1`
- Screen-unscoped names that collide across features

---

## Plurals

Use `<plurals>` for count-dependent strings:

```xml
<plurals name="habit_streak_days">
    <item quantity="one">%1$d giorno</item>
    <item quantity="other">%1$d giorni</item>
</plurals>
```

```kotlin
import org.jetbrains.compose.resources.pluralStringResource

Text(pluralStringResource(Res.plurals.habit_streak_days, count, count))
```

---

## Non-Translatable Strings

Do NOT put these in strings.xml:

- Log / debug messages → use string literals directly in `Log.d()`
- Firestore collection paths, map keys, API field names
- Test tags (`Modifier.testTag("...")`)
- Assertion messages (`require(...)`, `check(...)`, `error(...)`)
- Format tokens (`"%d"`, `"yyyy-MM-dd"`)
- Empty strings `""`

---

## Running the Lint Check

```bash
# From project root
./gradlew lintHardcodedStrings

# Report location
build/reports/hardcoded-strings.txt
```

The task scans all `*.kt` files under `features/`, `app/`, and `core/` for
patterns like `Text("literal")`, `title = "literal"`, `placeholder = "literal"`, etc.

It does **not** fail the build by default — it warns and writes a report.
To enforce a clean state in CI, add this to your workflow:

```bash
./gradlew lintHardcodedStrings
count=$(grep -c "^  features\|^  app\|^  core" build/reports/hardcoded-strings.txt || true)
if [ "$count" -gt "0" ]; then
  echo "ERROR: $count hardcoded string(s) found. See I18N_GUIDE.md."
  exit 1
fi
```

---

## Fixing a Hardcoded String (step-by-step)

Given a finding like:

```
features/home/src/androidMain/kotlin/com/lifo/home/HomeContent.kt:42
    Text("Benvenuto!")
```

1. Open `core/ui/src/commonMain/composeResources/values/strings.xml`
2. Add `<string name="home_welcome">Benvenuto!</string>`
3. Add the English counterpart to `values-en/strings.xml`
4. Replace the hardcoded call:
   ```kotlin
   // Before
   Text("Benvenuto!")
   // After
   Text(stringResource(Res.string.home_welcome))
   ```
5. Run `./gradlew lintHardcodedStrings` to confirm no remaining findings

---

## KMP Source Set Rules

| Source set | Import allowed |
|------------|---------------|
| `commonMain` | `org.jetbrains.compose.resources.stringResource` — YES |
| `commonMain` | `android.content.res.Resources` — NO |
| `androidMain` | Both allowed, but prefer compose-resources for UI strings |

`stringResource()` from Compose Resources resolves at runtime based on the
device locale, exactly like Android's `getString(R.string.xxx)`.

---

## Adding a New Locale

1. Create `core/ui/src/commonMain/composeResources/values-XX/strings.xml`
   (e.g., `values-fr` for French, `values-es` for Spanish)
2. Copy all `<string>` keys from `values/strings.xml`
3. Translate the values — do not translate the `name` attributes
4. Rebuild — the Compose Resources generator picks up the new file automatically

---

## Checklist Before Merging a PR

- [ ] No new `Text("literal")` calls — use `stringResource(Strings.X.y)` via the typed facade
- [ ] New string keys added to `values/strings.xml` (EN default) AND all 11 other locale files
- [ ] Typed `Strings` facade updated with the new key (compile-safe accessor)
- [ ] String names follow the semantic `<scope>_<element>` convention (`screen_*`, `action_*`, `a11y_*`, `error_*`, `state_*`, `nav_*`)
- [ ] `./gradlew lintHardcodedStrings` shows no new findings
- [ ] Detekt passes (wired on `core/ui` since 2026-04-19)
