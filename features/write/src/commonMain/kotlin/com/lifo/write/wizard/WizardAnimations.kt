package com.lifo.write.wizard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Palette colori per ogni step del wizard
 * Tonalita' pastello per un'esperienza rilassante
 */
object WizardColors {
    val stepBackgroundColors = listOf(
        Color(0xFFE3F2FD), // Blu chiaro - Emozioni
        Color(0xFFFFEBEE), // Rosso chiaro - Stress
        Color(0xFFFFF3E0), // Arancione chiaro - Energia
        Color(0xFFE8F5E9), // Verde chiaro - Calma
        Color(0xFFF3E5F5), // Viola chiaro - Trigger
        Color(0xFFE0F7FA), // Ciano chiaro - Corpo
        Color(0xFFFCE4EC)  // Rosa chiaro - Completamento
    )

    val stepAccentColors = listOf(
        Color(0xFF1976D2), // Blu - Emozioni
        Color(0xFFD32F2F), // Rosso - Stress
        Color(0xFFFF9800), // Arancione - Energia
        Color(0xFF388E3C), // Verde - Calma
        Color(0xFF7B1FA2), // Viola - Trigger
        Color(0xFF00796B), // Ciano - Corpo
        Color(0xFFE91E63)  // Rosa - Completamento
    )

    fun getBackgroundColor(stepIndex: Int): Color {
        return stepBackgroundColors.getOrElse(stepIndex) { stepBackgroundColors[0] }
    }

    fun getAccentColor(stepIndex: Int): Color {
        return stepAccentColors.getOrElse(stepIndex) { stepAccentColors[0] }
    }
}

/**
 * Colori simbolici per le metriche
 * Colori che rappresentano il significato di ogni metrica
 */
object MetricSymbolicColors {
    // Intensita' Emotiva: usa la scala dinamica da IntensityShapes
    // (viene gestito direttamente in EmotionIntensityStep)

    // Trigger: Viola (riflessione, introspezione)
    val trigger = Color(0xFF7B1FA2) // Viola trigger

    // Body Sensation: Ciano (corpo, fisicita')
    val bodySensation = Color(0xFF00796B) // Ciano corpo

    /**
     * Stress: Gradiente da verde (nessuno stress) a rosso (stress estremo)
     * 0 = Verde (nessuno stress)
     * 5 = Giallo (stress medio)
     * 10 = Rosso (stress estremo)
     */
    fun getStressColor(value: Int): Color {
        val normalizedValue = value.coerceIn(0, 10) / 10f
        return when {
            normalizedValue < 0.5f -> {
                // Verde -> Giallo (0-5)
                val fraction = normalizedValue * 2f
                lerp(Color(0xFF4CAF50), Color(0xFFFFEB3B), fraction)
            }
            else -> {
                // Giallo -> Rosso (5-10)
                val fraction = (normalizedValue - 0.5f) * 2f
                lerp(Color(0xFFFFEB3B), Color(0xFFE53935), fraction)
            }
        }
    }

    /**
     * Energia: Gradiente da rosso (esausto) a verde brillante (energico)
     * 0 = Rosso scuro (esausto)
     * 5 = Giallo/Arancione (medio)
     * 10 = Verde brillante (energico)
     */
    fun getEnergyColor(value: Int): Color {
        val normalizedValue = value.coerceIn(0, 10) / 10f
        return when {
            normalizedValue < 0.5f -> {
                // Rosso scuro -> Arancione (0-5)
                val fraction = normalizedValue * 2f
                lerp(Color(0xFFD32F2F), Color(0xFFFF9800), fraction)
            }
            else -> {
                // Arancione -> Verde brillante (5-10)
                val fraction = (normalizedValue - 0.5f) * 2f
                lerp(Color(0xFFFF9800), Color(0xFF66BB6A), fraction)
            }
        }
    }

    /**
     * Calma/Ansia: Gradiente da rosso ansioso a verde calmo
     * 0 = Rosso/Rosa (ansioso)
     * 5 = Giallo (neutro)
     * 10 = Verde (calmo)
     */
    fun getCalmColor(value: Int): Color {
        val normalizedValue = value.coerceIn(0, 10) / 10f
        return when {
            normalizedValue < 0.5f -> {
                // Rosa/Rosso -> Giallo (0-5)
                val fraction = normalizedValue * 2f
                lerp(Color(0xFFE91E63), Color(0xFFFFEB3B), fraction)
            }
            else -> {
                // Giallo -> Verde calmo (5-10)
                val fraction = (normalizedValue - 0.5f) * 2f
                lerp(Color(0xFFFFEB3B), Color(0xFF43A047), fraction)
            }
        }
    }

    /**
     * Funzione di interpolazione tra due colori
     */
    private fun lerp(start: Color, stop: Color, fraction: Float): Color {
        return Color(
            red = start.red + (stop.red - start.red) * fraction,
            green = start.green + (stop.green - start.green) * fraction,
            blue = start.blue + (stop.blue - start.blue) * fraction,
            alpha = start.alpha + (stop.alpha - start.alpha) * fraction
        )
    }
}

/**
 * Icone Material per lo step Intensita' Emotiva
 * Cambiano in base al valore dello slider (0-10)
 */
object EmotionIcons {
    val icons = listOf(
        Icons.Outlined.SentimentNeutral,      // 0 - Neutro
        Icons.Outlined.SentimentNeutral,      // 1 - Neutro
        Icons.Outlined.SentimentNeutral,      // 2 - Bassa intensita'
        Icons.Outlined.SentimentSatisfied,    // 3 - Medio-bassa
        Icons.Outlined.SentimentSatisfied,    // 4 - Medio-bassa
        Icons.Outlined.SentimentSatisfiedAlt, // 5 - Media
        Icons.Outlined.SentimentSatisfiedAlt, // 6 - Media
        Icons.Outlined.SentimentVerySatisfied,// 7 - Alta
        Icons.Outlined.SentimentVerySatisfied,// 8 - Alta
        Icons.Outlined.AutoAwesome,           // 9 - Molto alta
        Icons.Outlined.AutoAwesome            // 10 - Massima
    )

    fun getIcon(intensity: Int): ImageVector {
        return icons.getOrElse(intensity.coerceIn(0, 10)) { icons[5] }
    }
}

/**
 * Animazione per il bounce degli elementi
 */
@Composable
fun rememberBounceAnimation(
    targetValue: Float = 1f,
    initialValue: Float = 0.8f
): Animatable<Float, *> {
    val scale = remember { Animatable(initialValue) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = targetValue,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    return scale
}

/**
 * Animazione pulse per selezione
 */
@Composable
fun rememberPulseAnimation(): Animatable<Float, *> {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            scale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(600)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(600)
            )
        }
    }

    return scale
}

/**
 * Configurazione delle animazioni per le transizioni tra step
 */
object WizardTransitions {
    val enterTransition = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val exitTransition = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    const val TRANSITION_DURATION_MS = 300
}
