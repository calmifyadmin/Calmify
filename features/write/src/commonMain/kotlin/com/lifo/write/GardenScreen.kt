package com.lifo.write

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lifo.ui.components.CalmifyTopBar
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

// ── Halo palette (matches `C:\Design\Calmify\Meditation\Ikigai.html` 1:1) ──
// Soft watercolor pastels — pink (passion), sky (talent), teal-sage (mission), gold (profession).
// The center medallion uses Calmify's accent sage `#4CAF7D` for stroke and
// `#A8F0CB` (accent-bright) for the IKIGAI label / spa icon glyph.

private val PetalRose = Color(0xFFE08CA9)   // Passion
private val PetalSky = Color(0xFF8FB6E0)    // Talent
private val PetalSage = Color(0xFF7CC9A4)   // Mission
private val PetalSand = Color(0xFFE0B884)   // Profession
private val IkigaiAccent = Color(0xFF4CAF7D)        // Center medallion stroke
private val IkigaiAccentBright = Color(0xFFA8F0CB)  // Center spa + IKIGAI label

private val gardenPetalColors = listOf(PetalRose, PetalSky, PetalSage, PetalSand)

private val circleIcons = mapOf(
    GardenContract.IkigaiCircle.PASSION to Icons.Outlined.Favorite,
    GardenContract.IkigaiCircle.TALENT to Icons.Outlined.Star,
    GardenContract.IkigaiCircle.MISSION to Icons.Outlined.Public,
    GardenContract.IkigaiCircle.PROFESSION to Icons.Outlined.Work,
)

// ── Activity catalog (static data) ──

private data class GardenActivityData(
    val id: String,
    val nameRes: StringResource,
    val descriptionRes: StringResource,
    val longDescriptionRes: StringResource,
    val benefitsRes: List<StringResource>,
    val estimatedMinutes: Int,
    val difficulty: GardenContract.Difficulty,
    val icon: ImageVector,
    val accent: Color,
    val category: GardenContract.Category,
)

