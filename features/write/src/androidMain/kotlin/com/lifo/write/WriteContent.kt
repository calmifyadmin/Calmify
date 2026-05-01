package com.lifo.write

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lifo.ui.GalleryImage
import com.lifo.ui.GalleryState
import com.lifo.ui.components.GalleryUploader
import com.lifo.util.model.Diary
import com.lifo.ui.emotion.LargeMoodShape
import com.lifo.util.model.Mood
import com.lifo.write.wizard.BodySensationStep
import com.lifo.write.wizard.CalmAnxietyStep
import com.lifo.write.wizard.EmotionIntensityStep
import com.lifo.write.wizard.EnergyLevelStep
import com.lifo.write.wizard.StressLevelStep
import com.lifo.write.wizard.TriggerSelectionStep
import com.lifo.write.wizard.WizardColors
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*


private const val STEP_MOOD = 0
private const val STEP_TITLE = 1
private const val STEP_DESCRIPTION = 2
private const val STEP_EMOTION = 3
private const val STEP_STRESS = 4
private const val STEP_ENERGY = 5
private const val STEP_CALM = 6
private const val STEP_TRIGGER = 7
private const val STEP_BODY = 8
private const val STEP_SAVE = 9
private const val TOTAL_STEPS = 10

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WriteContent(
    uiState: UiState,
    pagerState: PagerState,
    galleryState: GalleryState,
    title: String,
    onTitleChanged: (String) -> Unit,
    description: String,
    onDescriptionChanged: (String) -> Unit,
    paddingValues: PaddingValues,
    onSaveClicked: (Diary) -> Unit,
    onImageSelect: (String) -> Unit,
    onImageClicked: (GalleryImage) -> Unit,
    viewModel: WriteViewModel
) {
    var currentStep by remember { mutableIntStateOf(STEP_MOOD) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding())
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = { (currentStep + 1f) / TOTAL_STEPS },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        // Step content
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "wizardStep"
        ) { step ->
            when (step) {
                STEP_MOOD -> MoodSelectionStep(pagerState = pagerState)
                STEP_TITLE -> TitleInputStep(
                    title = title,
                    onTitleChanged = onTitleChanged,
                    onNext = { currentStep = STEP_DESCRIPTION },
                )
                STEP_DESCRIPTION -> DescriptionInputStep(
                    description = description,
                    onDescriptionChanged = onDescriptionChanged,
                    uiState = uiState,
                    viewModel = viewModel,
                )
                STEP_EMOTION -> MetricStepWrapper(
                    title = "Intensita' emotiva",
                    description = "Quanto e' intensa l'emozione che provi?",
                ) {
                    EmotionIntensityStep(
                        value = uiState.emotionIntensity,
                        onValueChange = { viewModel.setEmotionIntensity(it) },
                        accentColor = WizardColors.getAccentColor(0),
                    )
                }
                STEP_STRESS -> MetricStepWrapper(
                    title = "Livello di stress",
                    description = "Quanto ti senti stressato/a?",
                ) {
                    StressLevelStep(
                        value = uiState.stressLevel,
                        onValueChange = { viewModel.setStressLevel(it) },
                        accentColor = WizardColors.getAccentColor(1),
                    )
                }
                STEP_ENERGY -> MetricStepWrapper(
                    title = "Livello di energia",
                    description = "Quanta energia hai?",
                ) {
                    EnergyLevelStep(
                        value = uiState.energyLevel,
                        onValueChange = { viewModel.setEnergyLevel(it) },
                        accentColor = WizardColors.getAccentColor(2),
                    )
                }
                STEP_CALM -> MetricStepWrapper(
                    title = "Calma / Ansia",
                    description = "Quanto ti senti calmo/a o ansioso/a?",
                ) {
                    CalmAnxietyStep(
                        value = uiState.calmAnxietyLevel,
                        onValueChange = { viewModel.setCalmAnxietyLevel(it) },
                        accentColor = WizardColors.getAccentColor(3),
                    )
                }
                STEP_TRIGGER -> MetricStepWrapper(
                    title = "Trigger principale",
                    description = "Cosa ha influenzato il tuo stato d'animo?",
                ) {
                    TriggerSelectionStep(
                        selectedTrigger = uiState.primaryTrigger,
                        onTriggerSelected = { viewModel.setPrimaryTrigger(it) },
                        accentColor = WizardColors.getAccentColor(4),
                    )
                }
                STEP_BODY -> MetricStepWrapper(
                    title = "Sensazione corporea",
                    description = "Cosa senti nel corpo?",
                ) {
                    BodySensationStep(
                        selectedSensation = uiState.dominantBodySensation,
                        onSensationSelected = { viewModel.setDominantBodySensation(it) },
                        accentColor = WizardColors.getAccentColor(5),
                    )
                }
                STEP_SAVE -> SaveStep(
                    uiState = uiState,
                    galleryState = galleryState,
                    viewModel = viewModel,
                    onImageSelect = onImageSelect,
                    onImageClicked = onImageClicked,
                    onSaveClicked = onSaveClicked,
                    pagerState = pagerState,
                )
            }
        }

        // Bottom navigation
        StepNavigationBar(
            currentStep = currentStep,
            canAdvance = when (currentStep) {
                STEP_TITLE -> title.isNotBlank()
                STEP_DESCRIPTION -> description.isNotBlank()
                else -> true
            },
            onBack = {
                focusManager.clearFocus()
                if (currentStep > 0) currentStep--
            },
            onNext = {
                focusManager.clearFocus()
                when (currentStep) {
                    STEP_TITLE -> {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Il titolo e' obbligatorio", Toast.LENGTH_SHORT).show()
                        } else {
                            currentStep++
                        }
                    }
                    STEP_DESCRIPTION -> {
                        if (description.isBlank()) {
                            Toast.makeText(context, "La descrizione e' obbligatoria", Toast.LENGTH_SHORT).show()
                        } else {
                            currentStep++
                        }
                    }
                    else -> {
                        if (currentStep < TOTAL_STEPS - 1) currentStep++
                    }
                }
            },
        )
    }
}

