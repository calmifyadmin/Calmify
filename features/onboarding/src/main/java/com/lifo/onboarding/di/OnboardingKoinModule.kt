package com.lifo.onboarding.di

import com.lifo.onboarding.OnboardingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val onboardingKoinModule = module {
    viewModel { OnboardingViewModel(get(), get(), get()) }
}
