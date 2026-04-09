# AI Server-Side — Piano Implementazione

> **Parent task**: KMP Full Migration (Option C Hybrid)
> **Dipende da**: BACKEND_KTOR_SERVER.md (server infra), BACKEND_PROTOBUF.md (serializzazione)
> **Effort stimato**: 1 settimana
> **Risultato**: AI centralizzata server-side — caching, routing, prompt engineering, sicurezza

---

## Perche' AI server-side

### Ora (client-side)

```
Client → Gemini API direttamente
  Problemi:
  ├── API key nell'APK (decompilabile)
  ├── Ogni utente consuma token separatamente (no cache)
  ├── Stessa domanda da 100 utenti = 100 chiamate API = 100x costi
  ├── Prompt engineering sparso nel codice client
  ├── Nessun controllo su cosa l'utente manda all'AI
  ├── Nessun audit/logging delle conversazioni AI
  └── Impossibile A/B test su prompt o modelli
```

### Target (server-side)

```
Client → Ktor Server → Gemini API
  Vantaggi:
  ├── API key SOLO sul server (zero esposizione)
  ├── Response cache: stessa domanda = risposta istantanea (0 token)
  ├── Prompt registry centralizzato (cambi prompt senza app update)
  ├── Model routing intelligente (Flash per chat, Pro per insight)
  ├── Content filtering pre/post (sicurezza, GDPR)
  ├── Token usage tracking per utente
  ├── A/B test su prompt/modelli senza deploy client
  └── Rate limiting AI per utente (anti-abuse)
```

---

## Architettura

```
┌─────────────────────────────────────────────────────────┐
│  Client                                                  │
│  POST /api/v1/ai/chat     {message, sessionId}          │
│  POST /api/v1/ai/insight  {diaryId}                     │
│  POST /api/v1/ai/analyze  {text}                        │
│  WS   /ws/ai/live         {audio chunks}                │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  AI Orchestrator (Server)                                │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Request Pipeline                                 │   │
│  │  1. Auth + Rate Limit                             │   │
│  │  2. Content Filter (input sanitization)           │   │
│  │  3. Cache Check (semantic hash)                   │   │
│  │  4. Context Builder (user history, preferences)   │   │
│  │  5. Model Router (Flash vs Pro vs Lite)           │   │
│  │  6. Prompt Assembly (template + context + input)  │   │
│  │  7. API Call (Gemini)                             │   │
│  │  8. Response Filter (safety, quality)             │   │
│  │  9. Cache Store                                   │   │
│  │  10. Audit Log                                    │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌────────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │PromptReg.  │  │ResponseCache │  │ TokenTracker   │  │
│  │            │  │              │  │                │  │
│  │ Templates  │  │ Semantic     │  │ Per-user       │  │
│  │ versioned  │  │ hash → resp  │  │ daily/monthly  │  │
│  │ A/B tested │  │ TTL based    │  │ quota tracking │  │
│  └────────────┘  └──────────────┘  └────────────────┘  │
└──────────────────────────┬──────────────────────────────┘
                           │
            ┌──────────────┴──────────────┐
            ▼                             ▼
    ┌───────────────┐           ┌─────────────────┐
    │ Gemini 2.0    │           │ Gemini 2.0      │
    │ Flash         │           │ Pro             │
    │ (chat, quick) │           │ (insight, deep) │
    └───────────────┘           └─────────────────┘
```

---

## Componenti

### 1. AI Routes

