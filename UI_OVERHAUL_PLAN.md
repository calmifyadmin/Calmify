# Piano Rifacimento UI — Calmify

**Obiettivo**: Trasformare le 19 screen da prototipi funzionali a UI premium con Material 3 Expressive, zero emoji, navigazione intelligente, e componenti grafici integrati.

---

## PROBLEMA 1: Emoji ovunque

**14 file modello** contengono campi `emoji: String` usati nella UI.

### Soluzione

Rimuovere TUTTI i campi `emoji` dai modelli. Sostituire con Material Icons nelle screen.
Ogni tipo/categoria avra' un'icona `ImageVector` associata tramite extension function o when-expression nella UI, mai nel modello dati.

**File da modificare (modelli):**
- `ConnectionEntry.kt` — ConnectionType.emoji
- `RecurringThought.kt` — ThoughtType (colori gia' presenti, emoji solo estetico)
- `EnvironmentDesign.kt` — EnvironmentCategory.emoji
- `MovementLog.kt` — MovementType.emoji
- `Block.kt` — BlockType.emoji
- `MeditationSession.kt` — MeditationType.emoji, BreathingPattern
- `ThoughtReframe.kt` — ReframeStep
- `Habit.kt` — HabitCategory.emoji
- `EnergyCheckIn.kt` — MovementType.emoji
- `GratitudeEntry.kt` — GratitudeCategory.emoji
- `Mood.kt` — emoji field
- `IkigaiExploration.kt` (via IkigaiContract) — Circle.emoji
- `AweContract.kt` — prompts con emoji

**File da modificare (screen):**
- Tutte le 19 screen che visualizzano emoji nei testi
- `InspirationContract.kt` — quote text con caratteri Unicode

**Pattern di sostituzione:**
```kotlin
// PRIMA (nel modello):
enum class MeditationType(val displayName: String, val emoji: String) {
    TIMER("Meditazione libera", "\uD83E\uDDD8"),
}

// DOPO (nel modello):
enum class MeditationType(val displayName: String) {
    TIMER("Meditazione libera"),
}

// DOPO (nella screen, extension function locale):
@Composable
fun MeditationType.icon(): ImageVector = when(this) {
    TIMER -> Icons.Default.SelfImprovement
    BREATHING -> Icons.Default.Air
    BODY_SCAN -> Icons.Default.Accessibility
}
```

---

## PROBLEMA 2: JournalHome mega-lista

JournalHomeScreen.kt ha **1348 righe** e contiene **19+ QuickCard** in una LazyColumn piatta. Nessuna gerarchia, nessuna organizzazione.

### Soluzione: Hub a 4 sezioni

Ristrutturare JournalHome con **4 sezioni collassabili** (o tab) + link al Percorso Hub:

```
┌─────────────────────────────────────┐
│  [Percorso Hub]  barra superiore    │
├─────────────────────────────────────┤
│                                     │
│  SCRIVI                             │
│  ┌──────┐ ┌──────┐ ┌──────┐       │
│  │Diario│ │Brain │ │Gratu-│       │
│  │      │ │Dump  │ │dine  │       │
│  └──────┘ └──────┘ └──────┘       │
│                                     │
│  MENTE                              │
│  ┌──────┐ ┌──────┐ ┌──────┐       │
│  │Medita│ │Refra-│ │Blocchi│      │
│  │zione │ │ming  │ │      │       │
│  └──────┘ └──────┘ └──────┘       │
│  Pensieri Ricorrenti  →            │
│                                     │
│  CORPO                              │
│  ┌──────┐ ┌──────┐ ┌──────┐       │
│  │Energy│ │Sonno │ │Movi- │       │
│  │      │ │      │ │mento │       │
│  └──────┘ └──────┘ └──────┘       │
│  Dashboard Terreno  →              │
│                                     │
│  SPIRITO                            │
│  ┌──────┐ ┌──────┐ ┌──────┐       │
│  │Valori│ │Ikigai│ │Awe   │       │
│  └──────┘ └──────┘ └──────┘       │
│  Silenzio · Connessioni · Ispir.   │
│                                     │
│  ─── Diari Recenti ───             │
│  [lista scrollabile]               │
└─────────────────────────────────────┘
```

**Layout tecnico:**
- Ogni sezione = `Column` con titolo (titleLarge) + `FlowRow` di card compatte (non OutlinedCard con freccia)
- Card compatte: icona + label, stile M3 Expressive `FilledTonalButton` o `ElevatedCard` piccola
- Max 3 card per riga, bordi arrotondati (MaterialShapes), colori per sezione
- Sezione "Scrivi" in evidenza con card piu' grande per "Nuovo Diario"
- Link "Percorso Hub" come `LargeExtendedFloatingActionButton` o banner top

---

## PROBLEMA 3: Grafica non integrata

7 componenti grafici creati in `core/ui/components/graphics/` ma **zero usati** nelle screen.

### Integrazione specifica:

| Componente | Dove integrarlo | Come |
|---|---|---|
| **InteractiveNodeGraph** | `PercorsoScreen` | Sostituire la lista piatta con 4 nodi (Mente/Corpo/Spirito/Abitudini) collegati, tap naviga alla sezione, size proporzionale al progresso |
| **RadarChart** | `DashboardScreen` | Ragnatela corpo-mente: 5 assi (Energia, Sonno, Movimento, Idratazione, Mood) — sostituisce le progress bar lineari |
| **HabitHeatMap** | `HabitListScreen` (features/habits) | Sotto la lista abitudini, heatmap ultimi 3 mesi di completamento |
| **LiquidMoodBackground** | `HomeScreen` / `JournalHomeScreen` | Background animato sotto la sezione hero, colori basati sull'ultimo mood registrato |
| **EmotionalColorEngine** | `WriteScreen` post-salvataggio | Dopo aver scritto un diario, il mood selezionato colora la card di conferma; nella Home il colore riflette il mood corrente |
| **GlassCard** | `PercorsoScreen`, `DashboardScreen` | Sostituire le Card normali con GlassCard per le sezioni principali, con tint mood-aware |
| **ParticleSystem** | `HabitListScreen`, `GratitudeScreen` | Trigger: completamento abitudine (streak milestone), salvataggio gratitudine |
| **RingProgress** | `PercorsoScreen` (gia' usato) | OK com'e' |
| **BubbleCloud** | `RecurringThoughtsScreen` (gia' usato) | OK com'e' |
| **VennDiagram** | `IkigaiScreen` (gia' usato) | OK com'e' |
| **BreathingCircle** | `MeditationScreen` (gia' usato) | OK com'e' |

---

## PROBLEMA 4: Material 3 Expressive non usato

Abbiamo `material3-expressive = "1.5.0-alpha07"` nel version catalog ma nessuna screen lo usa.

### Integrazione M3 Expressive:

1. **MaterialExpressiveTheme** — Sostituire `MaterialTheme` nel tema dell'app con `MaterialExpressiveTheme` + `MotionScheme.expressive()`. Tutte le animazioni diventano physics-based automaticamente.

2. **LoadingIndicator / ContainedLoadingIndicator** — Sostituire tutti i `CircularProgressIndicator` con `LoadingIndicator` (morphing shapes).

3. **FloatingToolbar** — Nella JournalHome, toolbar flottante per azioni rapide (Scrivi, Brain Dump, Gratitudine) al posto della lista.

4. **ButtonGroup** — Nelle screen con scelte multiple (MeditationType, ConnectionType, EnergyLevel) usare `ButtonGroup` al posto di `FilterChip` row.

5. **SplitButtonLayout** — Per azioni con menu secondario (es. "Salva" + opzioni).

6. **Shapes MaterialShapes** — Usare forme organiche (Cookie9Sided, ecc.) per card e indicatori, non solo RoundedCornerShape.

7. **Typography emphasized** — Usare la tipografia "emphasized" per titoli e metriche importanti.

---

## ORDINE DI ESECUZIONE

### Fase 1: Pulizia emoji (tutti i modelli + screen)
- Rimuovere campi `emoji` da tutti gli enum
- Creare extension functions `@Composable fun Type.icon(): ImageVector` nelle screen
- Aggiornare tutte le screen per usare `Icon()` invece di `Text(emoji)`

### Fase 2: Tema M3 Expressive
- Aggiungere `compose-material3-expressive` come dipendenza nei moduli che servono
- Sostituire `MaterialTheme` con `MaterialExpressiveTheme` + `MotionScheme.expressive()`
- Sostituire `CircularProgressIndicator` con `LoadingIndicator`

### Fase 3: Ristrutturare JournalHome
- Dividere in 4 sezioni (Scrivi/Mente/Corpo/Spirito)
- Card compatte in FlowRow (3 per riga)
- Rimuovere tutte le QuickCard/QuickActionCard piatte
- Aggiungere banner/link Percorso Hub
- Aggiungere LiquidMoodBackground come sfondo hero

### Fase 4: Integrare componenti grafici
- PercorsoScreen: InteractiveNodeGraph + GlassCard
- DashboardScreen: RadarChart
- HabitListScreen: HabitHeatMap + ParticleSystem (streak celebration)
- GratitudeScreen: ParticleSystem (on save)
- HomeScreen: LiquidMoodBackground + EmotionalColorEngine
- Screen varie: GlassCard al posto di Card dove appropriato

### Fase 5: Polish screen individuali
- Ogni screen: revisione layout, spacing, tipografia, colori
- Rimuovere testo ridondante, prompt troppo lunghi
- Usare `ButtonGroup` per selezioni multiple
- Usare `SplitButtonLayout` dove serve
- Animazioni ingresso/uscita con `MotionScheme.expressive()`

---

## VINCOLI TECNICI

- **KMP**: M3 Expressive (`MaterialExpressiveTheme`, `LoadingIndicator`, etc.) e' Android-only (`material3:1.5.0-alpha07`). Usare in `androidMain` o wrap con expect/actual.
- **Compose Multiplatform**: Il tema base (`MaterialTheme`) in commonMain resta. Le screen in androidMain possono usare M3 Expressive direttamente.
- **Build**: Le screen sono gia' tutte in `androidMain` (features/write/src/androidMain/) quindi possono usare M3 Expressive senza problemi.
- **Dipendenza**: Aggiungere `libs.compose.material3.expressive` ai moduli feature che lo usano.
