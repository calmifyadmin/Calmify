package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.data.realtime.EphemeralKeyManager
import com.lifo.chat.data.realtime.RealtimeWebRTCClient
import com.lifo.chat.domain.audio.AudioLevelExtractor
import com.lifo.chat.domain.audio.PushToTalkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for LiveChat WebRTC dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object LiveChatModule {

    /**
     * Provides EphemeralKeyManager for OpenAI session token management
     */
    @Provides
    @Singleton
    fun provideEphemeralKeyManager(): EphemeralKeyManager {
        return EphemeralKeyManager()
    }

    /**
     * Provides AudioLevelExtractor for real-time RMS extraction
     */
    @Provides
    @Singleton
    fun provideAudioLevelExtractor(): AudioLevelExtractor {
        return AudioLevelExtractor()
    }

    /**
     * Provides RealtimeWebRTCClient for WebRTC peer connection management
     */
    @Provides
    @Singleton
    fun provideRealtimeWebRTCClient(
        @ApplicationContext context: Context,
        ephemeralKeyManager: EphemeralKeyManager,
        audioLevelExtractor: AudioLevelExtractor
    ): RealtimeWebRTCClient {
        return RealtimeWebRTCClient(context, ephemeralKeyManager, audioLevelExtractor)
    }

    /**
     * Provides PushToTalkManager for PTT state management
     */
    @Provides
    @Singleton
    fun providePushToTalkManager(
        webRTCClient: RealtimeWebRTCClient,
        audioLevelExtractor: AudioLevelExtractor
    ): PushToTalkManager {
        return PushToTalkManager(webRTCClient, audioLevelExtractor)
    }
}