```kotlin
// server/src/main/kotlin/com/lifo/server/routing/AiRoutes.kt

fun Route.aiRoutes() {
    authenticate("firebase") {
        route("/api/v1/ai") {

            // Chat: singolo messaggio → risposta
            post("/chat") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<AiChatRequest>()
                val response = aiOrchestrator.chat(user.uid, request)
                call.respond(response)
            }

            // Chat streaming: risposta token by token
            post("/chat/stream") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<AiChatRequest>()
                call.respondSse {
                    aiOrchestrator.chatStream(user.uid, request).collect { chunk ->
                        send(SseEvent(data = chunk))
                    }
                }
            }

            // Diary insight: analisi completa di un diary entry
            post("/insight") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<AiInsightRequest>()
                val insight = aiOrchestrator.generateInsight(user.uid, request)
                call.respond(insight)
            }

            // Text analysis: sentiment + topics + cognitive patterns
            post("/analyze") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<AiAnalyzeRequest>()
                val analysis = aiOrchestrator.analyzeText(user.uid, request)
                call.respond(analysis)
            }

            // Usage stats
            get("/usage") {
                val user = call.principal<UserPrincipal>()!!
                val usage = tokenTracker.getUsage(user.uid)
                call.respond(usage)
            }
        }

        // Live voice chat (WebSocket → Gemini Live API)
        webSocket("/ws/ai/live") {
            val user = call.principal<UserPrincipal>()!!
            liveAiSession.handle(user.uid, this)
        }
    }
}
```

### 2. Request/Response Models

```kotlin
// shared-models

@Serializable
data class AiChatRequest(
    @ProtoNumber(1) val sessionId: String,
    @ProtoNumber(2) val message: String,
    @ProtoNumber(3) val contextHint: String? = null   // "user is feeling anxious"
)

@Serializable
data class AiChatResponse(
    @ProtoNumber(1) val messageId: String,
    @ProtoNumber(2) val content: String,
    @ProtoNumber(3) val emotion: String = "neutral",   // AI-detected emotion
    @ProtoNumber(4) val suggestedActions: List<String> = emptyList(),
    @ProtoNumber(5) val tokenUsed: Int = 0,
    @ProtoNumber(6) val cached: Boolean = false
)

@Serializable
data class AiInsightRequest(
    @ProtoNumber(1) val diaryId: String,
    @ProtoNumber(2) val diaryText: String,
    @ProtoNumber(3) val mood: Int,
    @ProtoNumber(4) val triggers: List<String> = emptyList()
)

@Serializable
data class AiInsightResponse(
    @ProtoNumber(1) val sentimentLabel: String,
    @ProtoNumber(2) val sentimentMagnitude: Float,
    @ProtoNumber(3) val cognitivePatterns: List<CognitivePatternProto> = emptyList(),
    @ProtoNumber(4) val topics: List<String> = emptyList(),
    @ProtoNumber(5) val summary: String,
    @ProtoNumber(6) val suggestions: List<String> = emptyList(),
    @ProtoNumber(7) val cached: Boolean = false
)

@Serializable
data class CognitivePatternProto(
    @ProtoNumber(1) val name: String,          // "catastrophizing", "black_white_thinking"
    @ProtoNumber(2) val confidence: Float,      // 0.0 - 1.0
    @ProtoNumber(3) val excerpt: String         // parte del testo che lo evidenzia
)

@Serializable
data class AiAnalyzeRequest(
    @ProtoNumber(1) val text: String,
    @ProtoNumber(2) val analysisType: String = "full"  // "sentiment", "topics", "full"
)

@Serializable
data class AiUsageResponse(
    @ProtoNumber(1) val tokensUsedToday: Int,
    @ProtoNumber(2) val tokensUsedMonth: Int,
    @ProtoNumber(3) val dailyLimit: Int,
    @ProtoNumber(4) val monthlyLimit: Int,
    @ProtoNumber(5) val isPremium: Boolean
)
```

### 3. AI Orchestrator

