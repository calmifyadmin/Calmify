package com.lifo.write.wizard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.util.model.BodySensation
import com.lifo.util.model.Trigger
import kotlin.math.sin

/**
 * Step 1: Intensita' Emotiva con shapes geometriche espressive
 * Sostituisce le emoji con forme Material 3 Expressive
 */
@Composable
fun EmotionIntensityStep(
    value: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "shapeScale"
    )

    // Colore dinamico basato sull'intensita'
    val shapeColor = IntensityShapes.getColorForIntensity(value)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Shape animata che cambia con l'intensita'
        key(value) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntensityShape(
                        intensity = value,
                        color = shapeColor,
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.minDimension / 2 * 0.9f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        WizardStepTitle(
            title = "Intensita' Emotiva",
            description = "Quanto intensamente stai provando le tue emozioni?"
        )

        Spacer(modifier = Modifier.height(8.dp))

        MetricValueBadge(value = value, accentColor = shapeColor)

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedMetricSlider(
            value = value,
            onValueChange = onValueChange,
            minLabel = "Debole",
            maxLabel = "Intensa",
            accentColor = shapeColor
        )
    }
}

/**
 * Step 2: Livello di Stress con termometro visivo
 */
@Composable
fun StressLevelStep(
    value: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val fillPercentage by animateFloatAsState(
        targetValue = value / 10f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "thermometerFill"
    )

    // Colore dinamico basato sul valore dello stress
    val stressColor = MetricSymbolicColors.getStressColor(value)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Termometro visivo
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            ThermometerVisual(
                fillPercentage = fillPercentage,
                fillColor = stressColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        WizardStepTitle(
            title = "Livello di Stress",
            description = "Quanto stress stai percependo in questo momento?"
        )

        Spacer(modifier = Modifier.height(8.dp))

        MetricValueBadge(value = value, accentColor = stressColor)

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedMetricSlider(
            value = value,
            onValueChange = onValueChange,
            minLabel = "Nessuno",
            maxLabel = "Estremo",
            accentColor = stressColor
        )
    }
}

/**
 * Componente visivo termometro
 */
@Composable
private fun ThermometerVisual(
    fillPercentage: Float,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val bulbRadius = width / 2
        val tubeWidth = width * 0.4f
        val tubeHeight = height - bulbRadius * 2

        // Sfondo tubo
        drawRoundRect(
            color = Color.LightGray.copy(alpha = 0.3f),
            topLeft = Offset((width - tubeWidth) / 2, 0f),
            size = Size(tubeWidth, tubeHeight),
            cornerRadius = CornerRadius(tubeWidth / 2)
        )

        // Riempimento tubo con colore dinamico
        val fillHeight = tubeHeight * fillPercentage
        drawRoundRect(
            color = fillColor,
            topLeft = Offset((width - tubeWidth) / 2, tubeHeight - fillHeight),
            size = Size(tubeWidth, fillHeight),
            cornerRadius = CornerRadius(tubeWidth / 2)
        )

        // Bulbo in basso
        drawCircle(
            color = fillColor,
            radius = bulbRadius * 0.8f,
            center = Offset(width / 2, height - bulbRadius)
        )
    }
}

/**
 * Step 3: Livello di Energia con batteria animata
 */
@Composable
fun EnergyLevelStep(
    value: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val fillPercentage by animateFloatAsState(
        targetValue = value / 10f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "batteryFill"
    )

    // Colore dinamico basato sul valore dell'energia
    val energyColor = MetricSymbolicColors.getEnergyColor(value)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Batteria visiva
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            BatteryVisual(
                fillPercentage = fillPercentage,
                fillColor = energyColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        WizardStepTitle(
            title = "Livello di Energia",
            description = "Quanta energia fisica e mentale hai?"
        )

        Spacer(modifier = Modifier.height(8.dp))

        MetricValueBadge(value = value, accentColor = energyColor)

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedMetricSlider(
            value = value,
            onValueChange = onValueChange,
            minLabel = "Esausto",
            maxLabel = "Energico",
            accentColor = energyColor
        )
    }
}

/**
 * Componente visivo batteria
 */
