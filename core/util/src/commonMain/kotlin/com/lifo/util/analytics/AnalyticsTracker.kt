package com.lifo.util.analytics

/**
 * Platform-agnostic analytics tracking interface.
 * Implementation uses Firebase Analytics on Android.
 */
interface AnalyticsTracker {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
    fun setUserId(userId: String?)
    fun setUserProperty(name: String, value: String?)
}

/** Standard event names for Calmify analytics. */
object AnalyticsEvents {
    const val DIARY_CREATED = "diary_created"
    const val DIARY_SAVED = "diary_saved"
    const val CHAT_MESSAGE_SENT = "chat_message_sent"
    const val CHAT_SESSION_STARTED = "chat_session_started"
    const val LIVE_CHAT_STARTED = "live_chat_started"
    const val THREAD_POSTED = "thread_posted"
    const val THREAD_LIKED = "thread_liked"
    const val SUBSCRIPTION_VIEWED = "subscription_viewed"
    const val SUBSCRIPTION_PURCHASED = "subscription_purchased"
    const val PROFILE_EDITED = "profile_edited"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val DATA_EXPORTED = "data_exported"

    /**
     * Bio-signal PRO gate impression — fires when a FREE user sees the
     * [com.lifo.ui.components.biosignal.BioProLock] in a bio card. Used to
     * validate that the sustainable-organism PRO split is actually being
     * encountered by FREE users (Phase 8.1, 2026-05-17).
     */
    const val BIO_PRO_GATE_IMPRESSION = "bio_pro_gate_impression"

    /** Bio-signal PRO gate click — fires when user taps the lock. */
    const val BIO_PRO_GATE_CLICK = "bio_pro_gate_click"
}

/** Stable surface identifiers for [AnalyticsEvents.BIO_PRO_GATE_*] events. */
object BioProGateSurface {
    /** Meditation outro card's HRV gate (Phase 5.3 Card 2). */
    const val MEDITATION_OUTRO_HRV = "meditation_outro_hrv"
    /** Insight cross-signal correlation card body (Phase 5.4 Card 4). */
    const val INSIGHT_CROSS_SIGNAL = "insight_cross_signal"
}
