package com.lifo.settings.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.settings.components.SettingsDropdownItem
import com.lifo.settings.components.SettingsSectionHeader
import com.lifo.util.model.AiTone
import com.lifo.util.model.ReminderFrequency
import com.lifo.util.model.TopicsToAvoid
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiPreferencesScreen(
    currentTone: String,
    currentReminderFrequency: String,
    currentTopicsToAvoid: List<String>,
    onNavigateBack: () -> Unit,
    onSave: (tone: String, reminderFrequency: String, topicsToAvoid: List<String>) -> Unit,
    isSaving: Boolean = false,
) {
    var tone by remember { mutableStateOf(currentTone) }
    var reminderFreq by remember { mutableStateOf(currentReminderFrequency) }
    var topicsToAvoid by remember { mutableStateOf(currentTopicsToAvoid.toSet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.ai_pref_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionHeader(title = stringResource(Res.string.ai_pref_tone_section))
            Text(
                text = stringResource(Res.string.ai_pref_tone_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsDropdownItem(
                title = stringResource(Res.string.ai_pref_tone_dropdown),
                icon = Icons.Default.Psychology,
                selectedValue = AiTone.entries.find { it.name == tone }?.displayName ?: "Amichevole",
                options = AiTone.entries.map { it.displayName },
                onValueChange = { selected ->
                    tone = AiTone.entries.find { it.displayName == selected }?.name ?: AiTone.FRIENDLY.name
                },
            )

            SettingsSectionHeader(title = stringResource(Res.string.ai_pref_reminders_section))
            Text(
                text = stringResource(Res.string.ai_pref_reminders_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsDropdownItem(
                title = stringResource(Res.string.ai_pref_frequency_dropdown),
                icon = Icons.Default.Notifications,
                selectedValue = ReminderFrequency.entries.find { it.name == reminderFreq }?.displayName ?: "Giornaliero",
                options = ReminderFrequency.entries.map { it.displayName },
                onValueChange = { selected ->
                    reminderFreq = ReminderFrequency.entries.find { it.displayName == selected }?.name ?: ReminderFrequency.DAILY.name
                },
            )

            SettingsSectionHeader(title = stringResource(Res.string.ai_pref_topics_section))
            Text(
                text = stringResource(Res.string.ai_pref_topics_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TopicsToAvoid.ALL.forEach { topic ->
                    val selected = topic in topicsToAvoid
                    FilterChip(
                        selected = selected,
                        onClick = {
                            topicsToAvoid = if (selected) topicsToAvoid - topic else topicsToAvoid + topic
                        },
                        label = { Text(topic, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onSave(tone, reminderFreq, topicsToAvoid.toList()) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.ai_pref_save), fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
