package com.lifo.insight.di

import com.lifo.insight.InsightViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val insightKoinModule = module {
    viewModel<InsightViewModel> { InsightViewModel(get()) }
}
