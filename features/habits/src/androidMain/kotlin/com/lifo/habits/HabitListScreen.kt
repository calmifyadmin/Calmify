package com.lifo.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.components.graphics.HabitHeatMap
import com.lifo.ui.components.graphics.HeatMapDay
import com.lifo.ui.components.graphics.ParticleConfig
import com.lifo.ui.components.graphics.ParticleSystem
import com.lifo.util.model.Habit
import com.lifo.util.model.HabitCategory
import com.lifo.util.model.HabitFrequency
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitListScreen(
    state: HabitContract.State,
    onIntent: (HabitContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    showCelebration: Boolean = false,
    onCelebrationComplete: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        HabitListContent(
            state = state,
            onIntent = onIntent,
            onBackPressed = onBackPressed,
        )

        ParticleSystem(
            trigger = showCelebration,
            origin = Offset.Unspecified,
            config = ParticleConfig(
                count = 50,
                spread = 360f,
                baseAngle = -90f,
                minSpeed = 200f,
                maxSpeed = 600f,
                gravity = 500f,
                lifetime = 1.8f,
            ),
            onComplete = onCelebrationComplete,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitListContent(
    state: HabitContract.State,
    onIntent: (HabitContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    val heatMapDays: List<HeatMapDay> = remember(state.completionHistory, state.habits) {
        if (state.habits.isEmpty()) return@remember emptyList()
        val totalHabits = state.habits.size.coerceAtLeast(1)
        state.completionHistory
            .groupBy { it.dayKey }
            .map { (dayKey, completions) ->
                HeatMapDay(
                    dayKey = dayKey,
                    value = (completions.size.toFloat() / totalHabits).coerceIn(0f, 1f),
                )
            }
            .sortedBy { it.dayKey }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Le Tue Abitudini",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val completedCount = state.todayCompletions.size
                        val totalCount = state.habits.size
                        if (totalCount > 0) {
                            Text(
                                text = "$completedCount/$totalCount completate oggi",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(HabitContract.Intent.ShowAddDialog) },
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuova abitudine")
            }
        },
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.habits.isEmpty()) {
            EmptyHabitsContent(
                onAddClick = { onIntent(HabitContract.Intent.ShowAddDialog) },
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.habits, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        isCompleted = habit.id in state.todayCompletions,
                        streak = state.streaks[habit.id] ?: 0,
                        onToggle = { onIntent(HabitContract.Intent.ToggleCompletion(habit.id)) },
                        onDelete = { onIntent(HabitContract.Intent.DeleteHabit(habit.id)) },
                    )
                }

                if (heatMapDays.isNotEmpty()) {
                    item(key = "heatmap_header") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Ultimi 90 giorni",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colorScheme.onSurface,
                        )
                    }
                    item(key = "heatmap") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = colorScheme.surfaceContainerLow
                        ) {
                            HabitHeatMap(
                                days = heatMapDays,
                                weeks = 13,
                                baseColor = colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AddHabitDialog(
                onDismiss = { onIntent(HabitContract.Intent.DismissAddDialog) },
                onSave = { habit -> onIntent(HabitContract.Intent.SaveNewHabit(habit)) },
            )
        }
    }
}

// ==================== HABIT CARD ====================

@Composable
private fun HabitCard(
    habit: Habit,
    isCompleted: Boolean,
    streak: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    val containerColor by animateColorAsState(
        targetValue = if (isCompleted) colorScheme.primaryContainer
        else colorScheme.surfaceContainerLow,
        animationSpec = tween(300),
        label = "habit_card_color"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { onToggle() },
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = habit.category.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).padding(end = 8.dp),
                        tint = if (isCompleted) colorScheme.onPrimaryContainer
                        else colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCompleted) colorScheme.onPrimaryContainer
                        else colorScheme.onSurface
                    )
                }
                if (habit.minimumAction.isNotBlank()) {
                    Text(
                        text = habit.minimumAction,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCompleted) colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else colorScheme.onSurfaceVariant,
                    )
                }
                if (habit.anchorHabit != null) {
                    Text(
                        text = "Dopo: ${habit.anchorHabit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                    )
                }
            }

            // Streak badge
            if (streak > 0) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.tertiary,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onTertiary
                        )
                        Text(
                            text = "$streak",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = colorScheme.onTertiary,
                        )
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Elimina",
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==================== EMPTY STATE ====================

@Composable
private fun EmptyHabitsContent(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = RoundedCornerShape(28.dp),
            color = colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Nessuna abitudine ancora",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Parti piccolo. Un'abitudine alla volta.",
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Aggiungi la prima", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ==================== ADD HABIT DIALOG ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddHabitDialog(
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var minimumAction by remember { mutableStateOf("") }
    var anchorHabit by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(HabitCategory.CRESCITA) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Nuova Abitudine",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    placeholder = { Text("es. Meditazione mattutina") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = minimumAction,
                    onValueChange = { minimumAction = it },
                    label = { Text("Azione minima") },
                    placeholder = { Text("es. Siediti e chiudi gli occhi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = anchorHabit,
                    onValueChange = { anchorHabit = it },
                    label = { Text("Dopo cosa? (aggancio)") },
                    placeholder = { Text("es. Dopo il caffe'") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Text(
                    text = "Categoria",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HabitCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = {
                                Text(
                                    cat.displayName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = if (category == cat) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            Habit(
                                name = name.trim(),
                                description = description.trim(),
                                minimumAction = minimumAction.trim(),
                                anchorHabit = anchorHabit.trim().ifBlank { null },
                                category = category,
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

// ==================== UTILS ====================

@Composable
private fun HabitCategory.icon(): ImageVector = when (this) {
    HabitCategory.MENTE -> Icons.Outlined.Psychology
    HabitCategory.CORPO -> Icons.Outlined.FitnessCenter
    HabitCategory.SPIRITO -> Icons.Outlined.AutoAwesome
    HabitCategory.RELAZIONI -> Icons.Outlined.People
    HabitCategory.CRESCITA -> Icons.AutoMirrored.Outlined.TrendingUp
}
