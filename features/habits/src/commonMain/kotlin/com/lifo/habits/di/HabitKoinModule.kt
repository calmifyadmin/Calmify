package com.lifo.habits.di

import com.lifo.habits.HabitViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val habitKoinModule = module {
    viewModel { HabitViewModel(get(), get()) }
}
