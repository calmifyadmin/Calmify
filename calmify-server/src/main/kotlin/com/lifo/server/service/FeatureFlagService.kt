package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import io.github.reactivecircus.cache4k.Cache
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

    // Cache flags for 5 minutes — avoid Firestore reads on every request
    private val cache = Cache.Builder<String, Map<String, Boolean>>()
        .expireAfterWrite(5.minutes)
        .build()

    suspend fun getFlags(): Map<String, Boolean> {
        val cached = cache.get("flags")
        if (cached != null) return cached

        val doc = db.collection("config").document("flags").get().get()
        if (!doc.exists()) return emptyMap()

        val flags = doc.data?.mapValues { (_, v) -> v as? Boolean ?: false } ?: emptyMap()
        cache.put("flags", flags)
        logger.info("Refreshed feature flags cache: ${flags.size} flags")
        return flags
    }

    fun invalidateCache() {
        cache.invalidateAll()
    }
}
