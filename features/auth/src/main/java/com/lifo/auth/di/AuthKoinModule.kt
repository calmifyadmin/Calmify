package com.lifo.auth.di

import com.lifo.auth.AuthenticationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authKoinModule = module {
    viewModel { AuthenticationViewModel(get()) }
}
