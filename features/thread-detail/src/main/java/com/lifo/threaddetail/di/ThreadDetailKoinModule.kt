package com.lifo.threaddetail.di

import com.lifo.threaddetail.ThreadDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val threadDetailKoinModule = module {
    viewModel { params -> ThreadDetailViewModel(params.get(), get(), get(), get()) }
}
