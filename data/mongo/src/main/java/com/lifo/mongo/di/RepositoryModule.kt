package com.lifo.mongo.di

import com.lifo.mongo.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository Module for Hilt dependency injection
 * Binds repository interfaces to their implementations
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileSettingsRepository(
        impl: FirestoreProfileSettingsRepository
    ): ProfileSettingsRepository
}
