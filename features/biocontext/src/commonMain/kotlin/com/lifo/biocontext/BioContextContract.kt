package com.lifo.biocontext

import com.lifo.util.model.BioSignalDataType
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.ProviderStatus

/**
 * MVI contract for [BioContextScreen] — the transparency dashboard.
 *
 * **Ethical positioning** (per `memory/feedback_calmify_values.md`):
 * - Show the user EVERYTHING we have, exactly. No paternalism.
 * - Every metric has a source + confidence + count.
 * - Delete is one tap away. Export is one tap away.
 * - No score, no goal, no comparison anxiety.
 */
object BioContextContract {

    sealed interface Intent : MviContract.Intent {
        /** Re-pull provider status + local counts (foreground refresh). */
        data object Refresh : Intent

        /** Trigger an immediate ingestion from the connected provider. */
        data object IngestNow : Intent

        /** GDPR Art.20 — show export sheet / share JSON. */
        data object ExportRequested : Intent

        /** GDPR Art.17 — user confirmed delete-all in the confirmation dialog. */
        data object DeleteAllConfirmed : Intent

        /**
         * Phase 9.1.1 — pause bio integration without deleting data.
         * Logs a REVOKE consent event but keeps the local store intact so
         * the user can rejoin later without losing their baseline / history.
         */
        data object DisconnectAll : Intent

        /** Phase 9.1.1 — change the inventory time window (7d / 30d / all-time). */
        data class SetWindow(val window: InventoryWindow) : Intent

        /** User toggled per-type integration in Settings → Bio-signals — refresh inventory. */
        data class TypeRevokeRequested(val type: BioSignalDataType) : Intent
    }

    /** Phase 9.1.1 — time window for the inventory grid. */
    enum class InventoryWindow { SEVEN_DAYS, THIRTY_DAYS, ALL_TIME }

    sealed interface Effect : MviContract.Effect {
        /** UI should open the OS share sheet with the given JSON payload. */
        data class ShareExport(val jsonPayload: String) : Effect

        /** Show a toast/snackbar (typically after delete or export success). */
        data class Toast(val messageKey: String) : Effect

        /** Health Connect deep-link request — UI opens the platform-level permissions screen. */
        data object OpenHealthConnectSettings : Effect
    }

    /**
     * Single inventory row — per data type tally for the UI.
     */
    data class TypeInventory(
        val type: BioSignalDataType,
        val sampleCount: Int,
        val uniqueSourceDevices: List<String>,
        val lastTimestampMillis: Long?,
        val isEnabled: Boolean,           // user has granted permission for this type
    )

    /**
     * Single source row for the "Connected sources" section.
     *
     * `kind` (added 2026-05-17) lets the UI distinguish phone-only data (Samsung
     * Health on Galaxy, Pixel Health on Pixel) from real wearable streams
     * (Mi Fitness, Fitbit, Garmin, etc.) — drives the "Non vedi il tuo wearable?"
     * hint banner in BioContextScreen.
     */
    data class ConnectedSource(
        val deviceName: String,
        val appName: String,
        val sampleCount: Int,
        val lastSeenMillis: Long?,
        val kind: com.lifo.util.model.SourceKind = com.lifo.util.model.SourceKind.WEARABLE,
    )

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isIngesting: Boolean = false,
        val isDeleting: Boolean = false,
        val isDisconnecting: Boolean = false,
        val providerStatus: ProviderStatus = ProviderStatus.NotInstalled,
        val typeInventory: List<TypeInventory> = emptyList(),
        val connectedSources: List<ConnectedSource> = emptyList(),
        val lastSyncMillis: Long? = null,
        val totalSamplesLocal: Int = 0,
        val totalAggregatesLocal: Int = 0,
        val totalAggregatesPending: Int = 0,   // is_dirty=1 awaiting Phase 4 server push
        /** Phase 9.1.1 — inventory time window for the screen. Defaults to 7-day rolling. */
        val selectedWindow: InventoryWindow = InventoryWindow.SEVEN_DAYS,
        val errorMessage: String? = null,
    ) : MviContract.State {
        val isReady: Boolean get() = providerStatus is ProviderStatus.Ready
        val isEmpty: Boolean get() = totalSamplesLocal == 0
    }
}
