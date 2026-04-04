package com.lifo.home.domain.aggregator

import com.lifo.home.domain.model.GrowthProgress
import com.lifo.home.domain.model.WellbeingAggregationResult
import com.lifo.home.domain.usecase.GetActivityImpactUseCase
import com.lifo.home.domain.usecase.GetGrowthProgressUseCase
import com.lifo.home.domain.usecase.GetSleepMoodCorrelationUseCase
import com.lifo.home.domain.usecase.GetWellbeingTrendUseCase
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.repository.BlockRepository
import com.lifo.util.repository.EnergyRepository
import com.lifo.util.repository.GratitudeRepository
import com.lifo.util.repository.HabitRepository
import com.lifo.util.repository.IkigaiRepository
import com.lifo.util.repository.InsightRepository
import com.lifo.util.repository.MovementRepository
import com.lifo.util.repository.ReframeRepository
import com.lifo.util.repository.RecurringThoughtRepository
import com.lifo.util.repository.SleepRepository
import com.lifo.util.repository.ValuesRepository
import com.lifo.util.repository.WellbeingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Aggregates ALL 14 data types into cross-domain correlations and narrative insights.
 *
 * Data sources:
 *   SleepLog, EnergyCheckIn, MovementLog, GratitudeEntry, ThoughtReframe,
 *   IkigaiExploration, ValuesDiscovery, Block, RecurringThought, WellbeingSnapshot,
 *   Habit + HabitCompletion, Diary, DiaryInsight, Mood (embedded in Diary).
 *
 * All repository reads are issued in parallel via [coroutineScope] + [async].
 * Must be called from a coroutine context (suspend fun).
 */
