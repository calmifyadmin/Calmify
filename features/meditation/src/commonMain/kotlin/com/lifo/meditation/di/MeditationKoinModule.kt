package com.lifo.meditation.di

import com.lifo.meditation.MeditationViewModel
import com.lifo.meditation.domain.GetSessionHrSummaryUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val meditationKoinModule = module {
    factory { GetSessionHrSummaryUseCase(get()) }

    viewModel { MeditationViewModel(get(), get()) }
}
