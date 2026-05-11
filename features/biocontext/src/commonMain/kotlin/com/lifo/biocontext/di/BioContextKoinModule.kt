package com.lifo.biocontext.di

import com.lifo.biocontext.BioContextViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the Bio-Signal transparency dashboard (Phase 2.UI). */
val bioContextKoinModule = module {
    viewModel { BioContextViewModel(get(), get(), get()) }
}