private val allActivities = listOf(
    // Scrittura
    GardenActivityData(
        "diary", Strings.Garden.Activity.diaryName, Strings.Garden.Activity.diaryDesc,
        Strings.GardenCard.diaryLong,
        listOf(Strings.GardenCard.diaryB1, Strings.GardenCard.diaryB2, Strings.GardenCard.diaryB3),
        5, GardenContract.Difficulty.FACILE, Icons.Outlined.Edit, Color(0xFF2E7D55), GardenContract.Category.SCRITTURA,
    ),
    GardenActivityData(
        "braindump", Strings.Garden.Activity.brainDumpName, Strings.Garden.Activity.brainDumpDesc,
        Strings.GardenCard.brainDumpLong,
        listOf(Strings.GardenCard.brainDumpB1, Strings.GardenCard.brainDumpB2, Strings.GardenCard.brainDumpB3),
        3, GardenContract.Difficulty.FACILE, Icons.Outlined.Stream, Color(0xFF78909C), GardenContract.Category.SCRITTURA,
    ),
    GardenActivityData(
        "gratitude", Strings.Garden.Activity.gratitudeName, Strings.Garden.Activity.gratitudeDesc,
        Strings.GardenCard.gratitudeLong,
        listOf(Strings.GardenCard.gratitudeB1, Strings.GardenCard.gratitudeB2, Strings.GardenCard.gratitudeB3),
        3, GardenContract.Difficulty.FACILE, Icons.Outlined.Favorite, Color(0xFFE91E63), GardenContract.Category.SCRITTURA,
    ),
    // Mente
    GardenActivityData(
        "meditation", Strings.Garden.Activity.meditationName, Strings.Garden.Activity.meditationDesc,
        Strings.GardenCard.meditationLong,
        listOf(Strings.GardenCard.meditationB1, Strings.GardenCard.meditationB2, Strings.GardenCard.meditationB3),
        10, GardenContract.Difficulty.MEDIO, Icons.Outlined.SelfImprovement, Color(0xFF7E57C2), GardenContract.Category.MENTE,
    ),
    GardenActivityData(
        "reframe", Strings.Garden.Activity.reframingName, Strings.Garden.Activity.reframingDesc,
        Strings.GardenCard.reframingLong,
        listOf(Strings.GardenCard.reframingB1, Strings.GardenCard.reframingB2, Strings.GardenCard.reframingB3),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.Psychology, Color(0xFF5C6BC0), GardenContract.Category.MENTE,
    ),
    GardenActivityData(
        "block", Strings.Garden.Activity.blocksName, Strings.Garden.Activity.blocksDesc,
        Strings.GardenCard.blocksLong,
        listOf(Strings.GardenCard.blocksB1, Strings.GardenCard.blocksB2, Strings.GardenCard.blocksB3),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.Extension, Color(0xFFEF6C00), GardenContract.Category.MENTE,
    ),
    GardenActivityData(
        "recurring", Strings.Garden.Activity.recurringThoughtsName, Strings.Garden.Activity.recurringThoughtsDesc,
        Strings.GardenCard.recurringThoughtsLong,
        listOf(Strings.GardenCard.recurringThoughtsB1, Strings.GardenCard.recurringThoughtsB2, Strings.GardenCard.recurringThoughtsB3),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.BubbleChart, Color(0xFF00897B), GardenContract.Category.MENTE,
    ),
    // Corpo
    GardenActivityData(
        "energy", Strings.Garden.Activity.energyName, Strings.Garden.Activity.energyDesc,
        Strings.GardenCard.energyLong,
        listOf(Strings.GardenCard.energyB1, Strings.GardenCard.energyB2, Strings.GardenCard.energyB3),
        2, GardenContract.Difficulty.FACILE, Icons.Outlined.BatteryChargingFull, Color(0xFFFF9800), GardenContract.Category.CORPO,
    ),
    GardenActivityData(
        "sleep", Strings.Garden.Activity.sleepName, Strings.Garden.Activity.sleepDesc,
        Strings.GardenCard.sleepLong,
        listOf(Strings.GardenCard.sleepB1, Strings.GardenCard.sleepB2, Strings.GardenCard.sleepB3),
        2, GardenContract.Difficulty.FACILE, Icons.Outlined.Bedtime, Color(0xFF5C6BC0), GardenContract.Category.CORPO,
    ),
    GardenActivityData(
        "movement", Strings.Garden.Activity.movementName, Strings.Garden.Activity.movementDesc,
        Strings.GardenCard.movementLong,
        listOf(Strings.GardenCard.movementB1, Strings.GardenCard.movementB2, Strings.GardenCard.movementB3),
        2, GardenContract.Difficulty.FACILE, Icons.AutoMirrored.Outlined.DirectionsRun, Color(0xFFEF6C00), GardenContract.Category.CORPO,
    ),
    GardenActivityData(
        "dashboard", Strings.Garden.Activity.dashboardName, Strings.Garden.Activity.dashboardDesc,
        Strings.GardenCard.dashboardLong,
        listOf(Strings.GardenCard.dashboardB1, Strings.GardenCard.dashboardB2, Strings.GardenCard.dashboardB3),
        1, GardenContract.Difficulty.FACILE, Icons.Outlined.Terrain, Color(0xFF26A69A), GardenContract.Category.CORPO,
    ),
    // Spirito
    GardenActivityData(
        "values", Strings.Garden.Activity.valuesName, Strings.Garden.Activity.valuesDesc,
        Strings.GardenCard.valuesLong,
        listOf(Strings.GardenCard.valuesB1, Strings.GardenCard.valuesB2, Strings.GardenCard.valuesB3),
        10, GardenContract.Difficulty.AVANZATO, Icons.Outlined.Explore, Color(0xFF42A5F5), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "ikigai", Strings.Garden.Activity.ikigaiName, Strings.Garden.Activity.ikigaiDesc,
        Strings.GardenCard.ikigaiLong,
        listOf(Strings.GardenCard.ikigaiB1, Strings.GardenCard.ikigaiB2, Strings.GardenCard.ikigaiB3),
        15, GardenContract.Difficulty.AVANZATO, Icons.Outlined.Interests, Color(0xFFAB47BC), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "awe", Strings.Garden.Activity.aweName, Strings.Garden.Activity.aweDesc,
        Strings.GardenCard.aweLong,
        listOf(Strings.GardenCard.aweB1, Strings.GardenCard.aweB2, Strings.GardenCard.aweB3),
        3, GardenContract.Difficulty.FACILE, Icons.Outlined.Park, Color(0xFF66BB6A), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "silence", Strings.Garden.Activity.silenceName, Strings.Garden.Activity.silenceDesc,
        Strings.GardenCard.silenceLong,
        listOf(Strings.GardenCard.silenceB1, Strings.GardenCard.silenceB2, Strings.GardenCard.silenceB3),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.SelfImprovement, Color(0xFF78909C), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "connection", Strings.Garden.Activity.connectionsName, Strings.Garden.Activity.connectionsDesc,
        Strings.GardenCard.connectionsLong,
        listOf(Strings.GardenCard.connectionsB1, Strings.GardenCard.connectionsB2, Strings.GardenCard.connectionsB3),
        5, GardenContract.Difficulty.FACILE, Icons.Outlined.People, Color(0xFFE91E63), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "inspiration", Strings.Garden.Activity.inspirationName, Strings.Garden.Activity.inspirationDesc,
        Strings.GardenCard.inspirationLong,
        listOf(Strings.GardenCard.inspirationB1, Strings.GardenCard.inspirationB2, Strings.GardenCard.inspirationB3),
        3, GardenContract.Difficulty.FACILE, Icons.Outlined.FormatQuote, Color(0xFFFFCA28), GardenContract.Category.SPIRITO,
    ),
    // Abitudini
    GardenActivityData(
        "habits", Strings.Garden.Activity.habitsName, Strings.Garden.Activity.habitsDesc,
        Strings.GardenCard.habitsLong,
        listOf(Strings.GardenCard.habitsB1, Strings.GardenCard.habitsB2, Strings.GardenCard.habitsB3),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.CheckCircle, Color(0xFF26A69A), GardenContract.Category.ABITUDINI,
    ),
    GardenActivityData(
        "environment", Strings.Garden.Activity.environmentName, Strings.Garden.Activity.environmentDesc,
        Strings.GardenCard.environmentLong,
        listOf(Strings.GardenCard.environmentB1, Strings.GardenCard.environmentB2, Strings.GardenCard.environmentB3),
        10, GardenContract.Difficulty.AVANZATO, Icons.Outlined.Spa, Color(0xFF4CAF7D), GardenContract.Category.ABITUDINI,
    ),
)

