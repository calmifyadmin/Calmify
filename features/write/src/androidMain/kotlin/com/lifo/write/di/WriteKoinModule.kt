package com.lifo.write.di

import com.lifo.write.EnergyViewModel
import com.lifo.write.GratitudeViewModel
import com.lifo.write.BlockViewModel
import com.lifo.write.MovementViewModel
import com.lifo.write.DashboardViewModel
import com.lifo.write.RecurringThoughtsViewModel
import com.lifo.write.ValuesViewModel
import com.lifo.write.IkigaiViewModel
import com.lifo.write.AweViewModel
import com.lifo.write.SilenceViewModel
import com.lifo.write.ConnectionViewModel
import com.lifo.write.InspirationViewModel
import com.lifo.write.PercorsoViewModel
import com.lifo.write.ReframeViewModel
import com.lifo.write.SleepLogViewModel
import com.lifo.write.GardenViewModel
import com.lifo.write.WriteViewModel
import com.lifo.write.domain.GetSleepNudgeUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val writeKoinModule = module {
    factory { GetSleepNudgeUseCase(get()) }

    viewModel { WriteViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { GratitudeViewModel(get(), get()) }
    viewModel { EnergyViewModel(get(), get()) }
    viewModel { SleepLogViewModel(get(), get()) }
    viewModel { ReframeViewModel(get(), get()) }
    viewModel { BlockViewModel(get(), get()) }
    viewModel { MovementViewModel(get(), get()) }
    viewModel { DashboardViewModel(get(), get(), get(), get()) }
    viewModel { RecurringThoughtsViewModel(get(), get()) }
    viewModel { ValuesViewModel(get(), get()) }
    viewModel { IkigaiViewModel(get(), get()) }
    viewModel { AweViewModel(get(), get()) }
    viewModel { SilenceViewModel() }
    viewModel { ConnectionViewModel(get(), get()) }
    viewModel { InspirationViewModel() }
    viewModel { PercorsoViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { GardenViewModel(get(), get(), get()) }
}
