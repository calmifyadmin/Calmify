package com.lifo.subscription.di

import com.lifo.subscription.SubscriptionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val subscriptionKoinModule = module {
    viewModel { SubscriptionViewModel(get(), get(), get(), get()) }
}
