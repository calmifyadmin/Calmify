package com.lifo.profile.di

import com.lifo.home.domain.aggregator.WellbeingAggregator
import com.lifo.home.domain.usecase.GetActivityImpactUseCase
import com.lifo.home.domain.usecase.GetGrowthProgressUseCase
import com.lifo.home.domain.usecase.GetSleepMoodCorrelationUseCase
import com.lifo.home.domain.usecase.GetWellbeingTrendUseCase
import com.lifo.profile.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileKoinModule = module {
    factory { GetSleepMoodCorrelationUseCase() }
    factory { GetActivityImpactUseCase() }
    factory { GetGrowthProgressUseCase() }
    factory { GetWellbeingTrendUseCase() }

    factory {
        WellbeingAggregator(
            authProvider = get(),
            sleepRepository = get(),
            energyRepository = get(),
            movementRepository = get(),
            gratitudeRepository = get(),
            habitRepository = get(),
            wellbeingRepository = get(),
            insightRepository = get(),
            blockRepository = get(),
            ikigaiRepository = get(),
            valuesRepository = get(),
            recurringThoughtRepository = get(),
            reframeRepository = get(),
            sleepMoodUseCase = get(),
            activityImpactUseCase = get(),
            growthProgressUseCase = get(),
            wellbeingTrendUseCase = get(),
        )
    }

    viewModel { ProfileViewModel(get(), get(), get()) }
}