```kotlin
// server/src/main/kotlin/com/lifo/server/ai/AiOrchestrator.kt

class AiOrchestrator(
    private val promptRegistry: PromptRegistry,
    private val responseCache: ResponseCache,
    private val tokenTracker: TokenTracker,
    private val contentFilter: ContentFilter,
    private val modelRouter: ModelRouter,
    private val geminiClient: GeminiClient,
    private val userContextBuilder: UserContextBuilder
) {
    suspend fun chat(userId: String, request: AiChatRequest): AiChatResponse {
        // 1. Content filter (blocca contenuti dannosi/inappropriati)
        contentFilter.validateInput(request.message)

        // 2. Rate limit check
        tokenTracker.checkQuota(userId)

        // 3. Cache check
        val cacheKey = responseCache.computeKey(userId, request)
        responseCache.get(cacheKey)?.let { return it.copy(cached = true) }

        // 4. Build context (storia utente, preferenze, ultime entries)
        val context = userContextBuilder.build(userId, request.sessionId)

        // 5. Select model
        val model = modelRouter.selectModel(
            taskType = TaskType.CHAT,
            contextLength = context.length,
            userTier = tokenTracker.getUserTier(userId)
        )

        // 6. Assemble prompt
        val prompt = promptRegistry.getPrompt("chat_v3")
            .withSystemInstruction(context.systemPrompt)
            .withHistory(context.recentMessages)
            .withUserMessage(request.message)

        // 7. Call Gemini
        val result = geminiClient.generate(model, prompt)

        // 8. Filter response
        val filtered = contentFilter.validateOutput(result)

        // 9. Track tokens
        tokenTracker.record(userId, result.tokenCount)

        // 10. Cache
        val response = AiChatResponse(
            messageId = randomUuid(),
            content = filtered.text,
            emotion = filtered.detectedEmotion,
            suggestedActions = filtered.suggestedActions,
            tokenUsed = result.tokenCount
        )
        responseCache.put(cacheKey, response, ttl = 1.hours)

        return response
    }

    // Streaming variant
    fun chatStream(userId: String, request: AiChatRequest): Flow<String> = flow {
        contentFilter.validateInput(request.message)
        tokenTracker.checkQuota(userId)

        val context = userContextBuilder.build(userId, request.sessionId)
        val model = modelRouter.selectModel(TaskType.CHAT_STREAM)
        val prompt = promptRegistry.getPrompt("chat_v3")
            .withSystemInstruction(context.systemPrompt)
            .withHistory(context.recentMessages)
            .withUserMessage(request.message)

        geminiClient.generateStream(model, prompt).collect { chunk ->
            emit(chunk.text)
        }
    }

    suspend fun generateInsight(userId: String, request: AiInsightRequest): AiInsightResponse {
        // Stessa pipeline ma con:
        // - Model: Gemini Pro (analisi profonda)
        // - Prompt: "insight_analysis_v2" (CBT-informed)
        // - Cache TTL: 24h (insight non cambia per lo stesso diary)
        // - Output strutturato: JSON mode per parsing affidabile
        // ...
    }
}
```

### 4. Prompt Registry

```kotlin
// server/src/main/kotlin/com/lifo/server/ai/PromptRegistry.kt

class PromptRegistry(
    private val firestore: Firestore  // Prompt salvati in Firestore → aggiornabili senza deploy
) {
    // Cache locale dei prompt (refresh ogni 5 min)
    private val prompts = mutableMapOf<String, PromptTemplate>()

    data class PromptTemplate(
        val id: String,
        val version: Int,
        val systemInstruction: String,
        val userTemplate: String,       // con {{placeholders}}
        val model: String,              // default model
        val temperature: Float,
        val maxTokens: Int,
        val abTestGroup: String? = null // "A", "B", null
    )

    suspend fun getPrompt(id: String, abGroup: String? = null): PromptTemplate {
        return prompts[id] ?: loadFromFirestore(id)
    }

    // Prompt attuali di Calmify (da migrare in Firestore)
    companion object {
        val DEFAULTS = mapOf(
            "chat_v3" to PromptTemplate(
                id = "chat_v3",
                version = 3,
                systemInstruction = """
                    Sei Calmify, un compagno di benessere emotivo.
                    Rispondi in italiano. Sii empatico, mai giudicante.
                    Usa tecniche CBT quando appropriato.
                    Se l'utente esprime pensieri autolesionistici,
                    suggerisci risorse professionali (Telefono Amico: 02 2327 2327).
                    Non dare diagnosi mediche. Sei un supporto, non un terapeuta.
                """.trimIndent(),
                userTemplate = "{{message}}",
                model = "gemini-2.0-flash",
                temperature = 0.8f,
                maxTokens = 4096
            ),
            "insight_analysis_v2" to PromptTemplate(
                id = "insight_analysis_v2",
                version = 2,
                systemInstruction = """
                    Analizza il seguente diary entry. Rispondi SOLO in JSON valido.
                    Identifica: sentiment (VERY_NEGATIVE, NEGATIVE, NEUTRAL, POSITIVE, VERY_POSITIVE),
                    magnitude (0.0-1.0), cognitive patterns (CBT: catastrophizing,
                    black_white_thinking, mind_reading, fortune_telling, emotional_reasoning,
                    overgeneralization, personalization, should_statements, labeling, magnification),
                    topics (max 5), summary (1-2 frasi italiano).
                """.trimIndent(),
                userTemplate = """
                    Mood: {{mood}}/10
                    Triggers: {{triggers}}
                    Testo: {{text}}
                """.trimIndent(),
                model = "gemini-2.0-flash",  // Pro per analisi profonda
                temperature = 0.3f,          // Bassa per output strutturato
                maxTokens = 2048
            ),
            "text_analysis_v1" to PromptTemplate(
                id = "text_analysis_v1",
                version = 1,
                systemInstruction = """
                    Analizza il testo per sentiment e argomenti.
                    Rispondi SOLO in JSON: {sentiment, magnitude, topics[]}.
                """.trimIndent(),
                userTemplate = "{{text}}",
                model = "gemini-2.0-flash",
                temperature = 0.2f,
                maxTokens = 1024
            )
        )
    }
}
```

