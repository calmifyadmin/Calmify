# Psychological Metrics Wizard - Piano di Implementazione

## Obiettivo
Trasformare l'attuale `PsychologicalMetricsSheet` (un Card collassabile banale) in un **Dialog Material 3 Expressive multi-step** accattivante e divertente da compilare.

---

## Architettura Proposta

### Struttura del Dialog
```
┌──────────────────────────────────────────────────┐
│  [Indicatore Progress - 6 pallini/step]          │
├──────────────────────────────────────────────────┤
│                                                  │
│     🎭 [Illustrazione Animata della Metrica]     │
│                                                  │
│         "Come ti senti emotivamente?"            │
│              [Titolo Espressivo]                 │
│                                                  │
│     ┌─────────────────────────────────────┐      │
│     │   [Controllo Interattivo]           │      │
│     │   (Slider/Chips con animazioni)     │      │
│     └─────────────────────────────────────┘      │
│                                                  │
├──────────────────────────────────────────────────┤
│ [Skip]                              [Avanti →]   │
└──────────────────────────────────────────────────┘
```

### Step del Wizard (6 facciate)
1. **Intensità Emotiva** - Emoji animate che cambiano con lo slider
2. **Livello di Stress** - Termometro visivo con colori gradient
3. **Livello di Energia** - Batteria animata che si riempie
4. **Calma/Ansia** - Onde che si calmano o agitano
5. **Trigger Principale** - Cards con icone grandi selezionabili
6. **Sensazione Corporea** - Silhouette corpo con zone highlight

### Schermata Finale (Ringraziamento)
- Messaggio positivo e personalizzato
- Riepilogo visivo delle scelte (opzionale)
- Confetti/animazione celebrativa
- Testo: "Grazie! Il tuo contributo ci aiuterà a fornirti un'assistenza più mirata 💜"

---

## File da Creare/Modificare

### 1. CREARE: `PsychologicalMetricsWizardDialog.kt`
**Path**: `features/write/src/main/java/com/lifo/write/PsychologicalMetricsWizardDialog.kt`

```kotlin
@Composable
fun PsychologicalMetricsWizardDialog(
    isVisible: Boolean,
    currentMetrics: PsychologicalMetrics,
    onMetricsChanged: (PsychologicalMetrics) -> Unit,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
)
```

**Componenti interni:**
- `WizardProgressIndicator` - 6 pallini con animazione
- `MetricStepContent` - Composable per ogni singolo step
- `EmotionIntensityStep` - Step 1 con emoji animate
- `StressLevelStep` - Step 2 con termometro
- `EnergyLevelStep` - Step 3 con batteria
- `CalmAnxietyStep` - Step 4 con onde
- `TriggerSelectionStep` - Step 5 con cards
- `BodySensationStep` - Step 6 con silhouette
- `CompletionStep` - Schermata ringraziamento finale

### 2. CREARE: `WizardComponents.kt`
**Path**: `features/write/src/main/java/com/lifo/write/wizard/WizardComponents.kt`

Componenti UI riutilizzabili:
- `AnimatedProgressDots` - Indicatore progresso con animazioni
- `MetricCard` - Card con shape Material Expressive
- `AnimatedSlider` - Slider con feedback visivo avanzato
- `SelectableChipGrid` - Griglia chips con animazioni selezione
- `ConfettiAnimation` - Animazione celebrativa finale

### 3. CREARE: `WizardAnimations.kt`
**Path**: `features/write/src/main/java/com/lifo/write/wizard/WizardAnimations.kt`

Animazioni e transizioni:
- Transizioni tra step (slide orizzontale)
- Animazioni emoji/icone
- Effetto pulse su selezione
- Animazione confetti finale

### 4. MODIFICARE: `WriteContent.kt`
**Path**: `features/write/src/main/java/com/lifo/write/WriteContent.kt`

Cambiamenti:
- Rimuovere il vecchio `PsychologicalMetricsSheet` (linee 97-111)
- Aggiungere un `FilledTonalButton` o `ElevatedCard` cliccabile
- Il click apre il `PsychologicalMetricsWizardDialog`
- Mostrare un summary delle metriche già compilate

