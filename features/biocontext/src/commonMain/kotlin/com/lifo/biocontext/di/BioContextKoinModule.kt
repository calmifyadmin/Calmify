package com.lifo.biocontext.di

import com.lifo.biocontext.BioContextViewModel
import com.lifo.biocontext.BioOnboardingViewModel
import com.lifo.biocontext.BioPatternFeedViewModel
import com.lifo.biocontext.BioSettingsViewModel
import com.lifo.biocontext.BioTimelineViewModel
import com.lifo.biocontext.domain.DetectHrvTrendPatternUseCase
import com.lifo.biocontext.domain.DetectSleepDriftPatternUseCase
import com.lifo.biocontext.domain.DetectSleepMoodPatternUseCase
import com.lifo.biocontext.domain.GetAllBioPatternsUseCase
import com.lifo.util.model.BioSignalDataType
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the Bio-Signal dashboard + onboarding (Phase 2.UI + 3) +
 * master Settings (Phase 9.1.3) + Timeline drill-down (Phase 9.2.1) +
 * Pattern Feed (Phase 9.2.2).
 */
val bioContextKoinModule = module {
    viewModel { BioContextViewModel(get(), get(), get()) }
    viewModel { BioOnboardingViewModel(get()) }
    factory { com.lifo.biocontext.domain.GetBaselineDriftUseCase(get(), get()) }
    viewModel { BioSettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { params ->
        BioTimelineViewModel(
            signal = params.get<BioSignalDataType>(),
            bioRepository = get(),
            meditationRepository = get(),
            mongoRepository = get(),
            authProvider = get(),
            preview = get(),
        )
    }
    // Phase 9.2.2 — Pattern Feed
    factory { DetectSleepMoodPatternUseCase(get(), get(), get()) }
    factory { DetectHrvTrendPatternUseCase(get(), get()) }
    factory { DetectSleepDriftPatternUseCase(get(), get()) }
    factory { GetAllBioPatternsUseCase(get(), get(), get()) }
    viewModel { BioPatternFeedViewModel(get()) }
}
