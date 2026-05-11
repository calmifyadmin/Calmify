package com.lifo.biocontext

import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.HealthDataProvider
import kotlinx.coroutines.launch

/**
 * ViewModel for the 5-step Bio-Signal onboarding.
 *
 * Permission flow is split between VM and UI because Android permission
 * contracts need an Activity host:
 * - [BioOnboardingContract.Intent.RequestPermission] → effect tells the UI to
 *   launch the platform ActivityResultContract.
 * - [BioOnboardingContract.Intent.PermissionResult] → UI feeds the outcome back
 *   here so we can update [BioOnboardingContract.State.grantedTypes] and
 *   unlock the "Continue" button on step 3.
 *
 * Audit logging of GRANT events is deferred to the repository: when the user
 * first runs `ingestFromProvider` (driven by `BioSignalSyncWorker` or the
 * "Sync now" button on [BioContextScreen]), the consent state is implicit
 * in the presence of granted permissions + recorded raw samples. The REVOKE
 * audit trail — which matters most for GDPR Art.7 demonstrability — already
 * fires on `repository.deleteAll()`.
 */
class BioOnboardingViewModel(
    private val provider: HealthDataProvider,
) : MviViewModel<BioOnboardingContract.Intent, BioOnboardingContract.State, BioOnboardingContract.Effect>(
    BioOnboardingContract.State()
) {

    init { refreshProviderStatus() }

    override fun handleIntent(intent: BioOnboardingContract.Intent) {
        when (intent) {
            BioOnboardingContract.Intent.Next -> goNext()
            BioOnboardingContract.Intent.Back -> goBack()
            BioOnboardingContract.Intent.Skip -> sendEffect(BioOnboardingContract.Effect.Skipped)
            BioOnboardingContract.Intent.Done -> sendEffect(BioOnboardingContract.Effect.Finished)

            is BioOnboardingContract.Intent.ToggleType -> updateState {
                copy(
                    enabledTypes = if (intent.enabled) enabledTypes + intent.type
                    else enabledTypes - intent.type,
                )
            }

            BioOnboardingContract.Intent.InstallHealthConnect ->
                sendEffect(BioOnboardingContract.Effect.OpenHealthConnectInstall)

            BioOnboardingContract.Intent.RefreshProviderStatus -> refreshProviderStatus()

            BioOnboardingContract.Intent.RequestPermission ->
                sendEffect(BioOnboardingContract.Effect.LaunchPermissionRequest(currentState.enabledTypes))

            is BioOnboardingContract.Intent.PermissionResult -> {
                updateState { copy(grantedTypes = intent.granted) }
                refreshProviderStatus()
            }
        }
    }

    private fun goNext() {
        val next = currentState.currentStep.next() ?: return
        updateState { copy(currentStep = next) }
        if (next == BioOnboardingContract.Step.Permission) refreshProviderStatus()
    }

    private fun goBack() {
        val prev = currentState.currentStep.previous() ?: return
        updateState { copy(currentStep = prev) }
    }

    private fun refreshProviderStatus() {
        scope.launch {
            updateState { copy(isProviderCheckInProgress = true) }
            val status = provider.checkAvailability()
            updateState { copy(providerStatus = status, isProviderCheckInProgress = false) }
        }
    }
}
