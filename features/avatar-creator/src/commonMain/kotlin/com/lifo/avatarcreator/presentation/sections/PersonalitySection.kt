package com.lifo.avatarcreator.presentation.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Intent
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.State
import com.lifo.avatarcreator.presentation.components.SectionHeader
import com.lifo.avatarcreator.presentation.components.SliderWithLabel
import com.lifo.avatarcreator.presentation.components.TagPicker
import com.lifo.util.model.DecisionStyle
import com.lifo.util.model.PersonalityTraits
import com.lifo.util.model.StressResponse

@Composable
fun PersonalitySection(
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
            title = "Personalita'",
            narrativeQuestion = "Come si comporta? Ogni persona ha un modo unico di stare al mondo. Definisci il carattere del tuo avatar — non esiste una risposta giusta.",
        )

        // Traits (max 3)
        TagPicker(
            label = "Scegli 3 parole che lo descrivono",
            options = PersonalityTraits.all,
            selectedOptions = state.form.traits,
            onToggle = { onIntent(Intent.ToggleTrait(it)) },
            maxSelection = 3,
            subtitle = "Massimo 3 tratti",
        )

        // Stress Response
        Text(
            text = "Come reagisce sotto pressione?",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        TagPicker(
            label = "",
            options = StressResponse.entries.map { it.displayName },
            selectedOptions = listOf(state.form.stressResponse.displayName),
            onToggle = { selected ->
                val response = StressResponse.entries.find { it.displayName == selected }
                if (response != null) onIntent(Intent.UpdateStressResponse(response))
            },
            maxSelection = 1,
        )

        // Directness
        SliderWithLabel(
            label = "Quanto e' diretto nel comunicare?",
            value = state.form.directness,
            onValueChange = { onIntent(Intent.UpdateDirectness(it)) },
            startLabel = "Diplomatico",
            endLabel = "Brutalmente onesto",
        )

        // Humor
        SliderWithLabel(
            label = "Ha senso dell'umorismo?",
            value = state.form.humor,
            onValueChange = { onIntent(Intent.UpdateHumor(it)) },
            startLabel = "Serio",
            endLabel = "Ironico",
        )

        // Decision Style
        Text(
            text = "Come prende decisioni?",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        TagPicker(
            label = "",
            options = DecisionStyle.entries.map { it.displayName },
            selectedOptions = listOf(state.form.decisionStyle.displayName),
            onToggle = { selected ->
                val style = DecisionStyle.entries.find { it.displayName == selected }
                if (style != null) onIntent(Intent.UpdateDecisionStyle(style))
            },
            maxSelection = 1,
        )

        // Authority
        SliderWithLabel(
            label = "Come si relaziona con l'autorita'?",
            value = state.form.authorityRelation,
            onValueChange = { onIntent(Intent.UpdateAuthorityRelation(it)) },
            startLabel = "Ribelle",
            endLabel = "Rispettoso",
        )
    }
}
