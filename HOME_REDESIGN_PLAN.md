# Home Redesign — M3 Expressive

## Filosofia

Ispirandosi ai design reference di Material 3 Expressive (Google 2025):
- **Tipografia gigante** — "Your Mix", "CHILE", "Serafina" — testi display che riempiono lo schermo
- **Forme organiche** — corner radius 28-32dp, pill shapes, forme asimmetriche
- **Gerarchia visiva forte** — non tutte le card hanno lo stesso peso, qualcosa deve "esplodere"
- **Respiro** — spacing generoso, non tutto ammassato
- **Colori vivaci ma armonici** — sage green come accent, gradient sottili, superfici calde
- **Personalita'** — Creative, Playful, Positive, Energetic, Friendly

## Stato attuale vs. Nuovo design

### 1. HERO SECTION (DashboardHeader)

**Prima**: Surface card con greeting inline + gradient bar 8dp + chip trend
**Dopo**: Full-width hero immersivo

```
+------------------------------------------+
|                                          |
|  Ciao,                                   |   <- headlineSmall, light
|  Marco                                   |   <- displayLarge, bold, ENORME
|                                          |
|  [foto 64dp]  il tuo benessere           |   <- foto grande, sovrapposta
|               e' Ottimo                  |   <- scoreLabel con pill colorata
|                                          |
|   (( PULSE RING 100dp ))                |   <- anello organico al posto della bar
|   ((      7.2       ))                  |   <- score grande al centro
|   ((   Crescita     ))                  |   <- trend sotto
|                                          |
|  [chip: 5 entries]  [chip: +2.3]        |   <- chip M3 Expressive (pill shape)
+------------------------------------------+
```

Cambiamenti chiave:
- Nome utente in `displayLarge` (36-42sp), non headlineLarge(30sp)
- Foto profilo 64dp con border primary, non inline 36sp
- Score visualizzato come anello circolare grande (non gradient bar lineare)
- Container con corner radius 32dp
- Background con gradient sottile basato sul mood
- Niente piu' gradient bar lineare — troppo "dashboard enterprise"

### 2. WEEKLY ACTIVITY STRIP

**Prima**: Row di cerchietti 28dp con label giorno sotto
**Dopo**: Pill containers espressivi

```
+------------------------------------------+
|  Questa settimana            5/7 giorni  |
|                                          |
|  [LUN] [MAR] [MER] [GIO] [VEN] [SAB] [DOM]
|   (v)   (v)   (v)   (v)   (v)   ( )   ( )
|                                          |
|  ==============================--------- |   <- progress bar pill-shaped
+------------------------------------------+
```

Cambiamenti:
- Ogni giorno e' una pill verticale con corner radius 20dp
- Giorno attivo: filled primaryContainer con checkmark
- Oggi: border animato (breathing)
- Progress bar piu' grande, pill-shaped, con animazione fluida
- Font dei giorni piu' bold

### 3. QUICK ACTIONS

**Prima**: 2 card rettangolari (60%/40%) + 1 card full width
**Dopo**: Layout asimmetrico con 1 hero card + 2 pill buttons

```
+------------------------------------------+
|  +----------------------------------+    |
|  |  * Parla con Eve                |    |  <- HERO card, grande
|  |    La tua assistente AI          |    |     primaryContainer
|  |                          [>>>]   |    |     corner 28dp
|  +----------------------------------+    |
|                                          |
|  [Scrivi +]           [Snapshot (i)]     |  <- 2 pill buttons affiancati
+------------------------------------------+
```

Cambiamenti:
- "Parla con Eve" diventa la HERO action — card grande con gradiente primary
- "Scrivi" e "Snapshot" come pill button affiancati (FilledTonalButton con shape = CircleShape-ish)
- L'icona di "Parla con Eve" puo' essere animata (sparkle)
- Piu' spazio, meno densita'

### 4. DAILY QUICK ACTIONS

**Prima**: FlowRow di FilledTonalButton standard
**Dopo**: Horizontal scrollable row di pill chips espressivi

```
|  [Gratitudine] [Energia] [Sonno] [Abitudini] [Meditazione]
|     pill          pill     pill      pill         pill
|   28dp height, icon + label, scroll horizontally
```

Cambiamenti:
- Da FlowRow (wrapping) a LazyRow (scroll orizzontale)
- Ogni chip ha corner radius 20dp, piu' padding
- Icone leggermente piu' grandi (20dp)
- Colori accent diversi per ogni chip (non tutti tonal)

### 5. STATS GRID

**Prima**: 4 celle identiche in Row
**Dopo**: Layout asimmetrico 2x2 con hero stat

