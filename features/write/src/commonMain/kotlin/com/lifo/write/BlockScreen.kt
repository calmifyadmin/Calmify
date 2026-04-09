package com.lifo.write

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.util.model.BlockResolution
import com.lifo.util.model.BlockType
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockScreen(
    state: BlockContract.State,
    onIntent: (BlockContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.block_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                    }
                },
                actions = {
                    // History toggle
                    if (state.step != BlockContract.Step.HISTORY) {
                        IconButton(onClick = {
                            onIntent(BlockContract.Intent.LoadBlocks)
                            // Navigate to history step
                        }) {
                            Icon(Icons.Default.History, contentDescription = stringResource(Res.string.block_history_cd))
                        }
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 3 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 3 })
            },
            label = "step",
        ) { step ->
            when (step) {
                BlockContract.Step.DESCRIBE -> DescribeStep(state, onIntent)
                BlockContract.Step.DIAGNOSIS -> DiagnosisStep(state, onIntent)
                BlockContract.Step.ACTION -> ActionStep(state, onIntent, onBackPressed)
                BlockContract.Step.HISTORY -> HistoryStep(state, onIntent)
            }
        }
    }
}

// ── STEP 1: DESCRIBE ──

@Composable
private fun DescribeStep(state: BlockContract.State, onIntent: (BlockContract.Intent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "\uD83E\uDDE9 Descrivimi cosa sta succedendo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            "Non devi analizzare, solo raccontare. Scrivi liberamente quello che senti.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = { onIntent(BlockContract.Intent.SetDescription(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = { Text(stringResource(Res.string.block_placeholder)) },
            shape = RoundedCornerShape(16.dp),
        )

        // Active blocks hint
        if (state.activeBlocks.isNotEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Hai ${state.activeBlocks.size} blocchi attivi. Ce la puoi fare!",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Button(
            onClick = { onIntent(BlockContract.Intent.AnalyzeBlock) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = state.description.length >= 10,
        ) {
            Text(stringResource(Res.string.block_analyze), fontSize = 16.sp)
        }
    }
}

// ── STEP 2: DIAGNOSIS ──

@Composable
private fun DiagnosisStep(state: BlockContract.State, onIntent: (BlockContract.Intent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val detectedType = state.detectedType ?: BlockType.UNKNOWN

        Text(
            detectedType.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Eve-style suggestion
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Eve dice:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    detectedType.suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Confirm or change type
        Text(stringResource(Res.string.block_type_label), style = MaterialTheme.typography.titleMedium)
        BlockType.entries.filter { it != BlockType.UNKNOWN }.forEach { type ->
            val selected = state.selectedType == type
            FilterChip(
                selected = selected,
                onClick = { onIntent(BlockContract.Intent.SelectBlockType(type)) },
                label = { Text(type.displayName) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onIntent(BlockContract.Intent.SaveBlock) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !state.isLoading,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(Res.string.block_register_proceed), fontSize = 16.sp)
            }
        }
    }
}

// ── STEP 3: ACTION ──

@Composable
private fun ActionStep(
    state: BlockContract.State,
    onIntent: (BlockContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "\uD83D\uDCA1 Ecco cosa puoi fare ora",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            "Scegli lo strumento che senti piu' adatto in questo momento:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Suggested actions based on block type
        val suggestedActions = when (state.selectedType) {
            BlockType.OVERLOAD -> listOf(
                ActionItem("Scarico mentale", Icons.Outlined.Psychology, "Scrivi tutto quello che hai in mente") { onIntent(BlockContract.Intent.NavigateToBrainDump) },
                ActionItem("Meditazione", Icons.Default.SelfImprovement, "Calma la mente con un respiro guidato") { onIntent(BlockContract.Intent.NavigateToMeditation) },
            )
            BlockType.LIMITING_BELIEF -> listOf(
                ActionItem("Riformula il pensiero", Icons.Outlined.Cloud, "Trasforma la credenza limitante") { onIntent(BlockContract.Intent.NavigateToReframing) },
                ActionItem("Scarico mentale", Icons.Outlined.Psychology, "Scrivi liberamente") { onIntent(BlockContract.Intent.NavigateToBrainDump) },
            )
            BlockType.FEAR_OF_FAILURE -> listOf(
                ActionItem("Riformula il pensiero", Icons.Outlined.Warning, "Ridimensiona la paura") { onIntent(BlockContract.Intent.NavigateToReframing) },
                ActionItem("Meditazione", Icons.Default.SelfImprovement, "Respira e centra la mente") { onIntent(BlockContract.Intent.NavigateToMeditation) },
            )
            BlockType.CREATIVE_BLOCK -> listOf(
                ActionItem("Scarico mentale", Icons.Outlined.Palette, "Libera la mente") { onIntent(BlockContract.Intent.NavigateToBrainDump) },
                ActionItem("Meditazione", Icons.Default.SelfImprovement, "Fai spazio a nuove idee") { onIntent(BlockContract.Intent.NavigateToMeditation) },
            )
            BlockType.UNKNOWN -> listOf(
                ActionItem("Scarico mentale", Icons.Outlined.Psychology, "Scrivi tutto senza giudicare") { onIntent(BlockContract.Intent.NavigateToBrainDump) },
                ActionItem("Riformula", Icons.Outlined.Cloud, "Cambia prospettiva") { onIntent(BlockContract.Intent.NavigateToReframing) },
                ActionItem("Meditazione", Icons.Default.SelfImprovement, "Respira e rilassati") { onIntent(BlockContract.Intent.NavigateToMeditation) },
            )
        }

        suggestedActions.forEach { action ->
            ActionCard(action)
        }

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = onBackPressed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.block_back_to_diary))
        }
    }
}

private data class ActionItem(
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(action: ActionItem) {
    OutlinedCard(
        onClick = action.onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── STEP 4: HISTORY ──

@Composable
private fun HistoryStep(state: BlockContract.State, onIntent: (BlockContract.Intent) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Active blocks
        if (state.activeBlocks.isNotEmpty()) {
            item { Text(stringResource(Res.string.block_active), style = MaterialTheme.typography.titleMedium) }
            items(state.activeBlocks, key = { it.id }) { block ->
                BlockHistoryCard(block = block, isResolved = false, onResolve = {
                    onIntent(BlockContract.Intent.ResolveBlock(block.id))
                })
            }
        }

        // Resolved blocks
        if (state.resolvedBlocks.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.block_overcome), style = MaterialTheme.typography.titleMedium)
            }
            items(state.resolvedBlocks, key = { it.id }) { block ->
                BlockHistoryCard(block = block, isResolved = true, onResolve = {})
            }
        }

        if (state.activeBlocks.isEmpty() && state.resolvedBlocks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("\uD83C\uDF1F", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Nessun blocco registrato",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Ottimo! Continua cosi'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockType.icon(): ImageVector = when (this) {
    BlockType.FEAR_OF_FAILURE -> Icons.Outlined.Warning
    BlockType.OVERLOAD -> Icons.Outlined.Psychology
    BlockType.LIMITING_BELIEF -> Icons.Outlined.Cloud
    BlockType.CREATIVE_BLOCK -> Icons.Outlined.Palette
    BlockType.UNKNOWN -> Icons.Outlined.HelpOutline
}

@Composable
private fun BlockHistoryCard(
    block: com.lifo.util.model.Block,
    isResolved: Boolean,
    onResolve: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = if (isResolved) CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) else CardDefaults.outlinedCardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = block.type.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    block.type.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (isResolved) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                block.description.take(100) + if (block.description.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isResolved) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onResolve) {
                    Text(stringResource(Res.string.block_mark_overcome))
                }
            } else if (block.resolutionNote.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Come l'ho superato: ${block.resolutionNote}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
