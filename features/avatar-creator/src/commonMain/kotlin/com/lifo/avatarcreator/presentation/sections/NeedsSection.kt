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
import com.lifo.avatarcreator.presentation.components.TagPicker
import com.lifo.util.model.EngagementFrequency
import com.lifo.util.model.PrimaryNeed
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@Composable
fun NeedsSection(
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
            title = "Bisogni & Desideri",
            narrativeQuestion = "Cosa cerca il tuo avatar in questa app? Ogni conversazione ha uno scopo. Ogni relazione ha un bisogno alla base.",
        )

        // Primary Need
        Text(
            text = stringResource(Res.string.avatar_needs_primary_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        TagPicker(
            label = "",
            options = PrimaryNeed.entries.map { it.displayName },
            selectedOptions = listOf(state.form.primaryNeed.displayName),
            onToggle = { selected ->
                val need = PrimaryNeed.entries.find { it.displayName == selected }
                if (need != null) onIntent(Intent.UpdatePrimaryNeed(need))
            },
            maxSelection = 1,
        )

        // Goals
        OutlinedTextField(
            value = state.form.goals,
            onValueChange = { onIntent(Intent.UpdateGoals(it)) },
            label = { Text(stringResource(Res.string.avatar_needs_goals_label)) },
            placeholder = { Text("Es: Capire meglio se stesso, avere qualcuno che lo ascolti...") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Avoid Topics
        OutlinedTextField(
            value = state.form.avoidTopics,
            onValueChange = { onIntent(Intent.UpdateAvoidTopics(it)) },
            label = { Text(stringResource(Res.string.avatar_needs_avoid_label)) },
            placeholder = { Text("Es: Non giudicare le mie scelte, non parlare di...") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Engagement Frequency
        Text(
            text = stringResource(Res.string.avatar_needs_frequency_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        TagPicker(
            label = "",
            options = EngagementFrequency.entries.map { it.displayName },
            selectedOptions = listOf(state.form.engagementFrequency.displayName),
            onToggle = { selected ->
                val freq = EngagementFrequency.entries.find { it.displayName == selected }
                if (freq != null) onIntent(Intent.UpdateEngagement(freq))
            },
            maxSelection = 1,
        )
    }
}
