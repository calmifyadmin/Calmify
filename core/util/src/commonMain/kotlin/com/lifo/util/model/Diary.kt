package com.lifo.util.model

import kotlinx.datetime.Clock

/**
 * Diary Model - Pure Kotlin (KMP)
 *
 * Firestore mapping handled by FirestoreMapper in data/mongo
 */
data class Diary(
    var _id: String = "",
    var ownerId: String = "",
    var mood: String = Mood.Neutral.name,
    var title: String = "",
    var description: String = "",
    var images: List<String> = emptyList(),
    var dateMillis: Long = Clock.System.now().toEpochMilliseconds(),
    var dayKey: String = "",
    var timezone: String = "",

    // Psychological Metrics
    var emotionIntensity: Int = 5,
    var stressLevel: Int = 5,
    var energyLevel: Int = 5,
    var calmAnxietyLevel: Int = 5,
    var primaryTrigger: String = Trigger.NONE.name,
    var dominantBodySensation: String = BodySensation.NONE.name
)

/**
 * Psychological Trigger Categories
 * Based on cognitive-behavioral therapy frameworks
 */
enum class Trigger(val displayName: String, val emoji: String) {
    NONE("Nessuno", "➖"),
    WORK("Lavoro", "💼"),
    FAMILY("Famiglia", "👨\u200D👩\u200D👧\u200D👦"),
    HEALTH("Salute", "🏥"),
    FINANCE("Finanze", "💰"),
    SOCIAL("Sociale", "👥"),
    SELF("Se stesso", "🪞"),
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
    FATIGUE("Affaticamento", "😮\u200D💨"),
    HEAVINESS("Pesantezza", "⚓"),
    AGITATION("Agitazione", "⚡"),
    RELAXATION("Rilassamento", "😌")
}
