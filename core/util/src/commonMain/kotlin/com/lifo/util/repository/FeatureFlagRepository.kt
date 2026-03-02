package com.lifo.util.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing feature flags with Firebase Remote Config.
 * Provides both synchronous (cached) and reactive (Flow) access to flags.
 */
interface FeatureFlagRepository {

    /** Current cached values of all feature flags. */
    val flags: StateFlow<FeatureFlags>

    /** Fetch latest flag values from server. Called on app start. */
    suspend fun fetchAndActivate(): Boolean

    /** Get a boolean flag value (cached). */
    fun getBoolean(key: String, default: Boolean = false): Boolean

    /** Get a string flag value (cached). */
    fun getString(key: String, default: String = ""): String

    /** Get a long flag value (cached). */
    fun getLong(key: String, default: Long = 0L): Long

    /** Observe a specific boolean flag reactively. */
    fun observeBoolean(key: String, default: Boolean = false): Flow<Boolean>

    /** All known feature flag keys. */
    companion object Keys {
        const val SOCIAL_ENABLED = "social_enabled"
        const val FEED_ENABLED = "feed_enabled"
        const val MESSAGING_ENABLED = "messaging_enabled"
        const val FEDERATION_ENABLED = "federation_enabled"
        const val SEMANTIC_SEARCH_ENABLED = "semantic_search_enabled"
        const val MEDIA_PIPELINE_ENABLED = "media_pipeline_enabled"
        const val PREMIUM_ENABLED = "premium_enabled"
        const val AB_TEST_NEW_HOME = "ab_test_new_home"
        const val MAX_FREE_MESSAGES_PER_DAY = "max_free_messages_per_day"
        const val MAINTENANCE_MODE = "maintenance_mode"
    }

    /** Immutable snapshot of all feature flags. */
    data class FeatureFlags(
        val socialEnabled: Boolean = false,
        val feedEnabled: Boolean = false,
        val messagingEnabled: Boolean = false,
        val federationEnabled: Boolean = false,
        val semanticSearchEnabled: Boolean = false,
        val mediaPipelineEnabled: Boolean = false,
        val premiumEnabled: Boolean = false,
        val abTestNewHome: Boolean = false,
        val maxFreeMessagesPerDay: Long = 50L,
        val maintenanceMode: Boolean = false,
    )
}
