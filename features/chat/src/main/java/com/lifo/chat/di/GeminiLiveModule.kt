package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.realtime.GeminiLiveWebSocketClient
import com.lifo.chat.domain.GeminiLiveSessionManager
import com.lifo.chat.domain.audio.GeminiLiveAudioManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Gemini Live API dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object GeminiLiveModule {

    /**
     * Provides GeminiLiveWebSocketClient for WebSocket connection to Live API
     */
    @Provides
    @Singleton
    fun provideGeminiLiveWebSocketClient(): GeminiLiveWebSocketClient {
        return GeminiLiveWebSocketClient()
    }

    /**
     * Provides GeminiLiveAudioManager for audio recording/playback
     */
    @Provides
    @Singleton
    fun provideGeminiLiveAudioManager(
        @ApplicationContext context: Context
    ): GeminiLiveAudioManager {
        return GeminiLiveAudioManager(context)
    }

    /**
     * Provides GeminiLiveSessionManager for coordinating Live API session
     */
    @Provides
    @Singleton
    fun provideGeminiLiveSessionManager(
        webSocketClient: GeminiLiveWebSocketClient,
        audioManager: GeminiLiveAudioManager,
        apiConfigManager: ApiConfigManager
    ): GeminiLiveSessionManager {
        return GeminiLiveSessionManager(webSocketClient, audioManager, apiConfigManager)
    }
}