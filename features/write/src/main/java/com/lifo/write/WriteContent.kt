package com.lifo.write

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lifo.ui.GalleryImage
import com.lifo.ui.GalleryState
import com.lifo.ui.components.GalleryUploader
import com.lifo.util.model.Diary
import com.lifo.ui.providers.MoodUiProvider
import com.lifo.util.model.Mood
import com.lifo.write.wizard.PsychologicalMetrics
import com.lifo.write.wizard.PsychologicalMetricsWizardDialog
import kotlinx.coroutines.launch


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
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val areImagesUploaded = remember { derivedStateOf { viewModel.areAllImagesUploaded() } }
    LaunchedEffect(key1 = scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(top = paddingValues.calculateTopPadding())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(state = scrollState)
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            HorizontalPager(state = pagerState) { page ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        modifier = Modifier.size(120.dp),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(MoodUiProvider.getIcon(Mood.values()[page]))
                            .build(),

                        contentDescription = "Mood Image"
                    )
                }
            }
            Spacer(modifier = Modifier.height(30.dp))

            // Psychological Metrics Card - Apre il wizard
            MetricsWizardCard(
                uiState = uiState,
                onOpenWizard = { viewModel.openMetricsWizard() }
            )

            // Psychological Metrics Wizard Dialog
            PsychologicalMetricsWizardDialog(
                isVisible = uiState.showMetricsWizard,
                currentMetrics = PsychologicalMetrics(
                    emotionIntensity = uiState.emotionIntensity,
                    stressLevel = uiState.stressLevel,
                    energyLevel = uiState.energyLevel,
                    calmAnxietyLevel = uiState.calmAnxietyLevel,
                    primaryTrigger = uiState.primaryTrigger,
                    dominantBodySensation = uiState.dominantBodySensation
                ),
                onMetricsChanged = { metrics ->
                    viewModel.setEmotionIntensity(metrics.emotionIntensity)
                    viewModel.setStressLevel(metrics.stressLevel)
                    viewModel.setEnergyLevel(metrics.energyLevel)
                    viewModel.setCalmAnxietyLevel(metrics.calmAnxietyLevel)
                    viewModel.setPrimaryTrigger(metrics.primaryTrigger)
                    viewModel.setDominantBodySensation(metrics.dominantBodySensation)
                },
                onDismiss = { viewModel.closeMetricsWizard() },
                onComplete = { viewModel.completeMetricsWizard() }
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = title,
                onValueChange = onTitleChanged,
                placeholder = { Text(text = "Title") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Unspecified,
                    unfocusedIndicatorColor = Color.Unspecified,
                    disabledIndicatorColor = Color.Unspecified,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        scope.launch {
                            scrollState.animateScrollTo(Int.MAX_VALUE)
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                ),
                maxLines = 1,
                singleLine = true
            )
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = description,
                onValueChange = onDescriptionChanged,
                placeholder = { Text(text = "Tell me about it.") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Unspecified,
                    unfocusedIndicatorColor = Color.Unspecified,
                    disabledIndicatorColor = Color.Unspecified,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusManager.clearFocus()
                    }
                )
            )
        }

        Column(verticalArrangement = Arrangement.Bottom) {
            Spacer(modifier = Modifier.height(12.dp))
            GalleryUploader(
                galleryState = galleryState,
                onAddClicked = { focusManager.clearFocus() },
                onImageSelect = onImageSelect,
                onImageClicked = onImageClicked
            )
            Spacer(modifier = Modifier.height(12.dp))
            // In the Button section, display the count correctly
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = viewModel.areAllImagesUploaded(),
                onClick = {
                    // Use displayImageCount instead of direct size check
                    if (viewModel.displayImageCount.value < 6) {
                        if (uiState.title.isNotEmpty() && uiState.description.isNotEmpty()) {
                            onSaveClicked(
                                Diary().apply {
                                    this.title = uiState.title
                                    this.description = uiState.description
                                    this.images = galleryState.images.map { it.remoteImagePath }
                                    // Include psychological metrics
                                    this.emotionIntensity = uiState.emotionIntensity
                                    this.stressLevel = uiState.stressLevel
                                    this.energyLevel = uiState.energyLevel
                                    this.calmAnxietyLevel = uiState.calmAnxietyLevel
                                    this.primaryTrigger = uiState.primaryTrigger.name
                                    this.dominantBodySensation = uiState.dominantBodySensation.name
                                }
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "Fields cannot be empty.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "MAX IMAGE 6.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                // Show the correct count here
                Text(text = "Save (${viewModel.displayImageCount.value} images)")
            }
            if (viewModel.isUploadingImages.value) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Card per aprire il wizard delle metriche psicologiche
 * Mostra un riepilogo se le metriche sono state completate
 */
@Composable
private fun MetricsWizardCard(
    uiState: UiState,
    onOpenWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasModifiedMetrics = uiState.emotionIntensity != 5 ||
            uiState.stressLevel != 5 ||
            uiState.energyLevel != 5 ||
            uiState.calmAnxietyLevel != 5 ||
            uiState.primaryTrigger != com.lifo.util.model.Trigger.NONE ||
            uiState.dominantBodySensation != com.lifo.util.model.BodySensation.NONE

    ElevatedCard(
        onClick = onOpenWizard,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (uiState.metricsCompleted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = if (uiState.metricsCompleted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = if (uiState.metricsCompleted) "Metriche Completate" else "Metriche Psicologiche",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (uiState.metricsCompleted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (uiState.metricsCompleted)
                            "Tocca per modificare"
                        else
                            "Tocca per compilare (opzionale)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Badge con stato
            if (uiState.metricsCompleted || hasModifiedMetrics) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "\u2713",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Riepilogo metriche se completate
        if (hasModifiedMetrics) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricMiniChip(
                    emoji = "\uD83D\uDCAB",
                    value = uiState.emotionIntensity.toString()
                )
                MetricMiniChip(
                    emoji = "\uD83D\uDE25",
                    value = uiState.stressLevel.toString()
                )
                MetricMiniChip(
                    emoji = "\u26A1",
                    value = uiState.energyLevel.toString()
                )
                MetricMiniChip(
                    emoji = "\uD83E\uDDD8",
                    value = uiState.calmAnxietyLevel.toString()
                )
            }
        }
    }
}

@Composable
private fun MetricMiniChip(
    emoji: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = emoji, style = MaterialTheme.typography.bodySmall)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}