package com.lifo.server.plugins

import com.lifo.server.routing.*
import com.lifo.server.service.GenericWellnessService
import com.lifo.shared.model.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    routing {
        // Infrastructure
        healthRoutes()

        // Core CRUD
        diaryRoutes()
        chatRoutes()
        insightRoutes()
        profileRoutes()

        // Social
        socialRoutes()

        // Aggregated + Config
        dashboardRoutes()
        notificationRoutes()

        // Wellness — 13 entity types, each gets full CRUD at /api/v1/wellness/{type}/
        wellnessRoutes()

        // AI — chat, insight, analysis, usage
        aiRoutes()

        // Sync — delta sync for offline-first
        syncRoutes()

        // Search — threads + users
        searchRoutes()

        // Presence — online/offline status
        presenceRoutes()

        // Content Moderation — Gemini-powered toxicity, sentiment, mood
        contentModerationRoutes()

        // Waitlist — PRO waitlist email signups
        waitlistRoutes()

        // Media — presigned GCS upload/read URLs
        mediaRoutes()

        // Messaging — REST + WebSocket fan-out
        messagingRoutes()

        // Stripe — checkout session + subscription state (authenticated)
        paymentRoutes()

        // Stripe webhook — signature-validated, NOT Firebase-authenticated
        stripeWebhookRoutes()

        // GDPR — data export + account deletion
        gdprRoutes()
    }
}

private fun Routing.wellnessRoutes() {
    val gratitude by inject<GenericWellnessService<GratitudeEntryProto>>(named("gratitude"))
    val energy by inject<GenericWellnessService<EnergyCheckInProto>>(named("energy"))
    val sleep by inject<GenericWellnessService<SleepLogProto>>(named("sleep"))
    val meditation by inject<GenericWellnessService<MeditationSessionProto>>(named("meditation"))
    val habits by inject<GenericWellnessService<HabitProto>>(named("habits"))
    val movement by inject<GenericWellnessService<MovementLogProto>>(named("movement"))
    val reframe by inject<GenericWellnessService<ThoughtReframeProto>>(named("reframe"))
    val wellbeing by inject<GenericWellnessService<WellbeingSnapshotProto>>(named("wellbeing"))
    val awe by inject<GenericWellnessService<AweEntryProto>>(named("awe"))
    val connection by inject<GenericWellnessService<ConnectionEntryProto>>(named("connection"))
    val recurring by inject<GenericWellnessService<RecurringThoughtProto>>(named("recurring"))
    val block by inject<GenericWellnessService<BlockProto>>(named("block"))
    val values by inject<GenericWellnessService<ValuesDiscoveryProto>>(named("values"))
    val db by inject<com.google.cloud.firestore.Firestore>()

    route("/api/v1/wellness") {
        route("/gratitude") { wellnessCrudRoutes(gratitude) }
        route("/energy") { wellnessCrudRoutes(energy) }
        route("/sleep") { wellnessCrudRoutes(sleep) }
        route("/meditation") { wellnessCrudRoutes(meditation) }
        route("/habits") {
            wellnessCrudRoutes(habits)
            habitCompletionRoutes(habits, db)
        }
        route("/movement") { wellnessCrudRoutes(movement) }
        route("/reframe") { wellnessCrudRoutes(reframe) }
        route("/wellbeing") { wellnessCrudRoutes(wellbeing) }
        route("/awe") { wellnessCrudRoutes(awe) }
        route("/connection") { wellnessCrudRoutes(connection) }
        route("/recurring") { wellnessCrudRoutes(recurring) }
        route("/block") { wellnessCrudRoutes(block) }
        route("/values") { wellnessCrudRoutes(values) }
    }
}