class WellbeingAggregator(
    private val authProvider: AuthProvider,
    private val sleepRepository: SleepRepository,
    private val energyRepository: EnergyRepository,
    private val movementRepository: MovementRepository,
    private val gratitudeRepository: GratitudeRepository,
    private val habitRepository: HabitRepository,
    private val wellbeingRepository: WellbeingRepository,
    private val insightRepository: InsightRepository,
    private val blockRepository: BlockRepository,
    private val ikigaiRepository: IkigaiRepository,
    private val valuesRepository: ValuesRepository,
    private val recurringThoughtRepository: RecurringThoughtRepository,
    private val reframeRepository: ReframeRepository,
    private val sleepMoodUseCase: GetSleepMoodCorrelationUseCase,
    private val activityImpactUseCase: GetActivityImpactUseCase,
    private val growthProgressUseCase: GetGrowthProgressUseCase,
    private val wellbeingTrendUseCase: GetWellbeingTrendUseCase,
) {
    private val userId: String get() = authProvider.currentUserId ?: ""

    /**
     * Collects all 14 data domains in parallel and computes cross-domain insights.
     */
    suspend fun aggregate(): WellbeingAggregationResult {
        if (userId.isEmpty()) return emptyResult()
        return try {
            coroutineScope { aggregateInternal() }
        } catch (e: Exception) {
            emptyResult()
        }
    }

    private suspend fun aggregateInternal(): WellbeingAggregationResult = coroutineScope {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val last7DayKeys = (0 until 7).map { today.minus(it, DateTimeUnit.DAY).toString() }.toSet()

        // ── Parallel data collection ───────────────────────────────────────────

        val sleepDef = async {
            sleepRepository.getRecentLogs(30).first().successOrEmpty()
        }
        val energyDef = async {
            energyRepository.getRecentCheckIns(30).first().successOrEmpty()
        }
        val movementDef = async {
            movementRepository.getRecentLogs(userId, 30).first()
        }
        val gratitudeDef = async {
            gratitudeRepository.getRecentEntries(30).first().successOrEmpty()
        }
        val habitsDef = async {
            habitRepository.getActiveHabits().first().successOrEmpty()
        }
        val insightsDef = async {
            insightRepository.getAllInsights().first().successOrEmpty()
        }
        val wellbeingDef = async {
            wellbeingRepository.getAllSnapshots().first().successOrEmpty()
        }
        val activeBlocksDef = async {
            blockRepository.getActiveBlocks(userId).first()
        }
        val resolvedBlocksDef = async {
            blockRepository.getResolvedBlocks(userId).first()
        }
        val ikigaiDef = async {
            ikigaiRepository.getExploration(userId).first()
        }
        val valuesDef = async {
            valuesRepository.getDiscovery(userId).first()
        }
        val thoughtsDef = async {
            recurringThoughtRepository.getThoughts(userId).first()
        }
        val reframesDef = async {
            reframeRepository.getRecentReframes(30).first().successOrEmpty()
        }

        // ── Await all ─────────────────────────────────────────────────────────

        val sleepLogs = sleepDef.await()
        val energyCheckIns = energyDef.await()
        val movementLogs = movementDef.await()
        val gratitudeEntries = gratitudeDef.await()
        val habits = habitsDef.await()
        val insights = insightsDef.await()
        val snapshots = wellbeingDef.await()
        val activeBlocks = activeBlocksDef.await()
        val resolvedBlocks = resolvedBlocksDef.await()
        val ikigai = ikigaiDef.await()
        val values = valuesDef.await()
        val recurringThoughts = thoughtsDef.await()
        val reframes = reframesDef.await()

        // ── Habit completions for last 7 days (per active habit) ──────────────

        val habitCompletionsDefs = habits.map { habit ->
            async {
                habitRepository.getCompletionsForHabit(habit.id, 90).first()
                    .successOrEmpty()
                    .filter { it.dayKey in last7DayKeys }
            }
        }
        val habitCompletionsLast7d = habitCompletionsDefs.flatMap { it.await() }

        // ── Cross-domain computation ───────────────────────────────────────────

        val sleepMoodCorr = sleepMoodUseCase(sleepLogs, insights)
        val activityImpact = activityImpactUseCase(
            energyCheckIns, movementLogs, gratitudeEntries, insights
        )
        val growthProgress = growthProgressUseCase(
            habits, habitCompletionsLast7d, gratitudeEntries,
            reframes, activeBlocks, resolvedBlocks, ikigai, values, recurringThoughts
        )
        val wellbeingTrend = wellbeingTrendUseCase(snapshots)

        val domainsWithData = listOf(
            sleepLogs.isNotEmpty(), energyCheckIns.isNotEmpty(), movementLogs.isNotEmpty(),
            gratitudeEntries.isNotEmpty(), habits.isNotEmpty(), insights.isNotEmpty(),
            snapshots.isNotEmpty(), (activeBlocks + resolvedBlocks).isNotEmpty(),
            ikigai != null, values != null, recurringThoughts.isNotEmpty(), reframes.isNotEmpty()
        ).count { it }

        WellbeingAggregationResult(
            sleepMoodCorrelation = sleepMoodCorr,
            activityImpact = activityImpact,
            growthProgress = growthProgress,
            wellbeingTrend = wellbeingTrend,
            dataCompleteness = domainsWithData / 12f,
            computedAtMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun <T> RequestState<List<T>>.successOrEmpty(): List<T> =
        if (this is RequestState.Success) data else emptyList()

    private fun emptyResult(): WellbeingAggregationResult = WellbeingAggregationResult(
        sleepMoodCorrelation = null,
        activityImpact = null,
        growthProgress = GrowthProgress(
            habitCompletionRate7d = 0f,
            gratitudeDays7d = 0,
            reframesCompleted = 0,
            blocksActive = 0,
            blocksResolved = 0,
            ikigaiProgress = 0f,
            valuesCount = 0,
            recurringThoughtsTamed = 0,
            strengths = emptyList(),
            suggestions = listOf("Inizia a registrare i tuoi dati per sbloccare gli insight personalizzati")
        ),
        wellbeingTrend = null,
        dataCompleteness = 0f,
        computedAtMillis = Clock.System.now().toEpochMilliseconds()
    )
}
