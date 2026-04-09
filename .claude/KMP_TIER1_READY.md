> **NOTA (2026-04-09)**: Questo file e' stato scritto PRIMA della Fase 1. Molte azioni sono gia' completate.
> Per lo stato attuale, vedi `.claude/KMP_MIGRATION_STATUS.md` (tracker aggiornato).
> Per i piani backend, vedi `.claude/BACKEND_*.md`.

# KMP TIER 1 — Ready NOW (12 moduli, zero code changes)

> Questi moduli hanno commonMain pulito al 100%. Nessun import Android in commonMain.
> Tutti usano `calmify.kmp.compose`. Convention plugin gia' configura iOS targets.

## Status: VERIFIED CLEAN

| Modulo | commonMain | androidMain | expect/actual | Blockers |
|--------|-----------|-------------|---------------|----------|
| avatar-creator | 19 | 0 | nessuno | NONE |
| notifications | 5 | 0 | nessuno | NONE |
| onboarding | 3 | 0 | nessuno | NONE |
| profile | 3 | 0 | nessuno | NONE |
| search | 5 | 0 | nessuno | NONE |
| subscription | 5 | 1 | nessuno | NONE |
| thread-detail | 3 | 2 | nessuno | NONE |
| social-profile | 4 | 3 | nessuno | NONE |
| feed | 3 | 2 | nessuno | NONE |
| history | 3 | 5 | nessuno | NONE |
| composer | 1 | 4 | nessuno | NONE |
| messaging | 8 | 1 | nessuno | NONE |

**Totale: 62 file commonMain + 18 file androidMain**

## Cosa significa "ready"

- commonMain compila senza import `android.*`, `java.time.*`, `java.io.*`
- Compose Multiplatform (non AndroidX Compose) usato ovunque
- Koin DI KMP-compatible
- `kotlinx.datetime` per date, `kotlinx.coroutines` per async
- Coil 3.x (KMP) per immagini

## Cosa serve per iOS/Web

Per ognuno di questi moduli, il lavoro e':

### 1. Verificare compilazione iOS target
```bash
./gradlew :features:{modulo}:compileKotlinIosSimulatorArm64
```

### 2. Creare iosMain se servono actual (improbabile per Tier 1)
```
features/{modulo}/src/iosMain/kotlin/com/lifo/{modulo}/
```

### 3. I moduli con androidMain files
Questi file restano in androidMain (screen entry points, navigation wiring).
Per iOS servono equivalenti iosMain entry points.

| Modulo | File androidMain | Scopo |
|--------|-----------------|-------|
| subscription | 1 | Billing Play Store (iOS: StoreKit 2) |
| thread-detail | 2 | Screen + Coil image loading |
| social-profile | 3 | Screen + Coil + navigation |
| feed | 2 | Screen + navigation |
| history | 5 | Screens + charting |
| composer | 4 | Screen + media picker |
| messaging | 1 | Screen entry |

## Ordine di esecuzione consigliato

1. **Batch 1** (0 androidMain — compilano subito):
   `avatar-creator`, `notifications`, `onboarding`, `profile`, `search`

2. **Batch 2** (1 androidMain — quasi immediati):
   `subscription`, `messaging`

3. **Batch 3** (2-5 androidMain — serve entry point iOS):
   `thread-detail`, `social-profile`, `feed`, `history`, `composer`

## Comandi di validazione

```bash
# Compila TUTTI i Tier 1 per iOS
./gradlew :features:avatar-creator:compileKotlinIosSimulatorArm64
./gradlew :features:notifications:compileKotlinIosSimulatorArm64
./gradlew :features:onboarding:compileKotlinIosSimulatorArm64
./gradlew :features:profile:compileKotlinIosSimulatorArm64
./gradlew :features:search:compileKotlinIosSimulatorArm64
./gradlew :features:subscription:compileKotlinIosSimulatorArm64
./gradlew :features:thread-detail:compileKotlinIosSimulatorArm64
./gradlew :features:social-profile:compileKotlinIosSimulatorArm64
./gradlew :features:feed:compileKotlinIosSimulatorArm64
./gradlew :features:history:compileKotlinIosSimulatorArm64
./gradlew :features:composer:compileKotlinIosSimulatorArm64
./gradlew :features:messaging:compileKotlinIosSimulatorArm64
```

## Note

- Nessun modulo ha `iosMain` o `desktopMain` creati — ma il convention plugin li supporta gia'
- Il plugin `calmify.kmp.compose` configura automaticamente `iosX64`, `iosArm64`, `iosSimulatorArm64`
- Non serve modificare `build.gradle` di nessun modulo
