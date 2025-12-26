package com.lifo.home.di

import com.lifo.home.domain.usecase.AggregateCognitivePatternsUseCase
import com.lifo.home.domain.usecase.CalculateMoodDistributionUseCase
import com.lifo.home.domain.usecase.CalculateStreaksUseCase
import com.lifo.home.domain.usecase.CalculateTodayPulseUseCase
import com.lifo.home.domain.usecase.CalculateTopicsFrequencyUseCase
import com.lifo.home.domain.usecase.GetAchievementsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * Home Use Case Module
 *
 * Provides use case dependencies for the Home feature
 * Scoped to ViewModel lifecycle
 */
@Module
@InstallIn(ViewModelComponent::class)
object HomeUseCaseModule {

    @Provides
    @ViewModelScoped
    fun provideCalculateMoodDistributionUseCase(): CalculateMoodDistributionUseCase {
        return CalculateMoodDistributionUseCase()
    }

    @Provides
    @ViewModelScoped
    fun provideAggregateCognitivePatternsUseCase(): AggregateCognitivePatternsUseCase {
        return AggregateCognitivePatternsUseCase()
    }

    @Provides
    @ViewModelScoped
    fun provideCalculateTopicsFrequencyUseCase(): CalculateTopicsFrequencyUseCase {
        return CalculateTopicsFrequencyUseCase()
    }

    @Provides
    @ViewModelScoped
    fun provideCalculateStreaksUseCase(): CalculateStreaksUseCase {
        return CalculateStreaksUseCase()
    }

    @Provides
    @ViewModelScoped
    fun provideCalculateTodayPulseUseCase(): CalculateTodayPulseUseCase {
        return CalculateTodayPulseUseCase()
    }

    @Provides
    @ViewModelScoped
    fun provideGetAchievementsUseCase(
        calculateStreaksUseCase: CalculateStreaksUseCase
    ): GetAchievementsUseCase {
        return GetAchievementsUseCase(calculateStreaksUseCase)
    }
}
