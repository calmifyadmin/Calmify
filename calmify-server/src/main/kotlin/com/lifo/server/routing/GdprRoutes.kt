package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.GdprService
import com.lifo.shared.api.ApiError
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.gdprRoutes() {
    val gdprService by inject<GdprService>()

    authenticate("firebase") {
        route("/api/v1/gdpr") {

            // GET /api/v1/gdpr/export — download all personal data (GDPR Art. 20)
            get("/export") {
                val user = call.principal<UserPrincipal>()!!
                val data = gdprService.exportUserData(user.uid)
                call.respond(GdprExportResponse(
                    userId = user.uid,
                    exportedAt = System.currentTimeMillis(),
                    data = data,
                ))
            }

            // POST /api/v1/gdpr/delete — request account deletion (GDPR Art. 17)
            // Requires confirmation body to prevent accidental deletion
            post("/delete") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<DeleteAccountRequest>()

                if (request.confirmation != "DELETE_MY_ACCOUNT") {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = "CONFIRMATION_REQUIRED",
                            message = "Send confirmation: 'DELETE_MY_ACCOUNT' to proceed",
                        ),
                    )
                    return@post
                }

                val deletedCount = gdprService.deleteUserAccount(user.uid)
                call.respond(GdprDeleteResponse(
                    userId = user.uid,
                    deletedAt = System.currentTimeMillis(),
                    documentsDeleted = deletedCount,
                    message = "Account and all associated data have been permanently deleted",
                ))
            }
        }
    }
}

@Serializable
data class GdprExportResponse(
    val userId: String,
    val exportedAt: Long,
    val data: Map<String, List<Map<String, @Serializable(with = AnySerializer::class) Any?>>>,
)

@Serializable
data class DeleteAccountRequest(
    val confirmation: String,
)

@Serializable
data class GdprDeleteResponse(
    val userId: String,
    val deletedAt: Long,
    val documentsDeleted: Int,
    val message: String,
)

/**
 * Basic Any serializer for GDPR export.
 * Converts Firestore values to JSON-safe types.
 */
object AnySerializer : kotlinx.serialization.KSerializer<Any?> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("Any")
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Any?) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder ?: return
        val element = when (value) {
            null -> kotlinx.serialization.json.JsonNull
            is String -> kotlinx.serialization.json.JsonPrimitive(value)
            is Number -> kotlinx.serialization.json.JsonPrimitive(value)
            is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
            is Map<*, *> -> kotlinx.serialization.json.buildJsonObject {
                value.forEach { (k, v) ->
                    put(k.toString(), serializeToElement(v))
                }
            }
            is List<*> -> kotlinx.serialization.json.buildJsonArray {
                value.forEach { add(serializeToElement(it)) }
            }
            else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
        }
        jsonEncoder.encodeJsonElement(element)
    }
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Any? = null

    private fun serializeToElement(value: Any?): kotlinx.serialization.json.JsonElement = when (value) {
        null -> kotlinx.serialization.json.JsonNull
        is String -> kotlinx.serialization.json.JsonPrimitive(value)
        is Number -> kotlinx.serialization.json.JsonPrimitive(value)
        is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
        is Map<*, *> -> kotlinx.serialization.json.buildJsonObject {
            value.forEach { (k, v) -> put(k.toString(), serializeToElement(v)) }
        }
        is List<*> -> kotlinx.serialization.json.buildJsonArray {
            value.forEach { add(serializeToElement(it)) }
        }
        else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
    }
}
