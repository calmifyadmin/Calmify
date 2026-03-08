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
}
