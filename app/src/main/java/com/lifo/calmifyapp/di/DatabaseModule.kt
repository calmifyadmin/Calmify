package com.lifo.calmifyapp.di

import android.content.Context
import com.lifo.mongo.database.AppDatabase
import com.lifo.mongo.di.MongoDatabaseProvider
import com.lifo.util.connectivity.NetworkConnectivityObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return MongoDatabaseProvider.getDatabase(context)
    }

    @Singleton
    @Provides
    fun provideFirstDao(database: AppDatabase) = database.imageToUploadDao()

    @Singleton
    @Provides
    fun provideSecondDao(database: AppDatabase) = database.imageToDeleteDao()

    @Singleton
    @Provides
    fun provideNetworkConnectivityObserver(
        @ApplicationContext context: Context
    ) = NetworkConnectivityObserver(context = context)
}