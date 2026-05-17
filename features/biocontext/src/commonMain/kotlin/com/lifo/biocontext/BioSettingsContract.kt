package com.lifo.biocontext

import com.lifo.util.model.BioSignalDataType
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.ProviderStatus

/**
 * MVI contract for [BioSettingsScreen] — the master Bio-signal control panel.
 *
 * 1:1 with `design/biosignal/Calmify Bio Settings.html` (1488 LOC mockup).
 * 6 sections (matches mockup):
 *   1. Master toggle (Bio-signal integration on/off + status block + sync now)
 *   2. Permissions per data type (per-type Switch + delete-collected-data per type)
 *   3. Connected sources (source cards + "Add another source" CTA)
 *   4. Privacy & data (nav-rows to BioContext / Export / Delete-all + cloud-upload tradeoff)
 *   5. Surface toggles (which bio cards are shown across Home/Journal/Meditation/Insight)
 *   6. PRO tier reminder card
 *
 * **Persistence**: the master + per-type + surface toggles persist via Firestore
 * profile settings (Phase 9.3 work — for 9.1.3 the toggles live in-memory in
 * State, mutations call existing `BioSignalRepository` or are session-only).
 *
 * **Navigation**: BioSettings becomes the SINGLE entry-point under Settings →
 * "Bio-segnali". From here the user deep-links to:
 *  - `Effect.NavigateToBioContext` → existing BioContextScreen
 *  - `Effect.NavigateToBioOnboarding` → existing BioOnboardingScreen (for adding sources)
 *  - `Effect.NavigateToSubscription` → PRO upgrade
 */
object BioSettingsContract {

    sealed interface Intent : MviContract.Intent {
        /** Re-pull provider status + sources + sample counts. */
        data object Refresh : Intent

        /** Master toggle — when off, bio is "paused" (REVOKE consent, keep data). */
        data class SetMasterEnabled(val enabled: Boolean) : Intent

        /** Trigger an immediate ingestion from the connected provider. */
        data object SyncNow : Intent

        /** Per-type switch — flips both `enabledTypes` AND fires platform revoke if needed. */
        data class SetTypeEnabled(val type: BioSignalDataType, val enabled: Boolean) : Intent

        /** Per-type "delete collected data" link. */
        data class DeleteTypeData(val type: BioSignalDataType) : Intent

        /** Nav to BioContextScreen (transparency dashboard). */
        data object OpenBioContext : Intent

        /** Nav to BioOnboardingScreen (add another source / reconnect). */
        data object OpenAddSource : Intent

        /** GDPR Art.20 — full export. */
        data object ExportAll : Intent

        /** GDPR Art.17 — confirmation sheet. */
        data object RequestDeleteAll : Intent
        data object ConfirmDeleteAll : Intent

        /** Section 4 — opt-in to raw sample cloud upload (PRO-only). */
        data class SetCloudUpload(val enabled: Boolean) : Intent

        /** Section 5 — per-surface toggles (which bio cards render). */
        data class SetSurfaceEnabled(val surface: BioSurface, val enabled: Boolean) : Intent

        /** PRO card CTA — nav to subscription. */
        data object OpenSubscription : Intent
    }

    sealed interface Effect : MviContract.Effect {
        data object NavigateToBioContext : Effect
        data object NavigateToBioOnboarding : Effect
        data object NavigateToSubscription : Effect
        data class ShareExport(val jsonPayload: String) : Effect
        data class Toast(val messageKey: String) : Effect
        data object OpenHealthConnectSettings : Effect
    }

    data class State(
        val masterEnabled: Boolean = true,
        val providerStatus: ProviderStatus = ProviderStatus.NotInstalled,
        val isRefreshing: Boolean = false,
        val isIngesting: Boolean = false,
        val isDeleting: Boolean = false,
        val enabledTypes: Set<BioSignalDataType> = BioSignalDataType.entries.toSet(),
        val connectedSources: List<BioContextContract.ConnectedSource> = emptyList(),
        val lastSyncMillis: Long? = null,
        val samplesToday: Int = 0,
        val cloudUploadEnabled: Boolean = false,
        val enabledSurfaces: Set<BioSurface> = BioSurface.entries.toSet(),
        val isPro: Boolean = false,
        /** Phase 9.2.5 — non-empty when baseline drift detected vs ~60d ago. */
        val baselineDrifts: List<com.lifo.ui.components.biosignal.BioBaselineDrift> = emptyList(),
    ) : MviContract.State

    /**
     * The 4 surfaces where bio cards render. Per dogma #3 (helpful, not
     * optimizing) every surface can be silenced independently.
     */
    enum class BioSurface {
        /** Journal sleep banner (Phase 5.2 Card 1). */
        JOURNAL_BANNER,
        /** Meditation outro HR card (Phase 5.3 Card 2). */
        MEDITATION_OUTRO,
        /** Home Today narrative card (Phase 5.1 Card 3). */
        HOME_TODAY,
        /** Insight cross-signal pattern (Phase 5.4 Card 4, PRO). */
        INSIGHT_PATTERN,
    }
}
