package com.lifo.write

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifo.ui.components.tooltips.InfoTooltip
import com.lifo.ui.components.tooltips.TooltipContent
import com.lifo.util.model.BodySensation
import com.lifo.util.model.Trigger

/**
 * PsychologicalMetricsSheet
 *
 * Collapsible/expandable sheet for capturing psychological metrics.
 * Target completion time: <10 seconds
 *
 * Features:
 * - Material3 Sliders for intensity/stress/energy/calm (0-10 range)
 * - Preset buttons for Trigger enum
 * - Preset buttons for BodySensation enum
 * - Skip button to keep defaults
 * - Collapsible to reduce visual clutter
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsychologicalMetricsSheet(
    modifier: Modifier = Modifier,
    emotionIntensity: Int,
    stressLevel: Int,
    energyLevel: Int,
    calmAnxietyLevel: Int,
    primaryTrigger: Trigger,
    dominantBodySensation: BodySensation,
    onEmotionIntensityChanged: (Int) -> Unit,
    onStressLevelChanged: (Int) -> Unit,
    onEnergyLevelChanged: (Int) -> Unit,
    onCalmAnxietyLevelChanged: (Int) -> Unit,
    onPrimaryTriggerChanged: (Trigger) -> Unit,
    onDominantBodySensationChanged: (BodySensation) -> Unit,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with expand/collapse button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Metriche Psicologiche",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Comprimi" else "Espandi"
                    )
                }
            }

            Text(
                text = "Opzionale • Completamento stimato: 10 sec",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Emotion Intensity Slider
                    MetricSlider(
                        label       = "Intensità Emotiva",
                        value       = emotionIntensity,
                        onValueChange = { onEmotionIntensityChanged(it.toInt()) },
                        minLabel    = "Debole",
                        maxLabel    = "Intensa",
                        tooltip     = TooltipContent.emotionalIntensity,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stress Level Slider
                    MetricSlider(
                        label       = "Livello di Stress",
                        value       = stressLevel,
                        onValueChange = { onStressLevelChanged(it.toInt()) },
                        minLabel    = "Nessuno",
                        maxLabel    = "Estremo",
                        tooltip     = TooltipContent.stressLevel,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Energy Level Slider
                    MetricSlider(
                        label       = "Livello di Energia",
                        value       = energyLevel,
                        onValueChange = { onEnergyLevelChanged(it.toInt()) },
                        minLabel    = "Esausto",
                        maxLabel    = "Energico",
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Calm/Anxiety Level Slider
                    MetricSlider(
                        label       = "Calma/Ansia",
                        value       = calmAnxietyLevel,
                        onValueChange = { onCalmAnxietyLevelChanged(it.toInt()) },
                        minLabel    = "Ansioso",
                        maxLabel    = "Calmo",
                        tooltip     = TooltipContent.calmAnxietyLevel,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Trigger Selection
                    Text(
                        text = "Trigger Principale",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TriggerSelector(
                        selectedTrigger = primaryTrigger,
                        onTriggerSelected = onPrimaryTriggerChanged
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Body Sensation Selection
                    Text(
                        text = "Sensazione Corporea Dominante",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BodySensationSelector(
                        selectedBodySensation = dominantBodySensation,
                        onBodySensationSelected = onDominantBodySensationChanged
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Skip button (collapses the sheet)
                    OutlinedButton(
                        onClick = { isExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mantieni Valori di Default")
                    }
                }
            }
        }
    }
}

/**
 * Reusable slider component for psychological metrics
 */
@Composable
private fun MetricSlider(
    label: String,
    value: Int,
    onValueChange: (Float) -> Unit,
    minLabel: String,
    maxLabel: String,
    tooltip: Pair<String, String>? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (tooltip != null) {
                    InfoTooltip(
                        title       = tooltip.first,
                        description = tooltip.second,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = value.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = 0f..10f,
            steps = 9, // 11 total values (0-10)
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = minLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = maxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Trigger selection component with chip-style buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerSelector(
    selectedTrigger: Trigger,
    onTriggerSelected: (Trigger) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: NONE, WORK, FAMILY, HEALTH
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Trigger.values().take(4).forEach { trigger ->
                FilterChip(
                    selected = selectedTrigger == trigger,
                    onClick = { onTriggerSelected(trigger) },
                    label = { Text(trigger.displayName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Row 2: FINANCE, SOCIAL, SELF, OTHER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Trigger.values().drop(4).forEach { trigger ->
                FilterChip(
                    selected = selectedTrigger == trigger,
                    onClick = { onTriggerSelected(trigger) },
                    label = { Text(trigger.displayName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Body sensation selection component with chip-style buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodySensationSelector(
    selectedBodySensation: BodySensation,
    onBodySensationSelected: (BodySensation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: NONE, TENSION, LIGHTNESS, FATIGUE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BodySensation.values().take(4).forEach { sensation ->
                FilterChip(
                    selected = selectedBodySensation == sensation,
                    onClick = { onBodySensationSelected(sensation) },
                    label = {
                        Text(
                            text = sensation.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Row 2: HEAVINESS, AGITATION, RELAXATION
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BodySensation.values().drop(4).forEach { sensation ->
                FilterChip(
                    selected = selectedBodySensation == sensation,
                    onClick = { onBodySensationSelected(sensation) },
                    label = {
                        Text(
                            text = sensation.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            // Add spacer to balance the row
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
