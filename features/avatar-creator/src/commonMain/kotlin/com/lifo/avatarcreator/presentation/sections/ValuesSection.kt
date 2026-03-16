package com.lifo.avatarcreator.presentation.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Intent
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.State
import com.lifo.avatarcreator.presentation.components.SectionHeader
import com.lifo.avatarcreator.presentation.components.TagPicker
import com.lifo.util.model.RecognizedBiases

@Composable
fun ValuesSection(
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
            title = "Valori & Bias",
            narrativeQuestion = "Cosa guida le sue scelte? Questa e' la sezione piu' profonda. Ogni persona ha valori non negoziabili, pregiudizi riconosciuti e ferite che la definiscono.",
        )

        // Non-negotiable values
        OutlinedTextField(
            value = state.form.values,
            onValueChange = { onIntent(Intent.UpdateValues(it)) },
            label = { Text("Cosa e' assolutamente non negoziabile per lui/lei?") },
            placeholder = { Text("Es: L'onesta', la liberta' personale, il rispetto...") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Recognized biases
        TagPicker(
            label = "Quali pregiudizi riconosce di avere?",
            options = RecognizedBiases.all,
            selectedOptions = state.form.biases,
            onToggle = { onIntent(Intent.ToggleBias(it)) },
            subtitle = "Seleziona quelli che riconosce",
        )

        // Core Wound
        OutlinedTextField(
            value = state.form.coreWound,
            onValueChange = { onIntent(Intent.UpdateCoreWound(it)) },
            label = { Text("Cosa lo/la fa sentire incompreso/a?") },
            placeholder = { Text("La ferita che non si chiude mai del tutto...") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Core Strength
        OutlinedTextField(
            value = state.form.coreStrength,
            onValueChange = { onIntent(Intent.UpdateCoreStrength(it)) },
            label = { Text("Qual e' la sua forza genuina?") },
            placeholder = { Text("Quella cosa che sa fare meglio di chiunque altro...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Cultural Background
        OutlinedTextField(
            value = state.form.culturalBackground,
            onValueChange = { onIntent(Intent.UpdateCulturalBackground(it)) },
            label = { Text("Background culturale che lo influenza") },
            placeholder = { Text("Es: Cresciuto in una famiglia del sud Italia...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // Cultural Reference
        OutlinedTextField(
            value = state.form.culturalReference,
            onValueChange = { onIntent(Intent.UpdateCulturalReference(it)) },
            label = { Text("Personaggio/opera che lo ispira") },
            placeholder = { Text("Es: Spike Spiegel, Amelie Poulain, Naruto...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}
