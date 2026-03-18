package com.lifo.mongo.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Shared Ktor-based client for calling Firebase Cloud Functions via HTTPS.
 *
 * Firebase callable functions protocol:
 *   POST https://<region>-<project>.cloudfunctions.net/<functionName>
 *   Content-Type: application/json
 *   Body: { "data": { ... } }
 *   Response: { "result": { ... } }
 *
 * @param httpClient Platform-provided Ktor HttpClient
 * @param baseUrl Cloud Functions base URL (e.g. "https://europe-west1-calmify-xxxxx.cloudfunctions.net")
 */
class KtorCloudFunctionsClient(
    private val httpClient: HttpClient,
    val baseUrl: String
) {
    @Serializable
    internal data class CallableRequest(val data: Map<String, String>)

    suspend fun call(functionName: String, data: Map<String, String>): JsonObject {
        val response: JsonObject = httpClient.post("$baseUrl/$functionName") {
            contentType(ContentType.Application.Json)
            setBody(CallableRequest(data))
        }.body()
        return response["result"]?.jsonObject ?: response
    }

    private val kotlinx.serialization.json.JsonElement.jsonObject: JsonObject?
        get() = this as? JsonObject
}
