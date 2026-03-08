# KMP Conversion — Piano Settimana 3-7 Marzo 2026

> Prerequisito completato: 15 fasi di cleanup (branch `migration/firebase-2025`)
> Obiettivo settimana: Setup KMP effettivo + primo modulo condiviso funzionante

---

## Lunedi 3 Marzo — Setup KMP Build System

### Mattina: Configurazione Gradle KMP
- [ ] Decommentare plugin KMP in `libs.versions.toml` (kotlin-multiplatform, compose-multiplatform)
- [ ] Decommentare versioni e librerie KMP (koin, sqldelight, voyager, kotlinx-datetime, kotlinx-serialization)
- [ ] Aggiungere `settings.gradle` KMP-aware (enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS"))
- [ ] Configurare `local.properties` con percorso Xcode SDK (se su macOS)

### Pomeriggio: Convertire core/util a KMP
- [ ] Cambiare `build.gradle` di core/util: `com.android.library` -> `kotlin-multiplatform` + `com.android.library`
- [ ] Creare struttura source sets: `commonMain/kotlin/`, `androidMain/kotlin/`
- [ ] Spostare tutti i file in `commonMain` (sono gia' pure Kotlin!)
- [ ] Sostituire eventuali `java.time` residui con `kotlinx-datetime`
- [ ] Verificare: `./gradlew :core:util:build` compila per Android
- [ ] **MILESTONE**: Primo modulo KMP funzionante

---

## Martedi 4 Marzo — core/ui + Compose Multiplatform

### Mattina: Setup Compose Multiplatform
- [ ] Convertire `build.gradle` di core/ui a KMP con Compose MP
- [ ] Creare `commonMain` per componenti Compose condivisi
- [ ] Spostare `Theme.kt`, componenti UI, providers in `commonMain`
- [ ] `androidMain` per codice Android-specific (se presente)

### Pomeriggio: Verifiche e fix
- [ ] Fix import: `androidx.compose.*` -> `org.jetbrains.compose.*` dove necessario
- [ ] Verificare che Material3 funzioni con Compose MP
- [ ] Testare: `./gradlew :core:ui:build`
- [ ] **MILESTONE**: UI condivisibile tra piattaforme

---

## Mercoledi 5 Marzo — DI (Hilt -> Koin) + Navigation (Voyager)

### Mattina: Migrazione Dependency Injection
- [ ] Aggiungere Koin dependencies a tutti i moduli
- [ ] Creare `commonMain` Koin modules per ogni feature
- [ ] Per ogni `@HiltViewModel`: rimuovere annotation, creare Koin viewModel { }
- [ ] Per ogni `@Inject constructor`: rimuovere annotation, registrare in Koin module
- [ ] Per ogni `hiltViewModel()`: sostituire con `koinViewModel()`
- [ ] Rimuovere Hilt plugin e dependencies
- [ ] Testare: `./gradlew assembleDebug`

### Pomeriggio: Migrazione Navigation
- [ ] Sostituire Navigation Compose con Voyager
- [ ] Convertire `Screen` routes in Voyager `Screen` classes
- [ ] Convertire `NavGraph.kt` in Voyager `Navigator`
- [ ] Convertire ogni `*Navigation.kt` in Voyager tab/screen
- [ ] Testare navigazione completa

> NOTA: Questa e' la giornata piu' impegnativa. Hilt->Koin tocca TUTTI i moduli.

---

## Giovedi 6 Marzo — data/mongo (Room -> SQLDelight + Firebase KMP)

### Mattina: Database migration
- [ ] Aggiungere SQLDelight plugin e dependencies
- [ ] Scrivere schema `.sq` per tutte le entita' Room (ChatSession, ChatMessage, Diary, etc.)
- [ ] Generare codice SQLDelight
- [ ] Implementare repository con SQLDelight queries
- [ ] Creare driver expect/actual (AndroidSqliteDriver / NativeSqliteDriver)

### Pomeriggio: Firebase KMP
- [ ] Aggiungere `dev.gitlive:firebase-kotlin-sdk` dependencies
- [ ] Convertire Firestore repositories da Firebase Android SDK a Firebase Kotlin SDK
- [ ] Convertire Firebase Auth da Android SDK a Kotlin SDK
- [ ] Spostare repositories in `commonMain`
- [ ] Testare: `./gradlew :data:mongo:build`

---

## Venerdi 7 Marzo — Feature Modules + Build Completo

### Mattina: Convertire feature modules a KMP
- [ ] Convertire features semplici: auth, insight, history, settings, onboarding, profile
- [ ] Per ogni feature: `build.gradle` KMP, `commonMain` per UI + ViewModel, `androidMain` se necessario
- [ ] Convertire features complesse: home, write
- [ ] Testare ogni modulo singolarmente

### Pomeriggio: Integration + Test
- [ ] Convertire app module (entry point resta Android-only)
- [ ] Full build: `./gradlew assembleDebug`
- [ ] Test manuale su device Android: verificare TUTTE le funzionalita'
- [ ] Fix eventuali regressioni
- [ ] **MILESTONE FINALE**: App Android funzionante al 100% con architettura KMP

---

## NON fare questa settimana (settimana successiva)

- [ ] features/chat (audio pipeline — richiede expect/actual per AudioRecord)
- [ ] features/humanoid (Filament — richiede binding iOS Metal)
- [ ] Target iOS effettivo (serve macOS con Xcode)
- [ ] Target Desktop/Web

---

## Rischi e Mitigazioni

| Rischio | Probabilita' | Mitigazione |
|---------|-------------|-------------|
| Hilt->Koin rompe tutto | ALTA | Migrare UN modulo alla volta, build dopo ogni modulo |
| Room->SQLDelight data loss | MEDIA | Scrivere migration script, backup database prima |
| Compose MP incompatibilita' | MEDIA | Verificare che Material3 Expressive sia supportato |
| Firebase Kotlin SDK API differences | BASSA | API molto simile, quasi drop-in replacement |
| Navigation Compose->Voyager | MEDIA | Voyager ha API simile, ma pattern diverso |

---

## Prerequisiti

- [ ] macOS con Xcode installato (per target iOS, non urgente per questa settimana)
- [ ] Android Studio Ladybug+ (supporto KMP integrato)
- [ ] JDK 17 (gia' configurato)
- [ ] Commit tutto il lavoro corrente su `migration/firebase-2025`
- [ ] Creare nuovo branch: `migration/kmp-conversion`
