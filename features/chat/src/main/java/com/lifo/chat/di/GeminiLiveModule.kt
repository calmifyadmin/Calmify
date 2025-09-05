package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
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
    fun provideGeminiLiveAudioManager(): GeminiLiveAudioManager {
        return GeminiLiveAudioManager()
    }

    /**
     * Provides GeminiLiveCameraManager for camera preview and image capture
     */
    @Provides
    @Singleton
    fun provideGeminiLiveCameraManager(@ApplicationContext context: Context): GeminiLiveCameraManager {
        return GeminiLiveCameraManager(context)
    }

}