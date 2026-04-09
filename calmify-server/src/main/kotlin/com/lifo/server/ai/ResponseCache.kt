package com.lifo.server.ai

import io.github.reactivecircus.cache4k.Cache
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

/**
 * In-memory cache for AI responses. Saves tokens and reduces latency.
 *
 * Cache keys:
 * - Chat: "chat:{userId}:{normalizedMessageHash}" — TTL 1h
 * - Insight: "insight:{diaryId}" — TTL 24h (same diary = same insight)
 * - Analysis: "analyze:{textHash}" — TTL 1h
 */
class ResponseCache {
    private val logger = LoggerFactory.getLogger(ResponseCache::class.java)

    private val chatCache = Cache.Builder<String, CachedEntry>()
        .maximumCacheSize(5_000)
        .expireAfterWrite(1.hours)
        .build()

    private val insightCache = Cache.Builder<String, CachedEntry>()
        .maximumCacheSize(2_000)
        .expireAfterWrite(24.hours)
        .build()

    // --- Chat cache ---

    fun getChatKey(userId: String, message: String): String {
        val normalized = message.lowercase().trim().replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
        return "chat:$userId:${normalized.hashCode()}"
    }

    fun getChat(key: String): CachedEntry? = chatCache.get(key)

    fun putChat(key: String, text: String, tokensUsed: Int) {
        chatCache.put(key, CachedEntry(text, tokensUsed, Clock.System.now()))
        logger.debug("Cached chat response: $key")
    }

    // --- Insight cache ---

    fun getInsightKey(diaryId: String): String = "insight:$diaryId"

    fun getInsight(key: String): CachedEntry? = insightCache.get(key)

    fun putInsight(key: String, jsonText: String, tokensUsed: Int) {
        insightCache.put(key, CachedEntry(jsonText, tokensUsed, Clock.System.now()))
        logger.debug("Cached insight response: $key")
    }

    fun invalidateAll() {
        chatCache.invalidateAll()
        insightCache.invalidateAll()
    }

    data class CachedEntry(
        val text: String,
        val tokensUsed: Int,
        val cachedAt: Instant,
    )
}
