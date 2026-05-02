package com.lifo.humanoid.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifo.humanoid.presentation.components.FilamentView
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Main Humanoid Avatar screen.
 * Displays the 3D avatar in a clean, production-ready view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HumanoidScreen(
    navigateBack: () -> Unit,
    onCreateAvatar: () -> Unit = {},
    avatarId: String? = null,
    viewModel: HumanoidViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vrmModelData by viewModel.vrmModelData.collectAsStateWithLifecycle()
    val vrmExtensions by viewModel.vrmExtensions.collectAsStateWithLifecycle()
    val blendShapeWeights by viewModel.blendShapeWeights.collectAsStateWithLifecycle()

    // Load specific avatar if ID provided (overrides default loaded in init)
    LaunchedEffect(avatarId) {
        if (avatarId != null) {
            viewModel.loadAvatarById(avatarId)
        }
    }

    // Update blend shapes every frame
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateBlendShapes(0.016f) // ~60 FPS
            kotlinx.coroutines.delay(16)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Strings.Nav.avatar)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Strings.Action.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "Errore nel caricamento dell'avatar",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = uiState.error!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { viewModel.loadDefaultAvatar() }) {
                                Text(stringResource(Strings.Action.retry))
                            }
                        }
                    }
                }
                uiState.avatarLoaded -> {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        FilamentView(
                            modifier = Modifier.fillMaxSize(),
                            vrmModelData = vrmModelData?.first,
                            vrmExtensions = vrmExtensions,
                            blendShapeWeights = blendShapeWeights,
                            isLayoutChanging = false,
                            onModelLoaded = { renderer, asset, nodeNames ->
                                viewModel.onModelLoaded(renderer, asset, nodeNames)
                            },
                            onBeforeCleanup = {
                                viewModel.stopAllControllersBeforeCleanup()
                            }
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Nessun avatar caricato",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(onClick = onCreateAvatar) {
                                Text(stringResource(Strings.Screen.Humanoid.create))
                            }
                            OutlinedButton(onClick = { viewModel.loadDefaultAvatar() }) {
                                Text(stringResource(Strings.Screen.Humanoid.loadDemo))
                            }
                        }
                    }
                }
            }
        }
    }
}
