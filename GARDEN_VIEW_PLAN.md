# Garden View — Piano di Implementazione

> Una vista navbar dedicata che trasforma l'esplorazione delle attivita' in un'esperienza visiva e immersiva.
> Non una lista — un giardino da coltivare.

---

## 1. Concept

Il **Garden** e' la vista principale di navigazione dove l'utente scopre, esplora e accede a tutte le attivita' di Calmify. A differenza di `ActivityGardenScreen` (lista piatta con filtri), il Garden e':

- **Visivo**: header grafico ispirato all'Ikigai Venn diagram come "mappa" del giardino
- **Narrativo**: ogni attivita' ha una scheda espandibile con benefici, contesto e "perche' farlo"
- **Vivo**: mostra lo stato di "fioritura" dell'utente — quali attivita' ha gia' provato, quali sono nuove
- **Navigabile**: tap su qualsiasi attivita' apre la relativa feature

---

## 2. Struttura della Vista

```
┌─────────────────────────────────────────────┐
│  [Garden Header — Ikigai-inspired]          │
│  ┌─────────────────────────────────────────┐│
│  │    Canvas: 4 cerchi sovrapposti         ││
│  │    (Scrittura, Mente, Corpo, Spirito)   ││
│  │    con al centro "IL TUO GIARDINO"      ││
│  │    Ogni cerchio mostra # attivita'      ││
│  │    fatte / totali (es. "2/3")           ││
│  │    Tap su un cerchio → scrolla alla     ││
│  │    sezione corrispondente               ││
│  └─────────────────────────────────────────┘│
│                                             │
│  [Barra progresso globale]                  │
│  "6 di 18 attivita' esplorate"             │
│  ═══════════════░░░░░░░░░░░░  33%          │
│                                             │
│  ── Scrittura (2/3) ──────────────────────  │
│  ┌─────────────────────────────────────────┐│
│  │ 🌿  Diario                         ▼   ││
│  │     "Scrivi i tuoi pensieri"            ││
│  │     ┌─ Espanso ──────────────────────┐  ││
│  │     │ Benefici:                      │  ││
│  │     │ • Riduce ansia del 20%         │  ││
│  │     │ • Migliora autoconsapevolezza   │  ││
│  │     │ Tempo: ~5 min                  │  ││
│  │     │ [Inizia →]                     │  ││
│  │     └────────────────────────────────┘  ││
│  └─────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────┐│
│  │ 🌱  Brain Dump                   nuovo  ││
│  │     "Svuota la mente"                   ││
│  └─────────────────────────────────────────┘│
│  ...                                        │
│                                             │
│  ── Mente (1/4) ──────────────────────────  │
│  ...                                        │
│                                             │
│  ── Corpo (0/4) ──────────────────────────  │
│  ...                                        │
│                                             │
│  ── Spirito (3/6) ────────────────────────  │
│  ...                                        │
│                                             │
│  ── Abitudini (0/2) ─────────────────────── │
│  ...                                        │
└─────────────────────────────────────────────┘
```

---

## 3. Garden Header — Dettaglio

Ispirato alla `VennDiagram` di `IkigaiScreen.kt`, ma riadattato:

### 3.1 Layout Canvas

- **4 cerchi sovrapposti** (come Ikigai) ma con semantica diversa:
  - **Top-left**: Scrittura (verde `#2E7D55`, alpha 0.20)
  - **Top-right**: Mente (viola `#7E57C2`, alpha 0.20)
  - **Bottom-left**: Corpo (arancione `#FF9800`, alpha 0.20)
  - **Bottom-right**: Spirito (blu `#42A5F5`, alpha 0.20)
- **Centro**: Label "IL TUO GIARDINO" in bold
- **Ogni cerchio** mostra:
  - Nome categoria
  - Conteggio `fatte/totali` (es. "2/3")
  - Icona piccola della categoria
- **Interazione**: tap su un cerchio → `LazyListState.animateScrollToItem()` alla sezione

### 3.2 Differenze dall'Ikigai originale

| Ikigai | Garden Header |
|--------|---------------|
| Statico, solo display | Interattivo (tap → scroll) |
| Conta items aggiunti | Conta attivita' esplorate |
| Colori fissi 4 cerchi | Cerchi "si illuminano" quando l'utente ha esplorato almeno 1 attivita' |
| Label esterna | Label dentro i cerchi |
| Solo Canvas | Canvas + sottile animazione pulse sul cerchio attivo |

