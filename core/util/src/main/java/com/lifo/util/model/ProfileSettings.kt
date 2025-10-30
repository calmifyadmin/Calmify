package com.lifo.util.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.time.Instant
import java.util.Date

/**
 * ProfileSettings Model - User's personal and psychological profile
 *
 * Used for:
 * - Personalized AI insights
 * - Better psychological profiling
 * - Mandatory onboarding process
 *
 * Firestore collection: profile_settings
 */
data class ProfileSettings(
    @DocumentId
    var id: String = "",                        // userId (same as ownerId)
    var ownerId: String = "",                   // User ID

    @ServerTimestamp
    var createdAt: Date = Date.from(Instant.now()),
    @ServerTimestamp
    var updatedAt: Date = Date.from(Instant.now()),

    var isOnboardingCompleted: Boolean = false, // Mandatory onboarding flag

    // Personal Information
    var fullName: String = "",                  // Full name
    var dateOfBirth: String = "",               // YYYY-MM-DD format
    var gender: String = Gender.PREFER_NOT_TO_SAY.name,
    var height: Int = 0,                        // cm
    var weight: Float = 0f,                     // kg
    var location: String = "",                  // City/Country (optional)

    // Psychological Profile
    var primaryConcerns: List<String> = emptyList(), // ["anxiety", "stress", "depression", etc.]
    var mentalHealthHistory: String = MentalHealthHistory.NO_DIAGNOSIS.name,
    var currentTherapy: Boolean = false,        // Currently in therapy?
    var medication: Boolean = false,            // Taking medication?

    // Lifestyle
    var occupation: String = "",                // Job/Student/Retired/etc.
    var sleepHoursAvg: Float = 7f,             // Average hours of sleep
    var exerciseFrequency: String = ExerciseFrequency.MODERATE.name,
    var socialSupport: String = SocialSupport.MODERATE.name,

    // Wellness Goals
    var primaryGoals: List<String> = emptyList(), // ["reduce_stress", "improve_mood", "better_sleep"]
    var preferredCopingStrategies: List<String> = emptyList(), // ["meditation", "exercise", "journaling"]

    // Privacy Settings
    var shareDataForResearch: Boolean = false,  // Consent for anonymized research
    var enableAdvancedInsights: Boolean = true  // Enable detailed AI analysis
) {
    // No-arg constructor required by Firestore
    constructor() : this(
        id = "",
        ownerId = "",
        createdAt = Date.from(Instant.now()),
        updatedAt = Date.from(Instant.now()),
        isOnboardingCompleted = false,
        fullName = "",
        dateOfBirth = "",
        gender = Gender.PREFER_NOT_TO_SAY.name,
        height = 0,
        weight = 0f,
        location = "",
        primaryConcerns = emptyList(),
        mentalHealthHistory = MentalHealthHistory.NO_DIAGNOSIS.name,
        currentTherapy = false,
        medication = false,
        occupation = "",
        sleepHoursAvg = 7f,
        exerciseFrequency = ExerciseFrequency.MODERATE.name,
        socialSupport = SocialSupport.MODERATE.name,
        primaryGoals = emptyList(),
        preferredCopingStrategies = emptyList(),
        shareDataForResearch = false,
        enableAdvancedInsights = true
    )

    /**
     * Calculate age from date of birth
     */
    fun getAge(): Int? {
        if (dateOfBirth.isBlank()) return null

        return try {
            val birthYear = dateOfBirth.split("-")[0].toInt()
            val currentYear = java.time.Year.now().value
            currentYear - birthYear
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate BMI (Body Mass Index)
     */
    fun getBMI(): Float? {
        if (height == 0 || weight == 0f) return null
        val heightInMeters = height / 100f
        return weight / (heightInMeters * heightInMeters)
    }
}

/**
 * Gender options
 */
enum class Gender(val displayName: String) {
    MALE("Male"),
    FEMALE("Female"),
    NON_BINARY("Non-binary"),
    PREFER_NOT_TO_SAY("Prefer not to say")
}

/**
 * Mental health history
 */
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

/**
 * Exercise frequency
 */
enum class ExerciseFrequency(val displayName: String) {
    SEDENTARY("Sedentary (little or no exercise)"),
    LIGHT("Light (1-2 times/week)"),
    MODERATE("Moderate (3-4 times/week)"),
    ACTIVE("Active (5-6 times/week)"),
    VERY_ACTIVE("Very active (daily)")
}

/**
 * Social support level
 */
enum class SocialSupport(val displayName: String) {
    NONE("No support system"),
    LIMITED("Limited support"),
    MODERATE("Moderate support"),
    STRONG("Strong support system")
}

/**
 * Common mental health concerns
 */
object MentalHealthConcerns {
    val ALL = listOf(
        "Anxiety",
        "Stress",
        "Depression",
        "Sleep issues",
        "Relationship problems",
        "Work-life balance",
        "Self-esteem",
        "Trauma",
        "Grief",
        "Loneliness"
    )
}

/**
 * Wellness goals
 */
object WellnessGoals {
    val ALL = listOf(
        "Reduce stress",
        "Improve mood",
        "Better sleep",
        "Increase energy",
        "Build resilience",
        "Improve relationships",
        "Find purpose",
        "Manage anxiety",
        "Process emotions",
        "Self-discovery"
    )
}

/**
 * Coping strategies
 */
object CopingStrategies {
    val ALL = listOf(
        "Meditation",
        "Exercise",
        "Journaling",
        "Breathing exercises",
        "Therapy",
        "Talking to friends",
        "Creative activities",
        "Nature walks",
        "Reading",
        "Music"
    )
}
