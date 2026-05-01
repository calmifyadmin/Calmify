package com.lifo.insight

import com.lifo.util.formatDecimal
import com.lifo.ui.components.tooltips.InfoTooltip
import com.lifo.ui.components.tooltips.TooltipContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifo.ui.components.CalmifyTopBar
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.lifo.util.model.*

/**
 * InsightScreen
 *
 * Displays AI-generated psychological insights for a diary entry
 * Week 5 - PSYCHOLOGICAL_INSIGHTS_PLAN.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightScreen(
    diaryId: String,
    onBackPressed: () -> Unit,
    onPromptClicked: (String) -> Unit,
    viewModel: InsightViewModel = koinViewModel<InsightViewModel>()
) {
    val uiState by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    // Correction dialog state
    var correctionText by remember { mutableStateOf("") }

    // Load insight on first composition
    LaunchedEffect(diaryId) {
        viewModel.onIntent(InsightContract.Intent.LoadInsight(diaryId))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CalmifyTopBar(
                title = "Insight Psicologico",
                onBackClick = onBackPressed,
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
                    // Loading state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(Strings.Screen.Insight.loading), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                uiState.error != null && uiState.insight == null -> {
                    // Error state (no insight available)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nessun insight disponibile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Gli insight AI verranno generati automaticamente per i tuoi diari",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.insight != null -> {
                    // Success state - show insight
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Sentiment Card
                        SentimentCard(
                            sentiment = uiState.insight!!.getSentimentLabel(),
                            polarity = uiState.insight!!.sentimentPolarity,
                            magnitude = uiState.insight!!.sentimentMagnitude
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Topics & Key Phrases
                        if (uiState.insight!!.topics.isNotEmpty() || uiState.insight!!.keyPhrases.isNotEmpty()) {
                            ContentAnalysisSection(
                                topics = uiState.insight!!.topics,
                                keyPhrases = uiState.insight!!.keyPhrases
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Cognitive Patterns
                        if (uiState.insight!!.cognitivePatterns.isNotEmpty()) {
                            CognitivePatternsCard(patterns = uiState.insight!!.cognitivePatterns)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // AI Summary (expandable)
                        if (uiState.insight!!.summary.isNotEmpty()) {
                            AISummaryCard(
                                summary = uiState.insight!!.summary,
                                confidence = uiState.insight!!.confidence,
                                modelUsed = uiState.insight!!.modelUsed,
                                isExpanded = uiState.isSummaryExpanded,
                                onExpandClick = { viewModel.onIntent(InsightContract.Intent.ToggleSummaryExpanded) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Suggested Prompts
                        if (uiState.insight!!.suggestedPrompts.isNotEmpty()) {
                            SuggestedPromptsCard(
                                prompts = uiState.insight!!.suggestedPrompts,
                                onPromptClick = onPromptClicked
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Feedback Section
                        FeedbackCard(
                            insight = uiState.insight!!,
                            feedbackSubmitted = uiState.feedbackSubmitted,
                            isSubmitting = uiState.isSubmittingFeedback,
                            onFeedbackClick = { isCorrect ->
                                if (isCorrect) {
                                    viewModel.onIntent(InsightContract.Intent.SubmitFeedback(isHelpful = true))
                                } else {
                                    viewModel.onIntent(InsightContract.Intent.ShowCorrectionDialog(true))
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // Correction Dialog
            if (uiState.showCorrectionDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.onIntent(InsightContract.Intent.ShowCorrectionDialog(false)) },
                    title = { Text(stringResource(Strings.Screen.Insight.feedbackDialogTitle)) },
                    text = {
                        OutlinedTextField(
                            value = correctionText,
                            onValueChange = { correctionText = it },
                            placeholder = { Text(stringResource(Strings.Screen.Insight.feedbackPlaceholder)) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.onIntent(InsightContract.Intent.SubmitFeedback(isHelpful = false, correction = correctionText))
                                viewModel.onIntent(InsightContract.Intent.ShowCorrectionDialog(false))
                                correctionText = ""
                            },
                            enabled = correctionText.isNotBlank()
                        ) {
                            Text(stringResource(Strings.Screen.Insight.feedbackSend))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.onIntent(InsightContract.Intent.ShowCorrectionDialog(false))
                            correctionText = ""
                        }) {
                            Text(stringResource(Strings.Action.cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SentimentCard(sentiment: SentimentLabel, polarity: Float, magnitude: Float) {
    val color = when (sentiment) {
        SentimentLabel.VERY_NEGATIVE, SentimentLabel.NEGATIVE -> MaterialTheme.colorScheme.errorContainer
        SentimentLabel.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        SentimentLabel.POSITIVE, SentimentLabel.VERY_POSITIVE -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = sentiment.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Come ti sentivi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(when (sentiment) {
                            SentimentLabel.VERY_NEGATIVE -> Strings.Sentiment.veryNegative
                            SentimentLabel.NEGATIVE -> Strings.Sentiment.negative
                            SentimentLabel.NEUTRAL -> Strings.Sentiment.neutral
                            SentimentLabel.POSITIVE -> Strings.Sentiment.positive
                            SentimentLabel.VERY_POSITIVE -> Strings.Sentiment.veryPositive
                        }),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Polarity indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tono:",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(80.dp)
                )
                LinearProgressIndicator(
                    progress = { (polarity + 1f) / 2f }, // Convert -1..1 to 0..1
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDecimal(2, polarity),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Magnitude indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Forza:",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(80.dp)
                )
                LinearProgressIndicator(
                    progress = { (magnitude / 10f).coerceIn(0f, 1f) }, // Normalize to 0..1
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDecimal(1, magnitude),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentAnalysisSection(topics: List<String>, keyPhrases: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Di cosa hai parlato",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (topics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Argomenti:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topics.forEach { topic ->
                        AssistChip(
                            onClick = { },
                            label = { Text(topic) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            if (keyPhrases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Concetti importanti:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keyPhrases.forEach { phrase ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(phrase, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CognitivePatternsCard(patterns: List<CognitivePattern>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Schemi di pensiero",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                InfoTooltip(
                    title       = TooltipContent.cognitivePatterns.first,
                    description = TooltipContent.cognitivePatterns.second,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            patterns.forEach { pattern ->
                PatternItem(pattern)
                if (pattern != patterns.last()) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun PatternItem(pattern: CognitivePattern) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pattern.patternName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            InfoTooltip(
                title       = TooltipContent.cognitivePatterns.first,
                description = TooltipContent.cognitivePatterns.second,
                modifier    = Modifier.padding(8.dp),
            )
        }
        Text(
            text = pattern.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pattern.evidence.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "\"${pattern.evidence}\"",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun AISummaryCard(
    summary: String,
    confidence: Float,
    modelUsed: String,
    isExpanded: Boolean,
    onExpandClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Cosa ho notato",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "$modelUsed • ${(confidence * 100).toInt()}% fiducia",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                IconButton(onClick = onExpandClick) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Comprimi" else "Espandi"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun SuggestedPromptsCard(
    prompts: List<String>,
    onPromptClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Per riflettere ancora",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            prompts.forEach { prompt ->
                FilledTonalButton(
                    onClick = { onPromptClick(prompt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(prompt, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SentimentLabel.icon(): ImageVector = when (this) {
    SentimentLabel.VERY_NEGATIVE -> Icons.Outlined.SentimentVeryDissatisfied
    SentimentLabel.NEGATIVE -> Icons.Outlined.SentimentDissatisfied
    SentimentLabel.NEUTRAL -> Icons.Outlined.SentimentNeutral
    SentimentLabel.POSITIVE -> Icons.Outlined.SentimentSatisfied
    SentimentLabel.VERY_POSITIVE -> Icons.Outlined.SentimentVerySatisfied
}

@Composable
private fun FeedbackCard(
    insight: DiaryInsight,
    feedbackSubmitted: Boolean,
    isSubmitting: Boolean,
    onFeedbackClick: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Valutazione",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                feedbackSubmitted || insight.userRating != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grazie per il tuo feedback!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onFeedbackClick(true) },
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(Strings.Screen.Insight.feedbackUseful))
                        }
                        OutlinedButton(
                            onClick = { onFeedbackClick(false) },
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(Strings.Screen.Insight.feedbackNotUseful))
                        }
                    }
                }
            }
        }
    }
}
