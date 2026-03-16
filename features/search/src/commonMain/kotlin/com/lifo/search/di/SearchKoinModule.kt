package com.lifo.search.di

import com.lifo.search.SearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchKoinModule = module {
    viewModelOf(::SearchViewModel)
}
