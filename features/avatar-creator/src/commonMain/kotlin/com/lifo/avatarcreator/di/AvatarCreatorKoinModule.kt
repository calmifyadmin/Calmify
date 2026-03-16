package com.lifo.avatarcreator.di

import com.lifo.avatarcreator.presentation.AvatarCreatorViewModel
import com.lifo.avatarcreator.presentation.AvatarListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val avatarCreatorKoinModule = module {
    viewModel { AvatarCreatorViewModel(get(), get()) }
    viewModel { AvatarListViewModel(get(), get()) }
}
