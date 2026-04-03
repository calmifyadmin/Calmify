package com.lifo.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.util.model.ChecklistCategory
import com.lifo.util.model.ChecklistItem
import com.lifo.util.model.RoutineStep
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentScreen(
    state: EnvironmentContract.State,
    onIntent: (EnvironmentContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
) {
    val tabs = listOf("Checklist", "Routine", "Digital Detox")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.environment_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tab row
            TabRow(selectedTabIndex = state.selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { onIntent(EnvironmentContract.Intent.SelectTab(index)) },
                        text = { Text(title, maxLines = 1) },
                    )
                }
            }

            // Tab content
            when (state.selectedTab) {
                0 -> ChecklistTab(state, onIntent)
                1 -> RoutineTab(state, onIntent)
                2 -> DetoxTab(state, onIntent)
            }
        }
    }
}

// ── CHECKLIST TAB ──

@Composable
private fun ChecklistTab(state: EnvironmentContract.State, onIntent: (EnvironmentContract.Intent) -> Unit) {
    val checklist = state.checklist ?: return
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Group by category
        ChecklistCategory.entries.forEach { category ->
            val items = checklist.items.filter { it.category == category }
            if (items.isNotEmpty()) {
                item(key = "header_${category.name}") {
                    Text(
                        category.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(items, key = { it.id }) { item ->
                    ChecklistItemRow(
                        item = item,
                        onToggle = { onIntent(EnvironmentContract.Intent.ToggleChecklistItem(item.id)) },
                        onRemove = { onIntent(EnvironmentContract.Intent.RemoveChecklistItem(item.id)) },
                    )
                }
            }
        }

        item(key = "add_button") {
            TextButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.environment_add_item))
            }
        }
    }

    if (showAddDialog) {
        AddChecklistDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { text, category ->
                onIntent(EnvironmentContract.Intent.AddChecklistItem(text, category))
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.isCompleted, onCheckedChange = { onToggle() })
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.environment_remove_cd), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddChecklistDialog(
    onDismiss: () -> Unit,
    onAdd: (String, ChecklistCategory) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ChecklistCategory.GENERALE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.environment_new_item_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.environment_description_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Text(stringResource(Res.string.environment_category_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChecklistCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onAdd(text, category) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(Res.string.environment_add_item)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

// ── ROUTINE TAB ──

@Composable
private fun RoutineTab(state: EnvironmentContract.State, onIntent: (EnvironmentContract.Intent) -> Unit) {
    val checklist = state.checklist ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Morning routine
        item(key = "morning_header") {
            Text(
                "\uD83C\uDF05 Routine Mattutina",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Totale: ${checklist.morningRoutine.sumOf { it.durationMinutes }} minuti",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(checklist.morningRoutine, key = { "m_${it.id}" }) { step ->
            RoutineStepRow(
                step = step,
                onToggle = { onIntent(EnvironmentContract.Intent.ToggleRoutineStep(step.id, true)) },
            )
        }

        // Evening routine
        item(key = "evening_header") {
            Spacer(Modifier.height(8.dp))
            Text(
                "\uD83C\uDF19 Routine Serale",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Totale: ${checklist.eveningRoutine.sumOf { it.durationMinutes }} minuti",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(checklist.eveningRoutine, key = { "e_${it.id}" }) { step ->
            RoutineStepRow(
                step = step,
                onToggle = { onIntent(EnvironmentContract.Intent.ToggleRoutineStep(step.id, false)) },
            )
        }
    }
}

@Composable
private fun RoutineStepRow(step: RoutineStep, onToggle: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = step.isCompleted, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(step.text, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${step.durationMinutes} min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── DIGITAL DETOX TAB ──

@Composable
private fun DetoxTab(state: EnvironmentContract.State, onIntent: (EnvironmentContract.Intent) -> Unit) {
    val detoxMinutes = state.checklist?.detoxTimerMinutes ?: 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Text("\uD83D\uDCF5", fontSize = 64.sp)

        Text(
            if (state.detoxActive) "Digital Detox attivo" else "Digital Detox",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            "Metti giu' il telefono. Respira. Vivi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.detoxActive) {
            // Timer display
            val min = state.detoxRemainingSeconds / 60
            val sec = state.detoxRemainingSeconds % 60
            Text(
                "${min}:${sec.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
            )

            // Progress
            LinearProgressIndicator(
                progress = { 1f - (state.detoxRemainingSeconds.toFloat() / (detoxMinutes * 60)) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { onIntent(EnvironmentContract.Intent.StopDetoxTimer) },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(Res.string.environment_stop_detox))
            }
        } else {
            // Duration selector
            Text(stringResource(Res.string.environment_detox_duration, detoxMinutes), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = detoxMinutes.toFloat(),
                onValueChange = { onIntent(EnvironmentContract.Intent.SetDetoxMinutes(it.toInt())) },
                valueRange = 15f..120f,
                steps = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onIntent(EnvironmentContract.Intent.StartDetoxTimer) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.environment_start_detox), fontSize = 16.sp)
            }
        }
    }
}
