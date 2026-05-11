package com.lifo.biocontext

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.BioSignalRepository
import com.lifo.util.repository.HealthDataProvider
import com.lifo.util.repository.ProviderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

/**
 * ViewModel for the Bio-Signal transparency dashboard.
 *
 * Reads from local SQLDelight (via [BioSignalRepository]) — does NOT trigger
 * a Health Connect read on every refresh, only when [BioContextContract.Intent.IngestNow]
 * is dispatched. This keeps the screen snappy and respectful of the wearable.
 */
class BioContextViewModel(
    private val repository: BioSignalRepository,
    private val provider: HealthDataProvider,
    private val authProvider: AuthProvider,
) : MviViewModel<BioContextContract.Intent, BioContextContract.State, BioContextContract.Effect>(
    BioContextContract.State()
) {

    init { refresh() }

    override fun handleIntent(intent: BioContextContract.Intent) {
        when (intent) {
            BioContextContract.Intent.Refresh -> refresh()
            BioContextContract.Intent.IngestNow -> ingestNow()
            BioContextContract.Intent.ExportRequested -> exportNow()
            BioContextContract.Intent.DeleteAllConfirmed -> deleteAll()
            is BioContextContract.Intent.TypeRevokeRequested -> {
                // The actual platform revoke happens via Health Connect deep-link;
                // we just bubble the UI effect up so the entry point can launch it.
                sendEffect(BioContextContract.Effect.OpenHealthConnectSettings)
            }
        }
    }

    private fun refresh() {
        scope.launch {
            updateState { copy(isRefreshing = true, errorMessage = null) }
            val status = provider.checkAvailability()

            val inventory = mutableListOf<BioContextContract.TypeInventory>()
            val sourceTallies = mutableMapOf<Pair<String, String>, BioContextContract.ConnectedSource>()
            var totalSamples = 0
            var lastTs: Long? = null

            // Walk a 30-day backwards window to feed the dashboard.
            val now = Clock.System.now()
            val windowFrom = now.minus(30.days)

            for (type in BioSignalDataType.entries) {
                val samples = repository.getRawSamples(type, windowFrom, now)
                val deviceNames = samples.mapTo(linkedSetOf()) { it.source.deviceName.ifBlank { "Unknown" } }
                val recent = samples.maxByOrNull { it.timestamp.toEpochMilliseconds() }
                inventory += BioContextContract.TypeInventory(
                    type = type,
                    sampleCount = samples.size,
                    uniqueSourceDevices = deviceNames.toList(),
                    lastTimestampMillis = recent?.timestamp?.toEpochMilliseconds(),
                    isEnabled = (status !is ProviderStatus.NeedsPermission || type !in status.missing),
                )
                totalSamples += samples.size
                if (recent != null) {
                    val candidate = recent.timestamp.toEpochMilliseconds()
                    if (lastTs == null || candidate > lastTs) lastTs = candidate
                }
                samples.forEach { s ->
                    val key = (s.source.deviceName.ifBlank { "Unknown" }) to s.source.appName
                    val existing = sourceTallies[key]
                    if (existing == null) {
                        sourceTallies[key] = BioContextContract.ConnectedSource(
                            deviceName = key.first,
                            appName = key.second,
                            sampleCount = 1,
                            lastSeenMillis = s.timestamp.toEpochMilliseconds(),
                        )
                    } else {
                        sourceTallies[key] = existing.copy(
                            sampleCount = existing.sampleCount + 1,
                            lastSeenMillis = maxOfNullable(existing.lastSeenMillis, s.timestamp.toEpochMilliseconds()),
                        )
                    }
                }
            }

            updateState {
                copy(
                    isLoading = false,
                    isRefreshing = false,
                    providerStatus = status,
                    typeInventory = inventory,
                    connectedSources = sourceTallies.values.sortedByDescending { it.sampleCount },
                    lastSyncMillis = lastTs,
                    totalSamplesLocal = totalSamples,
                )
            }
        }
    }

    private fun ingestNow() {
        scope.launch {
            updateState { copy(isIngesting = true, errorMessage = null) }
            val since = Clock.System.now().minus(1.days)
            val result = repository.ingestFromProvider(provider, since)
            updateState { copy(isIngesting = false) }
            if (result.errors.isNotEmpty()) {
                sendEffect(BioContextContract.Effect.Toast(messageKey = "bio_ingest_partial"))
            } else {
                sendEffect(BioContextContract.Effect.Toast(messageKey = "bio_ingest_success"))
            }
            refresh()
        }
    }

    private fun exportNow() {
        scope.launch {
            val json = repository.exportAll()
            sendEffect(BioContextContract.Effect.ShareExport(jsonPayload = json))
        }
    }

    private fun deleteAll() {
        scope.launch {
            updateState { copy(isDeleting = true) }
            repository.deleteAll()
            updateState { copy(isDeleting = false) }
            sendEffect(BioContextContract.Effect.Toast(messageKey = "bio_delete_success"))
            refresh()
        }
    }

    private fun maxOfNullable(a: Long?, b: Long): Long = if (a == null || b > a) b else a
}
