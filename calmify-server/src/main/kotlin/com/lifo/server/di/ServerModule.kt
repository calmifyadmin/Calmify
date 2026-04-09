package com.lifo.server.di

import com.lifo.server.firebase.FirestoreClient
import org.koin.dsl.module

val serverModule = module {
    // Firestore client (nullable — gracefully handles missing credentials)
    single { FirestoreClient.db }

    // Services will be registered here as they are implemented:
    // single { DiaryService(get()) }
    // single { ChatService(get()) }
    // single { ProfileService(get()) }
    // single { WellnessService(get()) }
    // single { SocialService(get()) }
    // single { InsightService(get()) }
    // single { MediaService(get()) }
    // single { NotificationService(get()) }
    // single { FeedService(get()) }
    // single { SearchService(get()) }
    // single { ModerationService(get()) }
}
