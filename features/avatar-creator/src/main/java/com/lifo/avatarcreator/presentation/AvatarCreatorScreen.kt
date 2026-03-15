package com.lifo.avatarcreator.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.CreationStatus
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Effect
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Intent
import com.lifo.avatarcreator.presentation.components.CreationProgressScreen
import com.lifo.avatarcreator.presentation.components.WizardProgressBar
import com.lifo.avatarcreator.presentation.sections.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCreatorScreen(
    viewModel: AvatarCreatorViewModel,
    onNavigateBack: () -> Unit,
    onAvatarCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is Effect.NavigateToAvatarViewer -> onAvatarCreated(effect.avatarId)
                is Effect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is Effect.ScrollToTop -> { /* handled by section scroll state reset */ }
            }
        }
    }

    // If we're in creation flow, show progress screen
    if (state.creationStatus != CreationStatus.IDLE) {
        CreationProgressScreen(
            status = state.creationStatus,
            progress = state.creationProgress,
            errorMessage = state.errorMessage,
            onRetry = { viewModel.onIntent(Intent.RetrySubmit) },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crea il tuo Avatar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi")
                    }
                },
            )   
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Progress bar
            WizardProgressBar(
                currentSection = state.currentSection,
                totalSections = state.totalSections,
                onSectionClick = { viewModel.onIntent(Intent.GoToSection(it)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Section content with animation
            AnimatedContent(
                targetState = state.currentSection,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                    }
                },
                label = "section_animation",
            ) { section ->
                when (section) {
                    0 -> IdentitySection(state = state, onIntent = viewModel::onIntent)
                    1 -> AppearanceSection(state = state, onIntent = viewModel::onIntent)
                    2 -> PersonalitySection(state = state, onIntent = viewModel::onIntent)
                    3 -> ValuesSection(state = state, onIntent = viewModel::onIntent)
                    4 -> NeedsSection(state = state, onIntent = viewModel::onIntent)
                    5 -> EmotionalSection(state = state, onIntent = viewModel::onIntent)
                    6 -> VoiceSection(state = state, onIntent = viewModel::onIntent)
                }
            }

            // Bottom navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (!state.isFirstSection) {
                    OutlinedButton(
                        onClick = { viewModel.onIntent(Intent.PreviousSection) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Indietro")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (state.isLastSection) {
                    Button(
                        onClick = { viewModel.onIntent(Intent.SubmitForm) },
                        enabled = state.canSubmit,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crea Avatar")
                    }
                } else {
                    Button(
                        onClick = { viewModel.onIntent(Intent.NextSection) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Avanti")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}
