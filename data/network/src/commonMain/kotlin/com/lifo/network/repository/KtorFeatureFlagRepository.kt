package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.repository.FeatureFlagRepository
import com.lifo.util.repository.FeatureFlagRepository.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

class KtorFeatureFlagRepository(
    private val api: KtorApiClient,
) : FeatureFlagRepository {

    private val _flags = MutableStateFlow(FeatureFlags())
    override val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()

    private var rawFlags: Map<String, Boolean> = emptyMap()

    override suspend fun fetchAndActivate(): Boolean {
        return try {
            val result = api.get<FlagsApiResponse>("/api/v1/feature-flags")
            val data = result.getDataOrNull()?.flags ?: return false
            rawFlags = data
            _flags.value = FeatureFlags(
                socialEnabled = data[FeatureFlagRepository.SOCIAL_ENABLED] ?: false,
                feedEnabled = data[FeatureFlagRepository.FEED_ENABLED] ?: false,
                messagingEnabled = data[FeatureFlagRepository.MESSAGING_ENABLED] ?: false,
                federationEnabled = data[FeatureFlagRepository.FEDERATION_ENABLED] ?: false,
                semanticSearchEnabled = data[FeatureFlagRepository.SEMANTIC_SEARCH_ENABLED] ?: false,
                mediaPipelineEnabled = data[FeatureFlagRepository.MEDIA_PIPELINE_ENABLED] ?: false,
                premiumEnabled = data[FeatureFlagRepository.PREMIUM_ENABLED] ?: false,
                abTestNewHome = data[FeatureFlagRepository.AB_TEST_NEW_HOME] ?: false,
                maintenanceMode = data[FeatureFlagRepository.MAINTENANCE_MODE] ?: false,
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = rawFlags[key] ?: default

    override fun getString(key: String, default: String): String = default // Flags are boolean-only on server

    override fun getLong(key: String, default: Long): Long = default

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> {
        return flags.map { flags ->
            when (key) {
                FeatureFlagRepository.SOCIAL_ENABLED -> flags.socialEnabled
                FeatureFlagRepository.FEED_ENABLED -> flags.feedEnabled
                FeatureFlagRepository.MESSAGING_ENABLED -> flags.messagingEnabled
                FeatureFlagRepository.PREMIUM_ENABLED -> flags.premiumEnabled
                FeatureFlagRepository.MAINTENANCE_MODE -> flags.maintenanceMode
                else -> default
            }
        }
    }
}

@Serializable
private data class FlagsApiResponse(val flags: Map<String, Boolean> = emptyMap())
