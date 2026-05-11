package com.lifo.calmifyapp.di

import android.app.Application
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.lifo.calmifyapp.connectivity.NetworkConnectivityObserver
import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.mongo.di.bioSignalModule
import com.lifo.mongo.di.firebaseModule
import com.lifo.mongo.di.repositoryModule
import com.lifo.mongo.repository.WaitlistSubscriptionRepository
import com.lifo.mongo.sync.SyncEngine
import com.lifo.mongo.sync.syncModule
import com.lifo.network.KtorApiClient
import com.lifo.network.di.networkModule as ktorNetworkModule
import com.lifo.network.repository.*
import com.lifo.util.Constants.DATABASE_NAME
import com.lifo.util.connectivity.ConnectivityObserver
import com.lifo.util.repository.*
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
import com.lifo.threaddetail.di.threadDetailKoinModule
import com.lifo.avatarcreator.di.avatarCreatorKoinModule
import com.lifo.biocontext.di.bioContextKoinModule
import com.lifo.habits.di.habitKoinModule
import com.lifo.meditation.di.meditationKoinModule
import com.lifo.ui.onboarding.OnboardingManager
import com.lifo.ui.onboarding.TutorialStorage
import com.lifo.ui.onboarding.TutorialStorageImpl
import com.lifo.util.tutorial.OnboardingManager as TutorialOnboardingManager
import com.lifo.util.tutorial.SharedPrefsOnboardingManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Backend mode — controls which implementation backs each repository.
 *
 * FIRESTORE: Direct Firestore (current, client-side rules)
 * REST:      Ktor Server REST API (server-mediated, zero creds in APK)
 *
 * Flip individual flags to migrate one domain at a time.
 * When all are REST, remove repositoryModule entirely.
 */
object BackendConfig {
    // ── Per-domain backend mode ──────────────────────────────────────
    // Flip to `true` to route through Ktor Server instead of Firestore

    /** Diary CRUD (write, home, history screens) */
    const val DIARY_REST = true

    /** Chat sessions + messages + AI responses */
    const val CHAT_REST = true

    /** Diary insights (sentiment, cognitive patterns) */
    const val INSIGHT_REST = true

    /** Psychological profiles */
    const val PROFILE_REST = true

    /** Social: threads, feed, notifications */
    const val SOCIAL_REST = true

    /** Habits + gratitude */
    const val WELLNESS_REST = true

    /** Feature flags */
    const val FLAGS_REST = true

    /** Media uploads via presigned GCS URLs (Phase 3) */
    const val MEDIA_REST = true

    /** Direct messaging via REST + WebSocket fan-out (Phase 3.2) */
    const val MESSAGING_REST = true

    /** Avatar pipeline via server-mediated REST + polling (Phase 4) */
    const val AVATAR_REST = true

    /** Holistic growth — Environment / Garden / Ikigai (Phase 5) */
    const val HOLISTIC_REST = true

    /** SocialGraph — follow/block + public profile (Phase 5) */
    const val SOCIAL_GRAPH_REST = true
}

// Database module: SQLDelight database, queries, connectivity
val databaseModule = module {
    single<Application> { androidContext() as Application }
    single {
        val driver = AndroidSqliteDriver(
            schema = CalmifyDatabase.Schema,
            context = androidContext(),
            name = DATABASE_NAME,
            callback = AndroidSqliteDriver.Callback(
                schema = CalmifyDatabase.Schema,
                AfterVersion(1) { /* v1->v2: cached_threads table added via schema */ },
            ),
        )
        CalmifyDatabase(driver)
    }
    single { get<CalmifyDatabase>().imageToUploadQueries }
    single { get<CalmifyDatabase>().imageToDeleteQueries }
    single { get<CalmifyDatabase>().cachedThreadQueries }
    single<ConnectivityObserver> { NetworkConnectivityObserver(androidContext()) }
    // SyncEngine's expect/actual ConnectivityObserver (uses Android ConnectivityManager)
    single { com.lifo.util.sync.ConnectivityObserver(androidContext()) }
    single<TutorialOnboardingManager> { SharedPrefsOnboardingManager(androidContext()) }
}

// OnboardingManager + TutorialStorage singleton
val onboardingUiModule = module {
    single<TutorialStorage> { TutorialStorageImpl(androidContext()) }
    single { OnboardingManager(get()) }
}

/**
 * REST override module — selectively replaces Firestore repos with REST-backed ones.
 *
 * Only registers bindings for domains where BackendConfig flag is true.
 * These override the earlier Firestore bindings because Koin uses last-wins.
 */
