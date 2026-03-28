package com.lifo.util.model

/**
 * A completed meditation or breathing session.
 */
data class MeditationSession(
    val id: String = "",
    val ownerId: String = "",
    val timestampMillis: Long = 0L,
    val type: MeditationType = MeditationType.TIMER,
    val breathingPattern: BreathingPattern? = null,
    val durationSeconds: Int = 300,
    val completedSeconds: Int = 0,
    val postNote: String = "",
)

enum class MeditationType(val displayName: String) {
    TIMER("Meditazione libera"),
    BREATHING("Respirazione guidata"),
    BODY_SCAN("Body Scan"),
}

enum class BreathingPattern(
    val displayName: String,
    val inhaleSeconds: Int,
    val holdInSeconds: Int,
    val exhaleSeconds: Int,
    val holdOutSeconds: Int,
) {
    BOX_BREATHING("Box Breathing", 4, 4, 4, 4),
    RELAXATION_478("4-7-8 Rilassamento", 4, 7, 8, 0),
    DIAPHRAGMATIC("Diaframmatica", 4, 0, 6, 0);

    val totalCycleSeconds: Int
        get() = inhaleSeconds + holdInSeconds + exhaleSeconds + holdOutSeconds
}
