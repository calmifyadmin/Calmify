package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.audio.OnDeviceNaturalVoiceSystem
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
    fun provideOnDeviceNaturalVoiceSystem(
        @ApplicationContext context: Context
    ): OnDeviceNaturalVoiceSystem {
        return OnDeviceNaturalVoiceSystem(context)
    }
}