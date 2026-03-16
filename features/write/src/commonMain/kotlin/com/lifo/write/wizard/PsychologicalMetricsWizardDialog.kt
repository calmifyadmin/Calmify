package com.lifo.write.wizard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lifo.util.model.BodySensation
import com.lifo.util.model.Trigger
import kotlinx.coroutines.launch

/**
 * Dialog principale del Wizard per le Metriche Psicologiche
 * Material 3 Expressive multi-step con animazioni
 */
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

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { WizardStep.entries.size })

    // Local state for metrics during wizard
    var localMetrics by remember(currentMetrics) { mutableStateOf(currentMetrics) }
    var showCompletion by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f),
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header con pulsante chiudi (nascosto nell'ultimo step)
                AnimatedVisibility(visible = pagerState.currentPage < WizardStep.COMPLETION.index) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Progress Indicator a sinistra
                        WizardProgressIndicator(
                            currentStep = pagerState.currentPage,
                            totalSteps = WizardStep.TOTAL_METRIC_STEPS,
                            modifier = Modifier.weight(1f)
                        )

                        // Pulsante chiudi a destra
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Chiudi",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (pagerState.currentPage < WizardStep.COMPLETION.index) 16.dp else 0.dp))

                // Step Content con HorizontalPager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false, // Solo navigazione tramite pulsanti
                    beyondViewportPageCount = 1
                ) { page ->
                    val step = WizardStep.fromIndex(page)
                    val stepBackgroundColor = WizardColors.getBackgroundColor(page)
                    val stepAccentColor = WizardColors.getAccentColor(page)

                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (step) {
                            WizardStep.EMOTION_INTENSITY -> EmotionIntensityStep(
                                value = localMetrics.emotionIntensity,
                                onValueChange = {
                                    localMetrics = localMetrics.copy(emotionIntensity = it)
                                    onMetricsChanged(localMetrics)
                                },
                                accentColor = stepAccentColor
                            )

                            WizardStep.STRESS_LEVEL -> StressLevelStep(
                                value = localMetrics.stressLevel,
                                onValueChange = {
                                    localMetrics = localMetrics.copy(stressLevel = it)
                                    onMetricsChanged(localMetrics)
                                },
                                accentColor = stepAccentColor
                            )

                            WizardStep.ENERGY_LEVEL -> EnergyLevelStep(
                                value = localMetrics.energyLevel,
                                onValueChange = {
                                    localMetrics = localMetrics.copy(energyLevel = it)
                                    onMetricsChanged(localMetrics)
                                },
                                accentColor = stepAccentColor
                            )

                            WizardStep.CALM_ANXIETY -> CalmAnxietyStep(
                                value = localMetrics.calmAnxietyLevel,
                                onValueChange = {
                                    localMetrics = localMetrics.copy(calmAnxietyLevel = it)
                                    onMetricsChanged(localMetrics)
                                },
                                accentColor = stepAccentColor
                            )

                            WizardStep.TRIGGER -> TriggerSelectionStep(
                                selectedTrigger = localMetrics.primaryTrigger,
                                onTriggerSelected = {
                                    localMetrics = localMetrics.copy(primaryTrigger = it)
                                    onMetricsChanged(localMetrics)
                                },
                                accentColor = stepAccentColor
                            )

                            WizardStep.BODY_SENSATION -> BodySensationStep(
                                selectedSensation = localMetrics.dominantBodySensation,
                                onSensationSelected = {
                                    localMetrics = localMetrics.copy(dominantBodySensation = it)
                                    onMetricsChanged(localMetrics)
                                },
                                accentColor = stepAccentColor
                            )

                            WizardStep.COMPLETION -> CompletionStep(
                                metrics = localMetrics,
                                onDone = onComplete
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Buttons (nascosti nell'ultimo step)
                AnimatedVisibility(visible = pagerState.currentPage < WizardStep.COMPLETION.index) {
                    WizardNavigationButtons(
                        onBack = {
                            scope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        onNext = {
                            scope.launch {
                                if (pagerState.currentPage < WizardStep.COMPLETION.index) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        isFirstStep = pagerState.currentPage == 0,
                        isLastStep = pagerState.currentPage == WizardStep.BODY_SENSATION.index
                    )
                }
            }
        }
    }
}
