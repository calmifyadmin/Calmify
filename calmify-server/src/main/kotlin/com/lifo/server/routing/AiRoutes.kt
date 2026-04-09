package com.lifo.server.routing

import com.lifo.server.ai.AiOrchestrator
import com.lifo.server.ai.ContentPolicyException
import com.lifo.server.ai.QuotaExceededException
import com.lifo.server.ai.TokenTracker
import com.lifo.server.security.UserPrincipal
import com.lifo.shared.api.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.koin.ktor.ext.inject

fun Route.aiRoutes() {
    val aiOrchestrator by inject<AiOrchestrator>()
    val tokenTracker by inject<TokenTracker>()

    authenticate("firebase") {
        route("/api/v1/ai") {

            // POST /api/v1/ai/chat — single message, full response
            post("/chat") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<AiChatRequest>()
                try {
                    val response = aiOrchestrator.chat(user.uid, request)
                    call.respond(response)
                } catch (e: ContentPolicyException) {
                    call.respond(HttpStatusCode.BadRequest,
                        AiChatResponse(error = ApiError(code = "CONTENT_POLICY", message = e.message ?: "")))
                } catch (e: QuotaExceededException) {
                    call.respond(HttpStatusCode.TooManyRequests,
                        AiChatResponse(error = ApiError(code = "QUOTA_EXCEEDED", message = e.message ?: "")))
                }
            }

            // POST /api/v1/ai/chat/stream — SSE streaming response
            post("/chat/stream") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<AiChatRequest>()
                try {
                    call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header("Connection", "keep-alive")
                    call.respondBytesWriter {
                        aiOrchestrator.chatStream(user.uid, request).collect { chunk ->
                            val escaped = chunk.replace("\n", "\\n")
                            writeStringUtf8("data: $escaped\n\n")
                            flush()
                        }
                        writeStringUtf8("data: [DONE]\n\n")
                        flush()
                    }
                } catch (e: ContentPolicyException) {
                    call.respond(HttpStatusCode.BadRequest,
                        AiChatResponse(error = ApiError(code = "CONTENT_POLICY", message = e.message ?: "")))
                } catch (e: QuotaExceededException) {
                    call.respond(HttpStatusCode.TooManyRequests,
                        AiChatResponse(error = ApiError(code = "QUOTA_EXCEEDED", message = e.message ?: "")))
                }
            }

            // POST /api/v1/ai/insight — diary insight analysis (CBT-informed)
            post("/insight") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<AiInsightRequest>()
                try {
                    val insight = aiOrchestrator.generateInsight(user.uid, request)
                    call.respond(insight)
                } catch (e: QuotaExceededException) {
                    call.respond(HttpStatusCode.TooManyRequests,
                        AiInsightResponse(error = ApiError(code = "QUOTA_EXCEEDED", message = e.message ?: "")))
                }
            }

            // POST /api/v1/ai/analyze — generic text analysis (sentiment + topics)
            post("/analyze") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<AiAnalyzeRequest>()
                try {
                    val analysis = aiOrchestrator.analyzeText(user.uid, request)
                    call.respond(analysis)
                } catch (e: ContentPolicyException) {
                    call.respond(HttpStatusCode.BadRequest,
                        AiAnalyzeResponse(error = ApiError(code = "CONTENT_POLICY", message = e.message ?: "")))
                } catch (e: QuotaExceededException) {
                    call.respond(HttpStatusCode.TooManyRequests,
                        AiAnalyzeResponse(error = ApiError(code = "QUOTA_EXCEEDED", message = e.message ?: "")))
                }
            }

            // GET /api/v1/ai/usage — token usage stats
            get("/usage") {
                val user = call.principal<UserPrincipal>()!!
                val usage = tokenTracker.getUsage(user.uid)
                call.respond(usage)
            }
        }
    }
}