### 3.3 Stato "Fioritura"

- Cerchio vuoto (0 esplorate): alpha 0.10, stroke tratteggiato
- Cerchio parziale: alpha 0.20, stroke solido
- Cerchio completo (tutte esplorate): alpha 0.30, stroke solido + leggero glow/particle

---

## 4. Activity Cards — Innovazione

Le card NON sono la semplice riga icon+titolo+chevron dell'attuale `ActivityGardenScreen`. Sono **card espandibili** con contenuto ricco.

### 4.1 Stato Collapsed (default)

```
┌──────────────────────────────────────────┐
│  [Icona 44dp]   Titolo            [Tag]  │
│  [bg accent]    Sottotitolo              │
│                                   [▼]    │
└──────────────────────────────────────────┘
```

- **Icona**: stessa pill colorata dell'attuale (accent 10% bg)
- **Tag** in alto a destra:
  - `nuovo` — mai aperta (Surface con primary color)
  - `preferito` — se l'utente la salva
  - nessun tag se gia' usata
- **Indicatore stato**: piccolo dot verde se "gia' esplorata"

### 4.2 Stato Expanded (tap per toggle)

```
┌──────────────────────────────────────────┐
│  [Icona 44dp]   Titolo            [Tag]  │
│  [bg accent]    Sottotitolo              │
│                                          │
│  ── Perche' farlo ────────────────────── │
│  Testo descrittivo dei benefici, scritto │
│  in modo empatico e motivazionale.       │
│                                          │
│  Benefici                                │
│  • Primo beneficio concreto              │
│  • Secondo beneficio                     │
│  • Terzo beneficio                       │
│                                          │
│  ⏱ ~5 min    📊 Livello: Facile         │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │         Inizia Attivita'  →       │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

- **animateContentSize()** per transizione smooth
- **Benefici**: 2-4 bullet points per attivita'
- **Metadata**: durata stimata + livello difficolta'
- **CTA button**: `FilledTonalButton` con accent color dell'attivita'

### 4.3 Dati per ogni Attivita'

Estendere il data class:

```kotlin
data class GardenActivity(
    val name: String,
    val description: String,       // sottotitolo breve
    val longDescription: String,   // "Perche' farlo" — 2-3 frasi
    val benefits: List<String>,    // 2-4 benefici
    val estimatedMinutes: Int,     // durata stimata
    val difficulty: Difficulty,    // FACILE, MEDIO, AVANZATO
    val icon: ImageVector,
    val accent: Color,
    val category: GardenCategory,
    val isExplored: Boolean,       // l'utente l'ha gia' aperta almeno 1 volta
    val isFavorite: Boolean,       // preferita dall'utente
    val onClick: () -> Unit,
)

