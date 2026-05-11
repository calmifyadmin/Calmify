package com.lifo.mongo.di

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.mongo.repository.ChatRepositoryImpl
import com.lifo.mongo.repository.FirestoreDiaryRepository
import com.lifo.mongo.repository.FirestoreFeedRepository
import com.lifo.mongo.repository.FirestoreInsightRepository
import com.lifo.mongo.repository.FirestoreNotificationRepository
import com.lifo.mongo.repository.FirestoreProfileRepository
import com.lifo.mongo.repository.FirestoreProfileSettingsRepository
import com.lifo.mongo.repository.FirestoreSearchRepository
import com.lifo.mongo.repository.FirestoreSocialGraphRepository
import com.lifo.mongo.repository.FirestoreSocialMessagingRepository
import com.lifo.mongo.repository.FirestoreThreadRepository
import com.lifo.mongo.repository.FirestoreEnergyRepository
import com.lifo.mongo.repository.FirestoreGratitudeRepository
import com.lifo.mongo.repository.FirestoreHabitRepository
import com.lifo.mongo.repository.FirestoreReframeRepository
import com.lifo.mongo.repository.FirestoreSleepRepository
import com.lifo.mongo.repository.FirestoreMeditationRepository
import com.lifo.mongo.repository.FirestoreBlockRepository
import com.lifo.mongo.repository.FirestoreMovementRepository
import com.lifo.mongo.repository.FirestoreEnvironmentRepository
import com.lifo.mongo.repository.FirestoreRecurringThoughtRepository
import com.lifo.mongo.repository.FirestoreValuesRepository
import com.lifo.mongo.repository.FirestoreIkigaiRepository
import com.lifo.mongo.repository.FirestoreAweRepository
import com.lifo.mongo.repository.FirestoreConnectionRepository
import com.lifo.mongo.repository.FirestoreGardenRepository
import com.lifo.mongo.repository.FirestoreWellbeingRepository
import com.lifo.mongo.repository.FirestorePresenceRepository
import com.lifo.mongo.repository.FirebaseMediaUploadRepository
import com.lifo.mongo.repository.CloudFunctionsContentModerationRepository
import com.lifo.mongo.repository.FirestoreFeatureFlagRepository
import com.lifo.mongo.repository.WaitlistSubscriptionRepository
import com.lifo.mongo.repository.UnifiedContentRepositoryImpl
import com.lifo.util.repository.ChatRepository
import com.lifo.util.repository.FeedRepository
import com.lifo.util.repository.InsightRepository
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.NotificationRepository
import com.lifo.util.repository.ProfileRepository
import com.lifo.util.repository.ProfileSettingsRepository
import com.lifo.util.repository.SearchRepository
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.SocialMessagingRepository
import com.lifo.util.repository.ThreadRepository
import com.lifo.util.repository.UnifiedContentRepository
import com.lifo.util.repository.UserPresenceRepository
import com.lifo.util.repository.MediaUploadRepository
import com.lifo.util.repository.ContentModerationRepository
import com.lifo.util.repository.FeatureFlagRepository
import com.lifo.util.repository.SubscriptionRepository
import com.lifo.util.repository.ThreadHydrator
import com.lifo.mongo.repository.FirestoreThreadHydrator
import com.lifo.mongo.analytics.FirebaseAnalyticsTracker
import com.lifo.util.analytics.AnalyticsTracker
import com.lifo.util.auth.AuthProvider
import com.lifo.util.auth.FirebaseAuthProvider
import com.lifo.util.repository.AvatarRepository
import com.lifo.util.repository.WaitlistRepository
import com.lifo.util.repository.EnergyRepository
import com.lifo.util.repository.GratitudeRepository
import com.lifo.util.repository.HabitRepository
import com.lifo.util.repository.ReframeRepository
import com.lifo.util.repository.MeditationRepository
import com.lifo.util.repository.BlockRepository
import com.lifo.util.repository.MovementRepository
import com.lifo.util.repository.EnvironmentRepository
import com.lifo.util.repository.RecurringThoughtRepository
import com.lifo.util.repository.ValuesRepository
import com.lifo.util.repository.IkigaiRepository
import com.lifo.util.repository.AweRepository
import com.lifo.util.repository.ConnectionRepository
import com.lifo.util.repository.GardenRepository
import com.lifo.util.repository.SleepRepository
import com.lifo.util.repository.WellbeingRepository
import com.lifo.mongo.repository.FirestoreWaitlistRepository
import com.lifo.mongo.repository.FirebaseAvatarRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val firebaseModule = module {
    single<FirebaseAuth> { Firebase.auth }
    single<AuthProvider> { FirebaseAuthProvider(get()) }
    single<FirebaseAnalytics> { FirebaseAnalytics.getInstance(get()) }
    single<AnalyticsTracker> { FirebaseAnalyticsTracker(get()) }
    single<FirebaseStorage> { Firebase.storage }
    single<FirebaseFunctions> { Firebase.functions("europe-west1") }
    single<FirebaseFirestore> {
        val firestore = FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            "calmify-native"
        )
        firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(
                persistentCacheSettings {
                    setSizeBytes(100 * 1024 * 1024) // 100MB
                }
            )
            setLocalCacheSettings(
                memoryCacheSettings { }
            )
        }
        firestore
    }
}

