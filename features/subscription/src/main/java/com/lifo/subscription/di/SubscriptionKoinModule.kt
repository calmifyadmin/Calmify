package com.lifo.subscription.di

import com.lifo.subscription.SubscriptionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val subscriptionKoinModule = module {
    viewModelOf(::SubscriptionViewModel)
}
