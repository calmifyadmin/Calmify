package com.lifo.write

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.lifo.ui.components.graphics.RadarAxis
import com.lifo.ui.components.graphics.RadarChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardContract.State,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Il Tuo Terreno") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Terrain level card
            TerrainLevelCard(level = state.terrainLevel)

            // Wellbeing radar
            val energiaNorm = (state.avgEnergy / 10f).coerceIn(0f, 1f)
            val sonnoNorm = (state.avgSleepHours / 8f).coerceIn(0f, 1f)
            val movimentoNorm = (state.movementDays.toFloat() / state.totalDays.coerceAtLeast(1)).coerceIn(0f, 1f)
            val idratazione = (state.avgWaterGlasses / 8f).coerceIn(0f, 1f)
            val umoreNorm = ((energiaNorm + sonnoNorm + movimentoNorm + idratazione) / 4f).coerceIn(0f, 1f)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Panoramica Benessere",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    RadarChart(
                        axes = listOf(
                            RadarAxis("Energia", energiaNorm),
                            RadarAxis("Sonno", sonnoNorm),
                            RadarAxis("Movimento", movimentoNorm),
                            RadarAxis("Idratazione", idratazione),
                            RadarAxis("Umore", umoreNorm),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        strokeColor = MaterialTheme.colorScheme.primary,
                        gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }

            // Metrics grid
            Text(
                "Metriche Settimanali",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            MetricRow(
                icon = Icons.Default.Bedtime,
                label = "Sonno",
                value = "${formatFloat(state.avgSleepHours)}h",
                progress = (state.avgSleepHours / 8f).coerceIn(0f, 1f),
                target = "target 8h",
            )
            MetricRow(
                icon = Icons.Default.BatteryChargingFull,
                label = "Energia",
                value = formatFloat(state.avgEnergy),
                progress = (state.avgEnergy / 10f).coerceIn(0f, 1f),
                target = "/10",
            )
            MetricRow(
                icon = Icons.Default.WaterDrop,
                label = "Acqua",
                value = "${formatFloat(state.avgWaterGlasses)} bicchieri",
                progress = (state.avgWaterGlasses / 8f).coerceIn(0f, 1f),
                target = "target 8",
            )
            MetricRow(
                icon = Icons.Default.DirectionsRun,
                label = "Movimento",
                value = "${state.movementDays}/${state.totalDays} giorni",
                progress = (state.movementDays.toFloat() / state.totalDays).coerceIn(0f, 1f),
                target = "",
            )

            // AI Narrative
            if (state.aiNarrative.isNotBlank()) {
                NarrativeCard(
                    title = "Questa Settimana",
                    text = state.aiNarrative,
                    icon = Icons.Default.AutoAwesome,
                )
            }

            // Correlation insight
            if (state.correlationInsight.isNotBlank()) {
                NarrativeCard(
                    title = "Impatto sulla Mente",
                    text = state.correlationInsight,
                    icon = Icons.Default.TrendingUp,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TerrainLevelCard(level: String) {
    val (color, icon) = when (level) {
        "OTTIMO" -> MaterialTheme.colorScheme.primary to Icons.Outlined.Landscape
        "BUONO" -> MaterialTheme.colorScheme.tertiary to Icons.Outlined.Forest
        "DA MIGLIORARE" -> MaterialTheme.colorScheme.secondary to Icons.Outlined.Park
        else -> MaterialTheme.colorScheme.error to Icons.Outlined.Grass
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = color)
            Column {
                Text(
                    "Il tuo terreno e'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    level,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float,
    target: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (target.isNotBlank()) {
                    Text(target, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun NarrativeCard(title: String, text: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatFloat(value: Float): String {
    val rounded = (value * 10).toInt() / 10f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}