### 5. Model Router

```kotlin
// server/src/main/kotlin/com/lifo/server/ai/ModelRouter.kt

class ModelRouter {
    enum class TaskType {
        CHAT,           // Conversazione → Flash (veloce, economico)
        CHAT_STREAM,    // Streaming → Flash
        INSIGHT,        // Analisi profonda → Pro (accurato)
        TEXT_ANALYSIS,  // Quick analysis → Flash
        SUMMARIZE,      // Riassunto → Flash
        CRISIS_DETECT   // Rilevamento crisi → Pro (accuratezza critica)
    }

    fun selectModel(
        taskType: TaskType,
        contextLength: Int = 0,
        userTier: UserTier = UserTier.FREE
    ): String {
        return when (taskType) {
            TaskType.CHAT -> when {
                userTier == UserTier.PREMIUM -> "gemini-2.0-pro"
                contextLength > 50000 -> "gemini-2.0-flash"  // long context
                else -> "gemini-2.0-flash"
            }
            TaskType.CHAT_STREAM -> "gemini-2.0-flash"
            TaskType.INSIGHT -> "gemini-2.0-flash"   // Pro per analisi CBT
            TaskType.TEXT_ANALYSIS -> "gemini-2.0-flash"
            TaskType.SUMMARIZE -> "gemini-2.0-flash"
            TaskType.CRISIS_DETECT -> "gemini-2.0-pro"  // Massima accuratezza
        }
    }
}
```

### 6. Response Cache

```kotlin
// server/src/main/kotlin/com/lifo/server/ai/ResponseCache.kt

class ResponseCache(
    private val cache: Cache<String, CachedResponse> = Cache.Builder()
        .maximumCacheSize(10_000)
        .expireAfterWrite(1.hours)
        .build()
) {
    // Semantic hashing: domande simili → stessa cache key
    fun computeKey(userId: String, request: AiChatRequest): String {
        // Normalizza: lowercase, rimuovi punteggiatura, trim
        val normalized = request.message.lowercase().trim()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
        return "$userId:${normalized.hashCode()}"
    }

    // Insight cache: chiave = diaryId (un diary produce sempre lo stesso insight)
    fun computeInsightKey(diaryId: String): String = "insight:$diaryId"

    fun get(key: String): AiChatResponse? = cache.get(key)?.response
    fun put(key: String, response: AiChatResponse, ttl: Duration) {
        cache.put(key, CachedResponse(response, Clock.System.now()))
    }

    data class CachedResponse(
        val response: AiChatResponse,
        val cachedAt: Instant
    )
}
```

### 7. Content Filter

