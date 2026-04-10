package com.lifo.server.ai

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

/**
 * Tracks AI token usage per user (daily + monthly).
 * Enforces quotas based on user tier (free vs premium).
 *
 * Storage: Firestore ai_usage/{userId}/daily/{date} and monthly/{yearMonth}
 * Local cache: 1-minute TTL to avoid Firestore reads on every request.
 */
class TokenTracker(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(TokenTracker::class.java)

    companion object {
        const val FREE_DAILY_LIMIT = 10_000      // ~20 chat messages
        const val FREE_MONTHLY_LIMIT = 200_000
        const val PREMIUM_DAILY_LIMIT = 100_000   // ~200 chat messages
        const val PREMIUM_MONTHLY_LIMIT = 2_000_000
    }

    // Short-lived cache to avoid reading Firestore on every request
    private val usageCache = Cache.Builder<String, UsageSnapshot>()
        .expireAfterWrite(1.minutes)
        .build()

    data class UsageSnapshot(
        val dailyTokens: Int,
        val monthlyTokens: Int,
        val isPremium: Boolean,
    )

    /**
     * Check if user has remaining quota. Throws [QuotaExceededException] if not.
     */
    suspend fun checkQuota(userId: String) {
        val usage = getUsageSnapshot(userId)
        val dailyLimit = if (usage.isPremium) PREMIUM_DAILY_LIMIT else FREE_DAILY_LIMIT
        val monthlyLimit = if (usage.isPremium) PREMIUM_MONTHLY_LIMIT else FREE_MONTHLY_LIMIT

        if (usage.dailyTokens >= dailyLimit) {
            throw QuotaExceededException(
                if (!usage.isPremium) "Hai raggiunto il limite giornaliero. Passa a Premium per piu' messaggi!"
                else "Hai raggiunto il limite giornaliero. Riprova domani."
            )
        }
        if (usage.monthlyTokens >= monthlyLimit) {
            throw QuotaExceededException("Hai raggiunto il limite mensile.")
        }
    }

    /**
     * Record token usage after a successful AI call.
     */
    suspend fun record(userId: String, tokens: Int) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val dayKey = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
        val monthKey = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"

        val userRef = db.collection("ai_usage").document(userId)

        // Daily counter (blocking ApiFuture.get() runs on IO dispatcher)
        userRef.collection("daily").document(dayKey)
            .set(mapOf("tokens" to FieldValue.increment(tokens.toLong()), "date" to dayKey), com.google.cloud.firestore.SetOptions.merge())
            .get()

        // Monthly counter
        userRef.collection("monthly").document(monthKey)
            .set(mapOf("tokens" to FieldValue.increment(tokens.toLong()), "month" to monthKey), com.google.cloud.firestore.SetOptions.merge())
            .get()

        // Invalidate cache
        usageCache.invalidate("usage:$userId")

        logger.debug("Recorded $tokens tokens for user $userId (day=$dayKey)")
    }

    /**
     * Get usage stats for the /ai/usage endpoint.
     */
    suspend fun getUsage(userId: String): com.lifo.shared.api.AiUsageResponse {
        val snapshot = getUsageSnapshot(userId)
        val dailyLimit = if (snapshot.isPremium) PREMIUM_DAILY_LIMIT else FREE_DAILY_LIMIT
        val monthlyLimit = if (snapshot.isPremium) PREMIUM_MONTHLY_LIMIT else FREE_MONTHLY_LIMIT

        return com.lifo.shared.api.AiUsageResponse(
            success = true,
            dailyTokensUsed = snapshot.dailyTokens,
            dailyTokensLimit = dailyLimit,
            monthlyTokensUsed = snapshot.monthlyTokens,
            monthlyTokensLimit = monthlyLimit,
            isPremium = snapshot.isPremium,
        )
    }

    fun getUserTier(userId: String): ModelRouter.UserTier {
        val cached = usageCache.get("usage:$userId")
        return if (cached?.isPremium == true) ModelRouter.UserTier.PREMIUM else ModelRouter.UserTier.FREE
    }

    private suspend fun getUsageSnapshot(userId: String): UsageSnapshot {
        usageCache.get("usage:$userId")?.let { return it }

        return withContext(Dispatchers.IO) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            val dayKey = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
            val monthKey = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"

            val userRef = db.collection("ai_usage").document(userId)

            val dailyDoc = userRef.collection("daily").document(dayKey).get().get()
            val monthlyDoc = userRef.collection("monthly").document(monthKey).get().get()
            // Android client uses "profile_settings" collection, not "profiles"
            val profileDoc = db.collection("profile_settings").document(userId).get().get()

            val snapshot = UsageSnapshot(
                dailyTokens = dailyDoc.getLong("tokens")?.toInt() ?: 0,
                monthlyTokens = monthlyDoc.getLong("tokens")?.toInt() ?: 0,
                isPremium = profileDoc.getBoolean("isPremium") ?: false,
            )

            usageCache.put("usage:$userId", snapshot)
            snapshot
        }
    }
}

class QuotaExceededException(message: String) : RuntimeException(message)
