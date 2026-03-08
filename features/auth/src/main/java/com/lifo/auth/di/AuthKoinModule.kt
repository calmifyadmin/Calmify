package com.lifo.auth.di

import com.lifo.auth.AuthenticationViewModel
import com.lifo.auth.domain.SignInWithGoogleUseCase
import com.lifo.auth.domain.SignOutUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authKoinModule = module {
    factory { SignInWithGoogleUseCase(get()) }
    factory { SignOutUseCase(get()) }
    viewModel { AuthenticationViewModel(get(), get(), get()) }
}