val restOverrideModule = module {
    if (BackendConfig.DIARY_REST) {
        single<MongoRepository> { KtorDiaryRepository(get()) }
        single<UnifiedContentRepository> { KtorUnifiedContentRepository(get()) }
    }
    if (BackendConfig.CHAT_REST) {
        single<ChatRepository> { KtorChatRepository(get()) }
    }
    if (BackendConfig.INSIGHT_REST) {
        single<InsightRepository> { KtorInsightRepository(get()) }
    }
    if (BackendConfig.PROFILE_REST) {
        single<ProfileRepository> { KtorProfileRepository(get()) }
        single<ProfileSettingsRepository> { KtorProfileSettingsRepository(get()) }
    }
    if (BackendConfig.SOCIAL_REST) {
        single<ThreadRepository> { KtorThreadRepository(get()) }
        single<FeedRepository> { KtorFeedRepository(get()) }
        single<NotificationRepository> { KtorNotificationRepository(get()) }
        single<ThreadHydrator> { KtorThreadHydrator(get()) }
        single<WaitlistRepository> { KtorWaitlistRepository(get()) }
        single<SearchRepository> { KtorSearchRepository(get()) }
        single<UserPresenceRepository> { KtorUserPresenceRepository(get()) }
        single<ContentModerationRepository> { KtorContentModerationRepository(get()) }
    }
    if (BackendConfig.WELLNESS_REST) {
        single<HabitRepository> { KtorHabitRepository(get()) }
        single<GratitudeRepository> { KtorGratitudeRepository(get()) }
        single<EnergyRepository> { KtorEnergyRepository(get()) }
        single<SleepRepository> { KtorSleepRepository(get()) }
        single<MeditationRepository> { KtorMeditationRepository(get()) }
        single<ReframeRepository> { KtorReframeRepository(get()) }
        single<MovementRepository> { KtorMovementRepository(get()) }
        single<ValuesRepository> { KtorValuesRepository(get()) }
        single<ConnectionRepository> { KtorConnectionRepository(get()) }
        single<AweRepository> { KtorAweRepository(get()) }
        single<BlockRepository> { KtorBlockRepository(get()) }
        single<RecurringThoughtRepository> { KtorRecurringThoughtRepository(get()) }
        single<WellbeingRepository> { KtorWellbeingRepository(get()) }
    }
    if (BackendConfig.FLAGS_REST) {
        single<FeatureFlagRepository> { KtorFeatureFlagRepository(get()) }
    }
    if (BackendConfig.MEDIA_REST) {
        single<MediaUploadRepository> { KtorMediaUploadRepository(get()) }
    }
    if (BackendConfig.MESSAGING_REST) {
        single<SocialMessagingRepository> { KtorSocialMessagingRepository(get()) }
    }
    if (BackendConfig.AVATAR_REST) {
        single<AvatarRepository> { KtorAvatarRepository(get()) }
    }
    if (BackendConfig.HOLISTIC_REST) {
        single<EnvironmentRepository> { KtorEnvironmentRepository(get()) }
        single<GardenRepository> { KtorGardenRepository(get()) }
        single<IkigaiRepository> { KtorIkigaiRepository(get()) }
    }
    if (BackendConfig.SOCIAL_GRAPH_REST) {
        single<SocialGraphRepository> { KtorSocialGraphRepository(get()) }
    }

    // Stripe subscription: runtime-gated by Remote Config `premium_enabled`.
    // When true  → KtorSubscriptionRepository (live Stripe hosted checkout).
    // When false → WaitlistSubscriptionRepository (wishlist UI, no checkout).
    single<SubscriptionRepository> {
        val flags = get<FeatureFlagRepository>()
        if (flags.getBoolean(FeatureFlagRepository.PREMIUM_ENABLED)) {
            KtorSubscriptionRepository(get())
        } else {
            WaitlistSubscriptionRepository()
        }
    }
}

// Social module: social feature use cases and ViewModels (repositories are in repositoryModule)
val socialModule = module {
    // Wave 6F: Repository implementations registered in repositoryModule (MongoKoinModule)
    // Wave 7+: Social feature ViewModels and use cases will go here
}

val allKoinModules = listOf(
    databaseModule,
    onboardingUiModule,
    firebaseModule,
    repositoryModule,           // Firestore-backed (base layer)
    bioSignalModule,            // Bio-Signal Phase 2 (Health Connect + local SQLDelight)
    ktorNetworkModule,          // KtorApiClient + SyncExecutor
    syncModule,                 // SyncEngine + DeltaApplier
    restOverrideModule,         // REST overrides (last-wins, only active flags)
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
    threadDetailKoinModule,
    // Monetization (Wave 9)
    subscriptionKoinModule,
    // Avatar System (Wave 10)
    avatarCreatorKoinModule,
    // Holistic Growth (Sprint 1+2)
    habitKoinModule,
    meditationKoinModule,
    // Bio-Signal Integration (Phase 2.UI, 2026-05-11)
    bioContextKoinModule,
)
