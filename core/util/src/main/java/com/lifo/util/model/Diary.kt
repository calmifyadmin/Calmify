package com.lifo.util.model

import com.google.firebase.Timestamp as FirebaseTimestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.time.Instant
import java.util.Date

/**
 * Diary Model - Firestore Compatible with Psychological Insights
 *
 * Migrato da Realm a Firestore (2025 Stack)
 * Extended with psychological metrics (Week 1 - PSYCHOLOGICAL_INSIGHTS_PLAN.md)
 */
data class Diary(
    @DocumentId
    var _id: String = "",
    var ownerId: String = "",
    var mood: String = Mood.Neutral.name,
    var title: String = "",
    var description: String = "",
    var images: List<String> = emptyList(),
    @ServerTimestamp
    var date: Date = Date.from(Instant.now()),

    // ✨ NEW: Psychological Metrics (10-second input)
    // Backward compatible with defaults for existing entries
    var emotionIntensity: Int = 5,          // 0-10 (how strongly felt)
    var stressLevel: Int = 5,               // 0-10 (0=none, 10=extreme)
    var energyLevel: Int = 5,               // 0-10 (0=exhausted, 10=energized)
    var calmAnxietyLevel: Int = 5,          // 0-10 (0=anxious, 10=calm)
    var primaryTrigger: String = Trigger.NONE.name,
    var dominantBodySensation: String = BodySensation.NONE.name
) {
    // No-arg constructor richiesto da Firestore
    constructor() : this(
        _id = "",
        ownerId = "",
        mood = Mood.Neutral.name,
        title = "",
        description = "",
        images = emptyList(),
        date = Date.from(Instant.now()),
        emotionIntensity = 5,
        stressLevel = 5,
        energyLevel = 5,
        calmAnxietyLevel = 5,
        primaryTrigger = Trigger.NONE.name,
        dominantBodySensation = BodySensation.NONE.name
    )

    companion object {
        fun fromTimestamp(timestamp: FirebaseTimestamp): Instant {
            return Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())
        }
    }
}

/**
 * Psychological Trigger Categories
 * Based on cognitive-behavioral therapy frameworks
 */
enum class Trigger(val displayName: String, val emoji: String) {
    NONE("Nessuno", "➖"),
    WORK("Lavoro", "💼"),
    FAMILY("Famiglia", "👨‍👩‍👧‍👦"),
    HEALTH("Salute", "🏥"),
    FINANCE("Finanze", "💰"),
    SOCIAL("Sociale", "👥"),
    SELF("Sé stesso", "🪞"),
    OTHER("Altro", "📝")
}

/**
 * Body Sensation Categories
 * Based on somatic experiencing and body-awareness practices
 */
enum class BodySensation(val displayName: String, val emoji: String) {
    NONE("Nessuna", "➖"),
    TENSION("Tensione", "😬"),
    LIGHTNESS("Leggerezza", "🪶"),
    FATIGUE("Affaticamento", "😮‍💨"),
    HEAVINESS("Pesantezza", "⚓"),
    AGITATION("Agitazione", "⚡"),
    RELAXATION("Rilassamento", "😌")
}
