package com.lifo.chat.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages API keys and configuration securely
 */
@Singleton
class ApiConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "api_config_prefs"
        // CORRECTED: This should be a meaningful string key, not the API key itself.
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_OPENAI_API = "openai_api_key"

        // For development/testing only - in production, retrieve from secure backend
        // CORRECTED: Default should be an empty string or null, or a specific placeholder.
        // It should NOT be a valid API key.
        private const val DEFAULT_API_KEY = "" // Changed to empty string
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Get Gemini API key
     * In production, this should retrieve from a secure backend
     */
    fun getGeminiApiKey(): String {
        return encryptedPrefs.getString(KEY_GEMINI_API, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }

    /**
     * Set Gemini API key (for configuration)
     */
    fun setGeminiApiKey(apiKey: String) {
        encryptedPrefs.edit()
            .putString(KEY_GEMINI_API, apiKey)
            .apply()
    }

    /**
     * Get OpenAI API key
     */
    fun getOpenAIApiKey(): String {
        return encryptedPrefs.getString(KEY_OPENAI_API, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }

    /**
     * Set OpenAI API key (for configuration)
     */
    fun setOpenAIApiKey(apiKey: String) {
        encryptedPrefs.edit()
            .putString(KEY_OPENAI_API, apiKey)
            .apply()
    }

    /**
     * Check if Gemini API key is configured
     */
    fun isGeminiApiKeyConfigured(): Boolean {
        // Now it correctly checks if the stored key is not empty
        return getGeminiApiKey().isNotEmpty() && getGeminiApiKey() != DEFAULT_API_KEY
    }

    /**
     * Check if OpenAI API key is configured
     */
    fun isOpenAIApiKeyConfigured(): Boolean {
        return getOpenAIApiKey().isNotEmpty() && getOpenAIApiKey() != DEFAULT_API_KEY
    }
}