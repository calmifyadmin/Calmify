package com.lifo.server.di

import com.lifo.server.ai.*
import com.lifo.server.firebase.FirestoreClient
import com.lifo.server.service.*
import com.lifo.shared.model.*
import org.koin.core.qualifier.named
import org.koin.dsl.module

val serverModule = module {
    // Firestore client
    single { FirestoreClient.db }

    // Core services
    single { DiaryService(get()) }
    single { ChatService(get()) }
    single { InsightService(get()) }
    single { ProfileService(get()) }
    single { SocialService(get()) }
    single { NotificationService(get()) }
    single { FeatureFlagService() }
    single { DashboardService(get(), get()) }

    // Wellness services — one per entity type
    single(named("gratitude")) { WellnessServiceFactory.gratitude(get()) }
    single(named("energy")) { WellnessServiceFactory.energy(get()) }
    single(named("sleep")) { WellnessServiceFactory.sleep(get()) }
    single(named("meditation")) { WellnessServiceFactory.meditation(get()) }
    single(named("habits")) { WellnessServiceFactory.habits(get()) }
    single(named("movement")) { WellnessServiceFactory.movement(get()) }
    single(named("reframe")) { WellnessServiceFactory.reframe(get()) }
    single(named("wellbeing")) { WellnessServiceFactory.wellbeing(get()) }
    single(named("awe")) { WellnessServiceFactory.awe(get()) }
    single(named("connection")) { WellnessServiceFactory.connection(get()) }
    single(named("recurring")) { WellnessServiceFactory.recurringThought(get()) }
    single(named("block")) { WellnessServiceFactory.block(get()) }
    single(named("values")) { WellnessServiceFactory.values(get()) }

    // Search, Presence, Content Moderation
    single { SearchService(get()) }
    single { PresenceService(get()) }
    single { ContentModerationService(get()) }

    // Media — GCS V4 signed URLs (no Firestore dep)
    single { MediaService() }

    // Messaging — REST + WebSocket fan-out
    single { MessagingService(get()) }
    single { MessagingHub() }

    // Avatar — server-mediated 2-stage pipeline
    single { AvatarService(get(), get()) }

    // Holistic growth — Environment / Garden / Ikigai + SocialGraph
    single { EnvironmentService(get()) }
    single { GardenService(get()) }
    single { IkigaiService(get()) }
    single { SocialGraphService(get()) }

    // Bio-Signal Integration (Phase 4, 2026-05-11) — server-side aggregates
    single { com.lifo.server.service.BioSignalService(get()) }

    // Bio-Signal AI narrative (Phase 8.3, 2026-05-17) — Gemini-backed weekly insight, 24h Firestore cache
    single { com.lifo.server.service.BioNarrativeService(db = get(), gemini = get()) }

    // Sync
    single { com.lifo.server.service.SyncService(get()) }

    // GDPR compliance
    single { GdprService(get()) }

    // Stripe — web-first subscription & payments
    single {
        val apiKey = System.getenv("STRIPE_SECRET_KEY")
        val isProduction = System.getenv("K_SERVICE") != null
        if (apiKey.isNullOrEmpty() && isProduction) {
            throw IllegalStateException("STRIPE_SECRET_KEY must be set in production")
        }
        StripeService(apiKey = apiKey ?: "", db = get())
    }

    // AI components
    single {
        val apiKey = System.getenv("GEMINI_API_KEY")
        val isProduction = System.getenv("K_SERVICE") != null
        if (apiKey.isNullOrEmpty() && isProduction) {
            throw IllegalStateException("GEMINI_API_KEY must be set in production")
        }
        GeminiClient(apiKey ?: "")
    }
    single { PromptRegistry(get()) }
    single { ModelRouter() }
    single { ResponseCache() }
    single { ContentFilter() }
    single { TokenTracker(get()) }
    single { AiOrchestrator(get(), get(), get(), get(), get(), get()) }
}
