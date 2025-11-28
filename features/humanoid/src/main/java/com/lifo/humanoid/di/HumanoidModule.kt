package com.lifo.humanoid.di

import android.content.Context
import com.lifo.humanoid.animation.BlinkController
import com.lifo.humanoid.animation.VrmaAnimationLoader
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import com.lifo.humanoid.data.vrm.VrmLoader
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.lipsync.PhonemeConverter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing dependencies for the Humanoid feature.
 *
 * Provides all animation, VRM loading, and lip-sync components.
 */
@Module
@InstallIn(SingletonComponent::class)
object HumanoidModule {

    // ==================== VRM Data Layer ====================

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

    @Provides
    @Singleton
    fun provideVrmHumanoidBoneMapper(): VrmHumanoidBoneMapper {
        return VrmHumanoidBoneMapper()
    }

    // ==================== Animation Layer ====================

    @Provides
    @Singleton
    fun provideBlinkController(): BlinkController {
        return BlinkController()
    }

    @Provides
    @Singleton
    fun provideVrmaAnimationLoader(
        @ApplicationContext context: Context
    ): VrmaAnimationLoader {
        return VrmaAnimationLoader(context)
    }

    // ==================== Lip-Sync Layer ====================

    @Provides
    @Singleton
    fun providePhonemeConverter(
        @ApplicationContext context: Context
    ): PhonemeConverter {
        return PhonemeConverter(context)
    }

    @Provides
    @Singleton
    fun provideLipSyncController(
        phonemeConverter: PhonemeConverter,
        blendShapeController: VrmBlendShapeController
    ): LipSyncController {
        return LipSyncController(phonemeConverter, blendShapeController)
    }
}
