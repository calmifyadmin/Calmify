# Calmify i18n Guide

## Rule: no hardcoded strings in UI code

Every user-visible string shown in Compose UI **must** come from a string resource.
The `checkHardcodedStrings` Gradle task enforces this automatically before every compilation.

---

## Quick reference

### Correct usage

```kotlin
// Single string
Text(stringResource(R.string.my_key))

// With format arguments
Text(stringResource(R.string.greeting, userName))

// Named parameter
OutlinedTextField(
    label = { Text(stringResource(R.string.email_label)) },
    placeholder = { Text(stringResource(R.string.email_placeholder)) }
)

// TopAppBar title
CalmifyTopBar(title = stringResource(R.string.settings_title))
```

### Adding a new string

1. Open `core/ui/src/commonMain/composeResources/values-en/strings.xml`
2. Add your key inside `<resources>` using the naming convention below
3. Reference it in Kotlin with `stringResource(R.string.your_key)`
4. Optionally add translations in `values-de/`, `values-es/`, `values-fr/`, `values-pt/`, `values/` (Italian default)

---

## String key naming convention

| Prefix | Where used | Example |
|--------|-----------|---------|
| `home_` | Home feature | `home_talk_to_eve` |
| `chat_` | Chat / Live screen | `chat_daily_limit_title` |
| `habits_` | Habits feature | `habits_empty_title` |
| `history_` | History screen | `history_search_placeholder` |
| `insight_` | Insight screen | `insight_empty_title` |
| `profile_` | Profile screen | `profile_loading` |
| `settings_` | Settings screens | `settings_logout` |
| `onboarding_` | Onboarding flow | `onboarding_skip` |
| `feed_` | Community feed | `feed_empty` |
| `nav_` | Navigation bar labels | `nav_home` |
| `error_` | Error / empty states | `error_loading` |
| `tutorial_` | Coach-mark tooltips | `tutorial_home_title` |
| *(none)* | Global actions | `save`, `cancel`, `retry` |

---

## Skipping the check (use sparingly)

Append the suppression comment to a single line:

```kotlin
Text("debug_only_string")  // noinspection HardcodedStringInCompose
```

Do **not** suppress entire files or functions — fix them instead.

---

## Running the check manually

```bash
./gradlew checkHardcodedStrings
```

The check runs automatically before any `compileKotlin` task.
To run a full build without the check (CI escape hatch only):

```bash
./gradlew assembleDebug -x checkHardcodedStrings
```

---

## Language files

| File | Locale |
|------|--------|
| `values-en/strings.xml` | English (primary source of truth) |
| `values/strings.xml` | Italian (app default) |
| `values-de/strings.xml` | German |
| `values-es/strings.xml` | Spanish |
| `values-fr/strings.xml` | French |
| `values-pt/strings.xml` | Portuguese |

Translation stubs for DE/ES/FR/PT are pre-populated — translate missing strings before shipping to those locales.

---

## Dynamic locale switching

The app supports runtime locale switching via **Settings → Language**.
Implementation lives in:
- `features/settings/src/androidMain/.../navigation/SettingsEntryPoint.kt` — `applyLocaleToContext()` + `applyAndSaveLanguage()`
- `app/src/main/java/com/lifo/calmifyapp/MainActivity.kt` — `attachBaseContext()` override
- Locale preference stored in `SharedPreferences` key `language_code`
