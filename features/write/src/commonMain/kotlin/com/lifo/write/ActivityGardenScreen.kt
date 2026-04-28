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
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
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
import com.lifo.ui.i18n.Strings
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class GardenCategory(val labelRes: StringResource, val icon: ImageVector) {
    TUTTE(Strings.Garden.categoryAll, Icons.Outlined.GridView),
    SCRITTURA(Strings.Garden.categoryWriting, Icons.Outlined.Edit),
    MENTE(Strings.Garden.categoryMind, Icons.Outlined.Psychology),
    CORPO(Strings.Garden.categoryBody, Icons.AutoMirrored.Outlined.DirectionsRun),
    SPIRITO(Strings.Garden.categorySpirit, Icons.Outlined.Spa),
    ABITUDINI(Strings.Garden.categoryHabits, Icons.Outlined.CheckCircle),
}

private data class GardenActivity(
    val id: String,
    val nameRes: StringResource,
    val descRes: StringResource,
    val icon: ImageVector,
    val accent: Color,
    val category: GardenCategory,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityGardenScreen(
    onBackPressed: () -> Unit = {},
    onMenuClicked: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
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
            GardenActivity("diary", Strings.Garden.Activity.diaryName, Strings.Garden.Activity.diaryDesc, Icons.Outlined.Edit, Color(0xFF2E7D55), GardenCategory.SCRITTURA, onWriteClick),
            GardenActivity("brain_dump", Strings.Garden.Activity.brainDumpName, Strings.Garden.Activity.brainDumpDesc, Icons.Outlined.Stream, Color(0xFF78909C), GardenCategory.SCRITTURA, onBrainDumpClick),
            GardenActivity("gratitude", Strings.Garden.Activity.gratitudeName, Strings.Garden.Activity.gratitudeDesc, Icons.Outlined.Favorite, Color(0xFFE91E63), GardenCategory.SCRITTURA, onGratitudeClick),
            // Mente
            GardenActivity("meditation", Strings.Garden.Activity.meditationName, Strings.Garden.Activity.meditationDesc, Icons.Outlined.SelfImprovement, Color(0xFF7E57C2), GardenCategory.MENTE, onMeditationClick),
            GardenActivity("reframing", Strings.Garden.Activity.reframingName, Strings.Garden.Activity.reframingDesc, Icons.Outlined.Psychology, Color(0xFF5C6BC0), GardenCategory.MENTE, onReframeClick),
            GardenActivity("blocks", Strings.Garden.Activity.blocksName, Strings.Garden.Activity.blocksDesc, Icons.Outlined.Extension, Color(0xFFEF6C00), GardenCategory.MENTE, onBlockClick),
            GardenActivity("recurring_thoughts", Strings.Garden.Activity.recurringThoughtsName, Strings.Garden.Activity.recurringThoughtsDesc, Icons.Outlined.BubbleChart, Color(0xFF00897B), GardenCategory.MENTE, onRecurringThoughtsClick),
            // Corpo
            GardenActivity("energy", Strings.Garden.Activity.energyName, Strings.Garden.Activity.energyDesc, Icons.Outlined.BatteryChargingFull, Color(0xFFFF9800), GardenCategory.CORPO, onEnergyClick),
            GardenActivity("sleep", Strings.Garden.Activity.sleepName, Strings.Garden.Activity.sleepDesc, Icons.Outlined.Bedtime, Color(0xFF5C6BC0), GardenCategory.CORPO, onSleepClick),
            GardenActivity("movement", Strings.Garden.Activity.movementName, Strings.Garden.Activity.movementDesc, Icons.AutoMirrored.Outlined.DirectionsRun, Color(0xFFEF6C00), GardenCategory.CORPO, onMovementClick),
            GardenActivity("dashboard", Strings.Garden.Activity.dashboardName, Strings.Garden.Activity.dashboardDesc, Icons.Outlined.Terrain, Color(0xFF26A69A), GardenCategory.CORPO, onDashboardClick),
            // Spirito
            GardenActivity("values", Strings.Garden.Activity.valuesName, Strings.Garden.Activity.valuesDesc, Icons.Outlined.Explore, Color(0xFF42A5F5), GardenCategory.SPIRITO, onValuesClick),
            GardenActivity("ikigai", Strings.Garden.Activity.ikigaiName, Strings.Garden.Activity.ikigaiDesc, Icons.Outlined.Interests, Color(0xFFAB47BC), GardenCategory.SPIRITO, onIkigaiClick),
            GardenActivity("awe", Strings.Garden.Activity.aweName, Strings.Garden.Activity.aweDesc, Icons.Outlined.Park, Color(0xFF66BB6A), GardenCategory.SPIRITO, onAweClick),
            GardenActivity("silence", Strings.Garden.Activity.silenceName, Strings.Garden.Activity.silenceDesc, Icons.Outlined.SelfImprovement, Color(0xFF78909C), GardenCategory.SPIRITO, onSilenceClick),
            GardenActivity("connections", Strings.Garden.Activity.connectionsName, Strings.Garden.Activity.connectionsDesc, Icons.Outlined.People, Color(0xFFE91E63), GardenCategory.SPIRITO, onConnectionClick),
            GardenActivity("inspiration", Strings.Garden.Activity.inspirationName, Strings.Garden.Activity.inspirationDesc, Icons.Outlined.FormatQuote, Color(0xFFFFCA28), GardenCategory.SPIRITO, onInspirationClick),
            // Abitudini
            GardenActivity("habits", Strings.Garden.Activity.habitsName, Strings.Garden.Activity.habitsDesc, Icons.Outlined.CheckCircle, Color(0xFF26A69A), GardenCategory.ABITUDINI, onHabitsClick),
            GardenActivity("environment", Strings.Garden.Activity.environmentName, Strings.Garden.Activity.environmentDesc, Icons.Outlined.Spa, Color(0xFF4CAF7D), GardenCategory.ABITUDINI, onEnvironmentClick),
        )
    }

    val filtered = remember(selectedCategory) {
        if (selectedCategory == GardenCategory.TUTTE) activities
        else activities.filter { it.category == selectedCategory }
    }

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Garden",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClicked) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(Strings.A11y.menu))
                    }
                },
                actions = {
                    IconButton(onClick = onNotificationsClick) {
                        Icon(Icons.Outlined.Notifications, contentDescription = stringResource(Strings.A11y.notifications))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    scrolledContainerColor = colorScheme.background
                ),
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
                                Text(stringResource(cat.labelRes), style = MaterialTheme.typography.labelMedium)
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
                            GardenSectionHeader(cat.labelRes, catActivities.size)
                        }
                        items(catActivities, key = { it.id }) { activity ->
                            GardenActivityCard(activity)
                        }
                    }
            } else {
                items(filtered, key = { it.id }) { activity ->
                    GardenActivityCard(activity)
                }
            }
        }
    }
}

@Composable
private fun GardenSectionHeader(labelRes: StringResource, count: Int) {
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
                text = stringResource(labelRes),
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
                        text = stringResource(activity.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(activity.descRes),
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