private val categoryColors = mapOf(
    GardenContract.Category.SCRITTURA to Color(0xFF2E7D55),
    GardenContract.Category.MENTE to Color(0xFF7E57C2),
    GardenContract.Category.CORPO to Color(0xFFFF9800),
    GardenContract.Category.SPIRITO to Color(0xFF42A5F5),
    GardenContract.Category.ABITUDINI to Color(0xFF26A69A),
)

private val categoryIcons = mapOf(
    GardenContract.Category.SCRITTURA to Icons.Outlined.Edit,
    GardenContract.Category.MENTE to Icons.Outlined.Psychology,
    GardenContract.Category.CORPO to Icons.AutoMirrored.Outlined.DirectionsRun,
    GardenContract.Category.SPIRITO to Icons.Outlined.Spa,
    GardenContract.Category.ABITUDINI to Icons.Outlined.CheckCircle,
)

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(
    state: GardenContract.State,
    onIntent: (GardenContract.Intent) -> Unit,
    onActivityClick: (String) -> Unit,
    onMenuClicked: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val categories = GardenContract.Category.entries
    val listState = rememberLazyListState()

    val totalActivities = allActivities.size
    val exploredCount = state.exploredIds.size

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colorScheme.background,
        topBar = {
            CalmifyTopBar(title = "Garden", scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            // ── Ikigai Header (halo + prompt + selector + add input + progress) ──
            // Progress footer is now inside the card per the design source
            // (`Ikigai.html` `progress-footer`); no separate progress item below.
            item(key = "ikigai_header") {
                GardenIkigaiHeader(
                    exploration = state.ikigaiExploration,
                    selectedCircle = state.selectedIkigaiCircle,
                    inputText = state.ikigaiInput,
                    exploredCount = exploredCount,
                    totalActivities = totalActivities,
                    onCircleSelect = { onIntent(GardenContract.Intent.SelectIkigaiCircle(it)) },
                    onInputChange = { onIntent(GardenContract.Intent.UpdateIkigaiInput(it)) },
                    onAddItem = { onIntent(GardenContract.Intent.AddIkigaiItem) },
                    onIkigaiClick = { onActivityClick("ikigai") },
                )
            }

            // ── Sections by category ──
            categories.forEach { category ->
                val catActivities = allActivities.filter { it.category == category }
                val catExplored = catActivities.count { it.id in state.exploredIds }

                item(key = "header_${category.name}") {
                    GardenSectionHeaderEnhanced(
                        category = category,
                        explored = catExplored,
                        total = catActivities.size,
                    )
                }

                items(catActivities, key = { it.id }) { activity ->
                    GardenExpandableCard(
                        activity = activity,
                        isExplored = activity.id in state.exploredIds,
                        isFavorite = activity.id in state.favoriteIds,
                        isExpanded = state.expandedId == activity.id,
                        onExpandToggle = { onIntent(GardenContract.Intent.ToggleExpand(activity.id)) },
                        onFavoriteToggle = { onIntent(GardenContract.Intent.ToggleFavorite(activity.id)) },
                        onStartClick = {
                            onIntent(GardenContract.Intent.MarkExplored(activity.id))
                            onActivityClick(activity.id)
                        },
                    )
                }
            }
        }
    }
}

