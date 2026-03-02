package com.lifo.mongo.repository

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.lifo.util.repository.FeatureFlagRepository
import com.lifo.util.repository.FeatureFlagRepository.FeatureFlags
import com.lifo.util.repository.FeatureFlagRepository.Keys
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Remote Config implementation of FeatureFlagRepository.
 *
 * - Fetches flags on app start with configurable minimum fetch interval.
 * - Exposes flags as a StateFlow for reactive UI updates.
 * - Falls back to in-app defaults when offline.
 */
@Singleton
class FirebaseFeatureFlagRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) : FeatureFlagRepository {

    private val _flags = MutableStateFlow(buildFlagsSnapshot())
    override val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()

    override suspend fun fetchAndActivate(): Boolean {
        return try {
            val activated = remoteConfig.fetchAndActivate().await()
            _flags.value = buildFlagsSnapshot()
            activated
        } catch (e: Exception) {
            // Offline or fetch failed — use cached/default values
            false
        }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        return remoteConfig.getBoolean(key)
    }

    override fun getString(key: String, default: String): String {
        return remoteConfig.getString(key).ifEmpty { default }
    }

    override fun getLong(key: String, default: Long): Long {
        return remoteConfig.getLong(key)
    }

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> {
        return flags.map { snapshot ->
            when (key) {
                Keys.SOCIAL_ENABLED -> snapshot.socialEnabled
                Keys.FEED_ENABLED -> snapshot.feedEnabled
                Keys.MESSAGING_ENABLED -> snapshot.messagingEnabled
                Keys.FEDERATION_ENABLED -> snapshot.federationEnabled
                Keys.SEMANTIC_SEARCH_ENABLED -> snapshot.semanticSearchEnabled
                Keys.MEDIA_PIPELINE_ENABLED -> snapshot.mediaPipelineEnabled
                Keys.PREMIUM_ENABLED -> snapshot.premiumEnabled
                Keys.AB_TEST_NEW_HOME -> snapshot.abTestNewHome
                Keys.MAINTENANCE_MODE -> snapshot.maintenanceMode
                else -> default
            }
        }.distinctUntilChanged()
    }

    private fun buildFlagsSnapshot(): FeatureFlags = FeatureFlags(
        socialEnabled = remoteConfig.getBoolean(Keys.SOCIAL_ENABLED),
        feedEnabled = remoteConfig.getBoolean(Keys.FEED_ENABLED),
        messagingEnabled = remoteConfig.getBoolean(Keys.MESSAGING_ENABLED),
        federationEnabled = remoteConfig.getBoolean(Keys.FEDERATION_ENABLED),
        semanticSearchEnabled = remoteConfig.getBoolean(Keys.SEMANTIC_SEARCH_ENABLED),
        mediaPipelineEnabled = remoteConfig.getBoolean(Keys.MEDIA_PIPELINE_ENABLED),
        premiumEnabled = remoteConfig.getBoolean(Keys.PREMIUM_ENABLED),
        abTestNewHome = remoteConfig.getBoolean(Keys.AB_TEST_NEW_HOME),
        maxFreeMessagesPerDay = remoteConfig.getLong(Keys.MAX_FREE_MESSAGES_PER_DAY),
        maintenanceMode = remoteConfig.getBoolean(Keys.MAINTENANCE_MODE),
    )
}
