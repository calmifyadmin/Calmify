package com.lifo.humanoid.di

import android.content.Context
import com.lifo.humanoid.data.ar.ArCoreSessionManager
import com.lifo.humanoid.domain.ar.ArSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing AR (ARCore) dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ArModule {

    @Provides
    @Singleton
    fun provideArSessionManager(
        @ApplicationContext context: Context
    ): ArSessionManager {
        return ArCoreSessionManager(context)
    }
}
