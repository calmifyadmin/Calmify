package com.lifo.biocontext.di

import com.lifo.biocontext.BioContextViewModel
import com.lifo.biocontext.BioOnboardingViewModel
import com.lifo.biocontext.BioSettingsViewModel
import com.lifo.biocontext.BioTimelineViewModel
import com.lifo.util.model.BioSignalDataType
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the Bio-Signal dashboard + onboarding (Phase 2.UI + 3) + master Settings (Phase 9.1.3) + Timeline drill-down (Phase 9.2.1). */
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
}
