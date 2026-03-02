package com.lifo.profile.di

import com.lifo.profile.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileKoinModule = module {
    viewModel { ProfileViewModel(get(), get()) }
}
