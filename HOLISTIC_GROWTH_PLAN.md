# Calmify — Piano di Crescita Olistica

**"Mente, Corpo, Spirito — separati si indeboliscono, insieme si amplificano."**

> Questo piano espande Calmify da app di journaling+AI a **sistema integrato di crescita personale**.
> Ogni concetto e' mappato a feature concrete, con priorita', dipendenze e stima di complessita'.
> Target: self-improver 25-40 che vuole un unico punto di riferimento per il suo percorso.

**Ultimo aggiornamento**: 2026-03-22

---

## Mappa Concettuale → Feature

```
                        ┌─────────────────────┐
                        │    CALMIFY SYSTEM    │
                        └──────────┬──────────┘
              ┌────────────────────┼────────────────────┐
              │                    │                     │
        ┌─────┴─────┐      ┌──────┴──────┐      ┌──────┴──────┐
        │   MENTE   │      │   CORPO     │      │  SPIRITO    │
        └─────┬─────┘      └──────┬──────┘      └──────┬──────┘
              │                    │                     │
     ┌────────┼────────┐    ┌─────┼──────┐       ┌──────┼──────┐
     │        │        │    │     │      │       │      │      │
  Chiarezza  Abitudini  │  Energia Sonno  │    Valori  Scopo   │
  Mentale    & Routine   │  Fisica  Salute │    Ikigai  Conn.   │
     │        │        │    │     │      │       │      │      │
  Reframing  Blocchi   │  Nutr.  Idr.  Mov.  Natura  Servizio │
  Pensiero   Mentali   │                      Arte   Silenzio │
                       │                      Relazioni       │
                 AI Coach                                     │
                 (Eve - trasversale)                           │
```

---

## TECNOLOGIE GRAFICHE & VISUALIZZAZIONE (KMP-native)

*"I dati senza forma visiva restano numeri. La visualizzazione li trasforma in comprensione."*

### Stack Grafico — Tutto in commonMain, Zero Dipendenze Android-Only

L'intero sistema di visualizzazione si appoggia su **Compose Multiplatform Canvas API** — nativo, performante, zero librerie esterne pesanti. L'app ha gia' una base solida (MoodShapeIndicator con RoundedPolygon, WizardAnimations con Canvas+DrawScope, gradient Brush nelle varie screen). Ora la espandiamo a sistema completo.

#### Layer 1: Motore Grafico (gia' disponibile)

