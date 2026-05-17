package com.lifo.biocontext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.lifo.util.model.BioSignalDataType
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Entry point for [BioTimelineScreen] (Phase 9.2.1, 2026-05-17).
 *
 * Signal type is passed positionally to the Koin viewModel factory so the
 * VM constructor can hold it as an immutable `val signal` (state-machine
 * starts at the right type and can never change).
 */
@Composable
fun BioTimelineRouteContent(
    signal: String,
    navigateBack: () -> Unit,
) {
    val parsed = runCatching { BioSignalDataType.valueOf(signal) }
        .getOrDefault(BioSignalDataType.HEART_RATE)
    val viewModel: BioTimelineViewModel = koinViewModel(
        key = "biotimeline_${parsed.name}",
        parameters = { parametersOf(parsed) },
    )
    val state by viewModel.state.collectAsState()

    BioTimelineScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = navigateBack,
    )
}
