package com.lifo.biocontext

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.preview.BioPreviewProvider
import com.lifo.util.repository.BioSignalRepository
import com.lifo.util.repository.MeditationRepository
import com.lifo.util.repository.MongoRepository
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

/**
 * ViewModel for [BioTimelineScreen] — Phase 9.2.1. Folds raw bio samples,
 * journal entries, and meditation sessions into a single time series for
 * the requested signal type + window.
 *
 * Re-engineering: the cross-feature integration (journal + meditation overlay
 * markers on a bio chart) is a re-engineering decision — Claude Design kept
 * surfaces siloed. We have all three repositories already, so this surface
 * is essentially free.
 */
class BioTimelineViewModel(
    val signal: BioSignalDataType,
    private val bioRepository: BioSignalRepository,
    private val meditationRepository: MeditationRepository,
    private val mongoRepository: MongoRepository,
    private val authProvider: AuthProvider,
    private val preview: BioPreviewProvider,
) : MviViewModel<BioTimelineContract.Intent, BioTimelineContract.State, BioTimelineContract.Effect>(
    BioTimelineContract.State(signal = signal)
) {

    init { refresh() }

    override fun handleIntent(intent: BioTimelineContract.Intent) {
        when (intent) {
            BioTimelineContract.Intent.Refresh -> refresh()
            is BioTimelineContract.Intent.SetWindow -> {
                updateState { copy(window = intent.window) }
                refresh()
            }
        }
    }

    private fun refresh() {
        scope.launch {
            updateState { copy(isLoading = true) }
            val now = Clock.System.now()
            val windowFrom = now.minus(currentState.window.days.toLong().days)

            val samples = bioRepository.getRawSamples(signal, windowFrom, now)
            val baseline = bioRepository.getBaseline(signal)

            val points = samples.mapNotNull { sample ->
                val value = projectValue(signal, sample) ?: return@mapNotNull null
                BioTimelineContract.TimelinePoint(
                    timestampMillis = sample.timestamp.toEpochMilliseconds(),
                    value = value,
                )
            }.sortedBy { it.timestampMillis }

            // Cross-feature overlay: journal entries + meditation sessions in window
            val journalMarkers = runCatching {
                mongoRepository.getAllDiaries()
                    .firstOrNull { it !is RequestState.Loading }
                    ?.let { state ->
                        if (state is RequestState.Success) {
                            state.data.values.flatten()
                                .filter {
                                    val ts = it.dateMillis
                                    ts in windowFrom.toEpochMilliseconds()..now.toEpochMilliseconds()
                                }
                                .map { diary ->
                                    BioTimelineContract.TimelineMarker(
                                        timestampMillis = diary.dateMillis,
                                        kind = BioTimelineContract.MarkerKind.JOURNAL,
                                        label = diary.title.take(40),
                                    )
                                }
                        } else emptyList()
                    } ?: emptyList()
            }.getOrDefault(emptyList())

            val medMarkers = runCatching {
                val userId = authProvider.currentUserId ?: return@runCatching emptyList()
                meditationRepository.getRecentSessions(userId, limit = 100)
                    .firstOrNull()
                    ?.filter {
                        it.timestampMillis in windowFrom.toEpochMilliseconds()..now.toEpochMilliseconds()
                    }
                    ?.map { session ->
                        BioTimelineContract.TimelineMarker(
                            timestampMillis = session.timestampMillis,
                            kind = BioTimelineContract.MarkerKind.MEDITATION,
                            label = session.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        )
                    } ?: emptyList()
            }.getOrDefault(emptyList())

            val isEmpty = points.size < 2
            if (isEmpty && preview.enabled) {
                // Preview fallback — synthetic curve so the user sees the design
                val previewState = buildPreviewState(currentState.window)
                updateState { previewState }
                return@launch
            }

            val avg = if (points.isEmpty()) 0.0 else points.map { it.value }.average()
            val min = points.minOfOrNull { it.value } ?: 0.0
            val max = points.maxOfOrNull { it.value } ?: 0.0
            val daysTracked = points
                .map { it.timestampMillis / 86_400_000L }
                .distinct()
                .size

            updateState {
                copy(
                    isLoading = false,
                    isEmpty = isEmpty,
                    points = points,
                    markers = (journalMarkers + medMarkers).sortedBy { it.timestampMillis },
                    averageValue = avg,
                    minValue = min,
                    maxValue = max,
                    daysTracked = daysTracked,
                    baselineLow = baseline?.p10,
                    baselineHigh = baseline?.p90,
                )
            }
        }
    }

    private fun projectValue(type: BioSignalDataType, sample: BioSignal): Double? = when (type) {
        BioSignalDataType.HEART_RATE -> (sample as? BioSignal.HeartRateSample)?.bpm?.toDouble()
        BioSignalDataType.HRV -> (sample as? BioSignal.HrvSample)?.rmssdMillis
        BioSignalDataType.SLEEP -> (sample as? BioSignal.SleepSession)?.let { (it.durationSeconds / 60.0) }
        BioSignalDataType.STEPS -> (sample as? BioSignal.StepCount)?.count?.toDouble()
        BioSignalDataType.RESTING_HEART_RATE -> (sample as? BioSignal.RestingHeartRate)?.bpm?.toDouble()
        BioSignalDataType.OXYGEN_SATURATION -> (sample as? BioSignal.OxygenSaturationSample)?.percent
        BioSignalDataType.ACTIVITY -> (sample as? BioSignal.ActivitySession)?.let { (it.durationSeconds / 60.0) }
    }

    private fun buildPreviewState(window: BioTimelineContract.TimelineWindow): BioTimelineContract.State {
        // Sine-wave-ish curve so the chart reads as illustrative
        val now = Clock.System.now().toEpochMilliseconds()
        val day = 86_400_000L
        val count = window.days
        val points = (0 until count).map { i ->
            val ts = now - (count - 1 - i).toLong() * day
            val base = 64.0
            val noise = kotlin.math.sin(i * 0.6) * 5.0
            BioTimelineContract.TimelinePoint(ts, base + noise)
        }
        val markers = listOf(
            BioTimelineContract.TimelineMarker(now - 3 * day, BioTimelineContract.MarkerKind.JOURNAL, "Preview note"),
            BioTimelineContract.TimelineMarker(now - 5 * day, BioTimelineContract.MarkerKind.MEDITATION, "Body scan"),
        )
        return BioTimelineContract.State(
            signal = signal,
            window = window,
            points = points,
            markers = markers,
            averageValue = points.map { it.value }.average(),
            minValue = points.minOf { it.value },
            maxValue = points.maxOf { it.value },
            daysTracked = count,
            baselineLow = 60.0,
            baselineHigh = 72.0,
            isLoading = false,
            isEmpty = false,
        )
    }
}
