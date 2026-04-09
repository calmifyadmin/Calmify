package com.lifo.write

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifo.util.model.ValuesDiscovery
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValuesScreen(
    state: ValuesContract.State,
    onIntent: (ValuesContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.values_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Step indicator
            val steps = listOf("Momenti Vivi", "Indignazione", "Domanda Finale", "I Tuoi Valori")
            TabRow(selectedTabIndex = state.currentStep) {
                steps.forEachIndexed { index, title ->
                    Tab(
                        selected = state.currentStep == index,
                        onClick = { onIntent(ValuesContract.Intent.SetStep(index)) },
                        text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            AnimatedContent(targetState = state.currentStep, label = "values_step") { step ->
                when (step) {
                    0 -> AliveMomentsStep(state, onIntent)
                    1 -> IndignationStep(state, onIntent)
                    2 -> FinalQuestionStep(state, onIntent)
                    3 -> ConfirmValuesStep(state, onIntent)
                }
            }
        }
    }
}

@Composable
private fun AliveMomentsStep(state: ValuesContract.State, onIntent: (ValuesContract.Intent) -> Unit) {
    val moments = state.discovery?.aliveMoments ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PromptCard(
                icon = Icons.Outlined.AutoAwesome,
                text = "Pensa a momenti della tua vita in cui ti sei sentito profondamente vivo. " +
                        "Non di successo — di autenticita'. Cosa stavi facendo? Con chi? Cosa c'era di speciale?",
            )
        }

        itemsIndexed(moments) { index, moment ->
            ItemCard(text = moment, onRemove = { onIntent(ValuesContract.Intent.RemoveItem(index)) })
        }

        item {
            InputRow(
                value = state.currentInput,
                onValueChange = { onIntent(ValuesContract.Intent.UpdateInput(it)) },
                onAdd = { onIntent(ValuesContract.Intent.AddCurrentInput) },
                placeholder = "Descrivi un momento...",
            )
        }

        if (moments.size >= 2) {
            item {
                NextStepButton(
                    onClick = { onIntent(ValuesContract.Intent.Save) },
                    isSaving = state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun IndignationStep(state: ValuesContract.State, onIntent: (ValuesContract.Intent) -> Unit) {
    val topics = state.discovery?.indignationTopics ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PromptCard(
                icon = Icons.Outlined.LocalFireDepartment,
                text = "Cosa ti fa arrabbiare quando lo vedi? L'ingiustizia? La superficialita'? La disonesta'? " +
                        "Le violazioni rivelano i tuoi valori.",
            )
        }

        itemsIndexed(topics) { index, topic ->
            ItemCard(text = topic, onRemove = { onIntent(ValuesContract.Intent.RemoveItem(index)) })
        }

        item {
            InputRow(
                value = state.currentInput,
                onValueChange = { onIntent(ValuesContract.Intent.UpdateInput(it)) },
                onAdd = { onIntent(ValuesContract.Intent.AddCurrentInput) },
                placeholder = "Cosa ti indigna...",
            )
        }

        if (topics.size >= 2) {
            item {
                NextStepButton(
                    onClick = { onIntent(ValuesContract.Intent.Save) },
                    isSaving = state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun FinalQuestionStep(state: ValuesContract.State, onIntent: (ValuesContract.Intent) -> Unit) {
    val reflection = state.discovery?.finalReflection ?: ""

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PromptCard(
                icon = Icons.Outlined.NightsStay,
                text = "Immagina di essere alla fine della tua vita. Cosa ti dispiaceresti aver trascurato? " +
                        "Non chi non hai impressionato — chi non hai amato, cosa non hai creato, chi non sei diventato.",
            )
        }

        item {
            OutlinedTextField(
                value = reflection,
                onValueChange = { onIntent(ValuesContract.Intent.SetFinalReflection(it)) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                label = { Text(stringResource(Res.string.values_reflection_label)) },
                shape = RoundedCornerShape(16.dp),
            )
        }

        if (reflection.length >= 20) {
            item {
                NextStepButton(
                    onClick = { onIntent(ValuesContract.Intent.Save) },
                    isSaving = state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun ConfirmValuesStep(state: ValuesContract.State, onIntent: (ValuesContract.Intent) -> Unit) {
    val discovery = state.discovery
    val values = discovery?.discoveredValues ?: emptyList()
    val confirmed = discovery?.confirmedValues ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PromptCard(
                icon = Icons.Outlined.Explore,
                text = if (confirmed.isNotEmpty()) {
                    "I tuoi valori confermati. Rivisitali ogni 6 mesi."
                } else {
                    "Dai tuoi racconti, quali valori emergono? Aggiungili qui sotto e conferma quelli in cui ti riconosci."
                },
            )
        }

        // Suggested values based on indignation patterns
        if (values.isEmpty() && confirmed.isEmpty()) {
            item {
                Text(
                    "Suggerimenti basati sulle tue risposte:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val suggestions = buildSuggestions(discovery)
            item {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            onIntent(ValuesContract.Intent.UpdateInput(suggestion))
                            onIntent(ValuesContract.Intent.AddCurrentInput)
                        },
                        label = { Text(suggestion) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }

        itemsIndexed(values) { index, value ->
            ItemCard(text = value, onRemove = { onIntent(ValuesContract.Intent.RemoveItem(index)) })
        }

        item {
            InputRow(
                value = state.currentInput,
                onValueChange = { onIntent(ValuesContract.Intent.UpdateInput(it)) },
                onAdd = { onIntent(ValuesContract.Intent.AddCurrentInput) },
                placeholder = "Un valore (es. Liberta', Autenticita')...",
            )
        }

        if (values.size >= 3) {
            item {
                Button(
                    onClick = { onIntent(ValuesContract.Intent.ConfirmValues(values)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !state.isSaving,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Conferma i Tuoi Valori")
                }
            }
        }

        // Show confirmed values
        if (confirmed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Valori Confermati",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            confirmed.forEach { value ->
                item(key = "confirmed_$value") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private fun buildSuggestions(discovery: ValuesDiscovery?): List<String> {
    if (discovery == null) return listOf("Liberta'", "Autenticita'", "Creativita'", "Connessione", "Giustizia")
    val all = (discovery.aliveMoments + discovery.indignationTopics + discovery.finalReflection).joinToString(" ").lowercase()
    val suggestions = mutableListOf<String>()
    if ("libert" in all || "scelta" in all || "vincol" in all) suggestions.add("Liberta'")
    if ("autentic" in all || "vero" in all || "maschera" in all) suggestions.add("Autenticita'")
    if ("creat" in all || "art" in all || "invent" in all) suggestions.add("Creativita'")
    if ("conness" in all || "relazion" in all || "amici" in all || "amor" in all) suggestions.add("Connessione")
    if ("giustizi" in all || "equit" in all || "disuguagl" in all) suggestions.add("Giustizia")
    if ("cresci" in all || "impara" in all || "migliora" in all) suggestions.add("Crescita")
    if ("coraggi" in all || "rischi" in all || "paur" in all) suggestions.add("Coraggio")
    if ("rispett" in all || "dignit" in all) suggestions.add("Rispetto")
    if ("natur" in all || "ambiente" in all) suggestions.add("Natura")
    if ("famig" in all || "figli" in all || "genitor" in all) suggestions.add("Famiglia")
    if (suggestions.isEmpty()) suggestions.addAll(listOf("Liberta'", "Autenticita'", "Creativita'", "Connessione", "Giustizia"))
    return suggestions.take(6)
}

// === Shared composables ===

@Composable
private fun PromptCard(icon: ImageVector, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemCard(text: String, onRemove: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Rimuovi", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit, placeholder: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )
        FilledIconButton(
            onClick = onAdd,
            enabled = value.isNotBlank(),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Aggiungi")
        }
    }
}

@Composable
private fun NextStepButton(onClick: () -> Unit, isSaving: Boolean) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        enabled = !isSaving,
    ) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Prossimo Step")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}
