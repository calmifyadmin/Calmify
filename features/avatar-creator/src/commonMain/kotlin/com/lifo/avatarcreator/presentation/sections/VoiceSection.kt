package com.lifo.avatarcreator.presentation.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Intent
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.State
import com.lifo.avatarcreator.presentation.components.SectionHeader
import com.lifo.avatarcreator.presentation.components.SliderWithLabel
import com.lifo.avatarcreator.presentation.components.TagPicker
import com.lifo.ui.i18n.Strings
import com.lifo.util.model.GeminiVoice
import com.lifo.util.model.VoiceTone
import org.jetbrains.compose.resources.stringResource

@Composable
fun VoiceSection(
    state: State,
    onIntent: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionHeader(
            title = "Voce & Stile",
            narrativeQuestion = "Come suona il tuo avatar? La voce e' l'anima che esce dalla bocca. Scegli il timbro che racconta chi e' davvero.",
        )

        // Voice Selection Cards
        Text(
            text = stringResource(Strings.Screen.Avatar.voiceChoose),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GeminiVoice.entries.forEach { voice ->
                val isSelected = state.form.voiceId == voice.voiceId
                Card(
                    onClick = { onIntent(Intent.UpdateVoiceId(voice.voiceId)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface,
                    ),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = voice.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = voice.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speaking Rate
        SliderWithLabel(
            label = "Ritmo del parlato",
            value = state.form.speakingRate,
            onValueChange = { onIntent(Intent.UpdateSpeakingRate(it)) },
            valueRange = 0.5f..2.0f,
            valueFormatter = { "%.1fx".format(it) },
            startLabel = "Lento e riflessivo",
            endLabel = "Veloce ed energico",
        )

        // Voice Tone
        Text(
            text = stringResource(Strings.Screen.Avatar.voiceTone),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        TagPicker(
            label = "",
            options = VoiceTone.entries.map { it.displayName },
            selectedOptions = listOf(state.form.voiceTone.displayName),
            onToggle = { selected ->
                val tone = VoiceTone.entries.find { it.displayName == selected }
                if (tone != null) onIntent(Intent.UpdateVoiceTone(tone))
            },
            maxSelection = 1,
        )

        // Pause Style
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Usa spesso pause?", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Le pause danno peso alle parole",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.form.pauseStyle,
                onCheckedChange = { onIntent(Intent.TogglePauseStyle(it)) },
            )
        }

        // Volume Gain
        SliderWithLabel(
            label = "Volume percepito",
            value = state.form.volumeGain,
            onValueChange = { onIntent(Intent.UpdateVolumeGain(it)) },
            valueRange = -6f..6f,
            valueFormatter = { "${if (it >= 0) "+" else ""}%.0f dB".format(it) },
            startLabel = "Sussurro",
            endLabel = "Voce alta",
        )
    }
}
