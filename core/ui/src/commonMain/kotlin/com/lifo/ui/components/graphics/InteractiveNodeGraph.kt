package com.lifo.ui.components.graphics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * InteractiveNodeGraph — Force-directed node graph with drag gestures.
 *
 * Nodes repel each other, edges act as springs.
 * User can tap nodes and drag them.
 */
data class GraphNode(
    val id: String,
    val label: String,
    val color: Color,
    val size: Float = 24f,
    val group: String = "",
)

data class GraphEdge(
    val fromId: String,
    val toId: String,
    val weight: Float = 1f,
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun InteractiveNodeGraph(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    modifier: Modifier = Modifier,
    onNodeTap: (GraphNode) -> Unit = {},
    selectedNodeId: String? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface

    // Node positions — mutable state
    val positions = remember(nodes.map { it.id }) {
        mutableStateMapOf<String, Offset>().apply {
            nodes.forEachIndexed { i, node ->
                val angle = 2.0 * PI * i / nodes.size
                put(node.id, Offset(
                    (300 + 150 * cos(angle)).toFloat(),
                    (300 + 150 * sin(angle)).toFloat(),
                ))
            }
        }
    }

    var draggedNodeId by remember { mutableStateOf<String?>(null) }

    // Force simulation
    val infiniteTransition = rememberInfiniteTransition(label = "force_sim")
    val tick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(50, easing = LinearEasing), RepeatMode.Restart),
        label = "tick",
    )

    // Apply forces each tick
    LaunchedEffect(tick) {
        if (nodes.size < 2) return@LaunchedEffect
        val repulsionStrength = 8000f
        val springStrength = 0.005f
        val springLength = 120f
        val damping = 0.85f

        val velocities = mutableMapOf<String, Offset>()
        nodes.forEach { velocities[it.id] = Offset.Zero }

        // Repulsion between all pairs
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val posA = positions[a.id] ?: continue
                val posB = positions[b.id] ?: continue
                val delta = posA - posB
                val dist = max(delta.getDistance(), 1f)
                val force = repulsionStrength / (dist * dist)
                val normalized = Offset(delta.x / dist, delta.y / dist)
                velocities[a.id] = (velocities[a.id] ?: Offset.Zero) + normalized * force
                velocities[b.id] = (velocities[b.id] ?: Offset.Zero) - normalized * force
            }
        }

        // Spring forces along edges
        edges.forEach { edge ->
            val posA = positions[edge.fromId] ?: return@forEach
            val posB = positions[edge.toId] ?: return@forEach
            val delta = posB - posA
            val dist = max(delta.getDistance(), 1f)
            val displacement = dist - springLength
            val force = springStrength * displacement * edge.weight
            val normalized = Offset(delta.x / dist, delta.y / dist)
            velocities[edge.fromId] = (velocities[edge.fromId] ?: Offset.Zero) + normalized * force
            velocities[edge.toId] = (velocities[edge.toId] ?: Offset.Zero) - normalized * force
        }

        // Apply velocities (skip dragged node)
        nodes.forEach { node ->
            if (node.id != draggedNodeId) {
                val pos = positions[node.id] ?: return@forEach
                val vel = (velocities[node.id] ?: Offset.Zero) * damping
                positions[node.id] = Offset(
                    (pos.x + vel.x).coerceIn(40f, 560f),
                    (pos.y + vel.y).coerceIn(40f, 560f),
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(nodes) {
                detectTapGestures { tapOffset ->
                    nodes.forEach { node ->
                        val pos = positions[node.id] ?: return@forEach
                        if ((tapOffset - pos).getDistance() < node.size + 12f) {
                            onNodeTap(node)
                            return@detectTapGestures
                        }
                    }
                }
            }
            .pointerInput(nodes) {
                detectDragGestures(
                    onDragStart = { offset ->
                        draggedNodeId = nodes.find { node ->
                            val pos = positions[node.id] ?: return@find false
                            (offset - pos).getDistance() < node.size + 12f
                        }?.id
                    },
                    onDrag = { change, _ ->
                        draggedNodeId?.let { id ->
                            positions[id] = change.position
                        }
                    },
                    onDragEnd = { draggedNodeId = null },
                    onDragCancel = { draggedNodeId = null },
                )
            }
    ) {
        // Draw edges
        edges.forEach { edge ->
            val from = positions[edge.fromId] ?: return@forEach
            val to = positions[edge.toId] ?: return@forEach
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = from,
                end = to,
                strokeWidth = 2f * edge.weight,
                cap = StrokeCap.Round,
            )
        }

        // Draw nodes
        nodes.forEach { node ->
            val pos = positions[node.id] ?: return@forEach
            val isSelected = node.id == selectedNodeId
            val radius = if (isSelected) node.size * 1.3f else node.size

            // Glow for selected
            if (isSelected) {
                drawCircle(
                    color = node.color.copy(alpha = 0.2f),
                    radius = radius + 8f,
                    center = pos,
                )
            }

            drawCircle(
                color = node.color,
                radius = radius,
                center = pos,
            )

            // Label
            val textResult = textMeasurer.measure(
                AnnotatedString(node.label),
                style = TextStyle(fontSize = 10.sp, color = labelColor),
            )
            drawText(
                textResult,
                topLeft = Offset(
                    pos.x - textResult.size.width / 2f,
                    pos.y + radius + 4f,
                ),
            )
        }
    }
}
