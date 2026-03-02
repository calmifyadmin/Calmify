package com.lifo.write.di

import com.lifo.write.WriteViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val writeKoinModule = module {
    viewModel { WriteViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
