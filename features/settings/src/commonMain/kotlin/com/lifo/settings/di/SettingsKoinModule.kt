package com.lifo.settings.di

import com.lifo.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsKoinModule = module {
    // SettingsViewModel(profileSettingsRepository, authProvider, diaryRepository, chatRepository)
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
}
