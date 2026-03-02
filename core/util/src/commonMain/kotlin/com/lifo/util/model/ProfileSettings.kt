package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ProfileSettings Model - Pure Kotlin (KMP)
 *
 * Firestore mapping handled by FirestoreMapper in data/mongo
 */
data class ProfileSettings(
    var id: String = "",
    var ownerId: String = "",

    var createdAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
    var updatedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),

    var isOnboardingCompleted: Boolean = false,

    // Personal Information
    var fullName: String = "",
    var dateOfBirth: String = "",
    var gender: String = Gender.PREFER_NOT_TO_SAY.name,
    var height: Int = 0,
    var weight: Float = 0f,
    var location: String = "",

    // Psychological Profile
    var primaryConcerns: List<String> = emptyList(),
    var mentalHealthHistory: String = MentalHealthHistory.NO_DIAGNOSIS.name,
    var currentTherapy: Boolean = false,
    var medication: Boolean = false,

    // Lifestyle
    var occupation: String = "",
    var sleepHoursAvg: Float = 7f,
    var exerciseFrequency: String = ExerciseFrequency.MODERATE.name,
    var socialSupport: String = SocialSupport.MODERATE.name,

    // Wellness Goals
    var primaryGoals: List<String> = emptyList(),
    var preferredCopingStrategies: List<String> = emptyList(),

    // Privacy Settings
    var shareDataForResearch: Boolean = false,
    var enableAdvancedInsights: Boolean = true
) {
    fun getAge(): Int? {
        if (dateOfBirth.isBlank()) return null

        return try {
            val birthYear = dateOfBirth.split("-")[0].toInt()
            val currentYear = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .year
            currentYear - birthYear
        } catch (e: Exception) {
            null
        }
    }

    fun getBMI(): Float? {
        if (height == 0 || weight == 0f) return null
        val heightInMeters = height / 100f
        return weight / (heightInMeters * heightInMeters)
    }
}

enum class Gender(val displayName: String) {
    MALE("Male"),
    FEMALE("Female"),
    NON_BINARY("Non-binary"),
    PREFER_NOT_TO_SAY("Prefer not to say")
}

enum class MentalHealthHistory(val displayName: String) {
    NO_DIAGNOSIS("No diagnosis"),
    ANXIETY_DISORDER("Anxiety disorder"),
    DEPRESSION("Depression"),
    BIPOLAR_DISORDER("Bipolar disorder"),
    PTSD("PTSD"),
    OCD("OCD"),
    OTHER("Other"),
    PREFER_NOT_TO_SAY("Prefer not to say")
}

enum class ExerciseFrequency(val displayName: String) {
    SEDENTARY("Sedentary (little or no exercise)"),
    LIGHT("Light (1-2 times/week)"),
    MODERATE("Moderate (3-4 times/week)"),
    ACTIVE("Active (5-6 times/week)"),
    VERY_ACTIVE("Very active (daily)")
}

enum class SocialSupport(val displayName: String) {
    NONE("No support system"),
    LIMITED("Limited support"),
    MODERATE("Moderate support"),
    STRONG("Strong support system")
}

object MentalHealthConcerns {
    val ALL = listOf(
        "Anxiety", "Stress", "Depression", "Sleep issues",
        "Relationship problems", "Work-life balance", "Self-esteem",
        "Trauma", "Grief", "Loneliness"
    )
}

object WellnessGoals {
    val ALL = listOf(
        "Reduce stress", "Improve mood", "Better sleep", "Increase energy",
        "Build resilience", "Improve relationships", "Find purpose",
        "Manage anxiety", "Process emotions", "Self-discovery"
    )
}

object CopingStrategies {
    val ALL = listOf(
        "Meditation", "Exercise", "Journaling", "Breathing exercises",
        "Therapy", "Talking to friends", "Creative activities",
        "Nature walks", "Reading", "Music"
    )
}
