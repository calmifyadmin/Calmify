package com.lifo.biocontext

import com.lifo.util.model.BioSignalDataType
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.ProviderStatus

/**
 * MVI contract for [BioOnboardingScreen] — the 5-step dedicated onboarding pager.
 *
 * **Phase ordering** (matches `design/biosignal/Calmify Bio Onboarding.html`):
 *  0. Intro       — empathic breath visual + first CTA
 *  1. DataTypes   — toggle rows per [BioSignalDataType] with explain text
 *  2. Why         — 3 narrative use-case cards (journal/meditation/insight)
 *  3. Permission  — Health Connect install detection + permission request
 *  4. Confirm     — completion + skip-it-all fallback
 *
 * **Skip path**: the user can exit any step via the "Skip for now" link.
 * Skipping marks `bioOnboardingSkipped=true` in local prefs (persisted via
 * [BioOnboardingPrefs]) so the entry isn't re-shown on app launch but stays
 * re-accessible from Settings → Bio-signals.
 */
object BioOnboardingContract {

    sealed interface Intent : MviContract.Intent {
        // Navigation
        data object Next : Intent
        data object Back : Intent
        data object Skip : Intent
        data object Done : Intent           // step 4 → exit flow

        // Step 1 — DataTypes
        data class ToggleType(val type: BioSignalDataType, val enabled: Boolean) : Intent

        // Step 3 — Permission
        /** User tapped "Install Health Connect" — UI handles deep-link, then re-checks status. */
        data object InstallHealthConnect : Intent

        /** Re-poll provider status (called after returning from HC install / permission UI). */
        data object RefreshProviderStatus : Intent

        /** UI launched the permission ActivityResultContract and got the grant result. */
        data class PermissionResult(val granted: Set<BioSignalDataType>) : Intent

        /** User tapped "Grant permissions" — UI must launch the ActivityResultContract. */
        data object RequestPermission : Intent
    }

    sealed interface Effect : MviContract.Effect {
        /** UI should launch the HC permission ActivityResultContract with these types. */
        data class LaunchPermissionRequest(val types: Set<BioSignalDataType>) : Effect

        /** UI should deep-link to Play Store / Health Connect install. */
        data object OpenHealthConnectInstall : Effect

        /** Flow finished — UI navigates back to whatever pushed onboarding (typically Settings). */
        data object Finished : Effect

        /** User skipped — same as Finished but mark prefs so we don't re-prompt next launch. */
        data object Skipped : Effect
    }

    data class State(
        val currentStep: Step = Step.Intro,
        val enabledTypes: Set<BioSignalDataType> = BioSignalDataType.entries.toSet(),
        val providerStatus: ProviderStatus = ProviderStatus.NotInstalled,
        val grantedTypes: Set<BioSignalDataType> = emptySet(),
        val isProviderCheckInProgress: Boolean = false,
    ) : MviContract.State {
        val canGoNext: Boolean
            get() = when (currentStep) {
                Step.Intro -> true
                Step.DataTypes -> enabledTypes.isNotEmpty()
                Step.Why -> true
                Step.Permission -> providerStatus is ProviderStatus.Ready
                Step.Confirm -> true
            }

        val totalSteps: Int get() = Step.entries.size
        val stepIndex: Int get() = currentStep.ordinal
    }

    /** 5 onboarding steps in their canonical order. */
    enum class Step {
        Intro,
        DataTypes,
        Why,
        Permission,
        Confirm;

        fun next(): Step? = entries.getOrNull(ordinal + 1)
        fun previous(): Step? = entries.getOrNull(ordinal - 1)
    }
}
