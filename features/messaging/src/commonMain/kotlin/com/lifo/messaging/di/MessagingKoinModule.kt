package com.lifo.messaging.di

import com.lifo.messaging.MessagingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val messagingKoinModule = module {
    viewModelOf(::MessagingViewModel)
}
