package com.lifo.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lifo.onboarding.steps.*
import com.lifo.ui.components.stepper.ExpressiveStepper

/**
 * Main onboarding screen container
 * Manages navigation between steps and orchestrates the onboarding flow
 *
 * Features:
 * - Step-by-step navigation with validation
 * - Animated transitions between steps
 * - Progress tracking with ExpressiveStepper
 * - Back press handling
 * - Loading and error states
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show exit confirmation dialog
    var showExitDialog by remember { mutableStateOf(false) }

    // Handle back press
    BackHandler {
        if (uiState.currentStep == 0) {
            showExitDialog = true
        } else {
            viewModel.previousStep()
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Onboarding?") },
            text = { Text("Your progress will not be saved. You'll need to complete onboarding before using the app.") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Stay")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    // TODO: Navigate back or close app
                }) {
                    Text("Exit")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps,
                isStepValid = viewModel.isCurrentStepValid(),
                isSaving = uiState.isSaving,
                onNext = { viewModel.nextStep() },
                onBack = { viewModel.previousStep() },
                onComplete = {
                    viewModel.completeOnboarding(
                        onSuccess = onComplete,
                        onError = { /* Error is shown in UI via uiState.error */ }
                    )
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stepper
            ExpressiveStepper(
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps,
                stepLabels = listOf(
                    "Personal",
                    "Health",
                    "Lifestyle",
                    "Goals",
                    "Review"
                ),
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Divider()

            // Error message
            if (uiState.error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Step content with animations
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        // Moving forward
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = spring()
                        ) + fadeIn(animationSpec = tween(300)) togetherWith
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = spring()
                                ) + fadeOut(animationSpec = tween(300))
                    } else {
                        // Moving backward
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = spring()
                        ) + fadeIn(animationSpec = tween(300)) togetherWith
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = spring()
                                ) + fadeOut(animationSpec = tween(300))
                    }
                },
                label = "step_transition",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    0 -> PersonalInfoStep(viewModel = viewModel)
                    1 -> HealthInfoStep(viewModel = viewModel)
                    2 -> LifestyleStep(viewModel = viewModel)
                    3 -> GoalsStep(viewModel = viewModel)
                    4 -> ReviewStep(
                        viewModel = viewModel,
                        onEditStep = { stepIndex ->
                            viewModel.navigateToStep(stepIndex)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Bottom navigation bar with Back, Next, and Complete buttons
 * Following Material3 Bottom App Bar pattern for wizard navigation
 */
@Composable
private fun OnboardingBottomBar(
    currentStep: Int,
    totalSteps: Int,
    isStepValid: Boolean,
    isSaving: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button (left side) - visible only when not on first step
            if (currentStep > 0) {
                TextButton(
                    onClick = onBack,
                    enabled = !isSaving,
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back")
                }
            } else {
                // Spacer to maintain layout balance when back button is hidden
                Spacer(modifier = Modifier.width(100.dp))
            }

            // Next/Complete Button (right side)
            if (currentStep < totalSteps - 1) {
                // Next Button
                Button(
                    onClick = onNext,
                    enabled = isStepValid && !isSaving,
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            } else {
                // Complete Button (last step)
                Button(
                    onClick = onComplete,
                    enabled = isStepValid && !isSaving,
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Text("Complete Setup")
                    }
                }
            }
        }
    }
}
