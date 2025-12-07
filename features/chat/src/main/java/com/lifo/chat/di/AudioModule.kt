package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.audio.GeminiLiveAudioSource
import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.chat.audio.GeminiVoiceAudioSource
import com.lifo.chat.audio.SynchronizedSpeechControllerImpl
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.util.speech.SpeechAudioSource
import com.lifo.util.speech.SynchronizedSpeechController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideApiConfigManager(
        @ApplicationContext context: Context
    ): ApiConfigManager {
        return ApiConfigManager(context)
    }

    @Provides
    @Singleton
    fun provideGeminiNativeVoiceSystem(
        @ApplicationContext context: Context
    ): GeminiNativeVoiceSystem {
        return GeminiNativeVoiceSystem(context)
    }

    @Provides
    @Singleton
    fun provideGeminiVoiceAudioSource(
        voiceSystem: GeminiNativeVoiceSystem
    ): GeminiVoiceAudioSource {
        return GeminiVoiceAudioSource(voiceSystem)
    }

    @Provides
    @Singleton
    fun provideGeminiLiveAudioSource(
        audioManager: GeminiLiveAudioManager
    ): GeminiLiveAudioSource {
        return GeminiLiveAudioSource(audioManager)
    }

    @Provides
    @Singleton
    fun provideSynchronizedSpeechController(): SynchronizedSpeechController {
        return SynchronizedSpeechControllerImpl()
    }
}