package com.lifo.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel

// ── Question definitions ─────────────────────────────────────────────────────

private data class QuestionDef(
    val section: String,
    val label: String,
    val question: String,
    val minLabel: String,
    val maxLabel: String,
    val getValue: (com.lifo.util.model.WellbeingMetrics) -> Int,
    val setValue: (SnapshotViewModel, Int) -> Unit
)

private val questions = listOf(
    QuestionDef(
        section = "Domini di Vita",
        label = "Soddisfazione di Vita",
        question = "Quanto sei soddisfatto della tua vita in generale?",
        minLabel = "Insoddisfatto",
        maxLabel = "Molto soddisfatto",
        getValue = { it.lifeSatisfaction },
        setValue = { vm, v -> vm.setLifeSatisfaction(v) }
    ),
    QuestionDef(
        section = "Domini di Vita",
        label = "Lavoro / Studio",
        question = "Quanto sei soddisfatto del tuo lavoro o studio?",
        minLabel = "Insoddisfatto",
        maxLabel = "Molto soddisfatto",
        getValue = { it.workSatisfaction },
        setValue = { vm, v -> vm.setWorkSatisfaction(v) }
    ),
    QuestionDef(
        section = "Domini di Vita",
        label = "Relazioni",
        question = "Come valuti la qualita' delle tue relazioni?",
        minLabel = "Scarsa",
        maxLabel = "Eccellente",
        getValue = { it.relationshipsQuality },
        setValue = { vm, v -> vm.setRelationshipsQuality(v) }
    ),
    QuestionDef(
        section = "Salute Psicologica",
        label = "Mindfulness",
        question = "Quanto sei presente e consapevole nel momento?",
        minLabel = "Distratto",
        maxLabel = "Molto presente",
        getValue = { it.mindfulnessScore },
        setValue = { vm, v -> vm.setMindfulnessScore(v) }
    ),
    QuestionDef(
        section = "Salute Psicologica",
        label = "Senso di Scopo",
        question = "Quanto senti di avere uno scopo o direzione nella vita?",
        minLabel = "Confuso",
        maxLabel = "Molto chiaro",
        getValue = { it.purposeMeaning },
        setValue = { vm, v -> vm.setPurposeMeaning(v) }
    ),
    QuestionDef(
        section = "Salute Psicologica",
        label = "Gratitudine",
        question = "Quanto ti senti grato per quello che hai?",
        minLabel = "Poco",
        maxLabel = "Molto",
        getValue = { it.gratitude },
        setValue = { vm, v -> vm.setGratitude(v) }
    ),
    QuestionDef(
        section = "Autodeterminazione",
        label = "Autonomia",
        question = "Quanto senti di avere il controllo sulle tue scelte e sulla tua vita?",
        minLabel = "Poco controllo",
        maxLabel = "Pieno controllo",
        getValue = { it.autonomy },
        setValue = { vm, v -> vm.setAutonomy(v) }
    ),
    QuestionDef(
        section = "Autodeterminazione",
        label = "Competenza",
        question = "Quanto ti senti capace e competente in quello che fai?",
        minLabel = "Poco capace",
        maxLabel = "Molto capace",
        getValue = { it.competence },
        setValue = { vm, v -> vm.setCompetence(v) }
    ),
    QuestionDef(
        section = "Autodeterminazione",
        label = "Connessione",
        question = "Quanto ti senti connesso e vicino alle persone importanti per te?",
        minLabel = "Disconnesso",
        maxLabel = "Molto connesso",
        getValue = { it.relatedness },
        setValue = { vm, v -> vm.setRelatedness(v) }
    ),
    QuestionDef(
        section = "Indicatori",
        label = "Solitudine",
        question = "Quanto ti senti solo o sola in questo periodo?",
        minLabel = "Per niente",
        maxLabel = "Molto solo/a",
        getValue = { it.loneliness },
        setValue = { vm, v -> vm.setLoneliness(v) }
    )
)

private const val STEP_INTRO = -1
private const val STEP_NOTES = 10
private const val TOTAL_QUESTIONS = 10

