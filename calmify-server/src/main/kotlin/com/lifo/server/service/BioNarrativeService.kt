package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.lifo.server.ai.GeminiClient
import com.lifo.server.ai.GeminiContent
import com.lifo.shared.model.BioNarrativeRequestProto
import com.lifo.shared.model.BioNarrativeResponseProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Server-side AI narrative service (Phase 8.3, 2026-05-17).
 *
 * Generates a Gemini-backed weekly narrative for the PRO weekly insight card
 * (Phase 8.2 atom `BioNarrativeCard`). Three guarantees:
 *
 * 1. **24h Firestore cache** at `bio_narratives/{userId}_{periodKey}` — the
 *    same week pulled up multiple times returns the same cached response.
 *    Cuts Gemini cost ~95% for a typical user (one generation per Mon morning
 *    + N free re-reads through the week).
 *
 * 2. **3 hard framing rules in the system prompt** (per
 *    `memory/feedback_biosignal_plan_as_compass.md`):
 *    - Never diagnostic ("You are stressed")
 *    - Always observational ("The pattern suggests…")
 *    - Never prescriptive ("You should…")
 *
 * 3. **Grounding** — the prompt receives the actual numeric values (weekAvg,
 *    baselineMedian, deltaPercent, daysCovered, sourceDevice). Gemini is
 *    instructed to USE those values and never invent new ones.
 *
 * **Cost control** (CLAUDE.md §QUALITY MANDATE rule 12): exponential-backoff
 * retry on 429 (same pattern as AvatarService). No per-user rate limit on
 * the server — the endpoint is PRO-gated by the auth realm + client tier
 * check, and the 24h cache absorbs accidental re-fetches.
 */
