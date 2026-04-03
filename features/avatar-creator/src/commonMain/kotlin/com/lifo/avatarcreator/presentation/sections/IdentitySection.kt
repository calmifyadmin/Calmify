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
import com.lifo.util.model.GenderIdentity
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

private val AVAILABLE_LANGUAGES = listOf(
    "Italiano", "English", "Espanol", "Francais", "Deutsch", "Portugues", "Japanese", "Korean",
)

@Composable
fun IdentitySection(
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
            title = "Identita' di Base",
            narrativeQuestion = "Chi e' questo avatar? Dagli un nome, un'eta', un'identita'. Questo e' il primo respiro della sua esistenza digitale.",
        )

        // Name
        OutlinedTextField(
            value = state.form.name,
            onValueChange = { onIntent(Intent.UpdateName(it)) },
            label = { Text(stringResource(Res.string.avatar_identity_name_label)) },
            placeholder = { Text("Es: Ren, Luna, Kai...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Perceived Age
        SliderWithLabel(
            label = "Eta' percepita",
            value = state.form.perceivedAge.toFloat(),
            onValueChange = { onIntent(Intent.UpdateAge(it.toInt())) },
            valueRange = 10f..80f,
            valueFormatter = { "${it.toInt()} anni" },
            startLabel = "10",
            endLabel = "80",
        )

        // Gender
        Text(
            text = stringResource(Res.string.avatar_identity_gender_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        TagPicker(
            label = "",
            options = GenderIdentity.entries.map { it.displayName },
            selectedOptions = listOf(state.form.gender.displayName),
            onToggle = { selected ->
                val gender = GenderIdentity.entries.find { it.displayName == selected }
                if (gender != null) onIntent(Intent.UpdateGender(gender))
            },
            maxSelection = 1,
        )

        // Languages
        TagPicker(
            label = "In che lingua preferisce comunicare?",
            options = AVAILABLE_LANGUAGES,
            selectedOptions = state.form.languages,
            onToggle = { onIntent(Intent.ToggleLanguage(it)) },
            subtitle = "Seleziona una o piu' lingue",
        )
    }
}