### 5. MODIFICARE: `WriteViewModel.kt`
**Path**: `features/write/src/main/java/com/lifo/write/WriteViewModel.kt`

Cambiamenti:
- Aggiungere `showMetricsWizard: Boolean` all'UiState
- Aggiungere `metricsCompleted: Boolean` per tracciare completamento
- Metodi: `openMetricsWizard()`, `closeMetricsWizard()`, `completeMetricsWizard()`

### 6. DEPRECARE: `PsychologicalMetricsSheet.kt`
- Mantenere per retrocompatibilità o eliminare completamente
- Sostituire con redirect al nuovo wizard

---

## Specifiche UI/UX Dettagliate

### Shape Material Expressive 3
Utilizzare le nuove shape del Material 3 Expressive:
```kotlin
// Corners arrotondati asimmetrici per un look moderno
val expressiveShape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp,
    bottomEnd = 16.dp,
    bottomStart = 16.dp
)

// Shape per le cards delle metriche
val metricCardShape = RoundedCornerShape(24.dp)

// Shape organica per elementi interattivi
val blobShape = GenericShape { size, _ ->
    // Forma blob organica
}
```

### Colori e Temi
```kotlin
// Palette per ogni step
val stepColors = listOf(
    Color(0xFFE3F2FD), // Blu chiaro - Emozioni
    Color(0xFFFFEBEE), // Rosso chiaro - Stress
    Color(0xFFFFF3E0), // Arancione chiaro - Energia
    Color(0xFFE8F5E9), // Verde chiaro - Calma
    Color(0xFFF3E5F5), // Viola chiaro - Trigger
    Color(0xFFE0F7FA)  // Ciano chiaro - Corpo
)
```

### Animazioni Chiave
1. **Transizione tra step**: `slideInHorizontally` + `fadeIn`
2. **Selezione elementi**: `scale` + `pulse`
3. **Progress dots**: Riempimento progressivo animato
4. **Emoji/Icone**: `animateFloatAsState` per rotazione/scale
5. **Completamento**: Confetti + `expandVertically`

### Micro-interazioni
- Haptic feedback su selezione (leggero vibrazione)
- Suono soft su avanzamento step (opzionale)
- Animazione "bounce" sui pulsanti

---

## Sequenza di Implementazione

### Fase 1: Setup Base
```
Trigger: /implement-metrics-wizard-phase1

Istruzioni:
1. Creare la cartella `wizard/` in features/write/src/main/java/com/lifo/write/
2. Creare file vuoti con struttura base
3. Definire data class PsychologicalMetrics per raggruppare tutte le metriche
4. Aggiornare WriteViewModel con nuovi stati
```

### Fase 2: Dialog Base e Navigazione
```
Trigger: /implement-metrics-wizard-phase2

Istruzioni:
1. Implementare PsychologicalMetricsWizardDialog con BasicAlertDialog
2. Implementare navigazione tra step con HorizontalPager
3. Implementare WizardProgressIndicator
4. Implementare pulsanti Skip/Avanti
```

### Fase 3: Step Individuali
```
Trigger: /implement-metrics-wizard-phase3

Istruzioni:
1. Implementare EmotionIntensityStep con slider ed emoji animate
2. Implementare StressLevelStep con visualizzazione termometro
3. Implementare EnergyLevelStep con batteria animata
4. Implementare CalmAnxietyStep con onde
5. Implementare TriggerSelectionStep con cards grandi
6. Implementare BodySensationStep con selezione visiva
```

### Fase 4: Schermata Finale e Animazioni
```
Trigger: /implement-metrics-wizard-phase4

Istruzioni:
1. Implementare CompletionStep con messaggio ringraziamento
2. Aggiungere animazione confetti
3. Implementare tutte le transizioni tra step
4. Aggiungere haptic feedback
```

