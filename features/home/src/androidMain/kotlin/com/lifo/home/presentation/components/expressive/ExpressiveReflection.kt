package com.lifo.home.presentation.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.home.domain.model.TodayPulse
import com.lifo.home.util.EmotionAwareColors

@Composable
internal fun ExpressiveReflection(
    todayPulse: TodayPulse?,
    recurringThemes: List<String>,
    userName: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val reflection = remember(todayPulse, recurringThemes) {
        buildReflectionText(todayPulse, recurringThemes, userName)
    }

    val scoreColor = if (todayPulse != null)
        EmotionAwareColors.getWellbeingScoreColor(todayPulse.score)
    else colorScheme.primary

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            scoreColor.copy(alpha = 0.12f),
            colorScheme.primary.copy(alpha = 0.06f),
            colorScheme.tertiary.copy(alpha = 0.04f)
        )
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .background(gradientBrush)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "La tua riflessione",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colorScheme.onSurface
                )
            }

            // Quote-style text
            Text(
                text = "\u201C$reflection\u201D",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 24.sp,
                    letterSpacing = 0.1.sp
                ),
                color = colorScheme.onSurface.copy(alpha = 0.85f)
            )

            // Theme pills
            if (recurringThemes.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recurringThemes.take(3).forEach { theme ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = theme,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 5.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildReflectionText(
    pulse: TodayPulse?,
    themes: List<String>,
    userName: String
): String {
    if (pulse == null) {
        return if (userName.isNotBlank()) {
            "$userName, oggi e' un buon giorno per fermarti e ascoltarti. Scrivi o parla — anche solo una riga."
        } else {
            "Oggi e' un buon giorno per fermarti e ascoltarti. Scrivi o parla — anche solo una riga."
        }
    }

    val name = if (userName.isNotBlank()) "$userName, " else ""
    val themePart = if (themes.isNotEmpty()) {
        " Ultimamente hai riflettuto su ${themes.joinToString(", ")}."
    } else ""

    return when {
        pulse.score >= 7 -> "${name}la tua settimana sta andando bene — il tuo punteggio e' ${String.format("%.0f", pulse.score)}/10.${themePart} Continua cosi'."
        pulse.score >= 4 -> "${name}questa settimana sei in equilibrio. ${pulse.weekSummary}${themePart} Ogni giorno di scrittura conta."
        else -> "${name}sembra una settimana impegnativa.${themePart} Scrivere aiuta a mettere ordine nei pensieri — anche poche righe."
    }
}
