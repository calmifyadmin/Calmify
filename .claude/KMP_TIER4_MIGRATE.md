> **NOTA (2026-04-09)**: Questo file e' stato scritto PRIMA della Fase 1. Molte azioni sono gia' completate.
> Per lo stato attuale, vedi `.claude/KMP_MIGRATION_STATUS.md` (tracker aggiornato).
> Per i piani backend, vedi `.claude/BACKEND_*.md`.

# KMP TIER 4 — Da migrare (3 moduli, nessun commonMain)

> Tutti i file sono in androidMain. Servono spostamenti fisici + abstraction layer.

---

## 1. features/habits (0 commonMain / 5 androidMain) — 4 ore

### File da migrare

| File | Destinazione | Note |
|------|-------------|------|
| `HabitContract.kt` | commonMain | MVI contract puro, zero Android deps |
| `HabitListScreen.kt` | commonMain | Compose UI, verificare imports |
| `HabitViewModel.kt` | commonMain | Rimuovere SavedStateHandle se presente |
| `HabitEntryPoint.kt` | androidMain (resta) | Navigation wiring Android-specific |
| `di/HabitKoinModule.kt` | commonMain | Koin e' KMP |

### Azioni

```
STEP 1: Creare directory commonMain
  mkdir -p features/habits/src/commonMain/kotlin/com/lifo/habits/di

STEP 2: Spostare HabitContract.kt → commonMain
  - Verificare: nessun import android.*
  
STEP 3: Spostare HabitViewModel.kt → commonMain
  - Rimuovere SavedStateHandle
  - Passare parametri via costruttore

STEP 4: Spostare HabitListScreen.kt → commonMain
  - Verificare che usi Compose Multiplatform (non AndroidX Compose)
  - Se usa compose.material3 → OK (KMP)
  
STEP 5: Spostare HabitKoinModule.kt → commonMain

STEP 6: HabitEntryPoint.kt resta in androidMain
  - Serve per navigation wiring con Decompose su Android
```

### Validazione
```bash
./gradlew :features:habits:compileCommonMainKotlinMetadata
```

---

## 2. features/meditation (0 commonMain / 6 androidMain) — 4 ore + expect/actual audio

### File da migrare

| File | Destinazione | Note |
|------|-------------|------|
| `MeditationContract.kt` | commonMain | MVI contract puro |
| `MeditationScreen.kt` | commonMain | Compose UI |
| `MeditationViewModel.kt` | commonMain | Rimuovere SavedStateHandle |
| `MeditationBellPlayer.kt` | expect/actual | USA Android MediaPlayer! |
| `MeditationEntryPoint.kt` | androidMain (resta) | Navigation wiring |
| `di/MeditationKoinModule.kt` | commonMain | Koin KMP |

### Blocker: MeditationBellPlayer

Questo file usa `android.media.MediaPlayer` per riprodurre suoni di campana.

```kotlin
// commonMain — interfaccia
expect class BellPlayer {
    fun playBell()
    fun release()
}

// androidMain — implementazione
actual class BellPlayer(private val context: Context) {
    private val mediaPlayer = MediaPlayer.create(context, R.raw.bell)
    actual fun playBell() { mediaPlayer.start() }
    actual fun release() { mediaPlayer.release() }
}

// iosMain — implementazione
actual class BellPlayer {
    private val player = AVAudioPlayer(/* bundle resource */)
    actual fun playBell() { player.play() }
    actual fun release() { /* cleanup */ }
}
```

### Azioni

```
STEP 1: Creare directory commonMain + expect BellPlayer
STEP 2: Spostare Contract, Screen, ViewModel → commonMain
STEP 3: actual BellPlayer in androidMain (wrappa MediaPlayer esistente)
STEP 4: actual BellPlayer in iosMain (AVAudioPlayer)
STEP 5: Aggiornare Koin module
```

### Validazione
```bash
./gradlew :features:meditation:compileCommonMainKotlinMetadata
```

---

## 3. features/auth (0 commonMain / 7 androidMain) — 2-3 giorni

### Il piu' complesso del Tier 4

| File | Destinazione | Note |
|------|-------------|------|
| `AuthenticationContent.kt` | commonMain | Compose UI (bottoni, form) |
| `AuthenticationScreen.kt` | androidMain (resta) | Navigation + platform init |
| `AuthenticationViewModel.kt` | commonMain | Rimuovere SavedStateHandle, astrarre auth |
| `di/AuthKoinModule.kt` | commonMain | Koin KMP |
| `domain/SignInWithGoogleUseCase.kt` | expect/actual | Google Credential Manager e' Android-only |
| `domain/SignOutUseCase.kt` | commonMain | Usa AuthProvider (gia' astratto) |
| `navigation/AuthEntryPoint.kt` | androidMain (resta) | Platform entry point |

### Blocker principale: Google Sign-In

```kotlin
// commonMain — interfaccia
expect class GoogleSignInProvider {
    suspend fun signIn(): AuthResult
}

// androidMain — Google Credential Manager
actual class GoogleSignInProvider(private val context: Context) {
    actual suspend fun signIn(): AuthResult {
        val credentialManager = CredentialManager.create(context)
        // ... existing Google Credential Manager code
    }
}

// iosMain — Google Sign-In iOS SDK
actual class GoogleSignInProvider {
    actual suspend fun signIn(): AuthResult {
        // GIDSignIn.sharedInstance.signIn(...)
    }
}

// web — Firebase Auth popup
actual class GoogleSignInProvider {
    actual suspend fun signIn(): AuthResult {
        // firebase.auth().signInWithPopup(GoogleAuthProvider())
    }
}
```

### Azioni

```
STEP 1: Creare GoogleSignInProvider expect/actual
STEP 2: Spostare AuthenticationContent.kt → commonMain (puro Compose)
STEP 3: Spostare AuthenticationViewModel.kt → commonMain
  - Rimuovere SavedStateHandle
  - Usare GoogleSignInProvider interface
STEP 4: Spostare SignOutUseCase.kt → commonMain (gia' usa AuthProvider)
STEP 5: Wrappare SignInWithGoogleUseCase con expect/actual
STEP 6: Aggiornare Koin module
```

### Dipendenze build.gradle da gestire

```
// Queste restano SOLO in androidMain:
credentials (1.5.0-rc01)
credentials.play.services (1.5.0-rc01) 
googleid (1.1.1)

// Questa va in commonMain (con Option C / GitLive):
firebase.auth → dev.gitlive:firebase-auth
```

### Validazione
```bash
./gradlew :features:auth:compileCommonMainKotlinMetadata
./gradlew :features:auth:compileKotlinIosSimulatorArm64
```

---

## Ordine di esecuzione consigliato

1. **habits** (4 ore) — Piu' semplice, zero expect/actual
2. **meditation** (4 ore + expect/actual) — Un solo expect/actual (BellPlayer)
3. **auth** (2-3 giorni) — Complesso, Google Sign-In platform-specific

## Pattern comune: SavedStateHandle removal

Tutti e 3 i moduli hanno ViewModel con SavedStateHandle.
Applicare lo stesso pattern di Tier 3 (insight):
- Rimuovere `import androidx.lifecycle.SavedStateHandle`
- Passare parametri via costruttore dal Decompose component
- Aggiornare Koin module con `params.getOrNull()`
