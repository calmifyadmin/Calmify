package com.lifo.write

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.util.model.ThoughtCategory
import kotlinx.coroutines.delay
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

/**
 * Reframe Screen — "Laboratorio dei Pensieri" (CBT Lite)
 *
 * 3-step guided cognitive reframing:
 * 1. Capture the blocking thought
 * 2. Socratic questioning (evidence for/against, friend perspective)
 * 3. Rewrite with a credible alternative
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReframeScreen(
    state: ReframeContract.State,
    onIntent: (ReframeContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stepTitles = listOf(
        stringResource(Strings.Wellness.reframeStep1Title),
        stringResource(Strings.Wellness.reframeStep2Title),
        stringResource(Strings.Wellness.reframeStep3Title),
    )
    val stepSubtitles = listOf(
        stringResource(Strings.Wellness.reframeStep1Subtitle),
        stringResource(Strings.Wellness.reframeStep2Subtitle),
        stringResource(Strings.Wellness.reframeStep3Subtitle),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.reframe_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(Strings.Wellness.reframeStepIndicator, state.currentStep + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.currentStep > 0) onIntent(ReframeContract.Intent.PrevStep) else onBackPressed()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                    }
                },
                actions = {
                    if (state.currentStep == 2) {
                        IconButton(
                            onClick = { onIntent(ReframeContract.Intent.Save) },
                            enabled = state.canProceed && !state.isSaving
                        ) {
                            if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Check, contentDescription = stringResource(Res.string.reframe_save), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.currentStep < 2) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onIntent(ReframeContract.Intent.NextStep) },
                            enabled = state.canProceed
                        ) {
                            Text(stringResource(Strings.Coach.buttonNext))
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        // Step progress indicator
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LinearProgressIndicator(
                progress = { (state.currentStep + 1) / 3f },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "step_transition"
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Step header
                    Text(
                        text = stepTitles[step],
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stepSubtitles[step],
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    when (step) {
                        0 -> StepCapture(state, onIntent)
                        1 -> StepQuestion(state, onIntent)
                        2 -> StepReframe(state, onIntent)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCapture(state: ReframeContract.State, onIntent: (ReframeContract.Intent) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(300); focusRequester.requestFocus() }

    ReframeTextField(
        value = state.originalThought,
        onValueChange = { onIntent(ReframeContract.Intent.SetOriginalThought(it)) },
        placeholder = stringResource(Strings.Wellness.reframePlaceholderThought),
        focusRequester = focusRequester,
        minHeight = 120.dp,
    )

    // Category
    Text(stringResource(Res.string.reframe_category_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ThoughtCategory.entries.forEach { cat ->
            FilterChip(
                selected = state.category == cat,
                onClick = { onIntent(ReframeContract.Intent.SetCategory(cat)) },
                label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

@Composable
private fun StepQuestion(state: ReframeContract.State, onIntent: (ReframeContract.Intent) -> Unit) {
    // Show the original thought
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Text(
            text = "\"${state.originalThought}\"",
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(16.dp)
        )
    }

    // Socratic questions
    QuestionField(
        question = stringResource(Strings.Wellness.reframeStep1Subtitle),
        subtext = stringResource(Strings.Wellness.reframeEvidenceFor),
        value = state.evidenceFor,
        onValueChange = { onIntent(ReframeContract.Intent.SetEvidenceFor(it)) },
    )
    QuestionField(
        question = stringResource(Strings.Wellness.reframeStep2Title),
        subtext = stringResource(Strings.Wellness.reframeException),
        value = state.evidenceAgainst,
        onValueChange = { onIntent(ReframeContract.Intent.SetEvidenceAgainst(it)) },
    )
    QuestionField(
        question = stringResource(Strings.Wellness.reframeFriendQuestion),
        subtext = stringResource(Strings.Wellness.reframeYearQuestion),
        value = state.friendPerspective,
        onValueChange = { onIntent(ReframeContract.Intent.SetFriendPerspective(it)) },
    )
}

@Composable
private fun StepReframe(state: ReframeContract.State, onIntent: (ReframeContract.Intent) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(300); focusRequester.requestFocus() }

    // Original vs reframed side by side
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.reframe_before_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Text(
                text = "\"${state.originalThought}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
            )
        }
    }

    Text(
        text = stringResource(Strings.Wellness.reframeRewritePrompt),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )

    ReframeTextField(
        value = state.reframedThought,
        onValueChange = { onIntent(ReframeContract.Intent.SetReframedThought(it)) },
        placeholder = stringResource(Strings.Wellness.reframePlaceholderRewrite),
        focusRequester = focusRequester,
        minHeight = 120.dp,
    )

    // Save button
    Button(
        onClick = { onIntent(ReframeContract.Intent.Save) },
        enabled = state.canProceed && !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(stringResource(Res.string.reframe_save), Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun QuestionField(
    question: String,
    subtext: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ReframeTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = stringResource(Strings.Wellness.reframePlaceholderGeneric),
                minHeight = 60.dp,
            )
        }
    }
}

@Composable
private fun ReframeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester? = null,
    minHeight: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val mod = if (focusRequester != null) Modifier.fillMaxWidth().focusRequester(focusRequester) else Modifier.fillMaxWidth()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = mod,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(Modifier.defaultMinSize(minHeight = minHeight)) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                innerTextField()
            }
        }
    )
}
