package com.lifo.mongo.di

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
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
import com.lifo.mongo.repository.FirestoreWellbeingRepository
import com.lifo.mongo.repository.FirebasePresenceRepository
import com.lifo.mongo.repository.FirebaseMediaUploadRepository
import com.lifo.mongo.repository.CloudFunctionsContentModerationRepository
import com.lifo.mongo.repository.FirebaseFeatureFlagRepository
import com.lifo.mongo.repository.PlayBillingSubscriptionRepository
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
import com.lifo.util.auth.AuthProvider
import com.lifo.util.auth.FirebaseAuthProvider
import com.lifo.util.repository.WellbeingRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val firebaseModule = module {
    single<FirebaseAuth> { Firebase.auth }
    single<AuthProvider> { FirebaseAuthProvider(get()) }
    single<FirebaseStorage> { Firebase.storage }
    single<FirebaseDatabase> { Firebase.database }
    single<FirebaseFunctions> { Firebase.functions }
    single<FirebaseRemoteConfig> {
        val config = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // 1 hour in production
            .build()
        config.setConfigSettingsAsync(settings)
        config.setDefaultsAsync(mapOf(
            "social_enabled" to false,
            "feed_enabled" to false,
            "messaging_enabled" to false,
            "federation_enabled" to false,
            "semantic_search_enabled" to false,
            "media_pipeline_enabled" to false,
            "premium_enabled" to false,
            "ab_test_new_home" to false,
            "max_free_messages_per_day" to 50L,
            "maintenance_mode" to false,
        ))
        config
    }
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
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get()) }
    single<UnifiedContentRepository> { UnifiedContentRepositoryImpl(get(), get()) }
    single<WellbeingRepository> { FirestoreWellbeingRepository(get(), get()) }
    single<InsightRepository> { FirestoreInsightRepository(get(), get()) }
    single<ProfileRepository> { FirestoreProfileRepository(get(), get()) }
    single<ProfileSettingsRepository> { FirestoreProfileSettingsRepository(get(), get()) }

    // Social repositories (Wave 6F)
    single<ThreadRepository> { FirestoreThreadRepository(get(), get()) }
    single<FeedRepository> { FirestoreFeedRepository(get(), get()) }
    single<SocialGraphRepository> { FirestoreSocialGraphRepository(get(), get()) }
    single<SearchRepository> { FirestoreSearchRepository(get(), get()) }
    single<NotificationRepository> { FirestoreNotificationRepository(get(), get()) }
    single<SocialMessagingRepository> { FirestoreSocialMessagingRepository(get(), get()) }

    // Wave 8: Presence, Media, AI Moderation
    single<UserPresenceRepository> { FirebasePresenceRepository(get()) }
    single<MediaUploadRepository> { FirebaseMediaUploadRepository(get()) }
    single<ContentModerationRepository> { CloudFunctionsContentModerationRepository(get()) }

    // Wave 9D: Feature Flags
    single<FeatureFlagRepository> { FirebaseFeatureFlagRepository(get()) }

    // Wave 9C: Subscription/Billing
    single<SubscriptionRepository> { PlayBillingSubscriptionRepository(androidContext(), get()) }
}