// ── Ikigai Header — watercolor petals + inline quick-add ──

@Composable
private fun GardenIkigaiHeader(
    @Suppress("UNUSED_PARAMETER") exploration: com.lifo.util.model.IkigaiExploration?,
    selectedCircle: GardenContract.IkigaiCircle?,
    inputText: String,
    exploredCount: Int,
    totalActivities: Int,
    onCircleSelect: (GardenContract.IkigaiCircle) -> Unit,
    onInputChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onIkigaiClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Card head: title + subtitle + spa chip ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onIkigaiClick() },
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Strings.Garden.ikigaiTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Strings.Garden.ikigaiCardSubtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFA864E8).copy(alpha = 0.18f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Spa,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFFC9A4F0),
                        )
                    }
                }
            }

            // ── Halo chart ─────────────────────────────────────────────────
            GardenIkigaiHalo(
                selectedCircle = selectedCircle,
                onTagClick = onCircleSelect,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Halo prompt ────────────────────────────────────────────────
            Text(
                text = stringResource(Strings.Garden.ikigaiHaloPrompt),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            // ── Circle selector pills (functional — tag clicks already select,
            //    but pills provide a more discoverable alternative entry point
            //    on smaller screens where tags can be tight to tap) ──────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GardenContract.IkigaiCircle.entries.forEachIndexed { i, circle ->
                    val isSelected = selectedCircle == circle
                    FilledTonalButton(
                        onClick = { onCircleSelect(circle) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSelected) gardenPetalColors[i].copy(alpha = 0.15f)
                            else colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) gardenPetalColors[i]
                            else colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = circleIcons[circle] ?: Icons.Outlined.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(circle.displayNameRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }

            // ── Quick-add input — only visible when a circle is selected ──
            if (selectedCircle != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = stringResource(
                                    Strings.Garden.ikigaiAddToTemplate,
                                    stringResource(selectedCircle.displayNameRes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (inputText.isNotBlank()) onAddItem() },
                        ),
                    )
                    Surface(
                        onClick = { if (inputText.isNotBlank()) onAddItem() },
                        shape = RoundedCornerShape(16.dp),
                        color = if (inputText.isNotBlank()) colorScheme.primary
                        else colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(Strings.Action.add),
                                modifier = Modifier.size(20.dp),
                                tint = if (inputText.isNotBlank()) colorScheme.onPrimary
                                else colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Progress footer (matches design `progress-footer`) ──
            GardenProgressFooter(
                explored = exploredCount,
                total = totalActivities,
            )
        }
    }
}

/** Garden activity progress — bottom of the Ikigai card. Matches design `progress-footer`. */
@Composable
private fun GardenProgressFooter(
    explored: Int,
    total: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = if (total > 0) explored.toFloat() / total else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ikigaiCardProgress",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Strings.Garden.ikigaiProgressLabel, explored, total),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceContainerHigh,
        )
    }
}

