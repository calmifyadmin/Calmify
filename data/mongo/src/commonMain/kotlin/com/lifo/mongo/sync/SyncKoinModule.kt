package com.lifo.mongo.sync

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.*
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for offline-first sync components.
 *
 * Provides:
 * - SyncEngine (background sync loop)
 * - SyncDiaryRepository (diary CRUD local-first)
 * - SyncChatRepository (chat CRUD local-first)
 * - 13 SyncWellnessRepository instances (one per wellness type)
 *
 * To switch from Firestore-direct to offline-first, replace `repositoryModule`
 * with `syncRepositoryModule` in your Koin setup.
 */
val syncModule = module {
    // SyncEngine — the core background sync processor
    single {
        SyncEngine(
            database = get(),
            connectivityObserver = get(),
            syncExecutor = get(),
        )
    }

    // Sync-aware diary repository
    single {
        val authProvider = get<AuthProvider>()
        SyncDiaryRepository(
            database = get(),
            syncEngine = get(),
            userId = { authProvider.currentUserId },
        )
    }

    // Sync-aware chat repository
    single {
        val authProvider = get<AuthProvider>()
        SyncChatRepository(
            database = get(),
            syncEngine = get(),
            userId = { authProvider.currentUserId },
        )
    }

    // 13 Sync-aware wellness repositories
    single(named("sync_gratitude")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.gratitude(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_energy")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.energy(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_sleep")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.sleep(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_meditation")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.meditation(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_habit")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.habit(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_movement")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.movement(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_reframe")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.reframe(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_wellbeing")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.wellbeing(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_awe")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.awe(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_connection")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.connection(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_recurring")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.recurringThought(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_block")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.block(get(), get(), userId = { authProvider.currentUserId })
    }
    single(named("sync_values")) {
        val authProvider = get<AuthProvider>()
        SyncWellnessFactory.values(get(), get(), userId = { authProvider.currentUserId })
    }
}
