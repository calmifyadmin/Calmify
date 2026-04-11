package com.lifo.server.service

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.Parameter
import com.google.firebase.remoteconfig.ParameterValue
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

/**
 * Reads feature flags from Firebase Remote Config (not Firestore).
 *
 * Uses the Admin SDK to fetch the published Remote Config template,
 * extracts default values from both top-level parameters and parameter groups
 * (e.g., "Social Features" group in the Firebase Console).
 *
 * Results are cached for 5 minutes to avoid hitting the Remote Config API on every request.
 */
class FeatureFlagService {
    private val logger = LoggerFactory.getLogger(FeatureFlagService::class.java)
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    private val cache = Cache.Builder<String, Map<String, Boolean>>()
        .expireAfterWrite(5.minutes)
        .build()

    suspend fun getFlags(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val cached = cache.get("flags")
        if (cached != null) return@withContext cached

        try {
            val template = remoteConfig.getTemplate()
            val flags = mutableMapOf<String, Boolean>()

            // Top-level parameters
            extractBooleanFlags(template.parameters, flags)

            // Parameters inside groups (e.g., "Social Features")
            for ((_, group) in template.parameterGroups) {
                extractBooleanFlags(group.parameters, flags)
            }

            cache.put("flags", flags)
            logger.info("Refreshed feature flags from Remote Config: {} flags loaded", flags.size)
            flags
        } catch (e: Exception) {
            logger.error("Failed to fetch Remote Config template: {}", e.message)
            emptyMap()
        }
    }

    fun invalidateCache() {
        cache.invalidateAll()
    }

    private fun extractBooleanFlags(
        parameters: Map<String, Parameter>,
        out: MutableMap<String, Boolean>,
    ) {
        for ((key, param) in parameters) {
            val defaultVal = param.defaultValue ?: continue
            if (defaultVal is ParameterValue.Explicit) {
                out[key] = defaultVal.value.equals("true", ignoreCase = true)
            }
        }
    }
}