/**
 * Halo Venn diagram for the Garden Ikigai header — 1:1 with the Claude
 * Design source `C:\Design\Calmify\Meditation\Ikigai.html`.
 *
 * Anatomy:
 * - **4 bloom halos** in a 2×2 grid (passion / talent / mission / profession),
 *   each a soft radial gradient drawn with `BlendMode.Plus` for the watercolor
 *   feel of overlapping petals.
 * - **4 thin outline rings** — one per bloom, faint at rest, brightened when
 *   that pillar's tag is the selected one (mirrors the design's CSS `:has()`
 *   hover state, but driven by `selectedCircle` state).
 * - **Center medallion** — dark circle with the Calmify accent sage stroke +
 *   a fainter inner ring at half the radius. Houses the spa icon glyph + the
 *   letter-spaced "IKIGAI" label.
 * - **4 pillar tag pills** positioned via `BiasAlignment` at the design's
 *   exact percentages (23.75% / 76.25% × 26.59% / 73.81%). Tap to select the
 *   corresponding pillar — selection animates the bloom out (r 64 → 76)
 *   and brightens the matching ring.
 *
 * The chart's intrinsic viewBox is 240×252 (matches the design); we honor
 * that via `aspectRatio(240f/252f)` so all geometry stays proportional
 * regardless of the parent's width.
 *
 * Per the design's restraint — no breathing animation, no glow pulse, no
 * count badges. Counts live in the IkigaiScreen detail view; the chart's
 * job is to invite engagement, not display data.
 */
