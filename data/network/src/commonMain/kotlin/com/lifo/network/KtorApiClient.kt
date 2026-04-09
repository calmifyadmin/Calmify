package com.lifo.network

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Central HTTP client for all server communication.
 *
 * Handles:
 * - Firebase ID token injection (Bearer auth)
 * - Auto token refresh on 401
 * - JSON content negotiation
 * - Error mapping to RequestState
 */
class KtorApiClient(
    @PublishedApi internal val authProvider: AuthProvider,
    private val baseUrl: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val client = HttpClient {
        install(ContentNegotiation) {
            // Protobuf preferred — 3-5x smaller payloads than JSON
            protobuf(ProtoBuf { encodeDefaults = true })
            // JSON fallback — for debugging, browser clients, or when server sends JSON
            json(this@KtorApiClient.json)
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }

        defaultRequest {
            url(baseUrl)
            // Request protobuf from server; server falls back to JSON if needed
            accept(ContentType.Application.ProtoBuf)
            contentType(ContentType.Application.ProtoBuf)
        }
    }

    suspend inline fun <reified T> get(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): RequestState<T> = authenticatedRequest<T> { token ->
        client.get(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            block()
        }
    }

    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): RequestState<T> = authenticatedRequest<T> { token ->
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (body != null) setBody(body)
            block()
        }
    }

    suspend inline fun <reified T> put(
        path: String,
        body: Any? = null,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): RequestState<T> = authenticatedRequest<T> { token ->
        client.put(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (body != null) setBody(body)
            block()
        }
    }

    suspend inline fun <reified T> delete(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): RequestState<T> = authenticatedRequest<T> { token ->
        client.delete(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            block()
        }
    }

    suspend fun postNoBody(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): RequestState<Boolean> {
        val token = authProvider.getIdToken() ?: return RequestState.Error(Exception("Not authenticated"))
        return try {
            val response = client.post(path) {
                header(HttpHeaders.Authorization, "Bearer $token")
                block()
            }
            if (response.status.isSuccess()) RequestState.Success(true)
            else RequestState.Error(Exception("HTTP ${response.status.value}"))
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    suspend fun deleteNoBody(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): RequestState<Boolean> {
        val token = authProvider.getIdToken() ?: return RequestState.Error(Exception("Not authenticated"))
        return try {
            val response = client.delete(path) {
                header(HttpHeaders.Authorization, "Bearer $token")
                block()
            }
            if (response.status.isSuccess()) RequestState.Success(true)
            else RequestState.Error(Exception("HTTP ${response.status.value}"))
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    @PublishedApi
    internal suspend inline fun <reified T> authenticatedRequest(
        request: (token: String) -> HttpResponse,
    ): RequestState<T> {
        val token = authProvider.getIdToken()
            ?: return RequestState.Error(Exception("Not authenticated"))

        return try {
            val response = request(token)
            when {
                response.status == HttpStatusCode.Unauthorized -> {
                    val freshToken = authProvider.getIdToken(forceRefresh = true)
                        ?: return RequestState.Error(Exception("Authentication expired"))
                    val retryResponse = request(freshToken)
                    if (retryResponse.status.isSuccess()) RequestState.Success(retryResponse.body<T>())
                    else RequestState.Error(Exception("HTTP ${retryResponse.status.value}"))
                }
                response.status.isSuccess() -> RequestState.Success(response.body<T>())
                else -> RequestState.Error(Exception("HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    fun close() {
        client.close()
    }
}
