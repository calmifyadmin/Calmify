package com.lifo.humanoid.di

import android.content.Context
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.data.vrm.VrmLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing dependencies for the Humanoid feature.
 */
@Module
@InstallIn(SingletonComponent::class)
object HumanoidModule {

    @Provides
    @Singleton
    fun provideVrmLoader(
        @ApplicationContext context: Context
    ): VrmLoader {
        return VrmLoader(context)
    }

    @Provides
    @Singleton
    fun provideVrmBlendShapeController(): VrmBlendShapeController {
        return VrmBlendShapeController()
    }
}
