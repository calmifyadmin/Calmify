package com.lifo.calmifyapp.di

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.lifo.calmifyapp.connectivity.NetworkConnectivityObserver
import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.mongo.di.firebaseModule
import com.lifo.mongo.di.repositoryModule
import com.lifo.util.Constants.DATABASE_NAME
import com.lifo.util.connectivity.ConnectivityObserver
import com.lifo.chat.di.chatKoinModule
import com.lifo.home.di.homeKoinModule
import com.lifo.auth.di.authKoinModule
import com.lifo.write.di.writeKoinModule
import com.lifo.history.di.historyKoinModule
import com.lifo.insight.di.insightKoinModule
import com.lifo.profile.di.profileKoinModule
import com.lifo.settings.di.settingsKoinModule
import com.lifo.onboarding.di.onboardingKoinModule
import com.lifo.humanoid.di.humanoidKoinModule
import com.lifo.feed.di.feedKoinModule
import com.lifo.composer.di.composerKoinModule
import com.lifo.socialprofile.di.socialProfileKoinModule
import com.lifo.search.di.searchKoinModule
import com.lifo.notifications.di.notificationsKoinModule
import com.lifo.messaging.di.messagingKoinModule
import com.lifo.subscription.di.subscriptionKoinModule
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// Database module: SQLDelight database, queries, connectivity
val databaseModule = module {
    single<Application> { androidContext() as Application }
    single {
        val driver = AndroidSqliteDriver(
            schema = CalmifyDatabase.Schema,
            context = androidContext(),
            name = DATABASE_NAME
        )
        CalmifyDatabase(driver)
    }
    single { get<CalmifyDatabase>().imageToUploadQueries }
    single { get<CalmifyDatabase>().imageToDeleteQueries }
    single<ConnectivityObserver> { NetworkConnectivityObserver(androidContext()) }
}

// Network module: Ktor HttpClient (future, for social API calls)
val networkModule = module {
    // Will be populated when core-network module is created
}

// Social module: social feature use cases and ViewModels (repositories are in repositoryModule)
val socialModule = module {
    // Wave 6F: Repository implementations registered in repositoryModule (MongoKoinModule)
    // Wave 7+: Social feature ViewModels and use cases will go here
}

val allKoinModules = listOf(
    databaseModule,
    firebaseModule,
    repositoryModule,
    networkModule,
    socialModule,
    chatKoinModule,
    homeKoinModule,
    authKoinModule,
    writeKoinModule,
    historyKoinModule,
    insightKoinModule,
    profileKoinModule,
    settingsKoinModule,
    onboardingKoinModule,
    humanoidKoinModule,
    // Social Features (Wave 7)
    feedKoinModule,
    composerKoinModule,
    socialProfileKoinModule,
    searchKoinModule,
    notificationsKoinModule,
    // Social Features (Wave 8)
    messagingKoinModule,
    // Monetization (Wave 9)
    subscriptionKoinModule,
)
