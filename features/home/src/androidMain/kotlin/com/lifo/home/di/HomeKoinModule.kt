package com.lifo.home.di

import com.lifo.home.HomeViewModel
import com.lifo.home.SnapshotViewModel
import com.lifo.home.domain.usecase.AggregateCognitivePatternsUseCase
import com.lifo.home.domain.usecase.CalculateMoodDistributionUseCase
import com.lifo.home.domain.usecase.CalculateStreaksUseCase
import com.lifo.home.domain.usecase.CalculateTodayPulseUseCase
import com.lifo.home.domain.usecase.CalculateTopicsFrequencyUseCase
import com.lifo.home.domain.usecase.GetAchievementsUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeKoinModule = module {
    // Use cases — factory (new instance each time, equivalent to @ViewModelScoped)
    factory { CalculateMoodDistributionUseCase() }
    factory { AggregateCognitivePatternsUseCase() }
    factory { CalculateTopicsFrequencyUseCase() }
    factory { CalculateStreaksUseCase() }
    factory { CalculateTodayPulseUseCase() }
    factory { GetAchievementsUseCase(get()) }

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