| Tecnologia | Cosa Fa | Dove Vive | KMP? |
|------------|---------|-----------|------|
| **Compose Canvas API** | DrawScope, Path, drawCircle, drawArc, drawPath, drawLine, drawRect | `compose.foundation` (gia' in deps) | SI |
| **Compose Animation API** | Animatable, animateFloatAsState, InfiniteTransition, spring physics, tween, keyframes | `compose.animation` (gia' in deps) | SI |
| **Brush API** | Brush.radialGradient, Brush.linearGradient, Brush.sweepGradient, SolidColor | `compose.ui.graphics` (gia' in deps) | SI |
| **Path API** | Bezier curves (cubicTo, quadraticTo), path operations, PathMeasure per animazioni lungo path | `compose.ui.graphics` (gia' in deps) | SI |
| **graphics-shapes 1.0.1** | RoundedPolygon, Morph (shape morphing M3 Expressive) — gia' usato in MoodShapeIndicator | `androidx.graphics:graphics-shapes` (gia' in deps) | SI (AndroidX ma API pura) |
| **BlendMode** | Multiply, Screen, Overlay, ColorDodge — per effetti luce, glow, glassmorphism | `compose.ui.graphics` (gia' in deps) | SI |
| **graphicsLayer** | Blur, alpha, rotationX/Y/Z, scaleX/Y, shadow, clip — per effetti 3D-like su 2D | `compose.ui` (gia' in deps) | SI |
| **GLSL RuntimeShader** | Shader GPU custom (API 33+) — gia' in `liquid_wave.glsl` per LiveChat | `android.graphics.RuntimeShader` (androidMain) | NO (expect/actual con Canvas fallback) |
| **Vico Charts 1.13.1** | Line, Bar, Column chart con spring physics e M3 — gia' in deps insight module | `com.patrykandpatrick.vico` (gia' in deps) | NO (Android-only, da valutare vs Koalaplot KMP) |
| **Lottie Compose 6.7.1** | Animazioni pre-renderizzate da After Effects — gia' in deps | `com.airbnb.android:lottie-compose` (gia' in deps) | NO (Android-only, per asset premium) |

#### Patrimonio Grafico Esistente (135+ file con animazioni)

L'app ha gia' un **motore grafico ricco** — non partiamo da zero:

| Componente Esistente | Dove | KMP? | Cosa Fa |
|---------------------|------|------|---------|
| **FluidAudioIndicator** | `features/chat/commonMain` | SI | Waveform con sine/cosine, colori emotion-aware, dual-channel |
| **GeminiLiquidVisualizer** | `features/chat/androidMain` | NO | GLSL shader GPU + fallback Canvas per onde fluide live chat |
| **MoodShapeIndicator** | `core/ui/androidMain` | NO* | 16 forme mood con RoundedPolygon, morphing, glow, pulse — **da portare in commonMain** |
| **TodayPulseIndicator** | `features/home/androidMain` | NO* | Arco circolare animato con sweepGradient — **portabile in commonMain** |
| **LikeParticleBurst** | `core/social-ui/commonMain` | SI | Sistema particelle cuori con gravita' — **base per ParticleSystem generico** |
| **ShimmerLoadingSkeleton** | `core/social-ui/commonMain` | SI | Effetto shimmer Brush-based — **gia' pronto, riusabile ovunque** |
| **AnimatedCount** | `core/social-ui/commonMain` | SI | Counter animato — riusabile per streak, completamento |
| **ExpressiveStepper** | `core/ui/commonMain` | SI | Step indicator con morphing colori e scale |
| **WizardAnimations** | `features/write/commonMain` | SI | Palette colori per step, forme geometriche animate |
| **IntensityShapes** | `features/write/commonMain` | SI | Forme che cambiano con intensita' emotiva |

*Portabili in commonMain: la logica e' pura Compose Canvas, solo `graphics-shapes` richiede expect/actual per iOS.

#### Layer 2: Librerie KMP da Aggiungere (+ esistenti da sfruttare)

| Libreria | Versione | Cosa Fa | Stato | KMP? |
|----------|----------|---------|-------|------|
| **Koalaplot-core** | `0.6.3` | Charting KMP: Line, Bar, Area, Pie, Polar, Stacked — Compose Canvas nativo | DA AGGIUNGERE | SI |
| **Haze** | `1.3.1` | Glassmorphism blur per Compose MP — blur reale cross-platform | DA AGGIUNGERE | SI |
| **ColorMath** | `3.6.1` | OKLab/HSL/LAB interpolazione colore, palette generation. Kotlin puro. | DA AGGIUNGERE | SI |
| **Vico** | `1.13.1` | Line, Bar, Column chart con spring physics e M3 theming | GIA' IN DEPS (insight module, non usato) | NO |
| **Lottie Compose** | `6.7.1` | Animazioni After Effects pre-renderizzate | GIA' IN DEPS (non usato attivamente) | NO |
| **ShimmerLoadingSkeleton** | custom | Effetto shimmer Brush-based | GIA' IMPLEMENTATO in `core/social-ui/commonMain` | SI |

**Decisione Vico vs Koalaplot**:
- **Vico** (gia' in deps): maturo, ben integrato M3, ottimo per Android. Ma **Android-only**.
- **Koalaplot** (da aggiungere): nativo KMP, Compose Canvas puro, gira su Android+iOS+Desktop+Web.
- **Strategia**: usare **Koalaplot per commonMain** (chart nelle nuove feature). Vico resta disponibile per chart Android-specific se servono feature avanzate. Per chart custom (radar, heatmap, bubble) costruiamo con Canvas diretto.

**Dipendenze da aggiungere a `libs.versions.toml`**:
```toml
[versions]
# Visualization KMP
koalaplot = "0.6.3"
haze = "1.3.1"
colormath = "3.6.1"

[libraries]
koalaplot-core = { module = "io.github.koalaplot:koalaplot-core", version.ref = "koalaplot" }
haze = { module = "dev.chrisbanes.haze:haze", version.ref = "haze" }
haze-materials = { module = "dev.chrisbanes.haze:haze-materials", version.ref = "haze" }
colormath = { module = "com.github.ajalt.colormath:colormath", version.ref = "colormath" }
```

#### Layer 3: Custom Rendering Engine (`core/ui/visualization/`)

Nuovo package in `core/ui` con componenti grafici riutilizzabili — **tutto commonMain, tutto Compose Canvas**.

---

### COMPONENTI GRAFICI PER FEATURE

#### A. Nodi Interattivi — "Mappa della Crescita"
**Usato in**: Percorso Hub, Scoperta Valori, Ikigai, Pensieri Ricorrenti

**Tecnologia**: Canvas + Path + Animatable + gesture (pointerInput/detectTapGestures)

```
Componente: InteractiveNodeGraph
├── NodeData (id, label, value, color, connections, state)
├── Canvas rendering:
│   ├── Nodi: drawCircle + radialGradient Brush (glow emozionale)
│   ├── Connessioni: cubicTo bezier curves tra nodi (animate pathMeasure)
│   ├── Labels: drawText (Compose 1.7+ TextMeasurer)
│   └── Selection: scale spring animation + ripple glow
├── Gesture: detectTapGestures → identifica nodo piu' vicino → callback
├── Layout: Force-directed simulation (spring physics tra nodi)
│   └── Ogni frame: calcola forze repulsione/attrazione → anima posizioni
└── Animazioni:
    ├── Entrata: nodi appaiono con staggered delay + scale from 0
    ├── Connessioni: PathMeasure animation (linea che "cresce")
    ├── Hover/tap: pulse glow + scale spring
    └── Transizione stato: morphing colore con ColorMath interpolation
```

**Applicazioni concrete**:
- **Mappa Mente-Corpo-Spirito**: 3 macro-nodi con sotto-nodi espandibili. Tap su "Mente" → espande Chiarezza, Abitudini, Reframing. Ogni nodo ha indicatore progresso (arc fill).
- **Pensieri Ricorrenti (4.2)**: Bolle di dimensione variabile (frequenza). Limitanti=rosso, Potenzianti=verde. Tap → apre dettaglio + reframing.
- **Valori Discovery (5.1)**: Nodi dei valori identificati, connessi ai momenti che li hanno rivelati.
- **Ikigai (5.2)**: 4 cerchi Venn interattivi con intersezioni evidenziate.

**File**: `core/ui/src/commonMain/kotlin/com/lifo/ui/visualization/InteractiveNodeGraph.kt`

---

#### B. Color Emotioning — Palette Emozionali Dinamiche
**Usato in**: Ogni screen — l'intera app "respira" con l'emozione dell'utente

**Tecnologia**: ColorMath (OKLab interpolation) + Brush API + graphicsLayer

```
Componente: EmotionalColorEngine
├── MoodColorPalette: mappa Mood → base color HSL
│   ├── Happy → HSL(45, 0.85, 0.60)  — oro caldo
│   ├── Calm → HSL(180, 0.50, 0.70)  — teal sereno
│   ├── Tense → HSL(0, 0.70, 0.55)   — rosso attenuato
│   ├── Depressed → HSL(230, 0.30, 0.45) — blu profondo
│   ├── Angry → HSL(10, 0.80, 0.50)  — arancio-rosso
│   └── ... (tutti i 16 mood mappati)
├── Interpolazione: OKLab perceptually uniform (non RGB lineare)
│   └── Transizione tra mood = smooth color morphing, non "salto"
├── Palette generation da base color:
│   ├── Surface: lighten 85% (sfondo card)
│   ├── Container: lighten 70% (container)
│   ├── Accent: saturate + darken (CTA, icone)
│   ├── Glow: base + alpha 0.3 (alone luminoso)
│   └── Gradient: base → complementare (sweep per cerchi)
├── Applicazione:
│   ├── Background gradient: Brush.verticalGradient(surface → white)
│   ├── Card glow: Box shadow con mood color
│   ├── Breathing animation: alpha pulse sul glow (InfiniteTransition)
│   └── Transition: animateColorAsState con OKLab space
└── Ambient Mode:
    ├── Se mood dell'ultimo diario e' noto → app assume quel colore
    ├── Se mood cambia durante sessione → transizione fluida 800ms
    └── Night mode: tutti i colori shiftano verso toni piu' caldi
```

**Effetto visivo**: l'app non ha un colore fisso. Quando l'utente e' sereno, tutto e' teal morbido. Quando e' teso, sfumature rosso-arancio appaiono sottilmente. L'app **sente** con te.

**File**: `core/ui/src/commonMain/kotlin/com/lifo/ui/emotion/EmotionalColorEngine.kt`

---

#### C. Diagrammi Interattivi & Chart
**Usato in**: Dashboard Energia, Trend Sonno, Streak, Correlazioni, Percorso

**Tecnologia**: Koalaplot (chart KMP) + Canvas custom per visualizzazioni speciali

```
1. LINE CHART EMOZIONALE (Koalaplot + custom overlay)
   ├── Asse X: giorni/settimane
   ├── Asse Y: mood/energia/sonno (1-10)
   ├── Linea: smooth bezier (Koalaplot DefaultLinearAxisModel)
   ├── Area fill: gradient dal colore linea → trasparente
   ├── Punti dati: cerchi con mood emoji overlay
   ├── Touch: drag per scrubbing → tooltip con dettaglio giorno
   ├── Multi-serie: mood + energia + sonno sovrapposti (alpha)
   └── Animazione entrata: path drawing animation (pathMeasure progress 0→1)

2. RADAR CHART / SPIDER WEB (Canvas custom)
   ├── Per: WellbeingSnapshot SDT (7 dimensioni)
   ├── Assi: autonomia, competenza, relazioni, mindfulness, scopo, gratitudine, (1-loneliness)
   ├── Poligono: Path con vertici su assi polari
   ├── Fill: radialGradient dal centro verso i bordi
   ├── Overlay: settimana corrente vs settimana precedente (2 poligoni sovrapposti)
   ├── Labels: drawText su ogni asse
   ├── Touch: tap su dimensione → highlight + dettaglio
   └── Animazione: vertici che "crescono" dal centro con spring physics

3. HEAT MAP CALENDARIO (Canvas custom)
   ├── Per: Habit streak "Don't Break the Chain"
   ├── Griglia: 7 colonne (Lu-Do) x N righe (settimane)
   ├── Celle: RoundedRect con colore da ColorMath gradient
   │   ├── 0 abitudini complete → grigio pallido
   │   ├── 50% → colore medio
   │   └── 100% → colore pieno + subtle glow
   ├── Oggi: bordo evidenziato + pulse animation
   ├── Touch: tap su giorno → dialog dettaglio
   └── Animazione entrata: staggered reveal dal giorno corrente verso il passato

4. RING/DONUT PROGRESS (Canvas custom)
   ├── Per: Completamento giornaliero, energie, idratazione
   ├── Arc: drawArc con strokeCap Round
   ├── Gradient: sweepGradient lungo l'arco
   ├── Centro: testo numerico o emoji
   ├── Animazione: arc sweep da 0 a valore con spring
   └── Multi-ring: anelli concentrici (Mente/Corpo/Spirito)

5. BUBBLE CLOUD (Canvas custom + force simulation)
   ├── Per: Pensieri ricorrenti (4.2), Topics, Trigger patterns
   ├── Bolle: drawCircle con dimensione = frequenza
   ├── Colore: da ColorMath palette (rosso=limitante, verde=potenziante)
   ├── Layout: force-directed packing (no sovrapposizione)
   ├── Labels: drawText centrato nella bolla
   ├── Touch: tap → espande + mostra dettaglio
   └── Animazione: bolle che "galleggiano" con subtle offset animation

6. VENN DIAGRAM INTERATTIVO (Canvas custom)
   ├── Per: Ikigai Explorer (5.2)
   ├── 4 cerchi sovrapposti con alpha 0.3
   ├── Colori: 4 colori distinti per ogni cerchio
   ├── Intersezioni: blend mode (Screen o Overlay)
   ├── Labels: in ogni sezione + nelle intersezioni
   ├── Touch: tap su intersezione → highlight + contenuto
   └── Animazione: cerchi che si avvicinano/sovrappongono con spring
```

**File**: `core/ui/src/commonMain/kotlin/com/lifo/ui/visualization/charts/`
- `EmotionalLineChart.kt`
- `RadarChart.kt`
- `HabitHeatMap.kt`
- `RingProgress.kt`
- `BubbleCloud.kt`
- `VennDiagram.kt`

---

#### D. Animazioni Immersive & Breathing
**Usato in**: Meditazione, Respirazione guidata, Silenzio, Onboarding valori

**Tecnologia**: Canvas + InfiniteTransition + Animatable + Brush

```
1. BREATHING CIRCLE (Meditazione 2.1)
   ├── Cerchio principale: scale animation sincronizzata con pattern respiratorio
   │   ├── Box Breathing: 4s expand, 4s hold, 4s contract, 4s hold
   │   ├── 4-7-8: 4s expand, 7s hold, 8s contract
   │   └── Diaframmatica: 4s expand, 6s contract
   ├── Visual:
   │   ├── Cerchio esterno: radialGradient che "respira" (alpha pulse)
   │   ├── Cerchio interno: colore solido che cambia fase (inspira=blu, trattieni=viola, espira=teal)
   │   ├── Particelle: 12-20 punti che orbitano il cerchio (rotate + offset)
   │   ├── Onde: 3 cerchi concentrici con delay sfasato (effetto onda)
   │   └── Testo fase: "Inspira..." / "Trattieni..." / "Espira..." con fade transition
   ├── Haptic feedback: vibrazione sottile a inizio fase (Android)
   └── Suono: campana sottile a ogni ciclo completato

2. LIQUID MOOD BACKGROUND (sfondo fluido emozionale)
   ├── 4-6 blob con Path + cubicTo
   ├── Ogni blob: posizione animata con sine wave offset
   ├── Colori: da EmotionalColorEngine, alpha 0.15-0.25
   ├── Blur: graphicsLayer blur 60-80dp (Haze library)
   ├── Velocita': mood calmo = lento, mood teso = piu' rapido
   └── Usato come sfondo di: meditazione, silenzio, check-in

3. MORPHING SHAPE TRANSITIONS
   ├── graphics-shapes Morph API (gia' disponibile)
   ├── Mood A → Mood B: forma geometrica si trasforma
   │   ├── Happy (cerchio) → Tense (stella appuntita) → Calm (onda morbida)
   │   └── Gia' implementato in MoodShapeIndicator — da estendere
   ├── Valore progress: shape che "fiorisce" man mano che il progresso cresce
   └── Usato in: transition tra sezioni percorso, onboarding valori

4. SPARKLINE ANIMATA
   ├── Mini line chart inline (gia' in JourneyLineCard)
   ├── Path drawing animation: la linea si "disegna" da sinistra a destra
   ├── Gradient fill sotto la linea
   ├── Punti chiave evidenziati (picchi, valli)
   └── Usato in: card riassuntive Home, Percorso, Dashboard Energia

5. PARTICLE SYSTEM (celebrazioni) — ESTENSIONE di LikeParticleBurst gia' in commonMain
   ├── Base: LikeParticleBurst (core/social-ui) gia' ha gravita', fade, scale, path custom
   ├── Generalizzare: estrarre GenericParticleSystem con shape configurabile
   │   ├── Shape: cuore (gia'), stella, cerchio, confetti (rettangoli ruotati)
   │   ├── Palette: configurabile per contesto (achievement=oro, gratitudine=rosa, streak=verde)
   │   └── Burst pattern: esplosione radiale (gia'), fontana, pioggia, spirale
   ├── Trigger: streak milestone, primo reframing, 30 giorni abitudine, completamento valori
   └── Leggero: max 50-80 particelle, lifetime 2-3 secondi
```

**File**: `core/ui/src/commonMain/kotlin/com/lifo/ui/visualization/immersive/`
- `BreathingCircle.kt`
- `LiquidMoodBackground.kt`
- `MorphingShapes.kt` (estensione MoodShapeIndicator)
- `ParticleSystem.kt`

---

#### E. Glassmorphism & Material Elevation
**Usato in**: Card overlay, meditazione, modal, paywall, insight AI

**Tecnologia**: Haze library + graphicsLayer + Brush

```
Componente: GlassCard
├── Haze: blur dello sfondo dietro la card (8-16dp radius)
├── Border: 1dp bianco alpha 0.2 (effetto vetro)
├── Fill: bianco alpha 0.1-0.15 (semi-trasparente)
├── Shadow: elevation 0 (il blur fa gia' profondita')
├── Tint: EmotionalColorEngine color con alpha 0.05 (colore emozionale sottile)
└── Varianti:
    ├── GlassCard.Frosted — blur forte (16dp), per meditazione/silenzio
    ├── GlassCard.Subtle — blur leggero (8dp), per card normali
    └── GlassCard.Elevated — blur + shadow leggera, per modal/dialog
```

**File**: `core/ui/src/commonMain/kotlin/com/lifo/ui/components/GlassCard.kt`

---

#### F. Gesture Interattive
**Usato in**: Nodi, chart, slider emozionali, respirazione

**Tecnologia**: Compose Gesture API (pointerInput, detectTapGestures, detectDragGestures, transformable)

```
Interazioni supportate:
├── Tap: selezione nodo, toggle giorno calendario, espandi bolla
├── Long press: dettaglio esteso, haptic feedback
├── Drag: scrubbing su timeline, riordinare abitudini
├── Pinch/zoom: zoom su node graph, espandi calendar range
├── Swipe: cambiare settimana/mese, dismiss card
└── Pan: navigare node graph quando zoomed
```

---

### MAPPING FEATURE → COMPONENTE GRAFICO

| Feature | Componenti Grafici | Effetto |
|---------|--------------------|---------|
| **Habit Tracker (1.1)** | HabitHeatMap + RingProgress + ParticleSystem | Calendario a colori, anello % giornaliera, confetti su milestone |
| **Meditazione (2.1)** | BreathingCircle + LiquidMoodBackground + GlassCard.Frosted | Cerchio che respira su sfondo fluido con card vetro |
| **Brain Dump (2.2)** | Animazione "svuotamento" — cerchio che si sgonfia | Feedback visivo dello "scarico mentale" |
| **Reframing (2.3)** | MorphingShapes (pensiero negativo=forma spigolosa → positivo=forma morbida) | Il pensiero si trasforma visivamente |
| **Gratitudine (2.4)** | Sparkline + emojional glow su ogni entry | Le 3 cose belle "brillano" |
| **Energy Tracker (3.1)** | RingProgress (multi-ring) + EmotionalLineChart | 4 anelli concentrici (sonno/acqua/moto/energia) + trend |
| **Dashboard Terreno (3.3)** | RadarChart + EmotionalLineChart + correlazioni animate | Ragnatela wellbeing + trend sovrapposti |
| **Blocchi (4.1)** | InteractiveNodeGraph (blocco → tipo → soluzione) | Mappa navigabile del processo di sblocco |
| **Pensieri Ricorrenti (4.2)** | BubbleCloud + animazione 90gg (bolla che si rimpicciolisce) | Bolle che visualmente "si sgonfiano" nel tempo |
| **Valori (5.1)** | InteractiveNodeGraph + MorphingShapes | Nodi connessi + forma che "fiorisce" per ogni valore scoperto |
| **Ikigai (5.2)** | VennDiagram interattivo | 4 cerchi con intersezioni cliccabili |
| **Awe (6.1)** | LiquidMoodBackground (colori nature: verdi, blu) + ParticleSystem (stelle) | Sfondo immersivo "natura" |
| **Silenzio (6.2)** | Cerchio minimale pulsante + GlassCard.Frosted + sfondo nero graduale | L'UI si "spegne" gradualmente — solo il cerchio resta |
| **Percorso Hub** | InteractiveNodeGraph (3 macro-nodi) + RingProgress per sezione | Mappa interattiva del percorso completo |
| **Eve Chat** | EmotionalColorEngine (sfondo chat cambia col mood) | La chat "sente" l'umore della conversazione |

---

### COLOR EMOTIONING — Principi di Design

```
L'app non ha UN colore. L'app ha IL TUO colore, in questo momento.

Principi:
1. MOOD-AWARE: il colore dominante dell'app riflette l'ultimo mood registrato
2. TRANSIZIONI FLUIDE: mai salti bruschi — OKLab interpolation per transizioni percettivamente uniformi
3. ACCESSIBILITY: contrast ratio WCAG AA minimo 4.5:1 su tutti i testi
4. SUBTLETY: il colore emozionale e' un ACCENTO, non un urlo. Alpha 0.05-0.30.
5. NIGHT SHIFT: dopo le 21:00, tutti i colori shiftano verso toni caldi (meno luce blu)
6. NEUTRAL FALLBACK: se nessun mood registrato, toni neutri caldi (beige/grigio morbido)
7. DARK MODE: stessi principi ma su base scura — colori emozionali come glow/highlight

Palette per dimensione:
├── MENTE: Blu-Indaco (chiarezza, profondita')
├── CORPO: Verde-Teal (vitalita', natura)
├── SPIRITO: Viola-Oro (trascendenza, calore)
├── ABITUDINI: Arancione-Ambra (energia, azione)
└── Ogni dimensione ha: surface, container, accent, glow, gradient
```

---

### MIGRAZIONE COMPONENTI GRAFICI → commonMain

Prima di costruire nuovi componenti, portiamo quelli esistenti in commonMain — riuso > riscrittura.

| Componente | Da | A | Blocco | Soluzione |
|------------|-----|---|--------|-----------|
| **MoodShapeIndicator** | `core/ui/androidMain` | `core/ui/commonMain` | `RoundedPolygon` (AndroidX) | expect/actual: `expect fun createMoodPolygon(mood: Mood): List<Float>` — actual Android usa RoundedPolygon, actual iOS/Web usa path points calcolati manualmente |
| **MoodShapeDefinitions** | `core/ui/androidMain` | `core/ui/commonMain` | Idem | Stessa expect/actual di sopra |
| **TodayPulseIndicator** | `features/home/androidMain` | `features/home/commonMain` | Nessuno! E' puro Canvas/drawArc | Spostamento diretto, zero modifiche |
| **GeminiLiquidVisualizer** | `features/chat/androidMain` | Resta androidMain | GLSL RuntimeShader (API 33+) | Il fallback Canvas e' gia' nel codice — estrarre come commonMain, shader resta actual Android |

**Nuovi componenti** da costruire direttamente in commonMain:
```
core/ui/src/commonMain/kotlin/com/lifo/ui/
├── emotion/
│   ├── EmotionalColorEngine.kt        ← NUOVO: palette dinamiche OKLab
│   ├── MoodShapeIndicator.kt          ← MIGRATO da androidMain
│   └── MoodShapeDefinitions.kt        ← MIGRATO da androidMain
├── visualization/
│   ├── InteractiveNodeGraph.kt        ← NUOVO: nodi interattivi force-directed
│   ├── RadarChart.kt                  ← NUOVO: ragnatela SDT wellbeing
│   ├── HabitHeatMap.kt                ← NUOVO: calendario a colori
│   ├── RingProgress.kt                ← NUOVO: anelli concentrici
│   ├── BubbleCloud.kt                 ← NUOVO: bolle pensieri ricorrenti
│   ├── VennDiagram.kt                 ← NUOVO: 4 cerchi ikigai
│   └── EmotionalLineChart.kt          ← NUOVO: trend con Koalaplot
├── immersive/
│   ├── BreathingCircle.kt             ← NUOVO: cerchio respirazione
│   ├── LiquidMoodBackground.kt        ← NUOVO: sfondo fluido emozionale
│   └── ParticleSystem.kt              ← NUOVO: estensione LikeParticleBurst
└── components/
    └── GlassCard.kt                   ← NUOVO: glassmorphism con Haze
```

---

### PERFORMANCE & BEST PRACTICES

```
1. Canvas rendering: usare drawWithCache per geometrie statiche
2. Animazioni: maxFrameRate non necessario — Compose gestisce automaticamente
3. Particelle: limitare a 50-80 per evitare jank su device low-end
4. Blur (Haze): cache del risultato, non ricalcolare ogni frame
5. Color interpolation: pre-calcolare palette all'avvio, non ad ogni recomposition
6. Node graph: spatial indexing (quadtree) se >50 nodi per gesture detection efficiente
7. Chart: virtualizzare punti dati se >1000 (mostrare solo viewport visibile)
8. Shimmer: usare InfiniteTransition (non LaunchedEffect loop) per efficienza
9. Gradient Brush: creare una volta e riusare (non ricreare in DrawScope)
10. Test: screenshot test per verificare rendering cross-platform (Android vs Desktop)
```

---

## INVENTARIO: Cosa Abbiamo vs Cosa Manca

### GIA' PRESENTE (base solida)
| Concetto | Feature Esistente | Stato |
|----------|------------------|-------|
| Journaling / Scarico mentale | Write module (diario + mood + trigger + sensazioni corporee) | Completo |
| AI Coach contestuale | Eve (text chat) + Karen (voice chat) con Context Engine | Completo |
| Smart Capture metriche | TextAnalyzer locale, inferenza mood/stress/energia dal testo | Completo |
| Profilo psicologico | PsychologicalProfile settimanale (stress, mood, resilienza) | Completo |
| Wellbeing Snapshot | WellbeingSnapshot SDT (autonomia, competenza, relazioni, mindfulness, scopo, gratitudine) | Completo |
| Prompt contestuali | getContextualPrompt() nel journaling (mood follow-up, streak, gap, time-of-day) | Completo |
| Weekly Reflection AI | WeeklyReflectionCard narrativa + JourneyLineCard sparkline | Completo |
| Goals & Coping | Selezione obiettivi + strategie di coping nelle Settings | Statico |
| Health Info | Profilo salute (sonno avg, esercizio freq, supporto sociale) | Statico |
| Lifestyle | Occupazione, stile di vita nelle Settings | Statico |
| Community | Feed, thread, repost, follow, messaging, notifiche | Completo |
| Avatar 3D | Filament + lip-sync + animazioni VRM nella voice chat | Completo |
| Mood tracking | 16 mood enum + emotion intensity + stress/energy/calm-anxiety levels | Completo |
| Today's Reflection | Card AI-generated sulla Home | Completo |
| Condivisione | CTA post-salvataggio diario verso community | Completo |

### DA COSTRUIRE (gap significativi)
| Concetto dal Framework | Cosa Manca | Priorita' |
|----------------------|------------|-----------|
| **Disciplina & Abitudini** | Nessun habit tracker, nessun streak visivo, nessuna catena, nessun design ambientale | P0 |
| **Chiarezza Mentale** | Nessuna meditazione guidata, nessun esercizio di respirazione, nessun timer silenzio | P0 |
| **Reframing Cognitivo** | Nessun tool CBT, nessuna osservazione pensieri, nessuna pratica gratitudine dedicata | P0 |
| **Blocchi Mentali** | Nessun sistema identificazione blocchi, nessun brain dump dedicato, nessun suggerimento azione minima | P1 |
| **Energia Fisica** | Nessun tracking sonno/idratazione/movimento/nutrizione, nessun ritmo energetico | P1 |
| **Salute come Base** | Info salute statiche, nessuna correlazione salute→mente, nessun dashboard salute | P1 |
| **Valori Fondamentali** | Nessun processo scoperta valori, nessun test coerenza, nessun framework decisionale | P1 |
| **Scopo / Ikigai** | Nessun esercizio ikigai, nessuna esplorazione scopo, nessun assessment talenti | P2 |
| **Connessione / Trascendenza** | Nessun prompt natura/awe, nessuna guida silenzio profondo, nessun tracker relazioni profonde | P2 |

---

## FASE 1: "IL TERRENO" — Abitudini & Disciplina
*La disciplina non e' forza di volonta' — e' design.*

### 1.1 Habit Tracker System
**Concetti coperti**: Parti piccolo, Aggancia a cio' che gia' fai, Non rompere la catena

**Modello dati** — `Habit` in `core/util/model/`:
```kotlin
data class Habit(
    val id: String,
    val ownerId: String,
    val name: String,                    // "Meditazione mattutina"
    val description: String,             // "5 minuti di respiro consapevole"
    val category: HabitCategory,         // MENTE, CORPO, SPIRITO
    val anchorHabit: String?,            // "Dopo il caffe'" (aggancio)
    val minimumAction: String,           // "Siediti e chiudi gli occhi" (azione minima)
    val targetFrequency: HabitFrequency, // DAILY, WEEKDAYS, CUSTOM
    val reminderTime: String?,           // "07:30"
    val createdAtMillis: Long,
    val isActive: Boolean
)

data class HabitCompletion(
    val id: String,
    val habitId: String,
    val ownerId: String,
    val completedAtMillis: Long,
    val dayKey: String,                  // "2026-03-22"
    val note: String?,                   // riflessione opzionale
    val difficulty: Int                  // 1-5, quanto e' stato facile
)

enum class HabitCategory { MENTE, CORPO, SPIRITO, RELAZIONI, CRESCITA }
enum class HabitFrequency { DAILY, WEEKDAYS, WEEKENDS, THREE_TIMES_WEEK, CUSTOM }
```

**UI** — `HabitTrackerScreen`:
- **Calendario visivo "Don't Break the Chain"**: griglia mensile con giorni colorati (verde=fatto, grigio=mancato, oggi=pulsante)
- **Streak counter** prominente con record personale
- **Regola d'oro**: se salti un giorno, banner: *"Un giorno puo' capitare. L'importante e' non saltarne due di fila."*
- **Azione minima**: quando l'utente esita, mostra l'azione minima definita: *"Non devi fare 20 minuti. Basta sederti e chiudere gli occhi."*
- **Suggerimento aggancio**: *"Dopo [ancora], fai [abitudine]"*
- **Habit templates**: Abitudini pre-configurate per categoria (meditazione, journaling, esercizio, gratitudine, lettura, idratazione, sonno regolare)
- **Progress settimanale/mensile**: % completamento + trend

**Integrazione AI (Eve)**:
- Eve vede le abitudini e il completamento nel Context Engine
- *"Ho notato che hai meditato 5 giorni su 7 questa settimana. Come ti senti rispetto alla settimana scorsa quando ne avevi fatti solo 2?"*
- Suggerisce nuove abitudini basate sui pattern del diario

**Complessita'**: ALTA (nuovo modulo `features/habits/`, nuovi modelli, nuovo repository, Firestore collection, SQLDelight cache)

**Deliverable**:
- [ ] `Habit.kt` + `HabitCompletion.kt` in `core/util/model/`
- [ ] `HabitRepository` interface in `core/util/repository/`
- [ ] `FirestoreHabitRepository` in `data/mongo/`
- [ ] `HabitCompletion.sq` in SQLDelight (cache locale)
- [ ] `features/habits/` module (Contract, ViewModel, Screen, Koin)
- [ ] Tab "Abitudini" nel bottom nav o sezione in "Il Mio Percorso"
- [ ] Calendario "Don't Break the Chain"
- [ ] Habit templates pre-configurati
- [ ] Integrazione Context Engine per Eve

---

### 1.2 Environment Design Tools
**Concetti coperti**: Progetta l'ambiente, Meno stimoli

**Feature**: Sezione "Il Tuo Ambiente" nelle Settings o nel percorso

- **Checklist ambiente**: suggerimenti personalizzabili
  - "Telefono lontano al mattino (primi 60 min)"
  - "Libro sul cuscino"
  - "Scrivania pulita prima di lavorare"
  - "Notifiche disattivate durante [attivita']"
- **Digital Detox Timer**: modalita' focus con countdown, blocco notifiche app
- **Morning Routine Builder**: sequenza personalizzabile di azioni mattutine
- **Evening Wind-Down**: routine serale (no schermi, lettura, preparazione domani)

**Complessita'**: MEDIA (estensione Settings + nuova sezione, no nuovo modulo)

**Deliverable**:
- [ ] `EnvironmentChecklist` model
- [ ] Sezione in Settings o percorso dedicato
- [ ] Digital Detox Timer (countdown con motivazione)
- [ ] Morning/Evening routine templates

---

## FASE 2: "LA MENTE CHIARA" — Chiarezza Mentale & Reframing
*La chiarezza mentale non si ottiene pensando di piu' — si ottiene rimuovendo il rumore.*

### 2.1 Meditazione & Respirazione Guidata
**Concetti coperti**: Silenzio intenzionale, 5-10 minuti senza stimoli

**Feature**: `features/meditation/` — nuovo modulo

- **Timer meditazione**: 3, 5, 10, 15, 20 minuti con suono campana inizio/fine
- **Respirazione guidata**:
  - Box Breathing (4-4-4-4)
  - 4-7-8 Rilassamento
  - Respirazione diaframmatica
  - Animazione visiva: cerchio che si espande/contrae al ritmo
- **Body Scan guidato**: sequenza audio (TTS Eve) che guida attraverso le sensazioni corporee
- **Suoni ambiente**: pioggia, foresta, onde, silenzio — loop sottili durante la pratica
- **Post-sessione**: mini-journaling opzionale: *"Come ti senti dopo la pratica?"*
- **Streak meditazione**: integrato nel Habit Tracker (auto-completamento)
- **Statistiche**: minuti totali, sessioni settimanali, streak

**Integrazione AI**:
- Eve suggerisce tipo di meditazione basato su mood attuale: *"Sembra una giornata tesa. Ti va un esercizio di respirazione 4-7-8?"*
- Post-meditazione, Eve chiede: *"Come e' andata? Vuoi scriverne?"*

**Complessita'**: ALTA (nuovo modulo, audio, animazioni, timer)

**Deliverable**:
- [ ] `features/meditation/` module (Contract, ViewModel, Screen)
- [ ] `MeditationSession` model (tipo, durata, completata)
- [ ] `MeditationRepository` + Firestore
- [ ] Animazioni respirazione (Compose Canvas)
- [ ] Timer con notifica campana
- [ ] Suoni ambiente (asset audio in resources)
- [ ] Integrazione Habit Tracker (auto-log)
- [ ] Integrazione Context Engine Eve

---

### 2.2 Brain Dump / Scarico Mentale
**Concetti coperti**: Scarico mentale, RAM cognitiva, svuotare il buffer

**Feature**: Modalita' "Scarica Tutto" nel Write module

- **Quick Dump**: pulsante rapido "Scarica tutto" in Home — apre editor minimale
  - Nessun titolo obbligatorio
  - Nessun mood obbligatorio
  - Solo testo libero, flusso di coscienza
  - Timer opzionale (5, 10, 15 minuti)
  - *"Non analizzare, non correggere. Solo scrivi."*
- **Categorizzazione post-dump**: dopo il salvataggio, AI categorizza automaticamente
  - Preoccupazioni / Compiti / Idee / Emozioni / Riflessioni
  - L'utente vede: *"Hai scritto 3 preoccupazioni, 2 compiti e 1 idea. Vuoi trasformare i compiti in azioni?"*
- **Compiti → Todo**: opzione di estrarre azioni concrete dal dump

**Complessita'**: BASSA (estensione Write module, nuovo entry point)

**Deliverable**:
- [ ] Quick Dump mode nel WriteViewModel (flag `isBrainDump`)
- [ ] UI minimale senza wizard/mood/titolo obbligatorio
- [ ] Pulsante "Scarica tutto" in Home quick actions
- [ ] AI categorizzazione post-dump (Gemini)
- [ ] Opzione estrazione compiti

---

### 2.3 Reframing Cognitivo (CBT Lite)
**Concetti coperti**: Osserva non identificarti, Metti il pensiero in discussione, Scegli alternativa credibile

**Feature**: "Laboratorio dei Pensieri" — sezione nel percorso o chat guidata

**Flusso guidato in 3 step:**

1. **Cattura il pensiero**
   - *"Scrivi il pensiero che ti blocca, esattamente come lo senti."*
   - Es: "Non sono capace di fare nulla bene"

2. **Osserva e metti in discussione**
   - AI guida con domande Socratiche:
     - *"E' davvero vero? Sempre, in ogni situazione?"*
     - *"Quali prove hai a favore? E contro?"*
     - *"Come lo direbbe un amico che ti vuole bene?"*
     - *"Tra un anno, quanto pesera' questo pensiero?"*

3. **Riscrivi in modo credibile**
   - Non positivita' tossica ("sono perfetto"), ma alternativa onesta
   - *"Non 'sono capace di tutto', ma 'sto imparando e ho gia' fatto progressi in [X]'"*
   - AI suggerisce riformulazione basata sui diari precedenti (prove concrete)

**Storico reframing**: l'utente puo' rileggere i suoi reframing passati — vede l'evoluzione dei pensieri nel tempo

**Integrazione**:
- Smart Capture rileva pensieri negativi ricorrenti nel diario → suggerisce reframing
- Eve puo' iniziare un reframing in chat: *"Hai scritto 'non ce la faccio' tre volte questa settimana. Ti va di guardarlo insieme?"*

**Complessita'**: MEDIA (estensione chat + nuovo flusso guidato, no nuovo modulo pesante)

**Deliverable**:
- [ ] `ThoughtReframe` model (pensiero originale, domande, riformulazione, data)
- [ ] Flusso guidato 3 step (screen dedicata o chat guidata Eve)
- [ ] Storico reframing consultabile
- [ ] Smart Capture: rilevamento pattern negativi → suggerimento reframing
- [ ] Domande Socratiche template

---

### 2.4 Pratica della Gratitudine
**Concetti coperti**: 3 momenti di gratitudine al giorno, ancorare il cervello al positivo

**Feature**: "3 Cose Belle" — rituale quotidiano

- **Prompt serale**: notifica gentile: *"Prima di dormire — 3 cose belle di oggi?"*
- **UI minimale**: 3 campi di testo, un campo per riga, nessuna pressione
  - Possibilita' di aggiungere foto
  - Possibilita' di taggare categoria (persone, natura, lavoro, se stessi, altro)
- **Calendario gratitudine**: vista mensile con giorni compilati
- **AI Insight mensile**: *"Questo mese hai notato la bellezza nella natura 12 volte e nelle relazioni 8 volte. Cosa ti dice?"*
- **Gratitudine nella community**: opzione di condividere (anonimo o firmato)

**Integrazione**:
- Campo `gratitude` gia' presente in WellbeingSnapshot (da collegare)
- Eve menziona le gratitudini: *"Ieri eri grato per la passeggiata al parco. Ti sei concesso un momento simile oggi?"*
- Home: "Le tue 3 cose belle di ieri" come card opzionale

**Complessita'**: BASSA-MEDIA (modello semplice, UI leggera, integrazione esistente)

**Deliverable**:
- [ ] `GratitudeEntry` model (3 items, data, categoria, foto)
- [ ] `GratitudeRepository` + Firestore
- [ ] UI card "3 Cose Belle" (accessibile da Home o percorso)
- [ ] Calendario gratitudine mensile
- [ ] Notifica serale (reminder frequency da AI Preferences)
- [ ] Insight AI mensile

---

### 2.5 Gestione Stimoli & Sonno
**Concetti coperti**: Meno stimoli, Sonno e recupero, 7+ ore, prefrontale

**Feature**: Estensione HealthInfo Settings + tracking attivo

- **Sleep Log**: registrazione orario letto/sveglia, qualita' percepita (1-5), fattori disturbanti
  - Trend settimanale: consistenza orario, media ore, qualita'
  - Correlazione con mood del giorno dopo (AI)
- **Screen Time Awareness**: auto-report serale (quanto tempo su schermi oggi?)
  - Non tracking automatico (privacy), ma consapevolezza
- **Pre-Sleep Routine Check**: *"Hai evitato schermi nell'ultima ora? Hai letto? Camera fresca?"*

**Complessita'**: MEDIA (estensione modelli esistenti + nuova UI tracking)

**Deliverable**:
- [ ] `SleepLog` model (bedtime, waketime, quality, disturbances)
- [ ] Tracking giornaliero sonno
- [ ] Trend settimanale sonno
- [ ] Correlazione sonno→mood (AI insight)
- [ ] Pre-sleep checklist

---

## FASE 3: "IL CORPO" — Energia Fisica & Salute
*L'energia fisica non si crea — si gestisce.*

### 3.1 Energy Tracker
**Concetti coperti**: Sonno, Idratazione, Movimento, Nutrizione stabile, Ladri invisibili

**Feature**: "Come Sta il Tuo Corpo?" — check-in fisico giornaliero

**Quick Check-in (30 secondi)**:
- Energia percepita (1-10 slider)
- Ore di sonno scorsa notte (slider o input)
- Bicchieri d'acqua oggi (counter +/-)
- Movimento fatto? (Si/No + tipo: camminata, palestra, sport, stretching)
- Pasto regolari? (Si/No)

**Dashboard Energia**:
- Trend settimana: energia, sonno, idratazione, movimento
- **Correlazioni AI**: *"Nei giorni in cui dormi 7+ ore e cammini, la tua energia e' in media 7.2. Quando dormi meno di 6 ore, scende a 4.1."*
- **Pattern**: *"Il martedi' e' il tuo giorno piu' basso — coincide con le riunioni lunghe."*
- **Ladri di energia**: AI identifica pattern negativi dai diari (*"Lo stress da email sembra prosciugarti piu' di qualsiasi altra cosa"*)

**Integrazione**:
- Write module: Smart Capture rileva menzioni di stanchezza/energia
- Eve: *"Oggi hai bevuto solo 3 bicchieri d'acqua e sei a energia 4. Coincidenza?"*
- WellbeingSnapshot: i dati fisici alimentano il calcolo overall score

**Complessita'**: MEDIA (nuovi modelli, nuova UI, integrazione AI)

**Deliverable**:
- [ ] `EnergyCheckIn` model (energia, sonno, acqua, movimento, pasti)
- [ ] `EnergyRepository` + Firestore
- [ ] Quick check-in UI (card in Home o percorso)
- [ ] Dashboard energia settimanale
- [ ] Correlazioni AI (Gemini)
- [ ] Integrazione Context Engine

---

### 3.2 Movimento & Corpo
**Concetti coperti**: Movimento regolare, 20-30 minuti, il corpo genera energia muovendosi, Blocco creativo → movimento

**Feature**: Integrazione movimento nel flusso quotidiano

- **Movement Prompts**: quando Eve rileva stress o blocco nel diario:
  *"Quando la mente si arena, il corpo e' la via d'uscita. Una camminata di 20 minuti?"*
- **Movement Log**: semplice (non un fitness tracker)
  - Tipo: camminata, corsa, yoga, stretching, palestra, sport, altro
  - Durata: minuti
  - Come ti senti dopo? (meglio/uguale/peggio)
- **Streak movimento**: integrato nel Habit Tracker
- **Correlazione**: *"Nelle settimane in cui ti muovi 4+ giorni, il tuo mood medio e' 7.1 vs 5.3"*

**Complessita'**: BASSA (estensione Energy Tracker, integrazione Habits)

**Deliverable**:
- [ ] `MovementLog` model (tipo, durata, sensazione post)
- [ ] Movement prompts nell'AI
- [ ] Correlazione movimento→mood
- [ ] Integrazione Habit Tracker

---

### 3.3 Salute come Moltiplicatore — Dashboard Integrata
**Concetti coperti**: La salute amplifica mente/relazioni/lavoro/spirito, moltiplicatore

**Feature**: "Il Tuo Terreno" — dashboard olistica nel Percorso

**Vista unificata**:
```
┌─────────────────────────────┐
│  IL TUO TERRENO             │
├──────────┬──────────────────┤
│  Sonno   │ ████████░░ 7.2h  │  ← trend settimanale
│  Energia │ ██████░░░░ 6.1   │
│  Acqua   │ ███████░░░ 1.8L  │
│  Moto    │ █████░░░░░ 4/7   │
├──────────┴──────────────────┤
│  "Questa settimana il tuo   │
│   terreno e' BUONO. Sonno   │
│   stabile, movimento in     │
│   crescita. L'idratazione   │
│   e' il tuo punto debole."  │  ← AI narrative
├─────────────────────────────┤
│  Impatto sulla mente:       │
│  Nei giorni con terreno     │
│  forte, mood +2.1 punti     │  ← correlazione automatica
└─────────────────────────────┘
```

**Complessita'**: MEDIA (aggregazione dati + UI + AI insight)

**Deliverable**:
- [ ] Dashboard "Il Tuo Terreno" nel Percorso
- [ ] Aggregazione metriche settimanali
- [ ] AI narrative insight (Gemini)
- [ ] Correlazione corpo→mente automatica

---

## FASE 4: "LO SPECCHIO" — Pensiero & Blocchi Mentali
*I blocchi mentali non sono muri — sono segnali.*

### 4.1 Identificazione Blocchi
**Concetti coperti**: Paura del fallimento, Sovraccarico, Credenze limitanti, Blocco creativo, Il blocco e' un messaggio

**Feature**: "Cosa Ti Blocca?" — flusso guidato Eve

**Quando si attiva**:
- L'utente scrive nel diario parole-chiave di blocco ("non riesco", "non ce la faccio", "sono bloccato", "non so cosa fare")
- L'utente lo chiede direttamente a Eve
- Pulsante nel Percorso: "Sto affrontando un blocco"

**Flusso guidato**:

1. **Identificazione**: *"Descrivimi cosa sta succedendo. Non devi analizzare, solo raccontare."*

2. **Diagnosi gentile** — Eve identifica il tipo:
   - **Paura del fallimento** → *"Sembra che il perfezionismo stia parlando. Qual e' la cosa piu' piccola che puoi fare ORA, anche imperfettamente?"*
   - **Sovraccarico** → *"Hai troppo in testa. Facciamo un brain dump: scrivi TUTTO quello che ti gira in mente."* → attiva Brain Dump (2.2)
   - **Credenza limitante** → *"'Non sono capace' e' una storia, non un fatto. Quando e' stata l'ultima volta che hai fatto qualcosa che pensavi di non saper fare?"* → attiva Reframing (2.3)
   - **Blocco creativo** → *"Quando la mente si arena, muovi il corpo. 20 minuti di camminata e poi ne riparliamo."* → suggerisce Movement (3.2)

3. **Follow-up**: il giorno dopo, Eve chiede: *"Come e' andata con quel blocco di ieri?"*

**Storico blocchi**: l'utente puo' rivedere blocchi passati e come li ha superati → costruisce la narrazione *"ce l'ho fatta prima, ce la faccio ancora"*

**Complessita'**: MEDIA (estensione chat AI + pattern detection nel Smart Capture)

**Deliverable**:
- [ ] Pattern detection blocchi nel TextAnalyzer
- [ ] Flusso guidato Eve per tipo di blocco
- [ ] Routing a Brain Dump / Reframing / Movement
- [ ] Storico blocchi superati
- [ ] Follow-up automatico giorno dopo

---

### 4.2 Pensieri Ricorrenti Tracker
**Concetti coperti**: Ogni pensiero ripetuto rafforza un circuito neurale, 60-90 giorni

**Feature**: "I Tuoi Circuiti" — mappa dei pensieri ricorrenti

- **Rilevamento automatico**: Smart Capture identifica temi ricorrenti nei diari
  - *"Hai menzionato 'non essere abbastanza' 7 volte in 3 settimane."*
- **Mappa visiva**: i pensieri piu' frequenti come bolle (piu' grande = piu' frequente)
  - Colore: rosso (limitante) / verde (potenziante) / neutro
- **Trasformazione**: tap su un pensiero limitante → avvia Reframing (2.3)
- **Tracker 90 giorni**: dopo un reframing, traccia per 90 giorni se il vecchio pensiero torna
  - *"'Non sono capace' e' comparso 7 volte il primo mese, 3 il secondo, 0 il terzo. Il circuito si sta indebolendo."*

**Complessita'**: MEDIA-ALTA (NLP analysis, visualizzazione, tracking longitudinale)

**Deliverable**:
- [ ] Rilevamento temi ricorrenti (TextAnalyzer enhanced)
- [ ] `RecurringThought` model + Firestore
- [ ] Mappa visiva pensieri (Compose Canvas)
- [ ] Tracker 90 giorni post-reframing
- [ ] Integrazione con Reframing (2.3)

---

## FASE 5: "LA BUSSOLA" — Valori & Scopo
*I valori fondamentali sono la bussola interna — non cio' che pensi di dover essere, ma cio' che sei quando nessuno guarda.*

### 5.1 Scoperta dei Valori
**Concetti coperti**: Momenti in cui ti sei sentito vivo, Cosa ti indigna, Domanda del letto di morte, Test di coerenza

**Feature**: "La Tua Bussola" — percorso guidato (una tantum + revisione periodica)

**Percorso in 4 step** (uno per sessione, non tutti insieme):

1. **I Momenti Vivi**
   - *"Pensa a 3 momenti della tua vita in cui ti sei sentito profondamente vivo. Non di successo — di autenticita'. Cosa stavi facendo? Con chi? Cosa c'era di speciale?"*
   - Journaling guidato con prompt specifici
   - AI estrae temi comuni: *"In tutti e 3 i momenti c'era liberta' e creativita'. Questo ti dice qualcosa?"*

2. **Cosa Ti Indigna**
   - *"Cosa ti fa arrabbiare quando lo vedi? L'ingiustizia? La superficialita'? La disonesta'?"*
   - Le violazioni rivelano i valori: indignazione per l'ingiustizia → giustizia come valore

3. **La Domanda Finale**
   - *"Immagina di essere alla fine della tua vita. Cosa ti dispiaceresti aver trascurato? Non chi non hai impressionato — chi non hai amato, cosa non hai creato, chi non sei diventato."*
   - Journaling profondo, Eve guida senza giudicare

4. **I Tuoi 3-5 Valori**
   - AI sintetizza: *"Dai tuoi racconti emergono questi valori: Liberta', Autenticita', Creativita', Connessione. Ti ci riconosci?"*
   - L'utente conferma/modifica
   - **Test di coerenza**: *"Il tuo valore e' Liberta'. Quanto tempo questa settimana hai dedicato a cose che ti fanno sentire libero?"*
   - Revisione consigliata ogni 6 mesi: *"Sei la stessa persona di 6 mesi fa? I tuoi valori sono ancora questi?"*

**Complessita'**: MEDIA (journaling guidato + AI synthesis, no infrastruttura pesante)

**Deliverable**:
- [ ] `ValuesDiscovery` model (step completati, valori identificati, data revisione)
- [ ] 4 screen di percorso guidato con prompt
- [ ] AI synthesis dei valori (Gemini)
- [ ] Test di coerenza settimanale (card nel Percorso)
- [ ] Reminder revisione 6 mesi
- [ ] Valori visibili nel profilo e usati da Eve nel context

---

### 5.2 Ikigai Explorer
**Concetti coperti**: Cosa ami, In cosa sei bravo, Cosa serve al mondo, Cosa ti pagherebbero

**Feature**: "Il Tuo Ikigai" — esplorazione guidata

**4 Cerchi interattivi**:
- **Passione**: *"Cosa fai quando il tempo scompare?"*
- **Talento**: *"In cosa sei naturalmente bravo? Cosa gli altri ti riconoscono?"*
- **Missione**: *"Di cosa ha bisogno il mondo intorno a te?"*
- **Professione**: *"Per cosa le persone ti pagherebbero?"*

- Ogni cerchio: 3-5 risposte libere
- **Visualizzazione Venn**: i 4 cerchi che si sovrappongono — le intersezioni mostrano dove sta emergendo il tuo ikigai
- **AI Insight**: *"L'intersezione tra il tuo talento (comunicare) e la tua passione (aiutare gli altri) potrebbe indicare un ruolo di coaching o mentoring. Ci hai mai pensato?"*
- **Non e' un risultato — e' una direzione**: Eve lo ricorda: *"L'ikigai si trova agendo, non pensandoci. Qual e' il prossimo esperimento che puoi fare?"*

**Complessita'**: MEDIA (UI Venn diagram, journaling guidato, AI)

**Deliverable**:
- [ ] `IkigaiExploration` model (4 cerchi con risposte)
- [ ] UI interattiva 4 cerchi (Compose Canvas)
- [ ] AI insight sulle intersezioni (Gemini)
- [ ] Collegamento con Valori (5.1)

---

## FASE 6: "IL SACRO" — Connessione & Trascendenza
*La connessione emerge quando il se' smette di essere il centro.*

### 6.1 Awe & Natura
**Concetti coperti**: Esperienza dell'awe, riduce l'ego, amplia la prospettiva

**Feature**: "Momenti di Meraviglia" — prompt + log

- **Prompt settimanale**: *"Questa settimana, hai avuto un momento di meraviglia? Un tramonto, un cielo stellato, qualcosa di bello che ti ha fermato?"*
- **Awe Journal**: entry speciale con foto + breve descrizione
- **Challenge settimanale**: *"Questa settimana prova a passare 20 minuti nella natura senza telefono. Poi raccontami."*
- **Integrazione**: Eve cita i momenti di meraviglia nei riflessi settimanali

**Complessita'**: BASSA (estensione journaling + prompt)

**Deliverable**:
- [ ] `AweEntry` model (descrizione, foto, contesto)
- [ ] Prompt settimanale awe
- [ ] Challenge settimanali natura
- [ ] Integrazione Weekly Reflection

---

### 6.2 Silenzio Profondo
**Concetti coperti**: Non assenza di rumore ma presenza consapevole, spazio pieno

**Feature**: Estensione Meditazione (2.1) — "Pratica del Silenzio"

- **Silenzio guidato**: timer senza istruzioni, solo campana inizio/fine
  - 10, 20, 30 minuti
  - Opzione: suono natura sottile o silenzio totale
- **Ritiro digitale**: modalita' "Giornata di Silenzio" — l'app si disattiva tranne il timer
- **Post-silenzio**: *"Cosa e' emerso nel silenzio?"* — journaling brevissimo

**Complessita'**: BASSA (estensione modulo meditazione)

**Deliverable**:
- [ ] Modalita' silenzio nel modulo meditazione
- [ ] Timer senza istruzioni
- [ ] Journaling post-silenzio

---

### 6.3 Relazioni Profonde & Servizio
**Concetti coperti**: Amore, intimita', uscire dal se', servizio autentico

**Feature**: "Le Tue Connessioni" — sezione nel percorso

- **Gratitudine relazionale**: *"A chi sei grato oggi? Glielo hai detto?"*
- **Atti di servizio log**: *"Cosa hai fatto per qualcun altro oggi senza che te lo chiedesse?"*
- **Quality time tracker**: *"Con chi hai passato tempo di qualita' questa settimana?"*
- **Relationship reflection mensile**: *"Le relazioni che ti nutrono e quelle che ti prosciugano"*
- **Integrazione community**: i post nella community come forma di connessione e servizio

**Complessita'**: BASSA-MEDIA (modelli semplici, journaling guidato)

**Deliverable**:
- [ ] Gratitudine relazionale (estensione 2.4)
- [ ] Atti di servizio log
- [ ] Reflection mensile relazioni (AI)
- [ ] Collegamento con community

---

### 6.4 Arte, Bellezza & Pratica Spirituale
**Concetti coperti**: Musica che da' brividi, arte come portale, pratica spirituale, ripetizione e presenza

**Feature**: Integrazione leggera nel flusso quotidiano

- **Prompt ispirazionali**: citazioni, poesie, domande profonde — una al giorno nella Home
  - Curate, non random — selezionate da tradizioni contemplative, filosofia, letteratura
- **Diario della bellezza**: variante del journaling: *"Cosa di bello hai notato oggi?"*
- **Pratica personale**: sezione nelle Settings dove l'utente definisce la sua pratica spirituale
  - Preghiera, meditazione, yoga, lettura sacra, passeggiata contemplativa, altro
  - Integrata nel Habit Tracker come abitudine tracciabile
- **Arte come riflessione**: possibilita' di allegare musica/arte al diario (link Spotify, foto opera)

**Complessita'**: BASSA (prompt + estensioni esistenti)

**Deliverable**:
- [ ] Citazione ispirazionale giornaliera in Home
- [ ] Pratica spirituale nelle Settings + Habit Tracker
- [ ] Possibilita' link multimediali nel diario

---

## INTEGRAZIONE TRASVERSALE: EVE COME COACH OLISTICO

### Eve 2.0 — System Prompt Upgrade

Eve deve evolvere da "AI che ti ascolta" a **"coach che vede il quadro completo"**.

**Context Engine espanso**:
```
Contesto attuale di [Nome]:

MENTE:
- Ultimi 5 diari: [riassunti]
- Pensieri ricorrenti: ["non essere abbastanza" x7 in 3 settimane]
- Ultimo reframing: [3 giorni fa, "non sono capace" → "sto imparando"]
- Meditazione: [4 sessioni questa settimana, streak 12 giorni]

CORPO:
- Sonno medio: [6.8 ore, in calo da 7.2]
- Energia media: [5.4, in calo]
- Movimento: [3/7 giorni]
- Idratazione: [sotto media]

SPIRITO:
- Valori: [Liberta', Autenticita', Creativita']
- Ultimo momento awe: [tramonto al parco, 5 giorni fa]
- Gratitudine: [compilata 5/7 giorni]
- Coerenza valori: [Liberta' al 60%, Creativita' al 80%]

ABITUDINI:
- Streak attive: meditazione (12), journaling (28), camminata (5)
- Abitudine in difficolta': lettura serale (saltata 3 giorni)

PATTERN AI:
- Stress alto il lunedi' (4 settimane consecutive)
- Mood migliora dopo movimento
- Sonno peggiora quando scrive di lavoro la sera
```

**Comportamento Eve**:
- Connette i puntini: *"Il tuo sonno e' calato insieme all'energia. E noto che hai scritto di lavoro le ultime 3 sere. C'e' un collegamento?"*
- Suggerisce azioni concrete: *"Domani prova a scrivere il diario PRIMA di cena, non a letto. E i 20 minuti di camminata che ti fanno cosi' bene?"*
- Non e' mai prescrittiva: *"Sono osservazioni, non ordini. Tu conosci te stesso meglio di me."*
- Anti-positivita' tossica: *"Non ti dico che va tutto bene. Ti dico che stai facendo cose concrete per stare meglio, e i dati lo confermano."*

---

## NAVIGAZIONE AGGIORNATA

### Bottom Nav (proposta)
```
[Home]  [Scrivi]  [Eve]  [Percorso]
```

### "Il Mio Percorso" — Hub Crescita
```
┌─────────────────────────────┐
│  IL MIO PERCORSO            │
├─────────────────────────────┤
│                             │
│  ┌─── MENTE ────┐          │
│  │ Meditazione   │ 12gg    │
│  │ Reframing     │ 3 fatti │
│  │ Gratitudine   │ 5/7     │
│  └───────────────┘          │
│                             │
│  ┌─── CORPO ────┐          │
│  │ Il Tuo Terreno│ Buono   │
│  │ Sonno         │ 7.1h    │
│  │ Movimento     │ 4/7     │
│  └───────────────┘          │
│                             │
│  ┌── SPIRITO ───┐          │
│  │ I Tuoi Valori │ ✓       │
│  │ Ikigai        │ In corso│
│  │ Connessioni   │ 3 atti  │
│  └───────────────┘          │
│                             │
│  ┌── ABITUDINI ─┐          │
│  │ ███████░░░░░  │ 71%     │
│  │ Streak record │ 28gg    │
│  └───────────────┘          │
│                             │
│  [Weekly Reflection AI]     │
│  [Insight Settimanale]      │
└─────────────────────────────┘
```

---

## ROADMAP IMPLEMENTAZIONE

### Sprint 1 — Fondamenta (2-3 settimane)
| # | Task | Complessita' | Dipendenze |
|---|------|-------------|------------|
| 1.1 | Habit Tracker System | ALTA | Nessuna |
| 2.2 | Brain Dump mode | BASSA | Write module |
| 2.4 | Pratica Gratitudine | BASSA-MEDIA | Nessuna |
| 3.1 | Energy Tracker (quick check-in) | MEDIA | Nessuna |

### Sprint 2 — Mente (2-3 settimane)
| # | Task | Complessita' | Dipendenze |
|---|------|-------------|------------|
| 2.1 | Meditazione & Respirazione | ALTA | Nessuna |
| 2.3 | Reframing Cognitivo | MEDIA | Smart Capture |
| 2.5 | Sleep Log & Gestione Stimoli | MEDIA | Energy Tracker |
| 4.1 | Identificazione Blocchi | MEDIA | Reframing, Brain Dump |

### Sprint 3 — Corpo & Integrazioni (2 settimane)
| # | Task | Complessita' | Dipendenze |
|---|------|-------------|------------|
| 3.2 | Movimento & Corpo | BASSA | Habit Tracker, Energy |
| 3.3 | Dashboard "Il Tuo Terreno" | MEDIA | Energy, Sleep, Movement |
| 4.2 | Pensieri Ricorrenti Tracker | MEDIA-ALTA | TextAnalyzer, Reframing |
| 1.2 | Environment Design Tools | MEDIA | Settings |

### Sprint 4 — Spirito & Profondita' (2-3 settimane)
| # | Task | Complessita' | Dipendenze |
|---|------|-------------|------------|
| 5.1 | Scoperta dei Valori | MEDIA | AI (Gemini) |
| 5.2 | Ikigai Explorer | MEDIA | Valori |
| 6.1 | Awe & Natura | BASSA | Journaling |
| 6.2 | Silenzio Profondo | BASSA | Meditazione |

### Sprint 5 — Connessioni & Polish (2 settimane)
| # | Task | Complessita' | Dipendenze |
|---|------|-------------|------------|
| 6.3 | Relazioni & Servizio | BASSA-MEDIA | Gratitudine |
| 6.4 | Arte, Bellezza & Pratica | BASSA | Settings, Habits |
| — | Eve 2.0 Context Engine | ALTA | Tutti i moduli |
| — | "Il Mio Percorso" Hub | MEDIA | Tutti i moduli |
| — | Navigazione aggiornata | MEDIA | Percorso Hub |

---

## PRINCIPI GUIDA DELL'IMPLEMENTAZIONE

1. **Mai clinico, sempre umano.** Le label sono "Come stai?" non "Valutazione psicometrica". "Il Tuo Terreno" non "Health Dashboard".

2. **Opt-in, mai obbligatorio.** Ogni feature e' disponibile, nessuna e' forzata. L'utente sceglie il suo percorso.

3. **AI come specchio, non come guru.** Eve osserva, connette, suggerisce — non prescrive. Anti-positivita' tossica.

4. **Dati al servizio della comprensione.** I numeri servono solo quando generano insight. Un grafico senza significato e' rumore.

5. **Piccolo e frequente > grande e raro.** 30 secondi di check-in ogni giorno > 30 minuti di assessment una volta al mese.

6. **Il corpo viene prima.** Se il terreno fisico e' fragile, Eve lo dice. Non si medita la stanchezza via.

7. **I valori guidano le abitudini.** Le abitudini non sono fine a se stesse — sono al servizio dei valori. "Medito perche' la chiarezza e' un mio valore", non "medito perche' devo".

8. **La crescita non e' lineare.** L'app celebra i progressi E normalizza le ricadute. "Un giorno puo' capitare. L'importante e' non saltarne due."

---

## METRICHE DI SUCCESSO

| Metrica | Target | Come si misura |
|---------|--------|---------------|
| Retention D7 | >40% | Analytics |
| Retention D30 | >20% | Analytics |
| Sessioni/settimana | >4 | Analytics |
| Abitudini attive per utente | >3 | Firestore |
| Streak media attiva | >7 giorni | Firestore |
| Completamento check-in energia | >50% giorni | Firestore |
| Gratitudine compilata | >3/7 giorni | Firestore |
| Meditazione settimanale | >2 sessioni | Firestore |
| NPS | >50 | In-app survey |
| Conversione Free→Pro | >5% | Play Billing |

---

*"Sir, il piano e' completo. 6 fasi, 18 deliverable, un sistema che copre mente, corpo e spirito. Non e' solo un'app — e' un compagno di crescita. Quando vuole iniziare, sono pronto."*