// ── Step: Mood Selection ─────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoodSelectionStep(pagerState: PagerState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Strings.SnapshotWizard.howFeel),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Strings.SnapshotWizard.swipeToChoose),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        HorizontalPager(state = pagerState) { page ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LargeMoodShape(
                    mood = Mood.values()[page],
                    modifier = Modifier.size(160.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = Mood.values()[pagerState.currentPage].name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── Step: Title Input ─────────────────────────────────────────
@Composable
private fun TitleInputStep(
    title: String,
    onTitleChanged: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Dai un titolo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Un titolo breve per il tuo diario",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChanged,
            placeholder = { Text(stringResource(Res.string.write_diary_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onNext() }),
        )
    }
}

// ── Step: Description Input ─────────────────────────────────────
@Composable
private fun DescriptionInputStep(
    description: String,
    onDescriptionChanged: (String) -> Unit,
    uiState: UiState,
    viewModel: WriteViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Racconta",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Scrivi come ti senti, cosa e' successo",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChanged,
            placeholder = { Text(stringResource(Res.string.write_mood_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
            shape = RoundedCornerShape(16.dp),
            maxLines = 12,
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        // Smart Capture
        SmartCaptureCard(
            uiState = uiState,
            onAnalyze = { viewModel.runSmartCapture() },
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ── Wrapper for metric steps ─────────────────────────────────────
@Composable
private fun MetricStepWrapper(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        content()

        Spacer(Modifier.height(24.dp))
    }
}

// ── Step: Gallery + Save ─────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaveStep(
    uiState: UiState,
    galleryState: GalleryState,
    viewModel: WriteViewModel,
    onImageSelect: (String) -> Unit,
    onImageClicked: (GalleryImage) -> Unit,
    onSaveClicked: (Diary) -> Unit,
    pagerState: PagerState,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val areImagesUploaded = remember { derivedStateOf { viewModel.areAllImagesUploaded() } }
    val pageNumber by remember { derivedStateOf { pagerState.currentPage } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Quasi fatto!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Aggiungi foto e salva il tuo diario",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        // Gallery
        GalleryUploader(
            galleryState = galleryState,
            onAddClicked = { focusManager.clearFocus() },
            onImageSelect = onImageSelect,
            onImageClicked = onImageClicked,
        )

        Spacer(Modifier.height(24.dp))

        // Save button
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = areImagesUploaded.value,
            shape = RoundedCornerShape(16.dp),
            onClick = {
                if (viewModel.displayImageCount.value < 6) {
                    if (uiState.title.isNotEmpty() && uiState.description.isNotEmpty()) {
                        onSaveClicked(
                            Diary().apply {
                                this.title = uiState.title
                                this.description = uiState.description
                                this.mood = Mood.values()[pageNumber].name
                                this.images = galleryState.images.map { it.remoteImagePath }
                                this.emotionIntensity = uiState.emotionIntensity
                                this.stressLevel = uiState.stressLevel
                                this.energyLevel = uiState.energyLevel
                                this.calmAnxietyLevel = uiState.calmAnxietyLevel
                                this.primaryTrigger = uiState.primaryTrigger.name
                                this.dominantBodySensation = uiState.dominantBodySensation.name
                            }
                        )
                    } else {
                        Toast.makeText(context, "Titolo e descrizione sono obbligatori.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Massimo 6 immagini.", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            if (viewModel.isUploadingImages.value) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Salva diario",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Bottom Navigation Bar ─────────────────────────────────────────
@Composable
private fun StepNavigationBar(
    currentStep: Int,
    canAdvance: Boolean = true,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            if (currentStep > 0) {
                TextButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Indietro")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            // Step counter
            Text(
                text = "${currentStep + 1} / $TOTAL_STEPS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Next button (hidden on last step — save button is inline)
            if (currentStep < TOTAL_STEPS - 1) {
                FilledTonalButton(onClick = onNext) {
                    Text(stringResource(Strings.Coach.buttonNext))
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}

// ── Smart Capture Card ────────────────────────────────────────────
@Composable
private fun SmartCaptureCard(
    uiState: UiState,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasEnoughText = uiState.description.length >= 30

    if (uiState.smartCaptureComplete) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Smart Capture",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "Mood: ${stringResource(TextAnalyzer.moodLabelRes(uiState.mood))} | " +
                            "Stress: ${uiState.stressLevel}/10 | " +
                            "Energia: ${uiState.energyLevel}/10",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TextButton(
                    onClick = onAnalyze,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = "Rianalizza",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    } else if (hasEnoughText) {
        OutlinedButton(
            onClick = onAnalyze,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Analizza il tuo testo")
        }
    }
}