val repositoryModule = module {
    // Repositories
    single<MongoRepository> { FirestoreDiaryRepository(get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get(), get()) }
    single<UnifiedContentRepository> { UnifiedContentRepositoryImpl(get(), get()) }
    single<WellbeingRepository> { FirestoreWellbeingRepository(get(), get()) }
    single<GratitudeRepository> { FirestoreGratitudeRepository(get(), get()) }
    single<EnergyRepository> { FirestoreEnergyRepository(get(), get()) }
    single<HabitRepository> { FirestoreHabitRepository(get(), get()) }
    single<SleepRepository> { FirestoreSleepRepository(get(), get()) }
    single<ReframeRepository> { FirestoreReframeRepository(get(), get()) }
    single<MeditationRepository> { FirestoreMeditationRepository(get()) }
    single<BlockRepository> { FirestoreBlockRepository(get()) }
    single<MovementRepository> { FirestoreMovementRepository(get()) }
    single<EnvironmentRepository> { FirestoreEnvironmentRepository(get()) }
    single<RecurringThoughtRepository> { FirestoreRecurringThoughtRepository(get()) }
    single<ValuesRepository> { FirestoreValuesRepository(get()) }
    single<IkigaiRepository> { FirestoreIkigaiRepository(get()) }
    single<AweRepository> { FirestoreAweRepository(get()) }
    single<ConnectionRepository> { FirestoreConnectionRepository(get()) }
    single<GardenRepository> { FirestoreGardenRepository(get()) }
    single<InsightRepository> { FirestoreInsightRepository(get(), get()) }
    single<ProfileRepository> { FirestoreProfileRepository(get(), get()) }
    single<ProfileSettingsRepository> { FirestoreProfileSettingsRepository(get(), get()) }

    // Social repositories (Wave 6F)
    single<ThreadRepository> { FirestoreThreadRepository(get(), get()) }
    single<FeedRepository> { FirestoreFeedRepository(get(), get(), get()) }
    single<SocialGraphRepository> { FirestoreSocialGraphRepository(get(), get()) }
    single<ThreadHydrator> { FirestoreThreadHydrator(get()) }
    single<SearchRepository> { FirestoreSearchRepository(get(), get()) }
    single<NotificationRepository> { FirestoreNotificationRepository(get(), get()) }
    single<SocialMessagingRepository> { FirestoreSocialMessagingRepository(get(), get()) }

    // Wave 8: Presence, Media, AI Moderation
    single<UserPresenceRepository> { FirestorePresenceRepository(get()) }
    single<MediaUploadRepository> { FirebaseMediaUploadRepository(get()) }
    single<ContentModerationRepository> { CloudFunctionsContentModerationRepository(get()) }

    // Feature Flags (Firestore-based, replaces Remote Config)
    single<FeatureFlagRepository> { FirestoreFeatureFlagRepository(get()) }

    // Default binding: waitlist mode. The network module overrides this with
    // KtorSubscriptionRepository when the PREMIUM_ENABLED flag is on.
    single<SubscriptionRepository> { WaitlistSubscriptionRepository() }

    // PRO Switch: Waitlist email capture
    single<WaitlistRepository> { FirestoreWaitlistRepository(get()) }

    // Avatar System (Wave 10)
    single<AvatarRepository> { FirebaseAvatarRepository(get(), get()) }
}

/**
 * Bio-Signal Integration (Phase 2, 2026-05-11) — Android Health Connect provider
 * + local SQLDelight-backed repository.
 *
 * Server-side syncPendingAggregates() is a no-op stub until Phase 4 wires the
 * Ktor REST client (see .claude/BIOSIGNAL_INTEGRATION_PLAN.md §6).
 *
 * Add to `allKoinModules` in `app/di/KoinModules.kt` to activate.
 */
val bioSignalModule = module {
    single<com.lifo.util.repository.HealthDataProvider> {
        com.lifo.mongo.biosignal.HealthConnectProvider(androidContext())
    }
    single<com.lifo.util.repository.BioSignalRepository> {
        com.lifo.mongo.biosignal.BioSignalRepositoryImpl(
            database = get(),
            authProvider = get(),
        )
    }
}
