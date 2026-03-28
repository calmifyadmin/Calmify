package com.lifo.write

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Peaceful palette — muted, sage-cohesive tones
private val PetalRose = Color(0xFFE8728B)
private val PetalSky = Color(0xFF5BA3D9)
private val PetalSage = Color(0xFF4CAF7D)
private val PetalSand = Color(0xFFE8A94D)
private val CenterGold = Color(0xFFD4C4A0)

private val petalColors = listOf(PetalRose, PetalSky, PetalSage, PetalSand)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IkigaiScreen(
    state: IkigaiContract.State,
    onIntent: (IkigaiContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Il Tuo Ikigai", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
        ) {
            // Venn diagram — hero card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IkigaiPetals(
                            exploration = state.exploration,
                            selectedCircle = state.selectedCircle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                        )

                        // Minimal legend — 4 colored dots with circle names
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            IkigaiContract.Circle.entries.forEachIndexed { i, circle ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Canvas(Modifier.size(8.dp)) {
                                        drawCircle(color = petalColors[i])
                                    }
                                    Text(
                                        circle.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Circle selector — tonal pill style
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IkigaiContract.Circle.entries.forEachIndexed { i, circle ->
                        val isSelected = state.selectedCircle == circle
                        FilledTonalButton(
                            onClick = { onIntent(IkigaiContract.Intent.SelectCircle(circle)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSelected) petalColors[i].copy(alpha = 0.15f)
                                else colorScheme.surfaceContainerHigh,
                                contentColor = if (isSelected) petalColors[i]
                                else colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(
                                circle.icon(),
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
            }

            // Current circle prompt
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = petalColors[state.selectedCircle.ordinal].copy(alpha = 0.10f),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = state.selectedCircle.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = petalColors[state.selectedCircle.ordinal],
                                )
                            }
                        }
                        Text(
                            state.selectedCircle.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Items for selected circle
            val items = when (state.selectedCircle) {
                IkigaiContract.Circle.PASSION -> state.exploration?.passionItems ?: emptyList()
                IkigaiContract.Circle.TALENT -> state.exploration?.talentItems ?: emptyList()
                IkigaiContract.Circle.MISSION -> state.exploration?.missionItems ?: emptyList()
                IkigaiContract.Circle.PROFESSION -> state.exploration?.professionItems ?: emptyList()
            }

            if (items.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = petalColors[state.selectedCircle.ordinal].copy(alpha = 0.08f),
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = petalColors[state.selectedCircle.ordinal],
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        item,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        color = colorScheme.onSurface,
                                    )
                                    IconButton(
                                        onClick = {
                                            onIntent(IkigaiContract.Intent.RemoveItem(state.selectedCircle, index))
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Rimuovi",
                                            modifier = Modifier.size(16.dp),
                                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                                if (index < items.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 44.dp),
                                        color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add input
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.currentInput,
                        onValueChange = { onIntent(IkigaiContract.Intent.UpdateInput(it)) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Aggiungi...") },
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true,
                    )
                    Surface(
                        onClick = { onIntent(IkigaiContract.Intent.AddItem) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (state.currentInput.isNotBlank()) colorScheme.primary
                        else colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Aggiungi",
                                tint = if (state.currentInput.isNotBlank()) colorScheme.onPrimary
                                else colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IkigaiContract.Circle.icon(): ImageVector = when (this) {
    IkigaiContract.Circle.PASSION -> Icons.Outlined.Favorite
    IkigaiContract.Circle.TALENT -> Icons.Outlined.Star
    IkigaiContract.Circle.MISSION -> Icons.Outlined.Public
    IkigaiContract.Circle.PROFESSION -> Icons.Outlined.Work
}

/**
 * Peaceful Ikigai Venn diagram — watercolor petals with breathing animation
 * and a warm luminous center where all four circles meet.
 */
@Composable
private fun IkigaiPetals(
    exploration: com.lifo.util.model.IkigaiExploration?,
    selectedCircle: IkigaiContract.Circle,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Breathing animation — slow, meditative
    val infiniteTransition = rememberInfiniteTransition(label = "ikigai")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    // Center glow pulse — offset from breath for depth
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    // Highlight animation for selected circle
    val selectedHighlight by animateFloatAsState(
        targetValue = selectedCircle.ordinal.toFloat(),
        animationSpec = tween(400),
        label = "selected",
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = size.minDimension * 0.33f
        val r = baseR * breathScale
        val spread = r * 0.36f

        val centers = listOf(
            Offset(cx - spread, cy - spread),   // Passion (top-left)
            Offset(cx + spread, cy - spread),   // Talent (top-right)
            Offset(cx - spread, cy + spread),   // Mission (bottom-left)
            Offset(cx + spread, cy + spread),   // Profession (bottom-right)
        )

        // Filled circles — visible, warm tones
        centers.forEachIndexed { i, center ->
            val isSelected = i == selectedHighlight.toInt()
            val coreAlpha = if (isSelected) 0.30f else 0.22f
            val edgeAlpha = if (isSelected) 0.12f else 0.08f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        petalColors[i].copy(alpha = coreAlpha),
                        petalColors[i].copy(alpha = edgeAlpha),
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )

            // Stroke
            val strokeAlpha = if (isSelected) 0.45f else 0.22f
            drawCircle(
                color = petalColors[i].copy(alpha = strokeAlpha),
                radius = r,
                center = center,
                style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.2.dp.toPx()),
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

        // Numbers — positioned in each petal's quiet zone (away from center)
        val counts = listOf(
            exploration?.passionItems?.size ?: 0,
            exploration?.talentItems?.size ?: 0,
            exploration?.missionItems?.size ?: 0,
            exploration?.professionItems?.size ?: 0,
        )

        counts.forEachIndexed { i, count ->
            // Push numbers outward into each circle's unique territory
            val pushFactor = 0.52f
            val numPos = Offset(
                centers[i].x + (centers[i].x - cx) * pushFactor,
                centers[i].y + (centers[i].y - cy) * pushFactor,
            )

            val isSelectedNum = i == selectedHighlight.toInt()
            val numResult = textMeasurer.measure(
                AnnotatedString("$count"),
                TextStyle(
                    fontSize = if (isSelectedNum) 20.sp else 17.sp,
                    fontWeight = if (isSelectedNum) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelectedNum) petalColors[i]
                    else petalColors[i].copy(alpha = 0.65f),
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
