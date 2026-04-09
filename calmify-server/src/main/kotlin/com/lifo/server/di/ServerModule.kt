package com.lifo.server.di

import com.lifo.server.firebase.FirestoreClient
import com.lifo.server.service.*
import org.koin.dsl.module

val serverModule = module {
    // Firestore client (nullable — gracefully handles missing credentials)
    single { FirestoreClient.db }

    // Core services
    single { DiaryService(get()) }
    single { ChatService(get()) }
    single { InsightService(get()) }
    single { ProfileService(get()) }

    // Wellness services will be registered per-type when factory is wired
    // Social services (Phase 4)
    // Media services (Phase 2, continued)
    // Notification services (Phase 2, continued)
}