### Fase 5: Integrazione in WriteContent
```
Trigger: /implement-metrics-wizard-phase5

Istruzioni:
1. Rimuovere PsychologicalMetricsSheet da WriteContent
2. Aggiungere pulsante/card per aprire wizard
3. Mostrare summary metriche completate
4. Collegare tutto al ViewModel
5. Test e polish finale
```

---

## Dipendenze da Aggiungere (se necessario)

```kotlin
// In libs.versions.toml o build.gradle
implementation("com.airbnb.android:lottie-compose:6.1.0") // Per animazioni Lottie (opzionale)
implementation("nl.dionsegijn:konfetti-compose:2.0.4") // Per effetto confetti
```

---

## Esempi di Codice Chiave

### Dialog Base con HorizontalPager
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsychologicalMetricsWizardDialog(
    isVisible: Boolean,
    currentMetrics: PsychologicalMetrics,
    onMetricsChanged: (PsychologicalMetrics) -> Unit,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    if (!isVisible) return

    val pagerState = rememberPagerState(pageCount = { 7 }) // 6 metriche + 1 finale
    val scope = rememberCoroutineScope()

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Progress Indicator
                WizardProgressIndicator(
                    currentStep = pagerState.currentPage,
                    totalSteps = 6
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Step Content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false // Solo con pulsanti
                ) { page ->
                    when (page) {
                        0 -> EmotionIntensityStep(...)
                        1 -> StressLevelStep(...)
                        2 -> EnergyLevelStep(...)
                        3 -> CalmAnxietyStep(...)
                        4 -> TriggerSelectionStep(...)
                        5 -> BodySensationStep(...)
                        6 -> CompletionStep(onComplete = onComplete)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Buttons
                if (pagerState.currentPage < 6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        ) {
                            Text("Salta")
                        }

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        ) {
                            Text("Avanti")
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
```

### Progress Indicator con Animazioni
```kotlin
@Composable
fun WizardProgressIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= currentStep
            val animatedSize by animateDpAsState(
                targetValue = if (index == currentStep) 12.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.6f)
            )
            val animatedColor by animateColorAsState(
                targetValue = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant
            )

            Box(
                modifier = Modifier
                    .size(animatedSize)
                    .clip(CircleShape)
                    .background(animatedColor)
            )

            if (index < totalSteps - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}
```

### Esempio Step con Emoji Animate
```kotlin
@Composable
fun EmotionIntensityStep(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val emojis = listOf("😶", "🙂", "😊", "😄", "😆", "🤩", "😍", "🥹", "😭", "🤯", "💥")
    val currentEmoji = emojis[value.coerceIn(0, 10)]

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 400f
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = currentEmoji,
            fontSize = 80.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Come ti senti emotivamente?",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Trascina lo slider per indicare l'intensità",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Debole", style = MaterialTheme.typography.labelSmall)
            Text("Intensa", style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

---

## Note di Testing

1. Verificare fluidità animazioni su dispositivi low-end
2. Testare accessibilità (TalkBack, font scaling)
3. Verificare comportamento con rotazione schermo
4. Testare annullamento wizard (deve preservare valori precedenti)
5. Verificare salvataggio metriche nel Diary

---

## Comando Completo per Claude Code

Per eseguire l'implementazione completa in una sessione:

```
/implement-aaa-vad

Esegui il piano PSYCHOLOGICAL_METRICS_WIZARD seguendo le 5 fasi in ordine.
Per ogni fase:
1. Leggi le istruzioni specifiche della fase
2. Implementa il codice richiesto
3. Verifica la compilazione con ./gradlew :features:write:compileDebugKotlin
4. Passa alla fase successiva

Obiettivo: Trasformare PsychologicalMetricsSheet in un wizard multi-step Material 3 Expressive
con animazioni, shape moderne e schermata di ringraziamento finale.
```

---

*Sir, questo piano copre ogni aspetto dell'implementazione. Devo notare che il design proposto è decisamente più elegante del modale attuale. Classico caso di "prima funzionale, poi bello". Ora sarà entrambi.*