```kotlin
// server/src/main/kotlin/com/lifo/server/ai/ContentFilter.kt

class ContentFilter {
    // Input: blocca prompt injection, contenuti illegali
    fun validateInput(text: String) {
        // 1. Max length
        require(text.length <= 10_000) { "Message too long" }

        // 2. Prompt injection detection
        val injectionPatterns = listOf(
            "ignore previous instructions",
            "you are now",
            "system prompt",
            "jailbreak"
        )
        val lower = text.lowercase()
        for (pattern in injectionPatterns) {
            if (pattern in lower) {
                throw ContentPolicyException("Richiesta non valida")
            }
        }
    }

    // Output: safety check su risposta AI
    fun validateOutput(result: GeminiResult): FilteredResult {
        // 1. Check Gemini safety ratings
        if (result.safetyRatings.any { it.blocked }) {
            return FilteredResult(
                text = "Mi dispiace, non posso rispondere a questa domanda. " +
                       "Posso aiutarti con qualcos'altro?",
                detectedEmotion = "neutral",
                suggestedActions = emptyList()
            )
        }

        // 2. PII detection (rimuovi dati personali accidentali dalla risposta)
        val cleaned = removePII(result.text)

        return FilteredResult(
            text = cleaned,
            detectedEmotion = detectEmotion(result.text),
            suggestedActions = extractSuggestions(result.text)
        )
    }
}
```

### 8. Token Tracker

```kotlin
// server/src/main/kotlin/com/lifo/server/ai/TokenTracker.kt

class TokenTracker(
    private val firestore: Firestore
) {
    // Limiti
    private val FREE_DAILY_LIMIT = 10_000     // ~20 messaggi chat
    private val FREE_MONTHLY_LIMIT = 200_000
    private val PREMIUM_DAILY_LIMIT = 100_000  // ~200 messaggi
    private val PREMIUM_MONTHLY_LIMIT = 2_000_000

    suspend fun checkQuota(userId: String) {
        val usage = getUsage(userId)
        val limit = if (usage.isPremium) PREMIUM_DAILY_LIMIT else FREE_DAILY_LIMIT
        if (usage.tokensUsedToday >= limit) {
            throw QuotaExceededException(
                "Hai raggiunto il limite giornaliero. " +
                if (!usage.isPremium) "Passa a Premium per piu' messaggi!" else "Riprova domani."
            )
        }
    }

    suspend fun record(userId: String, tokens: Int) {
        // Atomicamente incrementa contatori in Firestore
        // Collection: ai_usage/{userId}/daily/{date}
        // Collection: ai_usage/{userId}/monthly/{yearMonth}
    }
}
```

### 9. Live AI Session (Voice WebSocket)

```kotlin
// server/src/main/kotlin/com/lifo/server/ai/LiveAiSession.kt

class LiveAiSession(
    private val geminiLiveClient: GeminiLiveApiClient,
    private val tokenTracker: TokenTracker
) {
    suspend fun handle(userId: String, session: WebSocketServerSession) {
        tokenTracker.checkQuota(userId)

        // Apri connessione bidirezionale con Gemini Live API
        val geminiSession = geminiLiveClient.connect(
            model = "gemini-2.0-flash",
            config = LiveConfig(
                responseModality = "AUDIO",
                speechConfig = SpeechConfig(voiceName = "Aoede")
            )
        )

        // Forward bidirezionale:
        // Client audio → Server → Gemini Live API
        // Gemini Live API → Server → Client audio
        coroutineScope {
            // Client → Gemini
            launch {
                for (frame in session.incoming) {
                    geminiSession.sendAudio(frame.data)
                }
            }
            // Gemini → Client
            launch {
                geminiSession.incoming.collect { response ->
                    session.send(Frame.Binary(true, response.audioData))
                }
            }
        }
    }
}
```

---

## Client-Side Changes

### Prima (client chiama Gemini direttamente)

```kotlin
// features/chat — ChatRepositoryImpl.kt (ATTUALE)
val model = Firebase.ai.generativeModel("gemini-2.0-flash")
model.generateContentStream(prompt).collect { chunk -> ... }
```

### Dopo (client chiama server)

