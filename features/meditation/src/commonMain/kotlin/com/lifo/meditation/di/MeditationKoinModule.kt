package com.lifo.meditation.di

import com.lifo.meditation.MeditationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val meditationKoinModule = module {
    viewModel { MeditationViewModel(get(), get()) }
}
