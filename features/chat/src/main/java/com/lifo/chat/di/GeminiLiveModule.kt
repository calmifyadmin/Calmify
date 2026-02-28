package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.audio.engine.AAAudioEngine
import com.lifo.chat.audio.oboe.NativeAudioEngine
import com.lifo.chat.audio.vad.SileroVadEngine
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
import com.lifo.chat.domain.audio.AdaptiveBargeinDetector
import com.lifo.chat.domain.audio.AecReliabilityDetector
import com.lifo.chat.domain.audio.AudioQualityAnalyzer
import com.lifo.chat.domain.audio.ConversationContextManager
import com.lifo.chat.domain.audio.FullDuplexAudioSession
import com.lifo.chat.domain.audio.HeadphoneDetector
import com.lifo.chat.domain.audio.ReferenceSignalBargeInDetector
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
     * Provides ReferenceSignalBargeInDetector for echo-aware barge-in detection
     *
     * Features:
     * - Echo cancellation via reference signal correlation
     * - Distinguishes AI audio echo from actual user speech
     * - Sub-32ms latency for immediate barge-in response
     * - Adaptive thresholds based on noise floor
     */
    @Provides
    @Singleton
    fun provideReferenceSignalBargeInDetector(): ReferenceSignalBargeInDetector {
        return ReferenceSignalBargeInDetector()
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
     * Provides FullDuplexAudioSession for hardware AEC synchronization.
     *
     * Manages the shared audioSessionId between AudioRecord (VOICE_COMMUNICATION)
     * and AudioTrack (USAGE_ASSISTANT), and stores AEC/NS/AGC effect instances
     * to prevent garbage collection.
     */
    @Provides
    @Singleton
    fun provideFullDuplexAudioSession(
        @ApplicationContext context: Context
    ): FullDuplexAudioSession {
        return FullDuplexAudioSession(context)
    }

    /**
     * Provides HeadphoneDetector for real-time audio routing detection.
     *
     * When headphones are connected, AEC is bypassed (no acoustic echo path).
     * Enables diagnostic confirmation: if echo disappears with headphones,
     * it's 100% a hardware AEC failure.
     */
    @Provides
    @Singleton
    fun provideHeadphoneDetector(
        @ApplicationContext context: Context
    ): HeadphoneDetector {
        return HeadphoneDetector(context)
    }

    /**
     * Provides AecReliabilityDetector for runtime hardware AEC quality monitoring.
     *
     * Measures cross-correlation between reference signal and mic input to detect
     * if hardware AEC is actually cancelling echo. If HARDWARE_FAILING, triggers
     * software AEC fallback (Phase 3).
     */
    @Provides
    @Singleton
    fun provideAecReliabilityDetector(): AecReliabilityDetector {
        return AecReliabilityDetector()
    }

    /**
     * Provides NativeAudioEngine (Oboe C++ via JNI) for low-latency audio.
     *
     * Falls back gracefully if native library is not available — the caller
     * checks [NativeAudioEngine.isAvailable] and uses Java AudioTrack/AudioRecord instead.
     */
    @Provides
    @Singleton
    fun provideNativeAudioEngine(): NativeAudioEngine {
        return NativeAudioEngine()
    }

    /**
     * Provides GeminiLiveAudioManager for audio recording/playback with full-duplex.
     *
     * Includes:
     * - FullDuplexAudioSession: Hardware AEC session management
     * - AdaptiveBargeinDetector: Voice profile learning
     * - SileroVadEngine: Neural network VAD (local barge-in during AI speech)
     * - ReferenceSignalBargeInDetector: Software echo-aware detection (diagnostics)
     */
    @Provides
    @Singleton
    fun provideGeminiLiveAudioManager(
        @ApplicationContext context: Context,
        adaptiveBargeinDetector: AdaptiveBargeinDetector,
        sileroVadEngine: SileroVadEngine,
        referenceSignalDetector: ReferenceSignalBargeInDetector,
        fullDuplexSession: FullDuplexAudioSession
    ): GeminiLiveAudioManager {
        return GeminiLiveAudioManager(
            context, adaptiveBargeinDetector, sileroVadEngine, referenceSignalDetector,
            fullDuplexSession
        )
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