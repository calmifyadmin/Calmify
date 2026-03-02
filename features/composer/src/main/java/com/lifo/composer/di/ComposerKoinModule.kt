package com.lifo.composer.di

import com.lifo.composer.ComposerViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val composerKoinModule = module {
    viewModelOf(::ComposerViewModel)
}
