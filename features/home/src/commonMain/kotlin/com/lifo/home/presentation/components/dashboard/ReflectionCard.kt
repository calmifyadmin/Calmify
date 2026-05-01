package com.lifo.home.presentation.components.dashboard

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.TodayPulse
import com.lifo.home.util.EmotionAwareColors
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ReflectionCard(
    todayPulse: TodayPulse?,
    recurringThemes: List<String>,
    userName: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val reflection = remember(todayPulse, recurringThemes) {
        buildReflectionText(todayPulse, recurringThemes, userName)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .background(EmotionAwareColors.accentGradient(colorScheme.primary))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.primary
                )
                Text(
                    text = stringResource(Strings.Screen.Home.reflectionCardTitle),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
            }
            Text(
                text = reflection,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface.copy(alpha = 0.85f)
            )
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