@Composable
private fun GardenIkigaiHalo(
    selectedCircle: GardenContract.IkigaiCircle?,
    onTagClick: (GardenContract.IkigaiCircle) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Theme-adaptive watercolor: in dark mode we want overlaps to LIGHTEN
    // (the halos brighten the dark BG; medallion stays dark to punch through).
    // In light mode we need the opposite — overlaps DARKEN, medallion stays
    // light. Without the theme branch, `BlendMode.Plus` on a white surface
    // saturates to white and the halos disappear (the user-reported bug).
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val haloBlendMode = if (isLight) BlendMode.Multiply else BlendMode.Plus
    // Light mode needs higher alpha because Multiply on faint pastels barely
    // darkens; dark mode needs less because Plus over dark BG amplifies fast.
    val coreAlpha = if (isLight) 0.55f else 0.42f
    val midAlpha = if (isLight) 0.18f else 0.10f
    val medallionFill = MaterialTheme.colorScheme.surfaceContainerLow

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(IKIGAI_VIEWBOX_W / IKIGAI_VIEWBOX_H)
    ) {
        val pillarOrder = remember {
            listOf(
                GardenContract.IkigaiCircle.PASSION,
                GardenContract.IkigaiCircle.TALENT,
                GardenContract.IkigaiCircle.MISSION,
                GardenContract.IkigaiCircle.PROFESSION,
            )
        }

        // Animate bloom radius + ring opacity per pillar based on selection.
        // Matches the design's CSS transitions (360ms r, 320ms opacity) — we
        // run them in parallel via Compose animations.
        val animatedBloomR = pillarOrder.map { pillar ->
            animateFloatAsState(
                targetValue = if (selectedCircle == pillar) IKIGAI_BLOOM_R_HOVER else IKIGAI_BLOOM_R_REST,
                animationSpec = tween(durationMillis = 360, easing = EaseInOutCubic),
                label = "bloom_${pillar.name}",
            ).value
        }
        val animatedRingOpacity = pillarOrder.map { pillar ->
            animateFloatAsState(
                targetValue = if (selectedCircle == pillar) IKIGAI_RING_OPACITY_HOVER else IKIGAI_RING_OPACITY_REST,
                animationSpec = tween(durationMillis = 320, easing = EaseInOutCubic),
                label = "ring_${pillar.name}",
            ).value
        }
        val animatedRingStroke = pillarOrder.map { pillar ->
            animateFloatAsState(
                targetValue = if (selectedCircle == pillar) IKIGAI_RING_STROKE_HOVER_DP else IKIGAI_RING_STROKE_REST_DP,
                animationSpec = tween(durationMillis = 320, easing = EaseInOutCubic),
                label = "ringStroke_${pillar.name}",
            ).value
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val sx = size.width / IKIGAI_VIEWBOX_W
            val sy = size.height / IKIGAI_VIEWBOX_H

            fun pt(x: Float, y: Float) = Offset(x * sx, y * sy)
            fun radius(r: Float) = r * sx

            // Center medallion is anchored at viewBox (120, 126).
            val medallionCenter = pt(120f, 126f)

            // Bloom halos — one per pillar. Theme-adaptive blend mode:
            // - Dark surfaces: Plus (additive — halos lighten the dark BG, overlaps brighten)
            // - Light surfaces: Multiply (subtractive — halos tint the light BG, overlaps darken)
            // Both achieve the watercolor "petals meet" effect from the design.
            IKIGAI_BLOOM_CENTERS.forEachIndexed { i, (bx, by) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            gardenPetalColors[i].copy(alpha = coreAlpha),
                            gardenPetalColors[i].copy(alpha = midAlpha),
                            Color.Transparent,
                        ),
                        center = pt(bx, by),
                        radius = radius(animatedBloomR[i]),
                    ),
                    radius = radius(animatedBloomR[i]),
                    center = pt(bx, by),
                    blendMode = haloBlendMode,
                )
            }

            // Outline rings — kept thin to preserve the Venn structure
            // (matches design `circle.ring` stroke 0.6, opacity 0.18 default).
            IKIGAI_BLOOM_CENTERS.forEachIndexed { i, (bx, by) ->
                drawCircle(
                    color = gardenPetalColors[i].copy(alpha = animatedRingOpacity[i]),
                    radius = radius(IKIGAI_RING_R),
                    center = pt(bx, by),
                    style = Stroke(width = animatedRingStroke[i].dp.toPx()),
                )
            }

            // Center medallion: theme-aware fill + sage stroke + faint inner ring.
            // Fill matches the parent card's elevated surface so the medallion
            // visually "punches" the overlapping blooms — dark in dark mode,
            // a slightly elevated panel in light mode.
            drawCircle(
                color = medallionFill,
                radius = radius(IKIGAI_MEDALLION_R),
                center = medallionCenter,
            )
            drawCircle(
                color = IkigaiAccent,
                radius = radius(IKIGAI_MEDALLION_R),
                center = medallionCenter,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawCircle(
                color = IkigaiAccent.copy(alpha = 0.4f),
                radius = radius(IKIGAI_MEDALLION_INNER_R),
                center = medallionCenter,
                style = Stroke(width = 0.5.dp.toPx()),
            )
        }

        // Center label: spa icon + letter-spaced "IKIGAI"
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Spa,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = IkigaiAccentBright,
            )
            Text(
                text = "IKIGAI",
                color = IkigaiAccentBright,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }

        // 4 pillar tags — positioned via BiasAlignment to the design's exact
        // percentages. BiasAlignment(-1=start, +1=end, 0=center).
        pillarOrder.forEachIndexed { i, pillar ->
            val (xPct, yPct) = IKIGAI_TAG_POSITIONS[i]
            // Convert percentage → bias: bias = (pct - 0.5) / 0.5 = pct * 2 - 1
            PillarTag(
                pillar = pillar,
                color = gardenPetalColors[i],
                onClick = { onTagClick(pillar) },
                modifier = Modifier.align(
                    androidx.compose.ui.BiasAlignment(
                        horizontalBias = xPct * 2f - 1f,
                        verticalBias = yPct * 2f - 1f,
                    )
                ),
            )
        }
    }
}

/** Pillar tag pill — colored dot + label, design-styled outlined chip. */
@Composable
private fun PillarTag(
    pillar: GardenContract.IkigaiCircle,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = colorScheme.surface.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(Modifier.size(6.dp)) {
                drawCircle(color = color)
            }
            Text(
                text = stringResource(pillar.displayNameRes),
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp,
            )
        }
    }
}

// ── Halo geometry constants (viewBox 240×252, matches the design source) ──

private const val IKIGAI_VIEWBOX_W = 240f
private const val IKIGAI_VIEWBOX_H = 252f

