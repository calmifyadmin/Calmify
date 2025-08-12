package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.chat.config.ApiConfigManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
}