```kotlin
// features/chat — KtorChatRepository.kt (NUOVO)
// Chat normale
suspend fun sendMessage(sessionId: String, message: String): AiChatResponse {
    return apiClient.post("/api/v1/ai/chat", AiChatRequest(sessionId, message))
}

// Chat streaming (SSE)
fun sendMessageStream(sessionId: String, message: String): Flow<String> = flow {
    apiClient.client.preparePost("/api/v1/ai/chat/stream") {
        setBody(AiChatRequest(sessionId, message))
    }.execute { response ->
        val channel = response.bodyAsChannel()
        // Read SSE events
        while (!channel.isClosedForRead) {
            val chunk = channel.readSseEvent()
            emit(chunk.data)
        }
    }
}

// Voice live (WebSocket) — quasi invariato, cambia solo l'URL
// Ora:    wss://generativelanguage.googleapis.com/...
// Dopo:   wss://api.calmify.app/ws/ai/live
```

---

## Implementazione Step-by-Step

### Giorni 1-2: Core AI

- [ ] AiOrchestrator struttura base
- [ ] PromptRegistry con i 3 prompt attuali (chat, insight, text_analysis)
- [ ] ModelRouter (Flash vs Pro routing)
- [ ] GeminiClient wrapper (server-side, usa API key dal env var)
- [ ] AiRoutes: `/ai/chat`, `/ai/insight`, `/ai/analyze`
- [ ] Test: chat request → Gemini → response

### Giorno 3: Cache + Filtering

- [ ] ResponseCache con cache4k (semantic hashing)
- [ ] ContentFilter input (prompt injection, max length)
- [ ] ContentFilter output (safety ratings, PII removal)
- [ ] Test: stessa domanda 2x → seconda volta cached
- [ ] Test: prompt injection bloccato

### Giorno 4: Tracking + Quota

- [ ] TokenTracker (daily + monthly counters in Firestore)
- [ ] Quota check middleware
- [ ] Free vs Premium limits
- [ ] AiUsageResponse endpoint
- [ ] Test: free user supera quota → errore chiaro

### Giorno 5: Streaming + Live

- [ ] SSE streaming endpoint (`/ai/chat/stream`)
- [ ] WebSocket live voice (`/ws/ai/live`)
- [ ] Gemini Live API server-side client
- [ ] Test end-to-end: voice WebSocket through server
- [ ] Client migration: cambiare URL da Gemini diretto a server

---

## Dipendenze

```
BACKEND_AI_SERVER.md (questo file)
    ├── gira dentro → BACKEND_KTOR_SERVER.md (server infra)
    ├── modelli da → BACKEND_PROTOBUF.md (AiChatRequest, AiInsightResponse, etc.)
    ├── prompt attuali da → features/chat/ChatRepositoryImpl (temperature, model, system prompt)
    ├── voice pipeline da → features/chat/GeminiLiveWebSocketClient.kt
    └── insight logic da → data/mongo/repository/FirestoreInsightRepository.kt
```

---

## Impatto sui Costi

| | Ora (client-side) | Dopo (server-side) |
|---|---|---|
| Stessa domanda 100 utenti | 100 API calls | **1 API call** + 99 cache hits |
| Insight per diary | 1 call ogni volta apri | **1 call** poi cached 24h |
| API key exposure | Nell'APK | **Zero** (solo env var server) |
| Token tracking | Impossibile | **Per-utente, per-giorno** |
| Costo stimato 1000 utenti | ~$50/mese | ~$15/mese (con cache) |
| Costo stimato 10000 utenti | ~$500/mese | ~$80/mese (cache hit rate ~85%) |

---

## Metriche di Successo

| Metrica | Target |
|---|---|
| Cache hit rate (chat) | > 30% |
| Cache hit rate (insight) | > 90% (un diary = un insight) |
| Latenza P50 (cached) | < 50ms |
| Latenza P50 (non cached) | < 2s (Gemini Flash) |
| Token savings vs client-side | > 60% |
| Prompt injection blocked | 100% |
| Crisis detection accuracy | > 95% |
| Zero API key nell'APK | Si |
