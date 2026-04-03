package com.lifo.write

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilenceScreen(
    state: SilenceContract.State,
    onIntent: (SilenceContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        topBar = {
            if (state.phase != SilenceContract.Phase.ACTIVE) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.silence_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                        }
                    },
                )
            }
        },
    ) { padding ->
        AnimatedContent(targetState = state.phase, label = "silence_phase") { phase ->
            when (phase) {
                SilenceContract.Phase.SETUP -> SetupPhase(state, onIntent, Modifier.padding(padding))
                SilenceContract.Phase.ACTIVE -> ActivePhase(state, onIntent)
                SilenceContract.Phase.JOURNAL -> JournalPhase(state, onIntent, onBackPressed, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun SetupPhase(
    state: SilenceContract.State,
    onIntent: (SilenceContract.Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("\uD83E\uDD32", fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Il Silenzio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Non assenza di rumore, ma presenza consapevole.\nSolo una campana all'inizio e alla fine.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        // Duration selector
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SilenceContract.SilenceDuration.entries.forEach { dur ->
                FilterChip(
                    selected = state.duration == dur,
                    onClick = { onIntent(SilenceContract.Intent.SetDuration(dur)) },
                    label = { Text(dur.label) },
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onIntent(SilenceContract.Intent.StartSilence) },
            modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Inizia il Silenzio", fontSize = 16.sp)
        }
    }
}

@Composable
private fun ActivePhase(
    state: SilenceContract.State,
    onIntent: (SilenceContract.Intent) -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val circleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val innerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Pulsing circle
        Canvas(modifier = Modifier.size(200.dp)) {
            val r = size.minDimension / 2f * pulse
            drawCircle(color = innerColor, radius = r, center = Offset(size.width / 2f, size.height / 2f))
            drawCircle(
                color = circleColor,
                radius = r * 0.7f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }

        // Timer
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val min = state.remainingSeconds / 60
            val sec = state.remainingSeconds % 60
            Text(
                "${min}:${sec.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        // Stop button at bottom
        OutlinedButton(
            onClick = { onIntent(SilenceContract.Intent.StopSilence) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Interrompi")
        }
    }
}

@Composable
private fun JournalPhase(
    state: SilenceContract.State,
    onIntent: (SilenceContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("\uD83C\uDF3F", fontSize = 48.sp)
        Text(
            "Cosa e' emerso nel silenzio?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Scrivi brevemente, senza giudicare.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.journalText,
            onValueChange = { onIntent(SilenceContract.Intent.UpdateJournal(it)) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text(stringResource(Res.string.silence_journal_placeholder)) },
            shape = RoundedCornerShape(16.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBackPressed,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Salta")
            }
            Button(
                onClick = {
                    onIntent(SilenceContract.Intent.SaveJournal)
                    onBackPressed()
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = state.journalText.isNotBlank(),
            ) {
                Text(stringResource(Res.string.silence_save))
            }
        }
    }
}