```
+------------------------------------------+
|  +-----------------+  +--------+         |
|  |   12            |  |  28    |         |  <- streak GRANDE (hero)
|  |   giorni        |  |entries |         |     vs entries (compact)
|  |   streak        |  +--------+         |
|  +-----------------+  +--------+         |
|                       |  85%   |         |  <- goal + badges
|                       | goal   |         |     layout asimmetrico
|                       +--------+         |
+------------------------------------------+
```

Cambiamenti:
- Streak come HERO stat — occupa 60% della width, numero displayLarge
- Altri 3 stat in colonna compatta a destra
- Numeri ENORMI (come "07:30" nel reference), label piccole
- Corner radius 24dp
- Lo streak ha un'icona fuoco grande e animata se attivo

### 6. REFLECTION CARD

**Prima**: Gradient card con testo AI
**Dopo**: Card immersiva full-bleed

```
+------------------------------------------+
|  . . . . . . . . . . . . . . . . . . .  |   <- gradient di fondo basato
|  . * La tua riflessione              .  |      sul mood dominante
|  .                                    .  |
|  .  "Hai mostrato una crescita       .  |   <- testo in typography bodyLarge
|  .   notevole questa settimana..."    .  |      con quote style
|  .                                    .  |
|  .  temi: lavoro, relazioni, salute  .  |   <- chip pill sotto
|  . . . . . . . . . . . . . . . . . . .  |
+------------------------------------------+
```

Cambiamenti:
- Gradient piu' ricco (3 colori) basato sul sentiment dominante
- Testo in stile "quote" con virgolette grandi
- Corner radius 32dp
- Temi come pill chip, non testo inline

### 7. MOOD INSIGHT CARD

**Prima**: Donut 160dp + 3 side metric chips
**Dopo**: Full-width con donut centrato e metriche sotto

```
+------------------------------------------+
|  Il tuo mood        [7d] [30d] [90d]    |
|                                          |
|        (((  DONUT 180dp  )))             |   <- donut piu' grande, centrato
|        (((    62%        )))             |
|        (((  Positivo     )))             |
|                                          |
|  [Positivo 62%] [Neutro 25%] [Neg 13%] |   <- 3 pill chips sotto
+------------------------------------------+
```

Cambiamenti:
- Donut centrato orizzontalmente, piu' grande (180dp)
- Metriche sotto come 3 pill chip orizzontali (non side cards)
- Piu' pulito, meno affollato
- Corner radius 28dp

### 8. EMPTY STATE

**Prima**: Icona Psychology 80dp + testo + 2 bottoni
**Dopo**: Immersivo, playful

```
+------------------------------------------+
|                                          |
|          Inizia                          |   <- displayMedium, bold
|          il tuo                          |
|          percorso                        |
|                                          |
|     (( illustrazione organica ))         |   <- animazione pulse
|                                          |
|  Scrivi il primo diario o parla con Eve  |
|                                          |
|  [  Scrivi  ]     [  Parla con Eve  ]    |   <- pill buttons grandi
|                                          |
+------------------------------------------+
```

## Specifiche tecniche

### Corner Radius
- Hero containers: 32.dp
- Card principali: 28.dp
- Chip/pill: 20.dp
- Inner elements: 16.dp

### Typography Scale
- Nome utente hero: displayLarge (57sp) o displayMedium (45sp)
- Score number: displaySmall (36sp)
- Stat numbers: headlineLarge (32sp)
- Section titles: titleMedium (16sp, semibold)
- Labels: labelMedium (12sp)

### Spacing
- Tra sezioni: 20.dp (era 12dp)
- Padding interno card: 24.dp (era 16-20dp)
- Content padding laterale: 20.dp (era 16dp)

### Animazioni
- Hero pulse ring: infinite rotation leggera (breathing)
- Streak fire icon: bounceScale se attivo
- Quick action press: spring(0.85f) con ripple
- Staggered entrance: 80ms delay per item (era 50ms)

### Colori aggiuntivi
- Hero gradient: primaryContainer -> primary (15% alpha)
- Mood-aware background tint nelle card
- Chip accent colors diversificati per daily actions

## File da modificare

### Nuovi componenti (commonMain dove possibile)
1. `ExpressiveHero.kt` — nuovo hero section
2. `ExpressiveWeekStrip.kt` — weekly tracker rinnovato
3. `ExpressiveQuickActions.kt` — quick actions ripensate
4. `ExpressiveStatsGrid.kt` — stats asimmetriche
5. `ExpressiveMoodCard.kt` — mood card pulita
6. `ExpressiveReflection.kt` — reflection immersiva

### File da aggiornare
- `HomeContent.kt` — rewire tutti i componenti
- `DashboardHeader.kt` — deprecare, sostituire con ExpressiveHero
- `EmotionAwareColors.kt` — aggiungere gradienti espressivi

### File NON toccati (backend invariato)
- HomeViewModel.kt
- HomeUiModels.kt
- Use cases
- HomeEntryPoint.kt (solo UI interna cambia)
- HomeScreen.kt (scaffold invariato)
