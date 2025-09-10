package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
import com.lifo.chat.domain.audio.AdaptiveBargeinDetector
import com.lifo.chat.domain.audio.AudioQualityAnalyzer
import com.lifo.chat.domain.audio.ConversationContextManager
import com.lifo.chat.domain.audio.MultiDeviceAudioManager
import com.lifo.chat.domain.audio.AdvancedDuckingEngine
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
     * Provides AdaptiveBargeinDetector for intelligent voice activity detection
     */
    @Provides
    @Singleton
    fun provideAdaptiveBargeinDetector(): AdaptiveBargeinDetector {
        return AdaptiveBargeinDetector()
    }

    /**
     * Provides AudioQualityAnalyzer for real-time quality monitoring
     */
    @Provides
    @Singleton
    fun provideAudioQualityAnalyzer(): AudioQualityAnalyzer {
        return AudioQualityAnalyzer()
    }

    /**
     * Provides ConversationContextManager for intelligent context awareness
     */
    @Provides
    @Singleton
    fun provideConversationContextManager(): ConversationContextManager {
        return ConversationContextManager()
    }

    /**
     * Provides MultiDeviceAudioManager for seamless device switching
     */
    @Provides
    @Singleton
    fun provideMultiDeviceAudioManager(@ApplicationContext context: Context): MultiDeviceAudioManager {
        return MultiDeviceAudioManager(context)
    }

    /**
     * Provides AdvancedDuckingEngine for spatial audio and intelligent ducking
     */
    @Provides
    @Singleton
    fun provideAdvancedDuckingEngine(): AdvancedDuckingEngine {
        return AdvancedDuckingEngine()
    }

    /**
     * Provides GeminiLiveAudioManager for audio recording/playback with intelligent systems
     */
    @Provides
    @Singleton
    fun provideGeminiLiveAudioManager(
        @ApplicationContext context: Context,
        adaptiveBargeinDetector: AdaptiveBargeinDetector
    ): GeminiLiveAudioManager {
        return GeminiLiveAudioManager(context, adaptiveBargeinDetector)
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