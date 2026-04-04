package com.lifo.home.di

import com.lifo.home.HomeViewModel
import com.lifo.home.SnapshotViewModel
import com.lifo.home.domain.aggregator.WellbeingAggregator
import com.lifo.home.domain.usecase.AggregateCognitivePatternsUseCase
import com.lifo.home.domain.usecase.CalculateMoodDistributionUseCase
import com.lifo.home.domain.usecase.CalculateStreaksUseCase
import com.lifo.home.domain.usecase.CalculateTodayPulseUseCase
import com.lifo.home.domain.usecase.CalculateTopicsFrequencyUseCase
import com.lifo.home.domain.usecase.GetAchievementsUseCase
import com.lifo.home.domain.usecase.GetActivityImpactUseCase
import com.lifo.home.domain.usecase.GetGrowthProgressUseCase
import com.lifo.home.domain.usecase.GetSleepMoodCorrelationUseCase
import com.lifo.home.domain.usecase.GetWellbeingTrendUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeKoinModule = module {
    // Use cases
    factory { CalculateMoodDistributionUseCase() }
    factory { AggregateCognitivePatternsUseCase() }
    factory { CalculateTopicsFrequencyUseCase() }
    factory { CalculateStreaksUseCase() }
    factory { CalculateTodayPulseUseCase() }
    factory { GetAchievementsUseCase(get()) }
    factory { GetSleepMoodCorrelationUseCase() }
    factory { GetActivityImpactUseCase() }
    factory { GetGrowthProgressUseCase() }
    factory { GetWellbeingTrendUseCase() }

    // WellbeingAggregator — single (stateless, safe to share across VMs)
    single {
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

    // ViewModels
    // HomeViewModel(connectivity, auth, storage, imageToDeleteDao, unifiedContentRepository,
    //   diaryRepository, insightRepository, savedStateHandle,
    //   calculateMoodDistributionUseCase, aggregateCognitivePatternsUseCase,
    //   calculateTopicsFrequencyUseCase, calculateTodayPulseUseCase, getAchievementsUseCase,
    //   feedRepository, threadHydrator, socialGraphRepository, profileSettingsRepository)
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // SnapshotViewModel(wellbeingRepository, auth)
    viewModel { SnapshotViewModel(get(), get()) }
}
