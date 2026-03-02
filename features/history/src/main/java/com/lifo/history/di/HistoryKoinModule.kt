package com.lifo.history.di

import com.lifo.history.HistoryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val historyKoinModule = module {
    viewModel { HistoryViewModel(get(), get(), get()) }
}
