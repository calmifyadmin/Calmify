package com.lifo.biocontext.di

import com.lifo.biocontext.BioContextViewModel
import com.lifo.biocontext.BioOnboardingViewModel
import com.lifo.biocontext.BioSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the Bio-Signal dashboard + onboarding (Phase 2.UI + 3) + master Settings (Phase 9.1.3). */
val bioContextKoinModule = module {
    viewModel { BioContextViewModel(get(), get(), get()) }
    viewModel { BioOnboardingViewModel(get()) }
    viewModel { BioSettingsViewModel(get(), get(), get(), get()) }
}
