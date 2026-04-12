package com.lifo.subscription

import com.lifo.util.analytics.AnalyticsTracker
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.SubscriptionRepository
import com.lifo.util.repository.WaitlistRepository
import kotlinx.coroutines.launch

class SubscriptionViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val authProvider: AuthProvider,
    private val waitlistRepository: WaitlistRepository,
    private val analyticsTracker: AnalyticsTracker,
) : MviViewModel<SubscriptionContract.Intent, SubscriptionContract.State, SubscriptionContract.Effect>(
    initialState = SubscriptionContract.State()
) {

    private val currentUserId: String?
        get() = authProvider.currentUserId

    init {
        observeSubscription()
    }

    override fun handleIntent(intent: SubscriptionContract.Intent) {
        when (intent) {
            is SubscriptionContract.Intent.LoadSubscriptionState -> loadSubscriptionState()
            is SubscriptionContract.Intent.PurchaseSubscription -> purchaseSubscription(intent.lookupKey)
            is SubscriptionContract.Intent.RefreshSubscriptionState -> refreshSubscriptionState()
            is SubscriptionContract.Intent.DismissPaywall -> sendEffect(SubscriptionContract.Effect.NavigateBack)
            is SubscriptionContract.Intent.UpdateWaitlistEmail -> updateState { copy(waitlistEmail = intent.email) }
            is SubscriptionContract.Intent.SubmitWaitlistEmail -> submitWaitlistEmail()
            is SubscriptionContract.Intent.DismissWaitlistDialog ->
                updateState { copy(showWaitlistDialog = false, waitlistEmail = "", waitlistSubmitted = false) }
        }
    }

    private fun observeSubscription() {
        scope.launch {
            subscriptionRepository.observeSubscription().collect { state ->
                val prevTier = currentState.subscriptionTier
                updateState { copy(subscriptionTier = state.tier) }
                if (prevTier != state.tier &&
                    state.tier == SubscriptionRepository.SubscriptionTier.PRO) {
                    sendEffect(SubscriptionContract.Effect.PurchaseSuccess(state.tier))
                }
            }
        }
    }

    private fun loadSubscriptionState() {
        if (currentUserId == null) {
            sendEffect(SubscriptionContract.Effect.ShowError("User not authenticated"))
            return
        }

        updateState { copy(isLoading = true, error = null) }

        scope.launch {
            when (val result = subscriptionRepository.refreshSubscriptionState()) {
                is RequestState.Success -> updateState {
                    copy(subscriptionTier = result.data.tier, isLoading = false)
                }
                is RequestState.Error -> {
                    // Waitlist repo throws; treat that as waitlist mode rather than error.
                    val isWaitlist = result.error.message?.contains("waitlist", true) == true
                    updateState {
                        copy(
                            isLoading = false,
                            isWaitlistMode = isWaitlist,
                            error = if (isWaitlist) null else result.message,
                        )
                    }
                }
                else -> {}
            }

            when (val productsResult = subscriptionRepository.getAvailableProducts()) {
                is RequestState.Success -> updateState {
                    copy(availableProducts = productsResult.data, isLoading = false)
                }
                is RequestState.Error -> updateState { copy(isLoading = false) }
                else -> {}
            }
        }
    }

    private fun refreshSubscriptionState() {
        scope.launch {
            when (val result = subscriptionRepository.refreshSubscriptionState()) {
                is RequestState.Success -> {
                    updateState { copy(subscriptionTier = result.data.tier) }
                    if (result.data.tier == SubscriptionRepository.SubscriptionTier.PRO) {
                        sendEffect(SubscriptionContract.Effect.PurchaseSuccess(result.data.tier))
                    }
                }
                else -> {}
            }
        }
    }

    private fun purchaseSubscription(lookupKey: String) {
        if (currentUserId == null) {
            sendEffect(SubscriptionContract.Effect.ShowError("User not authenticated"))
            return
        }

        updateState { copy(isLoading = true, error = null) }

        scope.launch {
            when (val result = subscriptionRepository.createCheckoutSession(lookupKey)) {
                is RequestState.Success -> {
                    analyticsTracker.logEvent(
                        "checkout_session_created",
                        mapOf("lookup_key" to lookupKey),
                    )
                    updateState { copy(isLoading = false) }
                    sendEffect(SubscriptionContract.Effect.OpenUrl(result.data.url))
                }
                is RequestState.Error -> {
                    if (result.error.message?.contains("waitlist", true) == true) {
                        analyticsTracker.logEvent("paywall_viewed", mapOf("source" to "checkout_attempt"))
                        updateState { copy(isLoading = false, showWaitlistDialog = true) }
                    } else {
                        updateState { copy(isLoading = false, error = result.message) }
                        sendEffect(SubscriptionContract.Effect.ShowError(result.message))
                    }
                }
                else -> updateState { copy(isLoading = false) }
            }
        }
    }

    private fun submitWaitlistEmail() {
        val email = currentState.waitlistEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            sendEffect(SubscriptionContract.Effect.ShowError("Inserisci un'email valida"))
            return
        }

        scope.launch {
            updateState { copy(isLoading = true) }
            when (waitlistRepository.saveWaitlistEmail(
                email = email,
                userId = currentUserId,
                source = "paywall",
            )) {
                is RequestState.Success -> {
                    analyticsTracker.logEvent(
                        "waitlist_signup",
                        mapOf(
                            "source" to "paywall",
                            "email_domain" to email.substringAfter("@"),
                        ),
                    )
                    updateState { copy(isLoading = false, waitlistSubmitted = true) }
                    sendEffect(SubscriptionContract.Effect.WaitlistSubmitSuccess)
                }
                is RequestState.Error -> {
                    updateState { copy(isLoading = false) }
                    sendEffect(SubscriptionContract.Effect.ShowError("Errore nel salvataggio. Riprova."))
                }
                else -> {}
            }
        }
    }
}