/**
 * SnapshotScreen
 *
 * Weekly wellbeing questionnaire: Intro -> 10 individual question pages -> Notes -> Submit
 * Each question gets its own full-screen page with progress tracker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotScreen(
    onBackPressed: () -> Unit,
    onSnapshotComplete: () -> Unit,
    viewModel: SnapshotViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // -1 = intro, 0..9 = questions, 10 = notes/submit
    var currentStep by remember { mutableIntStateOf(STEP_INTRO) }
    var previousStep by remember { mutableIntStateOf(STEP_INTRO) }

    // Success dialog
    if (uiState.isSuccess) {
        AlertDialog(
            onDismissRequest = {
                viewModel.resetForm()
                onSnapshotComplete()
            },
            icon = { Icon(Icons.Default.Check, contentDescription = null) },
            title = { Text("Questionario Completato") },
            text = {
                Text(
                    "Grazie per aver dedicato del tempo a te stesso. " +
                        "Le tue risposte sono state salvate e verranno analizzate " +
                        "per offrirti insight personalizzati sul tuo benessere."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetForm()
                    onSnapshotComplete()
                }) {
                    Text("Perfetto")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (currentStep) {
                        STEP_INTRO -> Text("Check-in Settimanale")
                        STEP_NOTES -> Text("Ultime riflessioni")
                        else -> Text("Domanda ${currentStep + 1} di $TOTAL_QUESTIONS")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            currentStep == STEP_INTRO -> onBackPressed()
                            else -> {
                                previousStep = currentStep
                                currentStep--
                            }
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Progress tracker (visible only during questions and notes)
            if (currentStep >= 0) {
                ProgressTracker(
                    currentStep = currentStep,
                    totalSteps = TOTAL_QUESTIONS + 1, // 10 questions + notes
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            // Animated page content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val goingForward = targetState > previousStep
                    if (goingForward) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "snapshot_step"
            ) { step ->
                when (step) {
                    STEP_INTRO -> IntroductionPage(
                        modifier = Modifier.fillMaxSize()
                    )

                    STEP_NOTES -> NotesPage(
                        notes = viewModel.notes,
                        onNotesChange = { viewModel.updateNotes(it) },
                        isSubmitting = uiState.isSubmitting,
                        error = uiState.error,
                        modifier = Modifier.fillMaxSize()
                    )

                    else -> QuestionPage(
                        questionDef = questions[step],
                        value = questions[step].getValue(viewModel.metrics),
                        onValueChange = { questions[step].setValue(viewModel, it) },
                        questionIndex = step,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom navigation bar
            BottomNavBar(
                currentStep = currentStep,
                isSubmitting = uiState.isSubmitting,
                onNext = {
                    previousStep = currentStep
                    currentStep++
                },
                onSubmit = {
                    val userId = viewModel.getCurrentUserId()
                    viewModel.submitSnapshot(ownerId = userId)
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}

// ── Progress Tracker ─────────────────────────────────────────────────────────

@Composable
private fun ProgressTracker(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val progress = (currentStep + 1).toFloat() / totalSteps.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "progress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Step dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until totalSteps) {
                val isCompleted = i < currentStep
                val isCurrent = i == currentStep

                val dotColor by animateColorAsState(
                    targetValue = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    label = "dot_$i"
                )

                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

// ── Introduction Page ────────────────────────────────────────────────────────

@Composable
private fun IntroductionPage(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Il Tuo Momento\ndi Consapevolezza",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Dedicati 2 minuti per fare il punto su come stai davvero.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Why it matters
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Perche' e' importante?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                BenefitItem(
                    title = "Autoconsapevolezza",
                    description = "Fermarsi a riflettere su come stai e' il primo passo " +
                        "per migliorare. Spesso non ci rendiamo conto di come ci sentiamo " +
                        "finche' non ce lo chiediamo esplicitamente."
                )
                Spacer(modifier = Modifier.height(12.dp))
                BenefitItem(
                    title = "Tracciamento nel tempo",
                    description = "Ogni settimana costruisci un quadro sempre piu' completo " +
                        "del tuo benessere. Potrai vedere trend, progressi e aree su cui lavorare."
                )
                Spacer(modifier = Modifier.height(12.dp))
                BenefitItem(
                    title = "Insight personalizzati",
                    description = "Le tue risposte vengono analizzate dall'AI per offrirti " +
                        "riflessioni psicologiche su misura: pattern emotivi, punti di forza " +
                        "e suggerimenti concreti."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // How it works
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Come funziona",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                StepItem(
                    number = "1",
                    text = "10 domande, una alla volta — rispondi d'istinto con lo slider"
                )
                Spacer(modifier = Modifier.height(8.dp))
                StepItem(
                    number = "2",
                    text = "Copri 4 aree: vita quotidiana, salute psicologica, " +
                        "autodeterminazione e connessione sociale"
                )
                Spacer(modifier = Modifier.height(8.dp))
                StepItem(
                    number = "3",
                    text = "Ricevi un'analisi AI personalizzata basata sulle tue risposte"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy note
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "I tuoi dati sono privati",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Le risposte sono visibili solo a te. Non esistono risposte " +
                        "giuste o sbagliate — l'unica cosa che conta e' la tua onesta'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun BenefitItem(title: String, description: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun StepItem(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            color = MaterialTheme.colorScheme.secondary,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Question Page ────────────────────────────────────────────────────────────

@Composable
private fun QuestionPage(
    questionDef: QuestionDef,
    value: Int,
    onValueChange: (Int) -> Unit,
    questionIndex: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.15f))

        // Section badge
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = questionDef.section,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Question text — big and clear
        Text(
            text = questionDef.question,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Value display — large number
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Slider
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.fillMaxWidth()
        )

        // Min/Max labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = questionDef.minLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = questionDef.maxLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.weight(0.3f))
    }
}

// ── Notes Page ───────────────────────────────────────────────────────────────

@Composable
private fun NotesPage(
    notes: String,
    onNotesChange: (String) -> Unit,
    isSubmitting: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Vuoi aggiungere qualcosa?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scrivi liberamente una riflessione, un pensiero o qualcosa che vuoi ricordare di questa settimana.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Opzionale — scrivi qui...") },
            minLines = 5,
            maxLines = 8,
            shape = MaterialTheme.shapes.medium
        )

        // Error
        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ── Bottom Navigation Bar ────────────────────────────────────────────────────

@Composable
private fun BottomNavBar(
    currentStep: Int,
    isSubmitting: Boolean,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (currentStep) {
        STEP_INTRO -> {
            Button(
                onClick = onNext,
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    "Inizia il Questionario",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        STEP_NOTES -> {
            Button(
                onClick = onSubmit,
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Completa Questionario",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        else -> {
            Button(
                onClick = onNext,
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Avanti",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
