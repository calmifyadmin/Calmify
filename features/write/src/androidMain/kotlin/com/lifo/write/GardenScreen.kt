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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ── Peaceful palette (same as IkigaiScreen) ──

private val PetalRose = Color(0xFFE8728B)
private val PetalSky = Color(0xFF5BA3D9)
private val PetalSage = Color(0xFF4CAF7D)
private val PetalSand = Color(0xFFE8A94D)
private val CenterGold = Color(0xFFD4C4A0)

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
    val name: String,
    val description: String,
    val longDescription: String,
    val benefits: List<String>,
    val estimatedMinutes: Int,
    val difficulty: GardenContract.Difficulty,
    val icon: ImageVector,
    val accent: Color,
    val category: GardenContract.Category,
)

private val allActivities = listOf(
    // Scrittura
    GardenActivityData(
        "diary", "Diario", "Scrivi i tuoi pensieri",
        "Scrivere i tuoi pensieri ti aiuta a elaborare emozioni e trovare chiarezza. Il diario e' il cuore del tuo percorso di crescita.",
        listOf("Riduce ansia", "Migliora autoconsapevolezza", "Traccia la crescita personale"),
        5, GardenContract.Difficulty.FACILE, Icons.Outlined.Edit, Color(0xFF2E7D55), GardenContract.Category.SCRITTURA,
    ),
    GardenActivityData(
        "braindump", "Brain Dump", "Svuota la mente",
        "Libera la mente da tutto cio' che la occupa, senza giudizio e senza struttura.",
        listOf("Riduce carico mentale", "Migliora focus", "Facilita il sonno"),
        3, GardenContract.Difficulty.FACILE, Icons.Outlined.Stream, Color(0xFF78909C), GardenContract.Category.SCRITTURA,
    ),
    GardenActivityData(
        "gratitude", "Gratitudine", "3 cose belle",
        "Riconoscere il bello quotidiano allena il cervello alla positivita'.",
        listOf("Aumenta felicita'", "Riduce stress", "Migliora relazioni"),
        3, GardenContract.Difficulty.FACILE, Icons.Outlined.Favorite, Color(0xFFE91E63), GardenContract.Category.SCRITTURA,
    ),
    // Mente
    GardenActivityData(
        "meditation", "Meditazione", "Respira e centra",
        "Pochi minuti di presenza trasformano la qualita' della giornata.",
        listOf("Riduce cortisolo", "Migliora concentrazione", "Aumenta resilienza"),
        10, GardenContract.Difficulty.MEDIO, Icons.Outlined.SelfImprovement, Color(0xFF7E57C2), GardenContract.Category.MENTE,
    ),
    GardenActivityData(
        "reframe", "Reframing", "Cambia prospettiva",
        "Cambiare prospettiva su un pensiero negativo ne riduce il potere.",
        listOf("Rompe schemi negativi", "Aumenta flessibilita' mentale", "Riduce ruminazione"),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.Psychology, Color(0xFF5C6BC0), GardenContract.Category.MENTE,
    ),
    GardenActivityData(
        "block", "Blocchi", "Riconosci gli ostacoli",
        "Riconoscere cosa ti blocca e' il primo passo per superarlo.",
        listOf("Aumenta consapevolezza", "Identifica pattern", "Sblocca azione"),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.Extension, Color(0xFFEF6C00), GardenContract.Category.MENTE,
    ),
    GardenActivityData(
        "recurring", "Pensieri Ricorrenti", "Osserva i pattern",
        "Osservare i pattern mentali ti da' potere su di essi.",
        listOf("Riduce ruminazione", "Identifica trigger", "Aumenta meta-cognizione"),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.BubbleChart, Color(0xFF00897B), GardenContract.Category.MENTE,
    ),
    // Corpo
    GardenActivityData(
        "energy", "Energia", "Come stai oggi?",
        "Monitorare i tuoi livelli di energia rivela i tuoi ritmi naturali.",
        listOf("Ottimizza produttivita'", "Previene burnout", "Migliora decisioni"),
        2, GardenContract.Difficulty.FACILE, Icons.Outlined.BatteryChargingFull, Color(0xFFFF9800), GardenContract.Category.CORPO,
    ),
    GardenActivityData(
        "sleep", "Sonno", "Traccia il riposo",
        "Il sonno e' il fondamento di ogni altra area del benessere.",
        listOf("Migliora umore", "Aumenta energia", "Consolida memoria"),
        2, GardenContract.Difficulty.FACILE, Icons.Outlined.Bedtime, Color(0xFF5C6BC0), GardenContract.Category.CORPO,
    ),
    GardenActivityData(
        "movement", "Movimento", "Registra attivita'",
        "Registrare il movimento ti motiva a muoverti di piu'.",
        listOf("Riduce sedentarieta'", "Migliora umore", "Aumenta energia"),
        2, GardenContract.Difficulty.FACILE, Icons.AutoMirrored.Outlined.DirectionsRun, Color(0xFFEF6C00), GardenContract.Category.CORPO,
    ),
    GardenActivityData(
        "dashboard", "Dashboard", "Panoramica corpo",
        "Visione d'insieme del tuo benessere fisico in un colpo d'occhio.",
        listOf("Visione olistica", "Identifica trend", "Motiva azione"),
        1, GardenContract.Difficulty.FACILE, Icons.Outlined.Terrain, Color(0xFF26A69A), GardenContract.Category.CORPO,
    ),
    // Spirito
    GardenActivityData(
        "values", "Valori", "Scopri cosa conta",
        "Scoprire i tuoi valori guida decisioni allineate alla tua essenza.",
        listOf("Chiarezza decisionale", "Aumenta soddisfazione", "Riduce conflitti interni"),
        10, GardenContract.Difficulty.AVANZATO, Icons.Outlined.Explore, Color(0xFF42A5F5), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "ikigai", "Ikigai", "Trova il tuo scopo",
        "Trovare l'intersezione tra passione, talento, missione e professione.",
        listOf("Scopri il tuo scopo", "Allinea vita e lavoro", "Aumenta motivazione"),
        15, GardenContract.Difficulty.AVANZATO, Icons.Outlined.Interests, Color(0xFFAB47BC), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "awe", "Awe", "Meraviglia quotidiana",
        "Coltivare meraviglia espande la percezione del tempo e dello spazio.",
        listOf("Riduce stress", "Aumenta generosita'", "Amplia prospettiva"),
        3, GardenContract.Difficulty.FACILE, Icons.Outlined.Park, Color(0xFF66BB6A), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "silence", "Silenzio", "Pratica il vuoto",
        "Il silenzio intenzionale rigenera la mente e apre spazio alla creativita'.",
        listOf("Riduce sovraccarico", "Migliora ascolto interiore", "Aumenta creativita'"),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.SelfImprovement, Color(0xFF78909C), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "connection", "Connessioni", "Relazioni che contano",
        "Le relazioni significative sono il miglior predittore di benessere a lungo termine.",
        listOf("Rafforza legami", "Riduce solitudine", "Aumenta senso di appartenenza"),
        5, GardenContract.Difficulty.FACILE, Icons.Outlined.People, Color(0xFFE91E63), GardenContract.Category.SPIRITO,
    ),
    GardenActivityData(
        "inspiration", "Ispirazione", "Raccogli spunti",
        "Raccogliere spunti nutre la crescita personale e apre nuove strade.",
        listOf("Stimola creativita'", "Amplia orizzonti", "Motiva azione"),
        3, GardenContract.Difficulty.FACILE, Icons.Outlined.FormatQuote, Color(0xFFFFCA28), GardenContract.Category.SPIRITO,
    ),
    // Abitudini
    GardenActivityData(
        "habits", "Abitudini", "Costruisci routine",
        "Piccole azioni ripetute creano grandi trasformazioni nel tempo.",
        listOf("Automatizza il miglioramento", "Costruisce disciplina", "Crea momentum"),
        5, GardenContract.Difficulty.MEDIO, Icons.Outlined.CheckCircle, Color(0xFF26A69A), GardenContract.Category.ABITUDINI,
    ),
    GardenActivityData(
        "environment", "Ambiente", "Disegna il contesto",
        "Il contesto in cui vivi influenza profondamente chi diventi.",
        listOf("Riduce frizione", "Aumenta benessere", "Supporta obiettivi"),
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
) {
    val categories = GardenContract.Category.entries
    val listState = rememberLazyListState()

    val totalActivities = allActivities.size
    val exploredCount = state.exploredIds.size

    Scaffold { padding ->
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
            // ── Ikigai Header with inline add ──
            item(key = "ikigai_header") {
                GardenIkigaiHeader(
                    exploration = state.ikigaiExploration,
                    selectedCircle = state.selectedIkigaiCircle,
                    inputText = state.ikigaiInput,
                    onCircleSelect = { onIntent(GardenContract.Intent.SelectIkigaiCircle(it)) },
                    onInputChange = { onIntent(GardenContract.Intent.UpdateIkigaiInput(it)) },
                    onAddItem = { onIntent(GardenContract.Intent.AddIkigaiItem) },
                    onIkigaiClick = { onActivityClick("ikigai") },
                )
            }

            // ── Global Progress ──
            item(key = "progress") {
                GardenGlobalProgress(
                    explored = exploredCount,
                    total = totalActivities,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
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
    exploration: com.lifo.util.model.IkigaiExploration?,
    selectedCircle: GardenContract.IkigaiCircle?,
    inputText: String,
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
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable { onIkigaiClick() }
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Il Tuo Ikigai",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        "Tocca per approfondire",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFAB47BC).copy(alpha = 0.10f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Interests,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFFAB47BC),
                        )
                    }
                }
            }

            // Petals diagram
            GardenIkigaiPetals(
                exploration = exploration,
                selectedCircle = selectedCircle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(horizontal = 8.dp),
            )

            // Minimal legend with colored dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                GardenContract.IkigaiCircle.entries.forEachIndexed { i, circle ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Canvas(Modifier.size(8.dp)) {
                            drawCircle(color = gardenPetalColors[i])
                        }
                        Text(
                            circle.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Circle selector — ExpressiveDailyChip style (tonal pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                            circleIcons[circle] ?: Icons.Outlined.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            circle.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Quick-add input row — only visible when a circle is selected
            if (selectedCircle != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Aggiungi a ${selectedCircle.displayName}...",
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
                                Icons.Default.Add,
                                contentDescription = "Aggiungi",
                                modifier = Modifier.size(20.dp),
                                tint = if (inputText.isNotBlank()) colorScheme.onPrimary
                                else colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Watercolor petals Venn diagram for the Garden header —
 * breathing animation, radial gradients, warm center glow.
 */
@Composable
private fun GardenIkigaiPetals(
    exploration: com.lifo.util.model.IkigaiExploration?,
    selectedCircle: GardenContract.IkigaiCircle?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "garden-ikigai")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    // -1 = none selected (all equal)
    val selectedIndex = selectedCircle?.ordinal ?: -1

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = size.minDimension * 0.33f
        val r = baseR * breathScale
        val spread = r * 0.36f

        val centers = listOf(
            Offset(cx - spread, cy - spread),
            Offset(cx + spread, cy - spread),
            Offset(cx - spread, cy + spread),
            Offset(cx + spread, cy + spread),
        )

        // Filled circles
        val noneSelected = selectedIndex == -1
        centers.forEachIndexed { i, center ->
            val isSelected = i == selectedIndex
            val coreAlpha = when {
                noneSelected -> 0.28f   // all equal, vivid
                isSelected -> 0.35f
                else -> 0.12f           // dimmed when another is selected
            }
            val edgeAlpha = when {
                noneSelected -> 0.10f
                isSelected -> 0.14f
                else -> 0.04f
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        gardenPetalColors[i].copy(alpha = coreAlpha),
                        gardenPetalColors[i].copy(alpha = edgeAlpha),
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )

            val strokeAlpha = when {
                noneSelected -> 0.30f
                isSelected -> 0.50f
                else -> 0.10f
            }
            val strokeWidth = when {
                noneSelected -> 1.4.dp.toPx()
                isSelected -> 2.dp.toPx()
                else -> 0.8.dp.toPx()
            }
            drawCircle(
                color = gardenPetalColors[i].copy(alpha = strokeAlpha),
                radius = r,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }

        // Center glow
        val glowR = r * 0.38f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CenterGold.copy(alpha = glowAlpha),
                    CenterGold.copy(alpha = glowAlpha * 0.3f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = glowR,
            ),
            radius = glowR,
            center = Offset(cx, cy),
        )

        // "IKIGAI" label at center
        val ikigaiResult = textMeasurer.measure(
            AnnotatedString("IKIGAI"),
            TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor.copy(alpha = 0.65f),
                letterSpacing = 1.5.sp,
            ),
        )
        drawText(
            ikigaiResult,
            topLeft = Offset(
                cx - ikigaiResult.size.width / 2f,
                cy - ikigaiResult.size.height / 2f,
            ),
        )

        // Numbers
        val counts = listOf(
            exploration?.passionItems?.size ?: 0,
            exploration?.talentItems?.size ?: 0,
            exploration?.missionItems?.size ?: 0,
            exploration?.professionItems?.size ?: 0,
        )

        counts.forEachIndexed { i, count ->
            val pushFactor = 0.52f
            val numPos = Offset(
                centers[i].x + (centers[i].x - cx) * pushFactor,
                centers[i].y + (centers[i].y - cy) * pushFactor,
            )

            val isHighlighted = noneSelected || i == selectedIndex
            val numResult = textMeasurer.measure(
                AnnotatedString("$count"),
                TextStyle(
                    fontSize = if (isHighlighted) 18.sp else 14.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    color = if (isHighlighted) gardenPetalColors[i]
                    else gardenPetalColors[i].copy(alpha = 0.35f),
                ),
            )
            drawText(
                numResult,
                topLeft = Offset(
                    numPos.x - numResult.size.width / 2f,
                    numPos.y - numResult.size.height / 2f,
                ),
            )
        }
    }
}

// ── Global Progress Bar ──

@Composable
private fun GardenGlobalProgress(
    explored: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = if (total > 0) explored.toFloat() / total else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$explored di $total attivita' esplorate",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceContainerHigh,
        )
    }
}

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
                text = category.label,
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
                                text = activity.name,
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
                            text = activity.description,
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
                            text = activity.longDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                            lineHeight = 20.sp,
                        )

                        // Benefits
                        Text(
                            "Benefici",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                        activity.benefits.forEach { benefit ->
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
                                    benefit,
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
                                    activity.difficulty.label,
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
                                "Inizia Attivita'",
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
