package com.lifo.settings.di

import com.lifo.settings.SettingsViewModel
import com.lifo.settings.EnvironmentViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsKoinModule = module {
    // SettingsViewModel(profileSettingsRepository, authProvider, diaryRepository, chatRepository, mediaUploadRepository)
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
    // EnvironmentViewModel(repository, authProvider)
    viewModel { EnvironmentViewModel(get(), get()) }
}
