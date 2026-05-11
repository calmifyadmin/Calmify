package com.lifo.biocontext.di

import com.lifo.biocontext.BioContextViewModel
import com.lifo.biocontext.BioOnboardingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the Bio-Signal transparency dashboard + onboarding (Phase 2.UI + 3). */
val bioContextKoinModule = module {
    viewModel { BioContextViewModel(get(), get(), get()) }
    viewModel { BioOnboardingViewModel(get()) }
}
