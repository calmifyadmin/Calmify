> **NOTA (2026-04-09)**: Questo file e' stato scritto PRIMA della Fase 1. Molte azioni sono gia' completate.
> Per lo stato attuale, vedi `.claude/KMP_MIGRATION_STATUS.md` (tracker aggiornato).
> Per i piani backend, vedi `.claude/BACKEND_*.md`.

# KMP TIER 3 — Quick Fix (1 modulo, 30 minuti)

> Un singolo import Android in commonMain blocca l'intero modulo.

---

## features/insight (4 commonMain / 0 androidMain)

### Il problema: 3 righe in 1 file

**File:** `features/insight/src/commonMain/kotlin/com/lifo/insight/InsightViewModel.kt`

```kotlin
// Linea 3 — BLOCKER
import androidx.lifecycle.SavedStateHandle

// Linea 53-54 — BLOCKER  
class InsightViewModel constructor(
    private val savedStateHandle: SavedStateHandle,

// Linea 59 — BLOCKER
    private val diaryId: String? = savedStateHandle.get<String>("diaryId")
```

### Gli altri 3 file sono PULITI:
- `GetInsightByDiaryIdUseCase.kt` — pura business logic
- `InsightScreen.kt` — puro Compose UI
- `di/InsightKoinModule.kt` — Koin DI setup

### Fix

```kotlin
// PRIMA:
import androidx.lifecycle.SavedStateHandle

class InsightViewModel constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getInsightByDiaryIdUseCase: GetInsightByDiaryIdUseCase,
) : MviViewModel<InsightIntent, InsightState, InsightEffect>(...) {
    private val diaryId: String? = savedStateHandle.get<String>("diaryId")

// DOPO:
class InsightViewModel constructor(
    private val diaryId: String?,  // passato dal Decompose component
    private val getInsightByDiaryIdUseCase: GetInsightByDiaryIdUseCase,
) : MviViewModel<InsightIntent, InsightState, InsightEffect>(...) {
```

### Aggiornare il Koin module

```kotlin
// PRIMA (in InsightKoinModule.kt):
viewModel { InsightViewModel(get(), get()) }

// DOPO:
viewModel { params -> InsightViewModel(params.getOrNull(), get()) }
```

### Aggiornare il Decompose component (in RootComponent.kt)

Dove viene creato InsightViewModel, passare il `diaryId` dal destination:
```kotlin
// Il destination gia' ha diaryId serializzato
RootDestination.Insight(diaryId) -> {
    // Passare diaryId al viewModel via parametro Koin
}
```

### Validazione

```bash
# Dopo il fix, verificare:
./gradlew :features:insight:compileCommonMainKotlinMetadata
./gradlew :features:insight:compileKotlinIosSimulatorArm64
```

### Effort: 30 minuti max

Questo fix e' il template per TUTTI i SavedStateHandle nel progetto.
Una volta fatto qui, replicare su home, write, chat, auth, habits, meditation, humanoid.
