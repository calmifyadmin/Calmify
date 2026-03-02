package com.lifo.notifications.di

import com.lifo.notifications.NotificationsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val notificationsKoinModule = module {
    viewModel { NotificationsViewModel(get(), get()) }
}
