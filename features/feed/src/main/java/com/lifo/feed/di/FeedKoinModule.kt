package com.lifo.feed.di

import com.lifo.feed.FeedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val feedKoinModule = module {
    viewModel { FeedViewModel(get(), get(), get()) }
}
