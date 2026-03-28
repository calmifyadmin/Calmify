package com.lifo.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifo.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 4-step conversational onboarding.
 * Step 0: "Come ti chiami?" (Name)
 * Step 1: "Cosa ti ha portato qui?" (Motivations)
 * Step 2: "Come preferisci esprimerti?" (Write / Speak / Both)
 * Step 3: Prominent Disclosure — data collection consent (Play Store requirement)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    showStepper: Boolean = false,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Collect effects — navigate on OnboardingCompleted
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OnboardingContract.Effect.OnboardingCompleted -> onComplete()
                is OnboardingContract.Effect.ShowError -> { /* error shown via uiState.error */ }
            }
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }

    // Back navigation handled by Decompose's onBack callback

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(Res.string.onboarding_exit_title)) },
            text = { Text(stringResource(Res.string.onboarding_exit_message)) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(Res.string.onboarding_exit_stay))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(Res.string.onboarding_exit_leave))
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
                        onError = { /* Error shown in UI via uiState.error */ }
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
            // Step indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(uiState.totalSteps) { index ->
                    val isActive = index == uiState.currentStep
                    val isDone = index < uiState.currentStep
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(
                                width = if (isActive) 24.dp else 8.dp,
                                height = 8.dp
                            ),
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {}
                }
            }

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
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = spring()
                        ) + fadeIn(animationSpec = tween(300)) togetherWith
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = spring()
                                ) + fadeOut(animationSpec = tween(300))
                    } else {
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
                    0 -> NameStep(
                        name = uiState.name,
                        onNameChange = { viewModel.updateName(it) },
                        username = uiState.username,
                        onUsernameChange = { viewModel.updateUsername(it) },
                        usernameError = uiState.usernameError,
                    )
                    1 -> MotivationStep(
                        selectedMotivations = uiState.motivations,
                        onMotivationsChange = { viewModel.updateMotivations(it) }
                    )
                    2 -> PreferenceStep(
                        selectedPreference = uiState.preference,
                        onPreferenceChange = { viewModel.updatePreference(it) }
                    )
                    3 -> ProminentDisclosureStep(
                        consentAccepted = uiState.dataCollectionConsent,
                        onConsentChange = { viewModel.onIntent(OnboardingContract.Intent.UpdateDataConsent(it)) }
                    )
                }
            }
        }
    }
}

// ── Step 0: Name ────────────────────────────────────────────────────────────────

@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    usernameError: String?,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.onboarding_hello),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_whats_your_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.onboarding_name_label)) },
            supportingText = { Text(stringResource(Res.string.onboarding_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { if (it.length <= 20) onUsernameChange(it.lowercase()) },
            label = { Text(stringResource(Res.string.onboarding_username_label)) },
            prefix = { Text("@") },
            singleLine = true,
            isError = usernameError != null,
            supportingText = {
                Text(
                    text = usernameError ?: stringResource(Res.string.onboarding_username_hint),
                    color = if (usernameError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.onboarding_name_changeable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Step 1: Motivations ─────────────────────────────────────────────────────────

private val MOTIVATIONS = listOf(
    "Ridurre l'ansia",
    "Capire le mie emozioni",
    "Crescita personale",
    "Journaling quotidiano",
    "Mindfulness",
    "Dormire meglio",
    "Gestire lo stress"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotivationStep(
    selectedMotivations: List<String>,
    onMotivationsChange: (List<String>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.onboarding_motivations_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_motivations_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MOTIVATIONS.forEach { motivation ->
                val isSelected = motivation in selectedMotivations
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val updated = if (isSelected) {
                            selectedMotivations - motivation
                        } else {
                            selectedMotivations + motivation
                        }
                        onMotivationsChange(updated)
                    },
                    label = {
                        Text(
                            text = motivation,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}

// ── Step 2: Preference ──────────────────────────────────────────────────────────

private data class PreferenceOption(
    val id: String,
    val title: String,
    val description: String,
    val icon: @Composable () -> Unit
)

@Composable
private fun PreferenceStep(
    selectedPreference: String,
    onPreferenceChange: (String) -> Unit
) {
    val options = remember {
        listOf(
            PreferenceOption(
                id = "write",
                title = "Scrivere",
                description = "Preferisco mettere i pensieri nero su bianco"
            ) { Icon(Icons.Default.Edit, null) },
            PreferenceOption(
                id = "speak",
                title = "Parlare",
                description = "Mi trovo meglio a parlare a voce"
            ) { Icon(Icons.Default.Mic, null) },
            PreferenceOption(
                id = "both",
                title = "Entrambi",
                description = "Dipende dal momento"
            ) { Icon(Icons.Default.SyncAlt, null) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.onboarding_preference_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_preference_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { option ->
                val isSelected = selectedPreference == option.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onPreferenceChange(option.id) }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CompositionLocalProvider(
                                    LocalContentColor provides if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ) {
                                    option.icon()
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                    }
                }
            }
        }
    }
}

// ── Step 3: Prominent Disclosure ────────────────────────────────────────────────

@Composable
private fun ProminentDisclosureStep(
    consentAccepted: Boolean,
    onConsentChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.disclosure_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                DisclosureItem(
                    icon = Icons.Default.HealthAndSafety,
                    title = stringResource(Res.string.disclosure_wellness_title),
                    description = stringResource(Res.string.disclosure_wellness_desc)
                )
                Spacer(modifier = Modifier.height(16.dp))
                DisclosureItem(
                    icon = Icons.Default.Edit,
                    title = stringResource(Res.string.disclosure_diary_title),
                    description = stringResource(Res.string.disclosure_diary_desc)
                )
                Spacer(modifier = Modifier.height(16.dp))
                DisclosureItem(
                    icon = Icons.Default.Mic,
                    title = stringResource(Res.string.disclosure_voice_title),
                    description = stringResource(Res.string.disclosure_voice_desc)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = consentAccepted,
                    role = Role.Checkbox,
                    onClick = { onConsentChange(!consentAccepted) }
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = consentAccepted,
                onCheckedChange = null
            )
            Text(
                text = stringResource(Res.string.disclosure_consent_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.disclosure_revoke_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DisclosureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Bottom Bar ──────────────────────────────────────────────────────────────────

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
                    Text(stringResource(Res.string.back))
                }
            } else {
                Spacer(modifier = Modifier.width(100.dp))
            }

            if (currentStep < totalSteps - 1) {
                Button(
                    onClick = onNext,
                    enabled = isStepValid && !isSaving,
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(stringResource(Res.string.next))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            } else {
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
                        Text(stringResource(Res.string.onboarding_saving))
                    } else {
                        Text(stringResource(Res.string.onboarding_start))
                    }
                }
            }
        }
    }
}
