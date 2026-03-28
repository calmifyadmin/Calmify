package com.lifo.habits

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HabitRouteContent(
    navigateBack: () -> Unit,
) {
    val viewModel: HabitViewModel = koinViewModel(key = "habits")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showCelebration by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HabitContract.Effect.Error -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is HabitContract.Effect.HabitSaved -> {
                    Toast.makeText(context, "Abitudine salvata!", Toast.LENGTH_SHORT).show()
                }
                is HabitContract.Effect.HabitDeleted -> {
                    Toast.makeText(context, "Abitudine rimossa", Toast.LENGTH_SHORT).show()
                }
                is HabitContract.Effect.StreakMilestone -> {
                    showCelebration = true
                }
            }
        }
    }

    HabitListScreen(
        state = state,
        onIntent = { viewModel.onIntent(it) },
        onBackPressed = navigateBack,
        showCelebration = showCelebration,
        onCelebrationComplete = { showCelebration = false },
    )
}
