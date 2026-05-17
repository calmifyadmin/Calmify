package com.lifo.biocontext

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.BioSignalRepository
import com.lifo.util.repository.HealthDataProvider
import com.lifo.util.repository.ProviderStatus
import com.lifo.util.repository.SubscriptionRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * ViewModel for the master Bio-Signal control panel (Phase 9.1.3, 2026-05-17).
 *
 * Reads from the same local SQLDelight + provider stack that BioContextViewModel
 * does; mutations are local + best-effort. Most toggles in this surface are
 * session-only for 9.1.3 — durable persistence (Firestore profile settings)
 * lands in Phase 9.3.
 */
class BioSettingsViewModel(
    private val repository: BioSignalRepository,
    private val provider: HealthDataProvider,
    private val authProvider: AuthProvider,
    private val subscriptionRepository: SubscriptionRepository,
) : MviViewModel<BioSettingsContract.Intent, BioSettingsContract.State, BioSettingsContract.Effect>(
    BioSettingsContract.State()
) {

    init { refresh() }

    override fun handleIntent(intent: BioSettingsContract.Intent) {
        when (intent) {
            BioSettingsContract.Intent.Refresh -> refresh()
            is BioSettingsContract.Intent.SetMasterEnabled -> setMasterEnabled(intent.enabled)
            BioSettingsContract.Intent.SyncNow -> syncNow()
            is BioSettingsContract.Intent.SetTypeEnabled -> setTypeEnabled(intent.type, intent.enabled)
            is BioSettingsContract.Intent.DeleteTypeData -> deleteTypeData(intent.type)
            BioSettingsContract.Intent.OpenBioContext -> sendEffect(BioSettingsContract.Effect.NavigateToBioContext)
            BioSettingsContract.Intent.OpenAddSource -> sendEffect(BioSettingsContract.Effect.NavigateToBioOnboarding)
            BioSettingsContract.Intent.ExportAll -> exportNow()
            BioSettingsContract.Intent.RequestDeleteAll -> Unit   // UI shows sheet
            BioSettingsContract.Intent.ConfirmDeleteAll -> deleteAll()
            is BioSettingsContract.Intent.SetCloudUpload -> setCloudUpload(intent.enabled)
            is BioSettingsContract.Intent.SetSurfaceEnabled -> setSurfaceEnabled(intent.surface, intent.enabled)
            BioSettingsContract.Intent.OpenSubscription -> sendEffect(BioSettingsContract.Effect.NavigateToSubscription)
        }
    }

    private fun refresh() {
        scope.launch {
            updateState { copy(isRefreshing = true) }
            val status = provider.checkAvailability()

            // Walk the last 30 days for source tallies + last-sync + today-sample-count
            val now = Clock.System.now()
            val windowFrom = now.minus(30.days)
            val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

            val sourceTallies = mutableMapOf<Pair<String, String>, BioContextContract.ConnectedSource>()
            var lastTs: Long? = null
            var samplesToday = 0

            for (type in BioSignalDataType.entries) {
                val samples = repository.getRawSamples(type, windowFrom, now)
                samples.forEach { sample ->
                    val key = sample.source.deviceName.ifBlank { "Unknown" } to sample.source.appName
                    val existing = sourceTallies[key]
                    val ts = sample.timestamp.toEpochMilliseconds()
                    if (existing == null) {
                        sourceTallies[key] = BioContextContract.ConnectedSource(
                            deviceName = key.first,
                            appName = key.second,
                            sampleCount = 1,
                            lastSeenMillis = ts,
                        )
                    } else {
                        sourceTallies[key] = existing.copy(
                            sampleCount = existing.sampleCount + 1,
                            lastSeenMillis = if (existing.lastSeenMillis == null || ts > existing.lastSeenMillis) ts
                                             else existing.lastSeenMillis,
                        )
                    }
                    if (lastTs == null || ts > lastTs) lastTs = ts
                    val sampleDate = sample.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    if (sampleDate == today) samplesToday++
                }
            }

            // PRO tier
            val pro = runCatching {
                subscriptionRepository.observeSubscription().firstOrNull()?.tier ==
                    SubscriptionRepository.SubscriptionTier.PRO
            }.getOrDefault(false)

            // Currently-enabled types: derive from provider grant status. NeedsPermission
            // tells us which types are MISSING; everything else is "enabled".
            val enabled = if (status is ProviderStatus.NeedsPermission) {
                BioSignalDataType.entries.toSet() - status.missing
            } else if (status is ProviderStatus.Ready) {
                BioSignalDataType.entries.toSet()
            } else {
                emptySet()
            }

            updateState {
                copy(
                    isRefreshing = false,
                    providerStatus = status,
                    connectedSources = sourceTallies.values.sortedByDescending { it.sampleCount },
                    lastSyncMillis = lastTs,
                    samplesToday = samplesToday,
                    enabledTypes = enabled,
                    isPro = pro,
                )
            }
        }
    }

    private fun setMasterEnabled(enabled: Boolean) {
        // Phase 9.3: persist via Firestore profile settings. For now in-memory +
        // deep-link to HC settings on disable so user can fully revoke.
        updateState { copy(masterEnabled = enabled) }
        if (!enabled) {
            sendEffect(BioSettingsContract.Effect.OpenHealthConnectSettings)
        }
    }

    private fun syncNow() {
        scope.launch {
            updateState { copy(isIngesting = true) }
            val since = Clock.System.now().minus(1.days)
            val result = repository.ingestFromProvider(provider, since)
            updateState { copy(isIngesting = false) }
            sendEffect(
                BioSettingsContract.Effect.Toast(
                    if (result.errors.isEmpty()) "bio_ingest_success" else "bio_ingest_partial"
                )
            )
            refresh()
        }
    }

    private fun setTypeEnabled(type: BioSignalDataType, enabled: Boolean) {
        // For 9.1.3 — surface the deep-link to HC settings so the user can
        // manage per-type grant. State change is optimistic; refresh() corrects.
        updateState {
            copy(enabledTypes = if (enabled) enabledTypes + type else enabledTypes - type)
        }
        sendEffect(BioSettingsContract.Effect.OpenHealthConnectSettings)
    }

    private fun deleteTypeData(type: BioSignalDataType) {
        // Phase 9.3: actual per-type wipe via repository.deleteForType(type).
        // For 9.1.3: toast acknowledgement; the user can full-delete via section 4.
        sendEffect(BioSettingsContract.Effect.Toast("bio_delete_partial_todo"))
    }

    private fun exportNow() {
        scope.launch {
            val json = repository.exportAll()
            sendEffect(BioSettingsContract.Effect.ShareExport(jsonPayload = json))
        }
    }

    private fun deleteAll() {
        scope.launch {
            updateState { copy(isDeleting = true) }
            repository.deleteAll()
            updateState { copy(isDeleting = false) }
            sendEffect(BioSettingsContract.Effect.Toast("bio_delete_success"))
            refresh()
        }
    }

    private fun setCloudUpload(enabled: Boolean) {
        // Phase 8 raw-upload window for advanced PRO correlations. For 9.1.3
        // session-only; Phase 9.3 persists + flips a server flag.
        updateState { copy(cloudUploadEnabled = enabled) }
    }

    private fun setSurfaceEnabled(surface: BioSettingsContract.BioSurface, enabled: Boolean) {
        updateState {
            copy(enabledSurfaces = if (enabled) enabledSurfaces + surface else enabledSurfaces - surface)
        }
    }
}
