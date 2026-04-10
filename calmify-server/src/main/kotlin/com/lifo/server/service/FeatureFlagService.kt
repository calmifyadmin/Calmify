package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

@Serializable
data class FeatureFlagsProto(
    @ProtoNumber(1) val flags: Map<String, Boolean> = emptyMap(),
)

class FeatureFlagService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(FeatureFlagService::class.java)

    companion object {
        private const val CONFIG_COLLECTION = "config"
        private const val FLAGS_DOCUMENT = "flags"
    }

    // Cache flags for 5 minutes -- avoid Firestore reads on every request
    private val cache = Cache.Builder<String, Map<String, Boolean>>()
        .expireAfterWrite(5.minutes)
        .build()

    suspend fun getFlags(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val cached = cache.get(FLAGS_DOCUMENT)
        if (cached != null) return@withContext cached

        val doc = db.collection(CONFIG_COLLECTION).document(FLAGS_DOCUMENT).get().get()
        if (!doc.exists()) return@withContext emptyMap()

        val flags = doc.data?.mapValues { (_, v) -> v as? Boolean ?: false } ?: emptyMap()
        cache.put(FLAGS_DOCUMENT, flags)
        logger.info("Refreshed feature flags cache: ${flags.size} flags")
        flags
    }

    fun invalidateCache() {
        cache.invalidateAll()
    }
}
