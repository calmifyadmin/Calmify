package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.audio.engine.AAAudioEngine
import com.lifo.chat.audio.vad.SileroVadEngine
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
import com.lifo.chat.domain.audio.AdaptiveBargeinDetector
import com.lifo.chat.domain.audio.AudioQualityAnalyzer
import com.lifo.chat.domain.audio.ConversationContextManager
import com.google.firebase.auth.FirebaseAuth
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
    fun provideGeminiLiveWebSocketClient(firebaseAuth: FirebaseAuth): GeminiLiveWebSocketClient {
        return GeminiLiveWebSocketClient(firebaseAuth)
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
     * Provides SileroVadEngine for enterprise-grade Voice Activity Detection
     *
     * Features:
     * - Dual-engine architecture: WebRTC pre-filter + Silero VAD v6 (DNN)
     * - 60-70% CPU savings through WebRTC pre-filtering
     * - Combined accuracy > 98%
     * - Processing latency < 1ms per frame
     */
    @Provides
    @Singleton
    fun provideSileroVadEngine(
        @ApplicationContext context: Context
    ): SileroVadEngine {
        return SileroVadEngine(context)
    }

    /**
     * Provides AAAudioEngine for professional-grade audio playback
     *
     * Features:
     * - Adaptive jitter buffer (50-300ms)
     * - Lock-free ring buffer (3 seconds)
     * - Packet loss concealment
     * - High-priority audio thread
     * - USAGE_ASSISTANT for optimal AI voice routing
     */
    @Provides
    @Singleton
    fun provideAAAudioEngine(
        @ApplicationContext context: Context
    ): AAAudioEngine {
        return AAAudioEngine(context)
    }

    /**
     * Provides GeminiLiveAudioManager for audio recording/playback with intelligent systems
     */
    @Provides
    @Singleton
    fun provideGeminiLiveAudioManager(
        @ApplicationContext context: Context,
        adaptiveBargeinDetector: AdaptiveBargeinDetector,
        sileroVadEngine: SileroVadEngine
    ): GeminiLiveAudioManager {
        return GeminiLiveAudioManager(context, adaptiveBargeinDetector, sileroVadEngine)
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