package com.lifo.settings.di

import com.lifo.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsKoinModule = module {
    viewModel { SettingsViewModel(get(), get()) }
}
