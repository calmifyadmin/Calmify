package com.lifo.humanoid.di

import com.lifo.humanoid.animation.BlinkController
import com.lifo.humanoid.animation.VrmaAnimationLoader
import com.lifo.humanoid.animation.VrmaAnimationPlayerFactory
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import com.lifo.humanoid.data.vrm.VrmLoader
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.lipsync.PhonemeConverter
import com.lifo.humanoid.presentation.HumanoidViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val humanoidKoinModule = module {
    // VRM Data Layer
    single { VrmLoader(get()) }
    single { VrmBlendShapeController() }
    single { VrmHumanoidBoneMapper() }

    // Animation Layer
    single { BlinkController() }
    single { VrmaAnimationLoader(get()) }
    single { VrmaAnimationPlayerFactory(get()) }

    // Lip-Sync Layer
    single { PhonemeConverter(get()) }
    single { LipSyncController(get(), get()) }

    // ViewModel
    // HumanoidViewModel(vrmLoader, blendShapeController, boneMapper, blinkController,
    //   lipSyncController, vrmaAnimationLoader, vrmaAnimationPlayerFactory)
    viewModel { HumanoidViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
