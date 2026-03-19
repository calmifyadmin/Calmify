package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
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

/**
 * Firestore-based implementation of FeatureFlagRepository.
 *
 * Reads flags from Firestore document: config/flags
 * This replaces Firebase Remote Config, making flags accessible
 * from any platform (Android, iOS, Web, Desktop) via Firestore.
 *
 * Supports real-time updates via Firestore snapshot listener.
 *
 * Firestore document structure:
 *   config/flags {
 *     social_enabled: Boolean,
 *     feed_enabled: Boolean,
 *     messaging_enabled: Boolean,
 *     federation_enabled: Boolean,
 *     semantic_search_enabled: Boolean,
 *     media_pipeline_enabled: Boolean,
 *     premium_enabled: Boolean,
 *     ab_test_new_home: Boolean,
 *     max_free_messages_per_day: Long,
 *     maintenance_mode: Boolean
 *   }
 */
class FirestoreFeatureFlagRepository(
    private val firestore: FirebaseFirestore
) : FeatureFlagRepository {

    companion object {
        private const val TAG = "FirestoreFFRepo"
        private const val COLLECTION_CONFIG = "config"
        private const val DOCUMENT_FLAGS = "flags"
    }

    private val flagsDocRef by lazy {
        firestore.collection(COLLECTION_CONFIG).document(DOCUMENT_FLAGS)
    }

    private val _flags = MutableStateFlow(FeatureFlags())
    override val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()

    // Cache for synchronous access
    private var cachedData: Map<String, Any> = emptyMap()

    init {
        // Start real-time listener
        flagsDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("[$TAG] Error listening to flags: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                cachedData = snapshot.data ?: emptyMap()
                _flags.value = buildFlagsSnapshot()
                println("[$TAG] Flags updated: ${_flags.value}")
            }
        }
    }

    override suspend fun fetchAndActivate(): Boolean {
        return try {
            val snapshot = flagsDocRef.get().await()
            if (snapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                cachedData = snapshot.data ?: emptyMap()
                _flags.value = buildFlagsSnapshot()
                true
            } else {
                // Document doesn't exist yet — create with defaults
                flagsDocRef.set(flagsToMap(FeatureFlags())).await()
                println("[$TAG] Created default flags document")
                false
            }
        } catch (e: Exception) {
            println("[$TAG] Error fetching flags: ${e.message}")
            false
        }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        return cachedData[key] as? Boolean ?: default
    }

    override fun getString(key: String, default: String): String {
        return (cachedData[key] as? String)?.ifEmpty { default } ?: default
    }

    override fun getLong(key: String, default: Long): Long {
        return (cachedData[key] as? Number)?.toLong() ?: default
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
        socialEnabled = cachedData[Keys.SOCIAL_ENABLED] as? Boolean ?: false,
        feedEnabled = cachedData[Keys.FEED_ENABLED] as? Boolean ?: false,
        messagingEnabled = cachedData[Keys.MESSAGING_ENABLED] as? Boolean ?: false,
        federationEnabled = cachedData[Keys.FEDERATION_ENABLED] as? Boolean ?: false,
        semanticSearchEnabled = cachedData[Keys.SEMANTIC_SEARCH_ENABLED] as? Boolean ?: false,
        mediaPipelineEnabled = cachedData[Keys.MEDIA_PIPELINE_ENABLED] as? Boolean ?: false,
        premiumEnabled = cachedData[Keys.PREMIUM_ENABLED] as? Boolean ?: false,
        abTestNewHome = cachedData[Keys.AB_TEST_NEW_HOME] as? Boolean ?: false,
        maxFreeMessagesPerDay = (cachedData[Keys.MAX_FREE_MESSAGES_PER_DAY] as? Number)?.toLong() ?: 50L,
        maintenanceMode = cachedData[Keys.MAINTENANCE_MODE] as? Boolean ?: false,
    )

    private fun flagsToMap(flags: FeatureFlags): Map<String, Any> = mapOf(
        Keys.SOCIAL_ENABLED to flags.socialEnabled,
        Keys.FEED_ENABLED to flags.feedEnabled,
        Keys.MESSAGING_ENABLED to flags.messagingEnabled,
        Keys.FEDERATION_ENABLED to flags.federationEnabled,
        Keys.SEMANTIC_SEARCH_ENABLED to flags.semanticSearchEnabled,
        Keys.MEDIA_PIPELINE_ENABLED to flags.mediaPipelineEnabled,
        Keys.PREMIUM_ENABLED to flags.premiumEnabled,
        Keys.AB_TEST_NEW_HOME to flags.abTestNewHome,
        Keys.MAX_FREE_MESSAGES_PER_DAY to flags.maxFreeMessagesPerDay,
        Keys.MAINTENANCE_MODE to flags.maintenanceMode,
    )
}