class BioNarrativeService(
    private val db: Firestore,
    private val gemini: GeminiClient,
) {
    private val log = LoggerFactory.getLogger(BioNarrativeService::class.java)

    companion object {
        const val NARRATIVES_COLLECTION = "bio_narratives"
        private const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L
        private const val GEMINI_MODEL = "gemini-2.0-flash"
        private const val MAX_RETRIES = 4
    }

    /**
     * Generate (or serve cached) weekly narrative for the user.
     *
     * Authorization happens at the route layer (`principal.uid == userId`);
     * this service trusts the caller and uses `userId` for the cache key.
     */
    suspend fun generateNarrative(
        userId: String,
        request: BioNarrativeRequestProto,
    ): BioNarrativeResponseProto = withContext(Dispatchers.IO) {
        val docId = cacheKey(userId, request.periodKey)

        // 1) Cache lookup
        val cachedSnapshot = runCatching {
            db.collection(NARRATIVES_COLLECTION).document(docId).get().get()
        }.getOrNull()
        if (cachedSnapshot != null && cachedSnapshot.exists()) {
            val generatedAt = cachedSnapshot.getLong("generatedAtMillis") ?: 0L
            val ageMillis = System.currentTimeMillis() - generatedAt
            val cachedNarrative = cachedSnapshot.getString("narrative")
            if (ageMillis in 0..CACHE_TTL_MILLIS && !cachedNarrative.isNullOrBlank()) {
                log.info("bio.narrative.cache_hit user=$userId period=${request.periodKey} age=${ageMillis}ms")
                return@withContext BioNarrativeResponseProto(
                    narrative = cachedNarrative,
                    cached = true,
                    tokensUsed = 0,
                    generatedAtMillis = generatedAt,
                )
            }
        }

        // 2) Cache miss → call Gemini
        val systemInstruction = systemInstructionFor(request.langCode)
        val userContent = userPromptFor(request)
        val result = runCatching {
            generateWithRetry(systemInstruction, userContent)
        }.getOrElse { e ->
            log.warn("bio.narrative.gemini_failed user=$userId reason=${e.message}")
            return@withContext BioNarrativeResponseProto(
                narrative = "",
                cached = false,
                tokensUsed = 0,
                errorCode = if (e.message?.contains("429") == true || e.message?.contains("RESOURCE_EXHAUSTED") == true) "rate_limited" else "internal",
            )
        }

        if (result.blocked || result.text.isBlank()) {
            log.warn("bio.narrative.blocked user=$userId reason=${result.blockReason}")
            return@withContext BioNarrativeResponseProto(
                narrative = "",
                cached = false,
                tokensUsed = result.tokensUsed,
                errorCode = "blocked",
            )
        }

        val now = System.currentTimeMillis()
        val narrative = result.text.trim()

        // 3) Cache write (best-effort — narrative is still returned if write fails)
        runCatching {
            db.collection(NARRATIVES_COLLECTION).document(docId).set(
                mapOf(
                    "ownerId" to userId,
                    "periodKey" to request.periodKey,
                    "signalType" to request.signalType,
                    "flavor" to request.flavor,
                    "narrative" to narrative,
                    "langCode" to request.langCode,
                    "tokensUsed" to result.tokensUsed,
                    "generatedAtMillis" to now,
                )
            ).get()
        }.onFailure {
            log.warn("bio.narrative.cache_write_failed user=$userId reason=${it.message}")
        }

        log.info("bio.narrative.generated user=$userId period=${request.periodKey} tokens=${result.tokensUsed}")
        BioNarrativeResponseProto(
            narrative = narrative,
            cached = false,
            tokensUsed = result.tokensUsed,
            generatedAtMillis = now,
        )
    }

    /** GDPR Art.17 fan-out — invalidate every cached narrative for the user. */
    suspend fun deleteAllForUser(userId: String): Int = withContext(Dispatchers.IO) {
        val snapshot = runCatching {
            db.collection(NARRATIVES_COLLECTION)
                .whereEqualTo("ownerId", userId)
                .get().get()
        }.getOrNull() ?: return@withContext 0

        var deleted = 0
        snapshot.documents.chunked(500).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            runCatching { batch.commit().get() }
                .onSuccess { deleted += chunk.size }
                .onFailure { log.warn("bio.narrative.delete_chunk_failed user=$userId reason=${it.message}") }
        }
        deleted
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun cacheKey(userId: String, periodKey: String): String =
        "${userId}_$periodKey"

    /**
     * Locale-aware system instruction that BAKES IN the 3 hard framing rules
     * + the explicit "no new numbers" grounding constraint.
     */
    private fun systemInstructionFor(langCode: String): String {
        val voice = when (langCode.lowercase()) {
            "it" -> ITALIAN_VOICE
            else -> ENGLISH_VOICE
        }
        return """
            You are Calmify's wellness companion writing a weekly bio-narrative for the user.
            $voice

            HARD RULES (non-negotiable, always apply):
            1. NEVER diagnostic — never say "You are stressed", "You are healthy", or similar verdict language.
            2. ALWAYS observational — use "The pattern suggests…", "It seems that…", "Often this shows up after…".
            3. NEVER prescriptive — never say "You should…", "You need to…", "Try to…".
            4. Always include an implicit opt-out — phrases like "the pattern is not a verdict", "just an observation", "if it resonates", "many things could explain this".
            5. Ground EVERY number in the input values — never invent a new bpm/percent/threshold the user didn't provide. If you mention a value, it must be one of the numbers given.
            6. Tone is warm but understated. 4–6 sentences. Plain language. No emojis.
            7. Respect the user's source: if the data comes from a wrist wearable, acknowledge sample noise/variability briefly when relevant.
        """.trimIndent()
    }

    private fun userPromptFor(request: BioNarrativeRequestProto): String {
        val flavorLine = when (request.flavor) {
            "HIGHER" -> "This week ran higher than the user's usual."
            "LOWER" -> "This week ran lower than the user's usual."
            else -> "This week held close to the user's usual."
        }
        val signalLabel = when (request.signalType) {
            "HRV" -> "heart rate variability (HRV, RMSSD)"
            "SLEEP" -> "sleep duration"
            "STEPS" -> "daily steps"
            "HEART_RATE" -> "heart rate"
            "RESTING_HEART_RATE" -> "resting heart rate"
            "OXYGEN_SATURATION" -> "blood oxygen saturation (SpO₂)"
            "ACTIVITY" -> "activity sessions"
            else -> request.signalType.lowercase()
        }
        val units = when (request.signalType) {
            "HRV", "OXYGEN_SATURATION" -> if (request.signalType == "HRV") "ms" else "%"
            "SLEEP", "ACTIVITY" -> "minutes"
            "STEPS" -> "steps"
            else -> "bpm"
        }
        val sign = if (request.deltaPercent >= 0) "+" else ""
        val sourceLine = if (request.sourceDevice.isNotBlank()) {
            "Source device: ${request.sourceDevice} (${request.confidenceLevel.lowercase()} confidence)."
        } else {
            "Confidence: ${request.confidenceLevel.lowercase()}."
        }
        return """
            $flavorLine
            Signal: $signalLabel.
            This week's average: ${formatValue(request.weekAvg)} $units.
            User's 30-day baseline median: ${formatValue(request.baselineMedian)} $units.
            Delta: $sign${request.deltaPercent}% versus the user's typical.
            Days with data in the trailing 7: ${request.daysCovered}.
            $sourceLine

            Write 4–6 sentences of weekly narrative for this user. Use ONLY the numbers above; do not invent values. Follow the framing rules in the system instruction.
        """.trimIndent()
    }

    private fun formatValue(value: Double): String {
        if (value >= 100.0) return value.toInt().toString()
        val rounded = (value * 10).toInt() / 10.0
        return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
    }

    private suspend fun generateWithRetry(
        systemInstruction: String,
        userText: String,
    ): com.lifo.server.ai.GeminiResult {
        var lastError: Throwable? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                return gemini.generate(
                    model = GEMINI_MODEL,
                    systemInstruction = systemInstruction,
                    contents = listOf(GeminiContent(role = "user", text = userText)),
                    temperature = 0.7f,
                    maxTokens = 512,    // narratives are short
                )
            } catch (e: Exception) {
                lastError = e
                val msg = e.message ?: ""
                if ("429" in msg || "RESOURCE_EXHAUSTED" in msg) {
                    val wait = (1L shl attempt) * 1000L
                    log.info("bio.narrative.rate_limited attempt=${attempt + 1} wait=${wait}ms")
                    delay(wait)
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("Gemini narrative generation failed after $MAX_RETRIES attempts")
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Voice presets — per-language tone calibration for the system instruction.
// English + Italian shipped at launch; other locales fall through to English
// (Gemini handles the actual language switch via the user content + lang hint).
// ──────────────────────────────────────────────────────────────────────────

private val ENGLISH_VOICE = """
    Write in English. Voice: a thoughtful companion noticing something out loud, not a coach
    delivering a verdict. Use "your" not "you should". Prefer "this week" over "today".
""".trimIndent()

private val ITALIAN_VOICE = """
    Scrivi in italiano. Voce: un compagno riflessivo che osserva qualcosa ad alta voce,
    non un coach che emette un verdetto. Usa "il tuo" non "dovresti". Preferisci "questa
    settimana" a "oggi".
""".trimIndent()
