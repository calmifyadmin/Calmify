package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.ContentModerationService
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.contentModerationRoutes() {
    val moderationService by inject<ContentModerationService>()

    authenticate("firebase") {
        route("/api/v1/moderation") {

            // POST /api/v1/moderation/toxicity
            post("/toxicity") {
                val body = call.receive<ModerationTextRequest>()
                val result = moderationService.checkToxicity(body.text)
                call.respond(result)
            }

            // POST /api/v1/moderation/sentiment
            post("/sentiment") {
                val body = call.receive<ModerationTextRequest>()
                val result = moderationService.analyzeSentiment(body.text)
                call.respond(result)
            }

            // POST /api/v1/moderation/mood
            post("/mood") {
                val body = call.receive<ModerationTextRequest>()
                val mood = moderationService.classifyMood(body.text)
                call.respond(MoodResponse(mood = mood))
            }

            // POST /api/v1/moderation/duplicate
            post("/duplicate") {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<ModerationTextRequest>()
                val result = moderationService.checkDuplicate(body.text, user.uid)
                call.respond(result)
            }

            // POST /api/v1/moderation/embedding
            post("/embedding") {
                val body = call.receive<ModerationTextRequest>()
                val embedding = moderationService.generateEmbedding(body.text)
                call.respond(EmbeddingResponse(embedding = embedding.toList()))
            }
        }
    }
}

@Serializable
data class ModerationTextRequest(val text: String = "")

@Serializable
data class MoodResponse(val mood: String = "Neutral")

@Serializable
data class EmbeddingResponse(val embedding: List<Float> = emptyList())