/** Bloom halo centers in viewBox units. Top-left (passion), top-right (talent), bottom-left (mission), bottom-right (profession). */
private val IKIGAI_BLOOM_CENTERS = listOf(
    85f to 95f,    // PASSION
    155f to 95f,   // TALENT
    85f to 158f,   // MISSION
    155f to 158f,  // PROFESSION
)

/** Resting bloom radius (viewBox units) — matches `circle.bloom { r="64" }`. */
private const val IKIGAI_BLOOM_R_REST = 64f

/** Selected bloom radius — matches `:has(.halo-tag.X:hover) .bloom.X { r: 76 }`. */
private const val IKIGAI_BLOOM_R_HOVER = 76f

/** Outline ring radius — fixed at 58 viewBox units regardless of bloom expansion. */
private const val IKIGAI_RING_R = 58f

/** Outline ring resting opacity — matches `.ring { opacity: 0.18 }`. */
private const val IKIGAI_RING_OPACITY_REST = 0.18f

/** Outline ring selected opacity — matches `.ring.X { opacity: 0.85 }`. */
private const val IKIGAI_RING_OPACITY_HOVER = 0.85f

/** Outline ring stroke width in dp at rest (design uses 0.6 in viewBox units; we approximate to dp here). */
private const val IKIGAI_RING_STROKE_REST_DP = 0.6f

/** Outline ring stroke width when selected. */
private const val IKIGAI_RING_STROKE_HOVER_DP = 1.0f

/** Medallion outer radius (viewBox) — matches `<circle r="26">`. */
private const val IKIGAI_MEDALLION_R = 26f

/** Medallion inner faint ring radius — matches `<circle r="18">`. */
private const val IKIGAI_MEDALLION_INNER_R = 18f

/** Tag positions (xPct, yPct) of the parent halo-stage. Matches the design's inline `style="left: X%; top: Y%;"`. */
private val IKIGAI_TAG_POSITIONS = listOf(
    0.2375f to 0.2659f,  // PASSION
    0.7625f to 0.2659f,  // TALENT
    0.2375f to 0.7381f,  // MISSION
    0.7625f to 0.7381f,  // PROFESSION
)

// ── Section Header with mini progress ──

@Composable
private fun GardenSectionHeaderEnhanced(
    category: GardenContract.Category,
    explored: Int,
    total: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = categoryColors[category] ?: colorScheme.primary
    val icon = categoryIcons[category] ?: Icons.Outlined.GridView

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accent,
            )
            Text(
                text = stringResource(category.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Mini progress
            LinearProgressIndicator(
                progress = { if (total > 0) explored.toFloat() / total else 0f },
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.12f),
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "$explored/$total",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ── Expandable Activity Card ──

@Composable
private fun GardenExpandableCard(
    activity: GardenActivityData,
    isExplored: Boolean,
    isFavorite: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onStartClick: () -> Unit,
) {
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
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    )
                ),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surfaceContainerLow,
            tonalElevation = if (isExpanded) 2.dp else 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onExpandToggle() },
            ) {
                // ── Top row (always visible) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Colored icon pill
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(activity.nameRes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                            )
                            // Tag: "nuovo" if not explored
                            if (!isExplored) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "nuovo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(activity.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    Icon(
                        if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Chiudi" else "Espandi",
                        modifier = Modifier.size(20.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }

                // ── Expanded content ──
                if (isExpanded) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Long description
                        Text(
                            text = stringResource(activity.longDescriptionRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                            lineHeight = 20.sp,
                        )

                        // Benefits
                        Text(
                            stringResource(Strings.GardenCard.benefitsHeader),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                        activity.benefitsRes.forEach { benefitRes ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = activity.accent.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .size(6.dp)
                                        .offset(y = 6.dp),
                                ) {}
                                Text(
                                    stringResource(benefitRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Metadata row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "~${activity.estimatedMinutes} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(activity.difficulty.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            // Favorite toggle
                            IconButton(
                                onClick = onFavoriteToggle,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (isFavorite) "Rimuovi preferito" else "Aggiungi preferito",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isFavorite) Color(0xFFE91E63) else colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // CTA Button
                        FilledTonalButton(
                            onClick = onStartClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = activity.accent.copy(alpha = 0.12f),
                                contentColor = activity.accent,
                            ),
                        ) {
                            Text(
                                stringResource(Strings.GardenCard.startActivity),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