@Composable
private fun BatteryVisual(
    fillPercentage: Float,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val capHeight = height * 0.08f
        val capWidth = width * 0.3f
        val bodyHeight = height - capHeight
        val cornerRadius = width * 0.15f
        val padding = width * 0.1f

        // Cappuccio batteria
        drawRoundRect(
            color = Color.Gray,
            topLeft = Offset((width - capWidth) / 2, 0f),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(cornerRadius / 2)
        )

        // Corpo batteria (sfondo)
        drawRoundRect(
            color = Color.LightGray.copy(alpha = 0.3f),
            topLeft = Offset(0f, capHeight),
            size = Size(width, bodyHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )

        // Bordo batteria
        drawRoundRect(
            color = Color.Gray,
            topLeft = Offset(0f, capHeight),
            size = Size(width, bodyHeight),
            cornerRadius = CornerRadius(cornerRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )

        // Riempimento batteria con colore dinamico
        val innerHeight = (bodyHeight - padding * 2) * fillPercentage
        val innerWidth = width - padding * 2
        if (fillPercentage > 0) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(padding, capHeight + padding + (bodyHeight - padding * 2) * (1 - fillPercentage)),
                size = Size(innerWidth, innerHeight),
                cornerRadius = CornerRadius(cornerRadius * 0.5f)
            )
        }
    }
}

/**
 * Step 4: Calma/Ansia con onde animate
 */
@Composable
fun CalmAnxietyStep(
    value: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val waveAmplitude by animateFloatAsState(
        targetValue = (10 - value) / 10f, // Piu' calmo = onde piu' piatte
        animationSpec = spring(dampingRatio = 0.6f),
        label = "waveAmplitude"
    )

    // Colore dinamico basato sul valore di calma/ansia
    val calmColor = MetricSymbolicColors.getCalmColor(value)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Onde visuali
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            WaveVisual(
                amplitude = waveAmplitude,
                waveColor = calmColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        WizardStepTitle(
            title = "Calma / Ansia",
            description = "Come ti senti interiormente in questo momento?"
        )

        Spacer(modifier = Modifier.height(8.dp))

        MetricValueBadge(value = value, accentColor = calmColor)

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedMetricSlider(
            value = value,
            onValueChange = onValueChange,
            minLabel = "Ansioso",
            maxLabel = "Calmo",
            accentColor = calmColor
        )
    }
}

/**
 * Componente visivo onde
 */
@Composable
private fun WaveVisual(
    amplitude: Float,
    waveColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2
        val maxAmplitude = height * 0.3f * amplitude.coerceIn(0.1f, 1f)
        val frequency = if (amplitude > 0.5f) 0.05f else 0.03f

        val path = Path().apply {
            moveTo(0f, midY)
            for (x in 0..width.toInt() step 5) {
                val y = midY + sin(x * frequency) * maxAmplitude
                lineTo(x.toFloat(), y.toFloat())
            }
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = path,
            color = waveColor.copy(alpha = 0.5f)
        )

        // Linea principale dell'onda
        val linePath = Path().apply {
            moveTo(0f, midY)
            for (x in 0..width.toInt() step 5) {
                val y = midY + sin(x * frequency) * maxAmplitude
                lineTo(x.toFloat(), y.toFloat())
            }
        }

        drawPath(
            path = linePath,
            color = waveColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )
    }
}

/**
 * Step 5: Selezione Trigger con cards
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggerSelectionStep(
    selectedTrigger: Trigger,
    onTriggerSelected: (Trigger) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Colore simbolico per i trigger (viola)
    val triggerColor = MetricSymbolicColors.trigger

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WizardStepTitle(
            title = "Trigger Principale",
            description = "Cosa ha influenzato maggiormente il tuo stato d'animo?"
        )

        Spacer(modifier = Modifier.height(24.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Trigger.entries.forEach { trigger ->
                SelectableMetricCard(
                    emoji = trigger.emoji,
                    label = trigger.displayName,
                    isSelected = selectedTrigger == trigger,
                    onClick = { onTriggerSelected(trigger) },
                    accentColor = triggerColor,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}

/**
 * Step 6: Selezione Sensazione Corporea
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodySensationStep(
    selectedSensation: BodySensation,
    onSensationSelected: (BodySensation) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Colore simbolico per le sensazioni corporee (ciano)
    val bodySensationColor = MetricSymbolicColors.bodySensation

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WizardStepTitle(
            title = "Sensazione Corporea",
            description = "Cosa percepisci nel tuo corpo in questo momento?"
        )

        Spacer(modifier = Modifier.height(24.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BodySensation.entries.forEach { sensation ->
                SelectableMetricCard(
                    emoji = sensation.emoji,
                    label = sensation.displayName,
                    isSelected = selectedSensation == sensation,
                    onClick = { onSensationSelected(sensation) },
                    accentColor = bodySensationColor,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}
