package com.lifo.profile.di

import com.lifo.profile.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileKoinModule = module {
    // Use cases and WellbeingAggregator are registered in homeKoinModule
    viewModel { ProfileViewModel(get(), get(), get()) }
}
