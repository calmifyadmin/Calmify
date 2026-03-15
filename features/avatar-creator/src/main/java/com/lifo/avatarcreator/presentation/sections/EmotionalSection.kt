package com.lifo.avatarcreator.presentation.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Intent
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.State
import com.lifo.avatarcreator.presentation.components.SectionHeader
import com.lifo.avatarcreator.presentation.components.SliderWithLabel
import com.lifo.avatarcreator.presentation.components.TagPicker
import com.lifo.util.model.AttachmentStyle

@Composable
fun EmotionalSection(
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
            title = "Stile Emotivo",
            narrativeQuestion = "Come ama, come si protegge, come si apre? Le emozioni non sono un bug — sono il sistema operativo piu' importante.",
        )

        // Attachment Style
        Text(
            text = "Stile di attaccamento",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        TagPicker(
            label = "",
            options = AttachmentStyle.entries.map { it.displayName },
            selectedOptions = listOf(state.form.attachmentStyle.displayName),
            onToggle = { selected ->
                val style = AttachmentStyle.entries.find { it.displayName == selected }
                if (style != null) onIntent(Intent.UpdateAttachmentStyle(style))
            },
            maxSelection = 1,
        )

        // Jealousy
        SliderWithLabel(
            label = "Intensita' della gelosia",
            value = state.form.jealousyLevel,
            onValueChange = { onIntent(Intent.UpdateJealousy(it)) },
            startLabel = "Quasi assente",
            endLabel = "Molto intensa",
        )

        // Affection Style
        OutlinedTextField(
            value = state.form.affectionStyle,
            onValueChange = { onIntent(Intent.UpdateAffectionStyle(it)) },
            label = { Text("Come esprime affetto?") },
            placeholder = { Text("Es: Prese in giro, presenza silenziosa, gesti concreti...") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Vulnerability Triggers
        OutlinedTextField(
            value = state.form.vulnerabilityTriggers,
            onValueChange = { onIntent(Intent.UpdateVulnerabilityTriggers(it)) },
            label = { Text("Cosa lo rende vulnerabile?") },
            placeholder = { Text("Es: Essere ignorato, sentirsi inutile, il silenzio...") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Emotional Barrier
        OutlinedTextField(
            value = state.form.emotionalBarrier,
            onValueChange = { onIntent(Intent.UpdateEmotionalBarrier(it)) },
            label = { Text("Come si protegge emotivamente?") },
            placeholder = { Text("Es: Sarcasmo, distacco, cambia argomento...") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}
