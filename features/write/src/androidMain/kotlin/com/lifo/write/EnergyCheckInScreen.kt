package com.lifo.write

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Battery3Bar
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BedtimeOff
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.util.model.MovementType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Energy Check-In Screen — "Come Sta il Tuo Corpo?"
 *
 * Quick 30-second daily body check-in:
 * Energy (1-10), Sleep hours, Water glasses, Movement, Meals.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EnergyCheckInScreen(
    state: EnergyContract.State,
    onIntent: (EnergyContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Come Sta il Tuo Corpo?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.savedToday) "Aggiorna il check-in" else "Check-in fisico giornaliero",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(EnergyContract.Intent.Save) },
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Salva",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Energy Level (1-10)
            AnimatedSection(index = 0) {
                EnergySliderCard(
                    title = "Energia percepita",
                    icon = energyIcon(state.energyLevel),
                    value = state.energyLevel,
                    valueLabel = "${state.energyLevel}/10",
                    onValueChange = { onIntent(EnergyContract.Intent.SetEnergyLevel(it)) },
                    range = 1f..10f,
                    steps = 8,
                )
            }

            // 2. Sleep Hours
            AnimatedSection(index = 1) {
                EnergySliderCard(
                    title = "Ore di sonno",
                    icon = sleepIcon(state.sleepHours),
                    value = state.sleepHours.roundToInt(),
                    valueLabel = "${state.sleepHours.roundToHalf()}h",
                    onValueChange = { onIntent(EnergyContract.Intent.SetSleepHours(it.toFloat())) },
                    range = 0f..12f,
                    steps = 23,
                    floatValue = state.sleepHours,
                    onFloatChange = { onIntent(EnergyContract.Intent.SetSleepHours(it.roundToHalf())) },
                )
            }

            // 3. Water Glasses
            AnimatedSection(index = 2) {
                WaterCounterCard(
                    glasses = state.waterGlasses,
                    onIncrement = { onIntent(EnergyContract.Intent.SetWaterGlasses(state.waterGlasses + 1)) },
                    onDecrement = { onIntent(EnergyContract.Intent.SetWaterGlasses(state.waterGlasses - 1)) },
                )
            }

            // 4. Movement
            AnimatedSection(index = 3) {
                MovementCard(
                    didMovement = state.didMovement,
                    movementType = state.movementType,
                    onToggle = { onIntent(EnergyContract.Intent.SetDidMovement(it)) },
                    onTypeSelected = { onIntent(EnergyContract.Intent.SetMovementType(it)) },
                )
            }

            // 5. Regular Meals
            AnimatedSection(index = 4) {
                MealsCard(
                    regularMeals = state.regularMeals,
                    onToggle = { onIntent(EnergyContract.Intent.SetRegularMeals(it)) },
                )
            }

            // Save button
            Button(
                onClick = { onIntent(EnergyContract.Intent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (state.savedToday) "Aggiorna" else "Salva check-in",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Section Cards ──

@Composable
private fun EnergySliderCard(
    title: String,
    icon: ImageVector,
    value: Int,
    valueLabel: String,
    onValueChange: (Int) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    floatValue: Float? = null,
    onFloatChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = floatValue ?: value.toFloat(),
                onValueChange = { newVal ->
                    if (onFloatChange != null) {
                        onFloatChange(newVal)
                    } else {
                        onValueChange(newVal.roundToInt())
                    }
                },
                valueRange = range,
                steps = steps,
            )
        }
    }
}

@Composable
private fun WaterCounterCard(
    glasses: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "\uD83D\uDCA7", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bicchieri d'acqua",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = waterFeedback(glasses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onDecrement,
                    enabled = glasses > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Meno", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "$glasses",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                FilledTonalIconButton(
                    onClick = onIncrement,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Piu'", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovementCard(
    didMovement: Boolean,
    movementType: MovementType,
    onToggle: (Boolean) -> Unit,
    onTypeSelected: (MovementType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83C\uDFC3", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Movimento oggi?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = didMovement,
                    onCheckedChange = onToggle
                )
            }

            AnimatedVisibility(visible = didMovement) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Che tipo?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MovementType.entries
                            .filter { it != MovementType.NESSUNO }
                            .forEach { type ->
                                FilterChip(
                                    selected = movementType == type,
                                    onClick = { onTypeSelected(type) },
                                    label = {
                                        Text(
                                            text = type.displayName,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealsCard(
    regularMeals: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "\uD83C\uDF7D\uFE0F", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pasti regolari?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (regularMeals) "Mangiato con regolarita'" else "Pasti saltati o irregolari",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = regularMeals,
                onCheckedChange = onToggle
            )
        }
    }
}

// ── Utilities ──

@Composable
private fun AnimatedSection(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 100L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 }
    ) {
        content()
    }
}

@Composable
private fun energyIcon(level: Int): ImageVector = when {
    level <= 2 -> Icons.Outlined.BatteryAlert
    level <= 4 -> Icons.Outlined.Battery3Bar
    level <= 6 -> Icons.Outlined.Battery5Bar
    level <= 8 -> Icons.Outlined.BatteryFull
    else -> Icons.Outlined.ElectricBolt
}

@Composable
private fun sleepIcon(hours: Float): ImageVector = when {
    hours < 5 -> Icons.Outlined.BedtimeOff
    hours < 7 -> Icons.Outlined.Bedtime
    hours < 9 -> Icons.Outlined.NightsStay
    else -> Icons.Outlined.Hotel
}

private fun waterFeedback(glasses: Int): String = when {
    glasses == 0 -> "Nessun bicchiere ancora"
    glasses < 4 -> "Bevi di piu'!"
    glasses < 8 -> "Buon progresso"
    else -> "Ottima idratazione!"
}

private fun Float.roundToHalf(): Float = (this * 2).roundToInt() / 2f
