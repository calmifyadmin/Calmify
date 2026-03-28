package com.lifo.write

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.util.model.ConnectionType
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionScreen(
    state: ConnectionContract.State,
    onIntent: (ConnectionContract.Intent) -> Unit,
    navigateBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { onIntent(ConnectionContract.Intent.Load) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Check-in Settimanale",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Le tue connessioni",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    TextButton(onClick = { onIntent(ConnectionContract.Intent.ShowReflection) }) {
                        Text("Riflessione")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Weekly stats
            item {
                WeeklyConnectionStats(
                    gratitude = state.weeklyGratitudeCount,
                    service = state.weeklyServiceCount,
                    qualityTime = state.weeklyQualityTimeCount,
                )
            }

            // Type selector
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Che tipo di connessione?",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colorScheme.onSurface
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ConnectionType.entries.forEach { type ->
                                val isSelected = state.selectedType == type

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onIntent(ConnectionContract.Intent.SelectType(type)) },
                                    label = {
                                        Text(
                                            type.displayName,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
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
                }
            }

            // Input form
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = when (state.selectedType) {
                                ConnectionType.GRATITUDE -> "A chi sei grato oggi?"
                                ConnectionType.SERVICE -> "Cosa hai fatto per qualcuno?"
                                ConnectionType.QUALITY_TIME -> "Con chi hai trascorso del tempo?"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colorScheme.onSurface
                        )

                        Text(
                            text = when (state.selectedType) {
                                ConnectionType.GRATITUDE -> "Glielo hai detto?"
                                ConnectionType.SERVICE -> "Senza che te lo chiedesse"
                                ConnectionType.QUALITY_TIME -> "Tempo di qualita' insieme"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        OutlinedTextField(
                            value = state.personName,
                            onValueChange = { onIntent(ConnectionContract.Intent.UpdatePerson(it)) },
                            label = { Text("Chi?") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        )

                        OutlinedTextField(
                            value = state.description,
                            onValueChange = { onIntent(ConnectionContract.Intent.UpdateDescription(it)) },
                            label = {
                                Text(
                                    when (state.selectedType) {
                                        ConnectionType.GRATITUDE -> "Per cosa sei grato?"
                                        ConnectionType.SERVICE -> "Cosa hai fatto?"
                                        ConnectionType.QUALITY_TIME -> "Come avete passato il tempo?"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        )

                        if (state.selectedType == ConnectionType.GRATITUDE) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Glielo ho detto",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = state.expressed,
                                        onCheckedChange = { onIntent(ConnectionContract.Intent.ToggleExpressed(it)) },
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { onIntent(ConnectionContract.Intent.SaveEntry) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            enabled = state.personName.isNotBlank() && state.description.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Salva", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Past entries
            if (state.entries.isNotEmpty()) {
                item {
                    Text(
                        "Le tue connessioni recenti",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colorScheme.onSurface
                    )
                }
                items(state.entries.take(20), key = { it.id }) { entry ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.personName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    entry.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                                if (entry.type == ConnectionType.GRATITUDE && entry.expressed) {
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = colorScheme.primary.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            "Espresso",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onIntent(ConnectionContract.Intent.DeleteEntry(entry.id)) }) {
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
            }
        }
    }

    if (state.showReflection) {
        ReflectionDialog(state = state, onIntent = onIntent)
    }
}

// ==================== WEEKLY STATS ====================

@Composable
private fun WeeklyConnectionStats(gratitude: Int, service: Int, qualityTime: Int) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Questa settimana",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                ConnectionStatItem(
                    icon = Icons.Default.Favorite,
                    label = "Gratitudine",
                    count = gratitude,
                    color = colorScheme.onPrimaryContainer
                )
                Surface(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp),
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                ) {}
                ConnectionStatItem(
                    icon = Icons.Default.VolunteerActivism,
                    label = "Servizio",
                    count = service,
                    color = colorScheme.onPrimaryContainer
                )
                Surface(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp),
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                ) {}
                ConnectionStatItem(
                    icon = Icons.Default.People,
                    label = "Quality Time",
                    count = qualityTime,
                    color = colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatItem(icon: ImageVector, label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = color
        )
        Text(
            "$count",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}

// ==================== REFLECTION DIALOG ====================

@Composable
private fun ReflectionDialog(
    state: ConnectionContract.State,
    onIntent: (ConnectionContract.Intent) -> Unit,
) {
    var nurturingInput by remember { mutableStateOf("") }
    var drainingInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onIntent(ConnectionContract.Intent.DismissReflection) },
        title = {
            Text(
                "Riflessione Mensile",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "Le relazioni che ti nutrono",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(state.nurturingInput.size) { index ->
                    InputChip(
                        selected = false,
                        onClick = { onIntent(ConnectionContract.Intent.RemoveNurturing(index)) },
                        label = { Text(state.nurturingInput[index]) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Rimuovi", modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = nurturingInput,
                        onValueChange = { nurturingInput = it },
                        label = { Text("Aggiungi persona") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            if (nurturingInput.isNotBlank()) {
                                IconButton(onClick = {
                                    onIntent(ConnectionContract.Intent.AddNurturing(nurturingInput))
                                    nurturingInput = ""
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Aggiungi")
                                }
                            }
                        },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Le relazioni che ti prosciugano",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(state.drainingInput.size) { index ->
                    InputChip(
                        selected = false,
                        onClick = { onIntent(ConnectionContract.Intent.RemoveDraining(index)) },
                        label = { Text(state.drainingInput[index]) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Rimuovi", modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = drainingInput,
                        onValueChange = { drainingInput = it },
                        label = { Text("Aggiungi persona") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            if (drainingInput.isNotBlank()) {
                                IconButton(onClick = {
                                    onIntent(ConnectionContract.Intent.AddDraining(drainingInput))
                                    drainingInput = ""
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Aggiungi")
                                }
                            }
                        },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.intentionInput,
                        onValueChange = { onIntent(ConnectionContract.Intent.UpdateIntention(it)) },
                        label = { Text("La tua intenzione per il prossimo mese") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(ConnectionContract.Intent.SaveReflection) }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(ConnectionContract.Intent.DismissReflection) }) {
                Text("Chiudi")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