enum class Difficulty(val label: String) {
    FACILE("Facile"),
    MEDIO("Medio"),
    AVANZATO("Avanzato"),
}
```

---

## 5. Barra Progresso Globale

Sotto l'header, prima delle sezioni:

```kotlin
// Mostra quante attivita' l'utente ha provato almeno 1 volta
Column(Modifier.padding(horizontal = 24.dp)) {
    Text("6 di 18 attivita' esplorate", style = bodySmall)
    Spacer(4.dp)
    LinearProgressIndicator(
        progress = explored / total,
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = colorScheme.primary,
        trackColor = colorScheme.surfaceContainerHigh,
    )
}
```

---

## 6. Section Headers — Upgrade

Rispetto all'attuale `GardenSectionHeader` (semplice testo + badge count):

```
┌──────────────────────────────────────────┐
│  [Icona cat]  Scrittura     2 di 3  [━]  │
│               ████████░░░░          [━]  │
└──────────────────────────────────────────┘
```

- Icona della categoria a sinistra
- Progress bar mini (quante esplorate su totali)
- Animazione fadeIn + slideIn (gia' presente)

---

## 7. Navigazione e Integrazione

### 7.1 Navbar

- Aggiungere `Garden` come destinazione nella bottom navigation bar
- Icona: `Icons.Outlined.Yard` o `Icons.Outlined.Park`
- Label: "Garden"
- Posizione: valutare dove inserirla (es. seconda posizione dopo Home)

### 7.2 RootDestination

```kotlin
@Serializable
data object Garden : RootDestination
```

### 7.3 RootComponent

Aggiungere child per `Garden` con tutti i callback di navigazione verso le singole attivita'.

### 7.4 DecomposeApp

Rendering della `GardenScreen` nel Children stack. La bottom bar mostra l'icona Garden selezionata.

---

## 8. Stack Tecnico

### File da creare

| File | Posizione | Scopo |
|------|-----------|-------|
| `GardenScreen.kt` | `features/write/src/androidMain/` | Composable principale |
| `GardenContract.kt` | `features/write/src/commonMain/` | Intent/State/Effect |
| `GardenViewModel.kt` | `features/write/src/androidMain/` | MVI ViewModel |

### File da modificare

| File | Modifica |
|------|----------|
| `RootDestination.kt` | Aggiungere `Garden` destination |
| `RootComponent.kt` | Aggiungere child + navigation callbacks |
| `DecomposeApp.kt` | Rendering + bottom bar item |
| `KoinModules.kt` | Registrare `GardenViewModel` |

### Componenti riutilizzati

- `VennDiagram` — riscritta/adattata come `GardenVennHeader` (Canvas custom)
- `LinearProgressIndicator` — per barre progresso
- `AnimatedVisibility` — per animazioni staggered
- `animateContentSize()` — per expand/collapse card
- `LazyColumn` + `LazyListState` — scroll programmato ai cerchi

### Componenti grafici disponibili (core/ui)

- `LiquidMoodBackground` — potenziale sfondo animato per l'header
- `GlassCard` — effetto glassmorphism per le card expanded
- `ParticleSystem` — particelle per i cerchi "completati"
- `EmotionalColorEngine` — palette dinamica basata su mood

---

## 9. Stato e Persistenza

### 9.1 Come tracciare "attivita' esplorate"

Opzione consigliata: **Firestore document** `users/{userId}/garden/exploration`

```json
{
  "exploredActivities": ["diary", "meditation", "ikigai"],
  "favorites": ["diary", "gratitude"],
  "lastExploredAt": "2026-03-26T10:30:00Z"
}
```

### 9.2 Repository

```kotlin
// core/util — commonMain
interface GardenRepository {
    fun getExploredActivities(): Flow<Set<String>>
    fun getFavorites(): Flow<Set<String>>
    suspend fun markExplored(activityId: String)
    suspend fun toggleFavorite(activityId: String)
}
```

Implementazione in `data/mongo` con Firestore.

---

## 10. Contenuti Attivita' (Copy)

Ogni attivita' avra' contenuto statico hardcoded (non serve DB):

| Attivita' | Perche' farlo | Benefici | Min | Diff |
|-----------|---------------|----------|-----|------|
| Diario | Scrivere i tuoi pensieri ti aiuta a elaborare emozioni e trovare chiarezza. | Riduce ansia, migliora autoconsapevolezza, traccia crescita personale | 5 | Facile |
| Brain Dump | Libera la mente da tutto cio' che la occupa, senza giudizio. | Riduce carico mentale, migliora focus, facilita il sonno | 3 | Facile |
| Gratitudine | Riconoscere il bello quotidiano allena il cervello alla positivita'. | Aumenta felicita', riduce stress, migliora relazioni | 3 | Facile |
| Meditazione | Pochi minuti di presenza trasformano la qualita' della giornata. | Riduce cortisolo, migliora concentrazione, aumenta resilienza | 5-15 | Medio |
| Reframing | Cambiare prospettiva su un pensiero negativo ne riduce il potere. | Rompe schemi negativi, aumenta flessibilita' mentale, riduce ruminazione | 5 | Medio |
| Blocchi | Riconoscere cosa ti blocca e' il primo passo per superarlo. | Aumenta consapevolezza, identifica pattern, sblocca azione | 5 | Medio |
| Pensieri Ricorrenti | Osservare i pattern mentali ti da' potere su di essi. | Riduce ruminazione, identifica trigger, aumenta meta-cognizione | 5 | Medio |
| Energia | Monitorare i tuoi livelli di energia rivela ritmi naturali. | Ottimizza produttivita', previene burnout, migliora decisioni | 2 | Facile |
| Sonno | Il sonno e' il fondamento di ogni altra area del benessere. | Migliora umore, aumenta energia, consolida memoria | 2 | Facile |
| Movimento | Registrare il movimento ti motiva a muoverti di piu'. | Riduce sedentarieta', migliora umore, aumenta energia | 2 | Facile |
| Dashboard | Visione d'insieme del tuo benessere fisico. | Visione olistica, identifica trend, motiva azione | 1 | Facile |
| Valori | Scoprire i tuoi valori guida decisioni allineate. | Chiarezza decisionale, aumenta soddisfazione, riduce conflitti interni | 10 | Avanzato |
| Ikigai | Trovare l'intersezione tra passione, talento, missione e professione. | Scopri il tuo scopo, allinea vita e lavoro, aumenta motivazione | 15 | Avanzato |
| Awe | Coltivare meraviglia espande la percezione del tempo. | Riduce stress, aumenta generosita', amplia prospettiva | 3 | Facile |
| Silenzio | Il silenzio intenzionale rigenera la mente. | Riduce sovraccarico, migliora ascolto interiore, aumenta creativita' | 5 | Medio |
| Connessioni | Le relazioni significative sono il miglior predittore di benessere. | Rafforza legami, riduce solitudine, aumenta senso di appartenenza | 5 | Facile |
| Ispirazione | Raccogliere spunti nutre la crescita personale. | Stimola creativita', amplia orizzonti, motiva azione | 3 | Facile |
| Abitudini | Piccole azioni ripetute creano grandi trasformazioni. | Automatizza il miglioramento, costruisce disciplina, crea momentum | 5 | Medio |
| Ambiente | Il contesto in cui vivi influenza chi diventi. | Riduce frizione, aumenta benessere, supporta obiettivi | 10 | Avanzato |

---

## 11. Ordine di Implementazione

### Step 1 — Modello e Dati
1. Definire `GardenContract.kt` (State con lista attivita', explored set, favorites set)
2. Creare `GardenRepository` interface in `core/util`
3. Implementare `FirestoreGardenRepository` in `data/mongo`

### Step 2 — ViewModel
4. Creare `GardenViewModel.kt` con MVI pattern
5. Registrare in Koin

### Step 3 — UI Screen
6. `GardenVennHeader` — Canvas component (adattamento VennDiagram)
7. `GardenProgressBar` — barra globale
8. `GardenSectionHeader` — header sezione con mini progress
9. `GardenActivityCard` — card espandibile con benefici
10. `GardenScreen` — composable che assembla tutto

### Step 4 — Navigation
11. Aggiungere `Garden` a `RootDestination`
12. Aggiungere child a `RootComponent`
13. Rendering in `DecomposeApp`
14. Aggiungere item nella bottom navigation bar

### Step 5 — Polish
15. Animazioni staggered sulle card
16. Pulse animation sui cerchi header
17. Particle effect per cerchi completati (opzionale)
18. Dark mode testing

---

## 12. Differenze chiave da ActivityGardenScreen

| Aspetto | ActivityGardenScreen (attuale) | GardenScreen (nuovo) |
|---------|-------------------------------|----------------------|
| Accesso | Da write feature, navigazione interna | Navbar, accesso diretto |
| Header | TopAppBar semplice | Venn diagram interattivo |
| Card | Riga piatta icon+titolo+chevron | Card espandibile con benefici |
| Stato | Nessuno, solo lista | Traccia esplorazione + preferiti |
| Filtri | FilterChip orizzontali | Tap sui cerchi del diagramma |
| Contenuto | Solo nome + 1 riga descrizione | Descrizione lunga + benefici + metadata |
| Progresso | Nessuno | Barra globale + per sezione |
| Interazione | Solo click → naviga | Expand per info, poi naviga |

---

## 13. Note di Design

- **No emoji nel codice**: usare solo Material Icons
- **Colori**: riutilizzare la palette gia' definita in `ActivityGardenScreen` per coerenza
- **Typography**: M3 hierarchy (titleMedium per titoli sezione, titleSmall per card, bodySmall per descrizioni)
- **Spacing**: 24dp horizontal padding (come attuale), 8dp vertical gap tra card
- **Shape**: RoundedCornerShape(20.dp) per card, coerente con il design system
- **Animazioni**: sottili e funzionali, mai eccessive — fadeIn + slideIn staggered
