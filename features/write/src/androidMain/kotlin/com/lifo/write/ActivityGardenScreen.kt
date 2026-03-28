package com.lifo.write

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class GardenCategory(val label: String, val icon: ImageVector) {
    TUTTE("Tutte", Icons.Outlined.GridView),
    SCRITTURA("Scrittura", Icons.Outlined.Edit),
    MENTE("Mente", Icons.Outlined.Psychology),
    CORPO("Corpo", Icons.AutoMirrored.Outlined.DirectionsRun),
    SPIRITO("Spirito", Icons.Outlined.Spa),
    ABITUDINI("Abitudini", Icons.Outlined.CheckCircle),
}

private data class GardenActivity(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val category: GardenCategory,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityGardenScreen(
    onBackPressed: () -> Unit,
    // Scrittura
    onWriteClick: () -> Unit = {},
    onBrainDumpClick: () -> Unit = {},
    onGratitudeClick: () -> Unit = {},
    // Mente
    onMeditationClick: () -> Unit = {},
    onReframeClick: () -> Unit = {},
    onBlockClick: () -> Unit = {},
    onRecurringThoughtsClick: () -> Unit = {},
    // Corpo
    onEnergyClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    onMovementClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    // Spirito
    onValuesClick: () -> Unit = {},
    onIkigaiClick: () -> Unit = {},
    onAweClick: () -> Unit = {},
    onSilenceClick: () -> Unit = {},
    onConnectionClick: () -> Unit = {},
    onInspirationClick: () -> Unit = {},
    // Abitudini
    onHabitsClick: () -> Unit = {},
    onEnvironmentClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedCategory by remember { mutableStateOf(GardenCategory.TUTTE) }

    val activities = remember {
        listOf(
            // Scrittura
            GardenActivity("Diario", "Scrivi i tuoi pensieri", Icons.Outlined.Edit, Color(0xFF2E7D55), GardenCategory.SCRITTURA, onWriteClick),
            GardenActivity("Brain Dump", "Svuota la mente", Icons.Outlined.Stream, Color(0xFF78909C), GardenCategory.SCRITTURA, onBrainDumpClick),
            GardenActivity("Gratitudine", "3 cose belle", Icons.Outlined.Favorite, Color(0xFFE91E63), GardenCategory.SCRITTURA, onGratitudeClick),
            // Mente
            GardenActivity("Meditazione", "Respira e centra", Icons.Outlined.SelfImprovement, Color(0xFF7E57C2), GardenCategory.MENTE, onMeditationClick),
            GardenActivity("Reframing", "Cambia prospettiva", Icons.Outlined.Psychology, Color(0xFF5C6BC0), GardenCategory.MENTE, onReframeClick),
            GardenActivity("Blocchi", "Riconosci gli ostacoli", Icons.Outlined.Extension, Color(0xFFEF6C00), GardenCategory.MENTE, onBlockClick),
            GardenActivity("Pensieri Ricorrenti", "Osserva i pattern", Icons.Outlined.BubbleChart, Color(0xFF00897B), GardenCategory.MENTE, onRecurringThoughtsClick),
            // Corpo
            GardenActivity("Energia", "Come stai oggi?", Icons.Outlined.BatteryChargingFull, Color(0xFFFF9800), GardenCategory.CORPO, onEnergyClick),
            GardenActivity("Sonno", "Traccia il riposo", Icons.Outlined.Bedtime, Color(0xFF5C6BC0), GardenCategory.CORPO, onSleepClick),
            GardenActivity("Movimento", "Registra attivita'", Icons.AutoMirrored.Outlined.DirectionsRun, Color(0xFFEF6C00), GardenCategory.CORPO, onMovementClick),
            GardenActivity("Dashboard", "Panoramica corpo", Icons.Outlined.Terrain, Color(0xFF26A69A), GardenCategory.CORPO, onDashboardClick),
            // Spirito
            GardenActivity("Valori", "Scopri cosa conta", Icons.Outlined.Explore, Color(0xFF42A5F5), GardenCategory.SPIRITO, onValuesClick),
            GardenActivity("Ikigai", "Trova il tuo scopo", Icons.Outlined.Interests, Color(0xFFAB47BC), GardenCategory.SPIRITO, onIkigaiClick),
            GardenActivity("Awe", "Meraviglia quotidiana", Icons.Outlined.Park, Color(0xFF66BB6A), GardenCategory.SPIRITO, onAweClick),
            GardenActivity("Silenzio", "Pratica il vuoto", Icons.Outlined.SelfImprovement, Color(0xFF78909C), GardenCategory.SPIRITO, onSilenceClick),
            GardenActivity("Connessioni", "Relazioni che contano", Icons.Outlined.People, Color(0xFFE91E63), GardenCategory.SPIRITO, onConnectionClick),
            GardenActivity("Ispirazione", "Raccogli spunti", Icons.Outlined.FormatQuote, Color(0xFFFFCA28), GardenCategory.SPIRITO, onInspirationClick),
            // Abitudini
            GardenActivity("Abitudini", "Costruisci routine", Icons.Outlined.CheckCircle, Color(0xFF26A69A), GardenCategory.ABITUDINI, onHabitsClick),
            GardenActivity("Ambiente", "Disegna il contesto", Icons.Outlined.Spa, Color(0xFF4CAF7D), GardenCategory.ABITUDINI, onEnvironmentClick),
        )
    }

    val filtered = remember(selectedCategory) {
        if (selectedCategory == GardenCategory.TUTTE) activities
        else activities.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Tutte le Attivita'",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${filtered.size} strumenti disponibili",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Category filter chips
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    items(GardenCategory.entries, key = { it.name }) { cat ->
                        FilterChip(
                            selected = cat == selectedCategory,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(cat.label, style = MaterialTheme.typography.labelMedium)
                            },
                            leadingIcon = if (cat == selectedCategory) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                {
                                    Icon(
                                        cat.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(36.dp),
                        )
                    }
                }
            }

            // Group by category when showing all
            if (selectedCategory == GardenCategory.TUTTE) {
                GardenCategory.entries
                    .filter { it != GardenCategory.TUTTE }
                    .forEach { cat ->
                        val catActivities = activities.filter { it.category == cat }
                        item(key = "header_${cat.name}") {
                            GardenSectionHeader(cat.label, catActivities.size)
                        }
                        items(catActivities, key = { it.name }) { activity ->
                            GardenActivityCard(activity)
                        }
                    }
            } else {
                items(filtered, key = { it.name }) { activity ->
                    GardenActivityCard(activity)
                }
            }
        }
    }
}

@Composable
private fun GardenSectionHeader(label: String, count: Int) {
    val colorScheme = MaterialTheme.colorScheme

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GardenActivityCard(activity: GardenActivity) {
    val colorScheme = MaterialTheme.colorScheme

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(40); visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 3 },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { activity.onClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Colored icon background — pill style
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = activity.accent.copy(alpha = 0.10f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            activity.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = activity.accent,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = activity.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}
