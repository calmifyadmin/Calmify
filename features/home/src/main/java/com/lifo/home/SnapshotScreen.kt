package com.lifo.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth

/**
 * SnapshotScreen
 *
 * Weekly wellbeing snapshot questionnaire
 * Week 2 - PSYCHOLOGICAL_INSIGHTS_PLAN.md
 *
 * Target completion time: 2 minutes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotScreen(
    onBackPressed: () -> Unit,
    onSnapshotComplete: () -> Unit,
    viewModel: SnapshotViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val metrics = viewModel.metrics
    val notes = viewModel.notes
    val scrollState = rememberScrollState()

    // Show success dialog
    if (uiState.isSuccess) {
        AlertDialog(
            onDismissRequest = {
                viewModel.resetForm()
                onSnapshotComplete()
            },
            icon = { Icon(Icons.Default.Check, contentDescription = null) },
            title = { Text("Snapshot Salvato") },
            text = { Text("Il tuo snapshot di benessere è stato salvato con successo!") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetForm()
                    onSnapshotComplete()
                }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snapshot Settimanale") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Introduction
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📊 Snapshot Settimanale del Benessere",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Rispondi a queste 10 domande per monitorare il tuo benessere psicologico. Completamento stimato: 2 minuti",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 1: Life Domains
            SectionHeader("Domini di Vita")
            WellbeingSlider(
                label = "Soddisfazione di Vita",
                description = "Quanto sei soddisfatto della tua vita in generale?",
                value = metrics.lifeSatisfaction,
                onValueChange = { viewModel.setLifeSatisfaction(it) },
                minLabel = "Insoddisfatto",
                maxLabel = "Molto soddisfatto"
            )
            WellbeingSlider(
                label = "Soddisfazione Lavorativa/Studio",
                description = "Quanto sei soddisfatto del tuo lavoro o studio?",
                value = metrics.workSatisfaction,
                onValueChange = { viewModel.setWorkSatisfaction(it) },
                minLabel = "Insoddisfatto",
                maxLabel = "Molto soddisfatto"
            )
            WellbeingSlider(
                label = "Qualità delle Relazioni",
                description = "Come valuti la qualità delle tue relazioni?",
                value = metrics.relationshipsQuality,
                onValueChange = { viewModel.setRelationshipsQuality(it) },
                minLabel = "Scarsa",
                maxLabel = "Eccellente"
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Psychological Health
            SectionHeader("Salute Psicologica")
            WellbeingSlider(
                label = "Mindfulness",
                description = "Quanto sei presente e consapevole nel momento?",
                value = metrics.mindfulnessScore,
                onValueChange = { viewModel.setMindfulnessScore(it) },
                minLabel = "Distratto",
                maxLabel = "Molto presente"
            )
            WellbeingSlider(
                label = "Senso di Scopo",
                description = "Quanto senti di avere uno scopo o direzione?",
                value = metrics.purposeMeaning,
                onValueChange = { viewModel.setPurposeMeaning(it) },
                minLabel = "Confuso",
                maxLabel = "Chiaro"
            )
            WellbeingSlider(
                label = "Gratitudine",
                description = "Quanto ti senti grato nella tua vita?",
                value = metrics.gratitude,
                onValueChange = { viewModel.setGratitude(it) },
                minLabel = "Poco",
                maxLabel = "Molto"
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: SDT Pillars
            SectionHeader("Autodeterminazione (SDT)")
            WellbeingSlider(
                label = "Autonomia",
                description = "Quanto controllo hai sulla tua vita?",
                value = metrics.autonomy,
                onValueChange = { viewModel.setAutonomy(it) },
                minLabel = "Poco",
                maxLabel = "Molto"
            )
            WellbeingSlider(
                label = "Competenza",
                description = "Quanto ti senti capace e competente?",
                value = metrics.competence,
                onValueChange = { viewModel.setCompetence(it) },
                minLabel = "Poco",
                maxLabel = "Molto"
            )
            WellbeingSlider(
                label = "Connessione",
                description = "Quanto ti senti connesso agli altri?",
                value = metrics.relatedness,
                onValueChange = { viewModel.setRelatedness(it) },
                minLabel = "Poco",
                maxLabel = "Molto"
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: Risk Indicators
            SectionHeader("Indicatori di Rischio")
            WellbeingSlider(
                label = "Solitudine",
                description = "Quanto ti senti solo/a?",
                value = metrics.loneliness,
                onValueChange = { viewModel.setLoneliness(it) },
                minLabel = "Connesso",
                maxLabel = "Molto solo"
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Optional Notes
            Text(
                text = "Note (Opzionale)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.updateNotes(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Aggiungi eventuali riflessioni...") },
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    viewModel.submitSnapshot(
                        ownerId = userId,
                        onSuccess = {},
                        onError = { /* Error handled in UI state */ }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isSubmitting
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Salva Snapshot", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Error message
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.error!!,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun WellbeingSlider(
    label: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    minLabel: String,
    maxLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = value.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = minLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = maxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
