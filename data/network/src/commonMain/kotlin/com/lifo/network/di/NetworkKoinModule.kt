package com.lifo.network.di

import com.lifo.network.KtorApiClient
import com.lifo.network.repository.*
import com.lifo.util.repository.*
import org.koin.dsl.module

/**
 * Koin module for REST-backed repositories.
 *
 * Provides [KtorApiClient] + all repository implementations that talk to the Ktor Server.
 * To activate: replace `repositoryModule` with `networkRepositoryModule` in KoinModules.kt.
 *
 * Server base URL comes from environment or defaults to localhost for dev.
 */
val networkModule = module {
    // API Client — single instance shared by all repositories
    single {
        KtorApiClient(
            authProvider = get(),
            baseUrl = ServerConfig.baseUrl,
        )
    }
}

/**
 * Repository bindings — REST-backed implementations.
 *
 * These replace the Firestore-backed bindings in `repositoryModule` from data/mongo.
 * The interfaces remain the same — only the implementation changes.
 */
val networkRepositoryModule = module {
    // Core
    single<MongoRepository> { KtorDiaryRepository(get()) }
    single<ChatRepository> { KtorChatRepository(get()) }
    single<InsightRepository> { KtorInsightRepository(get()) }
    single<ProfileRepository> { KtorProfileRepository(get()) }

    // Social
    single<ThreadRepository> { KtorThreadRepository(get()) }
    single<FeedRepository> { KtorFeedRepository(get()) }
    single<NotificationRepository> { KtorNotificationRepository(get()) }

    // Wellness
    single<HabitRepository> { KtorHabitRepository(get()) }
    single<GratitudeRepository> { KtorGratitudeRepository(get()) }

    // Config
    single<FeatureFlagRepository> { KtorFeatureFlagRepository(get()) }
}

/**
 * Server configuration — resolves base URL for the Ktor API server.
 */
object ServerConfig {
    val baseUrl: String
        get() = "https://calmify-server-europe-west1.a.run.app" // Cloud Run URL

    val devBaseUrl: String
        get() = "http://10.0.2.2:8080" // Android emulator → localhost
}
