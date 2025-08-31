package com.lifo.chat.data.realtime

import android.util.Log
import com.lifo.chat.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages ephemeral keys for OpenAI Realtime API sessions
 * Handles token refresh and expiration automatically
 */
@Singleton
class EphemeralKeyManager @Inject constructor() {
    
    companion object {
        private const val TAG = "EphemeralKeyManager"
        private const val OPENAI_SESSIONS_URL = "https://api.openai.com/v1/realtime/sessions"
        private const val REFRESH_THRESHOLD_MS = 60_000L // Refresh 1 minute before expiry
        private const val REQUEST_TIMEOUT_SECONDS = 30L
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val mutex = Mutex()
    private var currentSession: EphemeralSession? = null
    
    /**
     * Gets a valid ephemeral key, refreshing if necessary
     */
    suspend fun getValidEphemeralKey(): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = currentSession
            
            // Check if we need to refresh
            if (session == null || shouldRefreshKey(session)) {
                Log.d(TAG, "🔑 Getting new ephemeral key...")
                return@withLock createNewSession()
            }
            
            Log.d(TAG, "🔑 Using existing ephemeral key")
            Result.success(session.ephemeralKey)
        }
    }
    
    /**
     * Gets the current session ID if available
     */
    fun getCurrentSessionId(): String? = currentSession?.sessionId
    
    /**
     * Clears the current session (forces refresh on next request)
     */
    suspend fun clearSession() {
        mutex.withLock {
            Log.d(TAG, "🗑️ Clearing current session")
            currentSession = null
        }
    }
    
    private fun shouldRefreshKey(session: EphemeralSession): Boolean {
        val timeUntilExpiry = session.expiresAt - System.currentTimeMillis()
        return timeUntilExpiry < REFRESH_THRESHOLD_MS
    }
    
    private suspend fun createNewSession(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.OPENAI_API_KEY
            if (apiKey.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("OpenAI API key not configured in BuildConfig")
                )
            }
            
            val requestBody = JSONObject().apply {
                put("model", "gpt-4o-realtime-preview-2024-10-01")
                put("voice", "alloy")
            }
            
            val request = Request.Builder()
                .url(OPENAI_SESSIONS_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "📡 Sending ephemeral key request to OpenAI...")
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "❌ Failed to get ephemeral key. Status: ${response.code}, Body: $errorBody")
                return@withContext Result.failure(
                    RuntimeException("HTTP ${response.code}: $errorBody")
                )
            }
            
            val responseBody = response.body?.string() 
                ?: return@withContext Result.failure(RuntimeException("Empty response body"))
            
            Log.d(TAG, "✅ Received ephemeral key response")
            
            val jsonResponse = JSONObject(responseBody)
            val sessionId = jsonResponse.getString("id")
            val ephemeralKey = jsonResponse.getJSONObject("client_secret").getString("value")
            val expiresAt = jsonResponse.getJSONObject("client_secret").getLong("expires_at") * 1000 // Convert to milliseconds
            
            currentSession = EphemeralSession(
                sessionId = sessionId,
                ephemeralKey = ephemeralKey,
                expiresAt = expiresAt
            )
            
            Log.d(TAG, "🔑 New session created - ID: $sessionId, Expires: ${java.util.Date(expiresAt)}")
            
            Result.success(ephemeralKey)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create ephemeral session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Data class representing an ephemeral session
     */
    private data class EphemeralSession(
        val sessionId: String,
        val ephemeralKey: String,
        val expiresAt: Long // Timestamp in milliseconds
    )
}